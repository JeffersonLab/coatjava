package cnuphys.splot.pdata;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import cnuphys.splot.fit.Evaluator;

/**
 * Backing data model for a strip chart / time-series plot.
 *
 * <p>
 * This class periodically samples a provided {@link Evaluator} and appends
 * (t, y) points into bounded X and Y columns. When capacity is reached, the
 * oldest samples are dropped ("scrolls off the left").
 * </p>
 *
 * <h3>Threading</h3>
 * <p>
 * Sampling runs on a background thread. The data columns are mutated under an
 * internal lock. Plotters should use {@link #snapshot()} to obtain a consistent
 * copy of the data for drawing without needing to synchronize on the model.
 * </p>
 *
 * <p>
 * If your plot is Swing-based and you want repaint on the EDT, provide an
 * {@link #setOnSample(Runnable)} callback that posts a repaint using
 * {@code SwingUtilities.invokeLater(...)}.
 * </p>
 */
public class StripChartCurve extends ACurve {

	/** Enable debug logging. */
	private static boolean debug = true;

	/** Maximum number of samples retained. Must be >= 2. */
	private int capacity;

	/** Produces the next value given time in seconds. */
	private final Evaluator accumulator;

	/** Sampling period in milliseconds. Must be > 0. */
	private long intervalMs;

	/** Time when sampling started (ms since epoch). */
	private volatile long startTimeMs;

	/** True while actively sampling. */
	private volatile boolean running;

	/** Optional callback invoked after each sample. */
	private volatile Runnable onSample;

	/** Series data columns. Mutated under {@link #lock}. */
	private final DataColumn xData;
	private final DataColumn yData;

	/** Scheduler for periodic sampling. */
	private final ScheduledExecutorService scheduler;
	private ScheduledFuture<?> future;

	/**
	 * Create strip-chart data.
	 *
	 * @param name        series name
	 * @param xName       x data column name
	 * @param yName       y data column name
	 * @param capacity    max number of retained samples (>= 2)
	 * @param accumulator value source; called as
	 *                    {@code accumulator.value(tSeconds)} (non-null)
	 * @param intervalMs  update interval in milliseconds (> 0)
	 */
	public StripChartCurve(String name, String xName, String yName, int capacity, Evaluator accumulator, long intervalMs) {
		super(name);
		this.accumulator = Objects.requireNonNull(accumulator, "accumulator");
		xData = new DataColumn(xName);
		yData = new DataColumn(yName);

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
	 * @param capacity must be >= 2
	 */
	public void setCapacity(int capacity) {
		if (capacity < 2) {
			throw new IllegalArgumentException("capacity must be >= 2");
		}
		this.capacity = capacity;

		// Enforce immediately.
		synchronized (lock) {
			trimToCapacityLocked();
		}
	}

	/** @return sampling interval in milliseconds. */
	public long getIntervalMs() {
		return intervalMs;
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
	 * Set a callback to be invoked after each sample.
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
	 * Permanently shut down this StripData (cannot be restarted). Use when the
	 * owning plot is being disposed.
	 */
	public void shutdown() {
		stop();
		scheduler.shutdownNow();
	}

	/**
	 * Clear all retained samples.
	 */
	public void clear() {
		synchronized (lock) {
			xData.clear();
			yData.clear();
		}
	}
	
	/**
	 * Notify listeners that a new sample is available.
	 */
	public void fireOnSample() {
		super.dataChanged();
		// invoke onSample callback if any
		Runnable callback = onSample;
		if (callback != null) {
			try {
				callback.run();
			} catch (Throwable t) {
				// Fail soft: log and continue
				t.printStackTrace();
			}
		}
	}

	/**
	 * Obtain a consistent snapshot of the current data, suitable for plotting
	 * without locking.
	 *
	 * @return snapshot containing primitive arrays of x and y data. Those arrays
	 *         are what should be used for plotting; they are copies of the internal
	 *         data. This is thread-safe.
	 *
	 */
	public Snapshot snapshot() {
		synchronized (lock) {
			return new Snapshot(xData.values(), yData.values());
		}
	}

	public void add(double x, double y) {
		synchronized (lock) {
			trimOneIfFullLocked();
			xData.add(x);
			yData.add(y);
		}
	}
	
	// ------------------------ Internals ------------------------

	/**
	 * This treats the actual running time as the independent variable (x) and
	 * samples the accumulator for the dependent variable (y).
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

			synchronized (lock) {
				trimOneIfFullLocked();
				xData.add(tms);
				yData.add(y);
				if (debug) {
					System.err.printf("StripData[%s] addSample: t=%.3f, y=%.3f%n", name(), tms/1000, y);
				}
		}

			// notify listeners and optional callback
			fireOnSample();
		} catch (Throwable t) {
			// Fail soft: stop sampling on unexpected exceptions to avoid runaway logs.
			stop();
		}
	}

	private void trimOneIfFullLocked() {
		while (xData.size() >= capacity) {
			// DataColumn extends DataList extends ArrayList, so "remove(0)" is available.
			// This is O(n), but capacity is typically small; if you need huge series,
			// we can swap in a ring buffer.
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

	@Override
	public void doFit(boolean force) {
		// no op (no fitting for strip chart)

	}

	@Override
	public int length() {
		return xData.size();
	}
	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double xMin() {
		return xData == null ? Double.NaN : xData.getMin();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double xMax() {
		return xData == null ? Double.NaN : xData.getMax();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double yMin() {
		return yData == null ? Double.NaN : yData.getMin();
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public double yMax() {
		return yData == null ? Double.NaN : yData.getMax();
	}

	// Simple test
	public static void main(String[] args) {
		
		StripChartCurve sd = new StripChartCurve("test", "time", "value", 10, t -> Math.sin(t), 1000);
		Runnable r = () -> {
			System.err.println("Sampled. Size = " + sd.length());
		};
		
		sd.setOnSample(r);
		sd.start();

		try {
			Thread.sleep(12000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		sd.stop();

		Snapshot snap = sd.snapshot();
		for (int i = 0; i < snap.length(); i++) {
			System.err.printf("t=%.2f, y=%.4f%n", snap.x[i], snap.y[i]);
		}

	}
}
