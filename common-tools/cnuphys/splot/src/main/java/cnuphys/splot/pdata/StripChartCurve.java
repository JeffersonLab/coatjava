package cnuphys.splot.pdata;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.Evaluator;
import cnuphys.splot.fit.FitResult;
import cnuphys.splot.fit.IFitter;
import cnuphys.splot.spline.CubicSpline;

/**
 * Backing data model for a strip chart / time-series plot.
 *
 * <p>
 * This class periodically samples a provided {@link Evaluator} and appends
 * (t, y) points into bounded X and Y columns. When capacity is reached, the
 * oldest samples are dropped ("scrolls off the left").
 * </p>
 *
 * <h2>Threading</h2>
 * <p>
 * Sampling runs on a background scheduler thread. To preserve the {@link ACurve}
 * contract that notifications occur on the Swing Event Dispatch Thread (EDT),
 * this class uses a lock-free staging queue:
 * </p>
 * <ul>
 *   <li>The background sampler thread computes samples and {@linkplain #enqueueSample(double, double) enqueues} them.</li>
 *   <li>An EDT drain pass applies samples in bounded batches under {@link #lock}.</li>
 *   <li>A single consolidated {@link #markDataChanged()} is fired per batch (EDT-only).</li>
 * </ul>
 *
 * <p>
 * The optional {@link #setOnSample(Runnable)} callback, if provided, is invoked on the EDT after
 * each drain pass. This is a convenient place to call {@code repaint()} safely.
 * </p>
 */
public class StripChartCurve extends ACurve {

	/** Display unit for the time axis (does not affect internal storage). */
	private volatile TimeUnit timeUnit = TimeUnit.MILLISECONDS;

	/** Maximum number of samples retained. Must be >= 2. */
	private int capacity;

	/** Produces the next value given time in ms since start. */
	private final Evaluator accumulator;

	/** Sampling period in milliseconds. Must be > 0. */
	private long intervalMs;

	/** Time when sampling started (ms since epoch). */
	private volatile long startTimeMs;

	/** True while actively sampling. */
	private volatile boolean running;

	/** Optional callback invoked after a drain pass (EDT). */
	private volatile Runnable onSample;

	/** Series data columns. Mutated under {@link #lock} on the EDT. */
	private final DataColumn xData;
	private final DataColumn yData;

	/** Scheduler for periodic sampling. */
	private final ScheduledExecutorService scheduler;
	private ScheduledFuture<?> future;

	// --------------------------------------------------------------------
	// Thread-safety infrastructure (mirrors Curve/HistoCurve pattern)
	// --------------------------------------------------------------------

	/** Pending samples enqueued from the scheduler/background thread. */
	private final PendingQueue<PendingSample> pending = new PendingQueue<>();

	/** Coalesces EDT drain scheduling. */
	private final AtomicBoolean drainScheduled = new AtomicBoolean(false);

	/** Default max samples applied per EDT drain pass (keeps EDT responsive). */
	private static final int DEFAULT_DRAIN_MAX = 10_000;

	/**
	 * Create strip-chart data.
	 *
	 * @param name        series name
	 * @param capacity    max number of retained samples (>= 2)
	 * @param accumulator value source; called as {@code accumulator.value(tMs)} (non-null)
	 * @param intervalMs  update interval in milliseconds (> 0)
	 */
	public StripChartCurve(String name, int capacity, Evaluator accumulator, long intervalMs) {
		super(name);
		this.accumulator = Objects.requireNonNull(accumulator, "accumulator");
		xData = new DataColumn();
		yData = new DataColumn();

		// Stair-step drawing is typical for strip charts.
		setCurveMethod(CurveDrawingMethod.STAIRS);
		setCapacity(capacity);
		setIntervalMs(intervalMs);

		// Dedicated single-thread scheduler, daemon thread so app can exit cleanly.
		this.scheduler = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory("StripData-" + name));
	}

	/** @return current capacity (max samples retained). */
	public int getCapacity() {
		return capacity;
	}

	/**
	 * Set the capacity (max samples retained). If the current size exceeds the new
	 * capacity, oldest samples are dropped immediately.
	 *
	 * <p>
	 * If called off the EDT, this method schedules the capacity enforcement on the EDT
	 * to keep column mutation and notifications Swing-correct.
	 * </p>
	 *
	 * @param capacity must be >= 2
	 */
	public void setCapacity(int capacity) {
		if (capacity < 2) {
			throw new IllegalArgumentException("capacity must be >= 2");
		}
		this.capacity = capacity;

		// Enforce immediately on EDT
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(() -> {
				synchronized (lock) {
					trimToCapacityLocked();
				}
				markDataChanged();
			});
			return;
		}

		synchronized (lock) {
			trimToCapacityLocked();
		}
		markDataChanged();
	}

	/** @return sampling interval in milliseconds. */
	public long getIntervalMs() {
		return intervalMs;
	}

	/** @return the current display unit for time on the x-axis. */
	public TimeUnit getTimeUnit() {
		return timeUnit;
	}

	/**
	 * Set the display unit for the time axis.
	 * <p>
	 * Internal storage remains milliseconds; snapshot/min/max are scaled.
	 * This triggers a data change notification so axes/ticks update.
	 * </p>
	 *
	 * @param unit non-null
	 */
	public void setTimeUnit(TimeUnit unit) {
		Objects.requireNonNull(unit, "unit");
		this.timeUnit = unit;

		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(() -> {
				markDataChanged();
				runOnSampleCallbackEdt();
			});
			return;
		}

		markDataChanged();
		runOnSampleCallbackEdt();
	}

	/** Scale internal ms values into the current display unit. */
	private double scaleTime(double tms) {
		// milliseconds per 1 unit (e.g., SECONDS -> 1000)
		final double denom = (double) timeUnit.toMillis(1);
		return tms / denom;
	}

	/** A short label you can use in axis titles ("Time (s)", etc.). */
	public String getTimeUnitShortLabel() {
		switch (timeUnit) {
		case NANOSECONDS:  return "ns";
		case MICROSECONDS: return "µs";
		case MILLISECONDS: return "ms";
		case SECONDS:      return "s";
		case MINUTES:      return "min";
		case HOURS:        return "hr";
		case DAYS:         return "day";
		default:           return timeUnit.toString().toLowerCase();
		}
	}
	/**
	 * Set the sampling interval. If currently running, restarts the schedule.
	 *
	 * @param intervalMs must be > 0
	 */
	public void setIntervalMs(long intervalMs) {
		if (intervalMs <= 0) {
			throw new IllegalArgumentException("intervalMs must be > 0");
		}
		this.intervalMs = intervalMs;

		// If running, restart to apply the new period.
		if (running) {
			stop();
			start();
		}
	}

	/**
	 * Set a callback to be invoked after each drain pass on the EDT.
	 * <p>
	 * This is a convenient place to call {@code repaint()} without additional
	 * {@code SwingUtilities.invokeLater(...)}.
	 * </p>
	 *
	 * @param onSample may be null
	 */
	public void setOnSample(Runnable onSample) {
		this.onSample = onSample;
	}

	/** @return true if actively sampling. */
	public boolean isRunning() {
		return running;
	}

	/**
	 * Start sampling. Safe to call multiple times; subsequent calls do nothing if
	 * already running.
	 */
	public void start() {
		if (running) {
			return;
		}
		running = true;
		startTimeMs = System.currentTimeMillis();

		future = scheduler.scheduleAtFixedRate(this::addSample, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
	}

	/**
	 * Stop sampling. Safe to call multiple times.
	 *
	 * <p>
	 * This cancels the scheduled task but keeps the scheduler alive so you can
	 * {@link #start()} again.
	 * </p>
	 */
	public void stop() {
		running = false;
		if (future != null) {
			future.cancel(false);
			future = null;
		}
	}

	/**
	 * Permanently shut down this StripChartCurve (cannot be restarted). Use when the
	 * owning plot is being disposed.
	 */
	public void shutdown() {
		stop();
		scheduler.shutdownNow();
	}

	/**
	 * Clear all retained samples.
	 * <p>
	 * Thread-safe: if called off-EDT, schedules the clear on the EDT. Pending queued
	 * samples are also discarded so the curve becomes truly empty.
	 * </p>
	 */
	public void clear() {
		if (!SwingUtilities.isEventDispatchThread()) {
			pending.clear();
			SwingUtilities.invokeLater(this::clear);
			return;
		}

		pending.clear();
		synchronized (lock) {
			xData.clear();
			yData.clear();
		}
		markDataChanged();
		runOnSampleCallbackEdt();
	}

	/**
	 * Obtain a consistent snapshot of the current data, suitable for plotting
	 * without locking.
	 *
	 * @return snapshot containing primitive arrays of x and y data. Those arrays
	 *         are copies of the internal data at the moment of the snapshot.
	 */
	@Override
	public Snapshot snapshot() {
		synchronized (lock) {
			double[] xs = xData.values();  // internal ms
			double[] ys = yData.values();

			// scale x into selected display unit
			for (int i = 0; i < xs.length; i++) {
				xs[i] = scaleTime(xs[i]);
			}

			return new Snapshot(xs, ys, null);
		}
	}

	/**
	 * Add an explicit sample (t, y) to this strip chart.
	 * <p>
	 * Thread-safe: may be called from any thread. Off-EDT calls enqueue and schedule
	 * a coalesced EDT drain. Notifications remain EDT-only per {@link ACurve}.
	 * </p>
	 *
	 * @param tms time value (typically ms since start)
	 * @param y   sample value
	 */
	public void add(double tms, double y) {
		if (!SwingUtilities.isEventDispatchThread()) {
			enqueueSample(tms, y);
			scheduleDrain();
			return;
		}

		synchronized (lock) {
			trimOneIfFullLocked();
			xData.add(tms);
			yData.add(y);
		}
		markDataChanged();
		runOnSampleCallbackEdt();
	}

	// ------------------------ Internals ------------------------

	/**
	 * Sampler task run by the scheduler thread.
	 * <p>
	 * Computes the sample and enqueues it; the data columns are mutated later on the EDT.
	 * </p>
	 */
	private void addSample() {
		try {
			if (!running) {
				return;
			}

			// elapsed time in ms since start
			double tms = (System.currentTimeMillis() - startTimeMs);

			// get the next value
			double y = accumulator.value(tms);

			// enqueue for EDT application
			enqueueSample(tms, y);

			// schedule a coalesced EDT drain pass
			scheduleDrain();

		} catch (Throwable t) {
			// Fail soft: stop sampling on unexpected exceptions to avoid runaway logs.
			stop();
		}
	}

	/** Enqueue a sample from any thread (lock-free). */
	private void enqueueSample(double tms, double y) {
		pending.enqueue(new PendingSample(tms, y));
	}

	/**
	 * Schedule a coalesced drain pass on the EDT.
	 * <p>
	 * If many background samples arrive, they will be applied in batches. This prevents
	 * repaint/event storms and preserves Swing EDT discipline.
	 * </p>
	 */
	private void scheduleDrain() {
		if (drainScheduled.compareAndSet(false, true)) {
			SwingUtilities.invokeLater(() -> {
				drainScheduled.set(false);

				int drained = drainPendingOnEDT(DEFAULT_DRAIN_MAX);

				// If we hit the cap and backlog remains, schedule another pass.
				if (drained >= DEFAULT_DRAIN_MAX && pending.getPendingCount() > 0) {
					scheduleDrain();
				}
			});
		}
	}

	/**
	 * Drain pending samples on the EDT, apply them, and fire a single consolidated DATA event.
	 *
	 * @param max maximum samples to apply this pass
	 * @return number drained
	 */
	private int drainPendingOnEDT(int max) {
		requireEdt("StripChartCurve.drainPendingOnEDT");

		final int cap = Math.max(1, max);

		return pending.drainPendingOnEDT(cap, batch -> {
			synchronized (lock) {
				for (PendingSample s : batch) {
					trimOneIfFullLocked();
					xData.add(s.tms);
					yData.add(s.y);
				}
			}

			// One invalidation + notification for the whole batch (EDT-only)
			markDataChanged();
			runOnSampleCallbackEdt();
		});
	}

	/** Invoke the onSample callback on the EDT (no-op if null). */
	private void runOnSampleCallbackEdt() {
		requireEdt("StripChartCurve.runOnSampleCallbackEdt");
		Runnable callback = onSample;
		if (callback != null) {
			try {
				callback.run();
			} catch (Throwable t) {
				// Fail soft
				t.printStackTrace();
			}
		}
	}

	private void trimOneIfFullLocked() {
		while (xData.size() >= capacity) {
			// DataColumn extends DataList extends ArrayList, so "remove(0)" is available.
			xData.remove(0);
			yData.remove(0);
		}
	}

	private void trimToCapacityLocked() {
		while (xData.size() > capacity) {
			xData.remove(0);
			yData.remove(0);
		}
	}

	/**
	 * Simple daemon thread factory for the sampler thread.
	 */
	private static final class DaemonThreadFactory implements ThreadFactory {
		private final String baseName;

		private DaemonThreadFactory(String baseName) {
			this.baseName = baseName;
		}

		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, baseName);
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY);
			return t;
		}
	}

	/**
	 * Strip charts do not perform fitting; this method is a no-op.
	 */
	@Override
	public void doFit(boolean force) {
		requireEdt("doFit");
		if (!force && !isDirty()) {
			return;
		}

		boolean success = false;

		try {
			final CurveDrawingMethod method = getCurveDrawingMethod();

			clearComputedArtifacts();

			switch (method) {

			case NONE:
			case CONNECT:
			case STAIRS:
				success = true;
				break;

			case CUBICSPLINE: {
				FitVectors v = new FitVectors(xData, yData, null);
				if (v != null && v.length() >= 2) {
					setCubicSpline(new CubicSpline(v.x, v.y));
					success = true;
				}
				break;
			}

			case POLYNOMIAL:
			case ERF:
			case ERFC:
			case GAUSSIAN:
			case GAUSSIANS: {
				IFitter fitter = createFitterForCurrentMethod();
				if (fitter != null) {
					FitVectors v = new FitVectors(xData, yData, null);
					FitResult fr = fitWithOptionalWeights(fitter, v);
					setFitResult(fr);
					success = (fr != null);
				}
				break;
			}

			default:
				break;
			}

		} catch (Exception e) {
			// Fail soft
		} finally {
			if (success) {
				setDirty(false);
			}
		}
	}
	@Override
	public int length() {
		return xData.size();
	}

	@Override
	public double xMin() {
		return xData == null ? Double.NaN : scaleTime(xData.getMin());
	}

	@Override
	public double xMax() {
		return xData == null ? Double.NaN : scaleTime(xData.getMax());
	}

	@Override
	public double yMin() {
		return yData == null ? Double.NaN : yData.getMin();
	}

	@Override
	public double yMax() {
		return yData == null ? Double.NaN : yData.getMax();
	}

	/**
	 * Immutable pending sample payload used for cross-thread staging.
	 */
	private static final class PendingSample {
		final double tms;
		final double y;

		PendingSample(double tms, double y) {
			this.tms = tms;
			this.y = y;
		}
	}
}
