package cnuphys.splot.pdata;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.ErfFitter;
import cnuphys.splot.fit.ErfcFitter;
import cnuphys.splot.fit.FitResult;
import cnuphys.splot.fit.GaussianFitter;
import cnuphys.splot.fit.IFitter;
import cnuphys.splot.fit.MultiGaussianFitter;
import cnuphys.splot.fit.PolynomialFitter;
import cnuphys.splot.spline.CubicSpline;

/**
 * A standard XY curve consisting of X, Y, and an optional Y-error column (E).
 *
 * <h2>Thread-safety model</h2>
 * <p>
 * {@code Curve} supports two distinct modes of use:
 * </p>
 *
 * <h3>1) Thread-safe mutation via {@code add(...)} / {@code addAll(...)}</h3>
 * <p>
 * Methods such as {@link #add(double, double)} and {@link #addAll(double[], double[])} may be called
 * from <em>any</em> thread. They preserve the {@link ACurve} contract that notifications occur on
 * the Swing EDT:
 * </p>
 * <ul>
 *   <li>If called on the EDT, they apply immediately and fire a DATA change event.</li>
 *   <li>If called off the EDT, they enqueue points and schedule a coalesced EDT drain pass. The drain
 *       applies a batch and then fires a single consolidated DATA change event.</li>
 * </ul>
 *
 * <h3>2) Streaming / DAQ mode via {@code enqueue(...)} + {@code drainPendingOnEDT(...)}</h3>
 * <p>
 * Background producer threads may call {@link #enqueue(double, double)} / {@link #enqueue(double, double, double)}
 * and the UI (EDT) drains periodically using {@link #drainPendingOnEDT(int)} (often via {@link #startPendingDrainTimer}).
 * </p>
 *
 * @author heddle
 */
public class Curve extends ACurve {

	// --------------------------------------------------------------------
	// Data columns for x, y, and optional y-error (e)
	// --------------------------------------------------------------------

	private final DataColumn xData;
	private final DataColumn yData;
	private final DataColumn eData;

	// --------------------------------------------------------------------
	// Streaming / DAQ staging queue (any thread enqueue, EDT drains)
	// --------------------------------------------------------------------

	/**
	 * Lock-free staging queue holding points that have been produced on background threads but
	 * not yet applied to the curve's data columns.
	 */
	private final ConcurrentLinkedQueue<PendingPoint> pending = new ConcurrentLinkedQueue<>();

	/**
	 * Approximate pending queue size for monitoring/backpressure.
	 * <p>
	 * We maintain this separately because {@link ConcurrentLinkedQueue#size()} is O(n).
	 * </p>
	 */
	private final AtomicLong pendingCount = new AtomicLong(0);

	/**
	 * Optional convenience timer that drains {@link #pending} on the EDT.
	 */
	private volatile Timer pendingDrainTimer;

	/**
	 * Coalescing latch: ensures we only post one drain runnable to the EDT at a time,
	 * no matter how many background threads call {@link #add} / {@link #addAll}.
	 */
	private final AtomicBoolean drainScheduled = new AtomicBoolean(false);

	/**
	 * Maximum points to apply per scheduled drain pass. This prevents the EDT from being
	 * monopolized if producers temporarily outrun the UI.
	 */
	private static final int DEFAULT_DRAIN_MAX = 10_000;

	/**
	 * Create a standard XY curve.
	 *
	 * @param name  the curve name
	 * @param xData the x data column (non-null)
	 * @param yData the y data column (non-null)
	 * @param eData the optional y-error column (may be null)
	 * @throws PlotDataException if the data columns have inconsistent lengths
	 */
	public Curve(String name, DataColumn xData, DataColumn yData, DataColumn eData) throws PlotDataException {
		super(name);
		this.xData = Objects.requireNonNull(xData, "xData");
		this.yData = Objects.requireNonNull(yData, "yData");
		this.eData = eData;

		if (!consistentData()) {
			throw new PlotDataException("Inconsistent data lengths in curve: " + name);
		}
	}

	/**
	 * Check that x, y, and (optional) e data lengths are consistent.
	 */
	private boolean consistentData() {
		int n = xData.size();
		return yData.size() == n && (eData == null || eData.size() == n);
	}

	@Override
	public int length() {
		return xData.size();
	}

	/** @return the x data column (backing storage; do not mutate externally) */
	public DataColumn xData() {
		return xData;
	}

	/** @return the y data column (backing storage; do not mutate externally) */
	public DataColumn yData() {
		return yData;
	}

	/** @return the optional y-error data column (may be null) */
	public DataColumn eData() {
		return eData;
	}

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
				FitVectors v = new FitVectors(xData, yData, eData);
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
					FitVectors v = new FitVectors(xData, yData, eData);
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

	// --------------------------------------------------------------------
	// Immediate append API (NOW THREAD-SAFE)
	// --------------------------------------------------------------------

	/**
	 * Append a point and fire a DATA change event.
	 * <p>
	 * Safe to call from any thread. Off-EDT calls enqueue and schedule a coalesced EDT drain.
	 * </p>
	 */
	public void add(double x, double y) {
		if (!SwingUtilities.isEventDispatchThread()) {
			enqueue(x, y);
			scheduleDrain();
			return;
		}

		synchronized (lock) {
			xData.add(x);
			yData.add(y);
			if (eData != null) {
				eData.add(0.0);
			}
		}
		markDataChanged(); // EDT-only (ACurve contract)
	}

	/**
	 * Append a point with Y error and fire a DATA change event.
	 * <p>
	 * Safe to call from any thread. Off-EDT calls enqueue and schedule a coalesced EDT drain.
	 * </p>
	 *
	 * @throws IllegalStateException if this curve has no error column (eData is null)
	 */
	public void add(double x, double y, double ey) {
		if (eData == null) {
			throw new IllegalStateException("This curve has no error column (eData is null).");
		}

		if (!SwingUtilities.isEventDispatchThread()) {
			enqueue(x, y, ey);
			scheduleDrain();
			return;
		}

		synchronized (lock) {
			xData.add(x);
			yData.add(y);
			eData.add(ey);
		}
		markDataChanged(); // EDT-only
	}

	/**
	 * Append many points and fire a single DATA change event.
	 * <p>
	 * Safe to call from any thread. Off-EDT calls enqueue and schedule a coalesced EDT drain.
	 * </p>
	 */
	public void addAll(double[] x, double[] y) {
		Objects.requireNonNull(x, "x");
		Objects.requireNonNull(y, "y");
		if (x.length != y.length) {
			throw new IllegalArgumentException("x and y lengths differ: " + x.length + " vs " + y.length);
		}

		if (!SwingUtilities.isEventDispatchThread()) {
			for (int i = 0; i < x.length; i++) {
				enqueue(x[i], y[i]);
			}
			scheduleDrain();
			return;
		}

		synchronized (lock) {
			for (int i = 0; i < x.length; i++) {
				xData.add(x[i]);
				yData.add(y[i]);
				if (eData != null) {
					eData.add(0.0);
				}
			}
		}
		markDataChanged(); // EDT-only
	}

	/**
	 * Append many points with Y errors and fire a single DATA change event.
	 * <p>
	 * Safe to call from any thread. Off-EDT calls enqueue and schedule a coalesced EDT drain.
	 * </p>
	 *
	 * @throws IllegalStateException if this curve has no error column (eData is null)
	 */
	public void addAll(double[] x, double[] y, double[] ey) {
		if (eData == null) {
			throw new IllegalStateException("This curve has no error column (eData is null).");
		}
		Objects.requireNonNull(x, "x");
		Objects.requireNonNull(y, "y");
		Objects.requireNonNull(ey, "ey");
		if (x.length != y.length || x.length != ey.length) {
			throw new IllegalArgumentException(
					"lengths differ: x=" + x.length + " y=" + y.length + " ey=" + ey.length);
		}

		if (!SwingUtilities.isEventDispatchThread()) {
			for (int i = 0; i < x.length; i++) {
				enqueue(x[i], y[i], ey[i]);
			}
			scheduleDrain();
			return;
		}

		synchronized (lock) {
			for (int i = 0; i < x.length; i++) {
				xData.add(x[i]);
				yData.add(y[i]);
				eData.add(ey[i]);
			}
		}
		markDataChanged(); // EDT-only
	}

	/**
	 * Schedule a coalesced drain pass on the EDT.
	 * <p>
	 * If multiple background threads call this repeatedly, only one drain runnable
	 * is posted until it begins execution.
	 * </p>
	 */
	private void scheduleDrain() {
		if (drainScheduled.compareAndSet(false, true)) {
			SwingUtilities.invokeLater(() -> {
				drainScheduled.set(false);

				// Apply a bounded chunk to keep EDT responsive.
				int drained = drainPendingOnEDT(DEFAULT_DRAIN_MAX);

				// If we likely hit the cap, schedule another pass.
				if (drained >= DEFAULT_DRAIN_MAX && getPendingCount() > 0) {
					scheduleDrain();
				}
			});
		}
	}

	// --------------------------------------------------------------------
	// Clear/snapshot/min/max (unchanged)
	// --------------------------------------------------------------------

	/**
	 * Clear all data from this curve and fire a DATA change event.
	 * <p>
	 * Thread-safe: may be called from any thread. If called off the EDT, the clear is
	 * performed later on the EDT and coalesced with other scheduled drains.
	 * </p>
	 * <p>
	 * Note: this also clears any queued (not-yet-applied) points so the curve truly becomes empty.
	 * </p>
	 */
	public void clearData() {
		if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
			// Make the curve truly empty: discard not-yet-applied points.
			clearPending();

			// Coalesce with any scheduled drains; run the clear on the EDT.
			scheduleDrainOnce(() -> clearData());
			return;
		}

		synchronized (lock) {
			xData.clear();
			yData.clear();
			if (eData != null) {
				eData.clear();
			}
			markDataChanged();
		}
	}

	@Override
	public Snapshot snapshot() {
		synchronized (lock) {
			return new Snapshot(xData.values(), yData.values(), eData == null ? null : eData.values());
		}
	}

	@Override
	public double xMin() {
		return xData == null ? Double.NaN : xData.getMin();
	}

	@Override
	public double xMax() {
		return xData == null ? Double.NaN : xData.getMax();
	}

	@Override
	public double yMin() {
		return yData == null ? Double.NaN : yData.getMin();
	}

	@Override
	public double yMax() {
		return yData == null ? Double.NaN : yData.getMax();
	}

	// --------------------------------------------------------------------
	// Streaming / DAQ API (unchanged)
	// --------------------------------------------------------------------

	private void enqueue(double x, double y) {
		pending.offer(new PendingPoint(x, y));
		pendingCount.incrementAndGet();
	}

	private void enqueue(double x, double y, double ey) {
		if (eData == null) {
			throw new IllegalStateException("This curve has no error column (eData is null).");
		}
		pending.offer(new PendingPoint(x, y, ey));
		pendingCount.incrementAndGet();
	}

	public long getPendingCount() {
		return pendingCount.get();
	}

	public int drainPendingOnEDT(int max) {
		requireEdt("drainPendingOnEDT");
		if (max <= 0) {
			return 0;
		}

		int drained = 0;
		PendingPoint p;

		final ArrayList<PendingPoint> batch = new ArrayList<>(Math.min(max, 256));
		while (drained < max && (p = pending.poll()) != null) {
			pendingCount.decrementAndGet();
			batch.add(p);
			drained++;
		}

		if (drained == 0) {
			return 0;
		}

		synchronized (lock) {
			for (PendingPoint pp : batch) {
				if (pp.hasEy) {
					appendNoNotify(pp.x, pp.y, pp.ey);
				} else {
					appendNoNotify(pp.x, pp.y);
				}
			}
			markDataChanged();
		}

		return drained;
	}

	public void startPendingDrainTimer(int periodMs, int maxPerTick) {
		startPendingDrainTimer(periodMs, maxPerTick, null);
	}

	public void startPendingDrainTimer(int periodMs, int maxPerTick, IntConsumer drainedCallback) {

		stopPendingDrainTimer();
		if (periodMs <= 0) {
			return;
		}

		final int max = Math.max(1, maxPerTick);

		pendingDrainTimer = new Timer(periodMs, e -> {
			int drained = drainPendingOnEDT(max);
			if (drainedCallback != null) {
				drainedCallback.accept(drained);
			}
		});

		pendingDrainTimer.setCoalesce(true);
		pendingDrainTimer.start();
	}

	public void stopPendingDrainTimer() {
		Timer t = pendingDrainTimer;
		pendingDrainTimer = null;
		if (t != null) {
			t.stop();
		}
	}

	public void clearPending() {
		pending.clear();
		pendingCount.set(0);
	}

	// --------------------------------------------------------------------
	// Internal append helpers (no notifications)
	// --------------------------------------------------------------------

	private void appendNoNotify(double x, double y) {
		xData.add(x);
		yData.add(y);
		if (eData != null) {
			eData.add(0.0);
		}
	}

	private void appendNoNotify(double x, double y, double ey) {
		if (eData == null) {
			throw new IllegalStateException("This curve has no error column (eData is null).");
		}
		xData.add(x);
		yData.add(y);
		eData.add(ey);
	}

	private static final class PendingPoint {
		final double x;
		final double y;
		final boolean hasEy;
		final double ey;

		PendingPoint(double x, double y) {
			this.x = x;
			this.y = y;
			this.ey = 0.0;
			this.hasEy = false;
		}

		PendingPoint(double x, double y, double ey) {
			this.x = x;
			this.y = y;
			this.ey = ey;
			this.hasEy = true;
		}
	}
}
