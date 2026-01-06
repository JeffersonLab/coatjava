package cnuphys.splot.pdata;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;

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
 * <h3>1) Immediate mutation via {@code add(...)} / {@code addAll(...)}</h3>
 * <p>
 * Methods such as {@link #add(double, double)} and {@link #addAll(double[], double[])} append data
 * immediately and then call {@link #markDataChanged()}, which fires a curve-change event that
 * typically leads to a Swing repaint.
 * </p>
 * <p>
 * Because UI notifications ultimately touch Swing, these immediate mutation methods should be
 * called from the <b>Swing Event Dispatch Thread (EDT)</b> in typical interactive applications.
 * (Internally they synchronize on {@link #lock} to keep the columns consistent.)
 * </p>
 *
 * <h3>2) Streaming / DAQ mode via {@code enqueue(...)} + {@code drainPendingOnEDT(...)}</h3>
 * <p>
 * For data acquisition (DAQ) and other streaming scenarios where points arrive on background threads,
 * this class provides a lock-free staging queue:
 * </p>
 * <ul>
 *   <li>{@link #enqueue(double, double)} / {@link #enqueue(double, double, double)} may be called from <em>any</em> thread.</li>
 *   <li>{@link #drainPendingOnEDT(int)} must be called on the <b>EDT</b> to apply queued points.</li>
 * </ul>
 *
 * <p>
 * The intended pattern is:
 * </p>
 *
 * <pre>
 * background producer threads  --enqueue-->  Curve.pending queue
 *                                  |
 *                                  v
 * Swing Timer (EDT)           --drain-->    curve data columns + single DATA event
 * </pre>
 *
 * <p>
 * The drain operation is batched: many queued points are appended and then a single
 * {@link #markDataChanged()} is fired, preventing a repaint/event storm.
 * </p>
 *
 * <h2>Fitting and caching</h2>
 * <p>
 * Fit results and spline caches are invalidated by {@link #markDataChanged()}, which calls
 * {@code clearComputedArtifacts()} in the base class. Rendering code typically calls
 * {@link #doFit(boolean)} when needed.
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
	 * <p>
	 * This is useful for examples and small apps. Larger apps may prefer a single timer at the
	 * canvas/model level to drain all curves.
	 * </p>
	 */
	private volatile Timer pendingDrainTimer;

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
	protected IFitter createFitterForCurrentMethod() {
		switch (getCurveDrawingMethod()) {
		case POLYNOMIAL:
			return new PolynomialFitter(getFitOrder());

		case ERF:
			return new ErfFitter();
			
		case ERFC:
			return new ErfcFitter();

		case GAUSSIAN:
			return new GaussianFitter();

		case GAUSSIANS:
			// "order" interpreted as number of Gaussians. (Constructor: (count, includeBaseline))
			return new MultiGaussianFitter(Math.max(1, getFitOrder()), true);

		default:
			return null;
		}
	}

	/**
	 * Compute derived artifacts needed for rendering: fit results or cubic spline cache, depending on
	 * {@link #getCurveDrawingMethod()}.
	 *
	 * <p>
	 * This method is typically called from the rendering pipeline. In Swing, rendering occurs on the EDT,
	 * and callers should treat {@code doFit} as an EDT operation.
	 * </p>
	 *
	 * <p>
	 * Implementation notes:
	 * </p>
	 * <ul>
	 *   <li>If {@code force==false} and the curve is not dirty, this method returns immediately.</li>
	 *   <li>On entry, stale artifacts are cleared.</li>
	 *   <li>If computation succeeds, {@link #setDirty(boolean)} is set to {@code false}.</li>
	 *   <li>If computation fails (exception, insufficient data, fitter returns null), the curve remains dirty.</li>
	 * </ul>
	 *
	 * @param force force recomputation even if not dirty
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

			// Clear stale artifacts (fitResult, spline, etc.) before recomputing
			clearComputedArtifacts();

			switch (method) {

			case NONE:
			case CONNECT:
			case STAIRS:
				// No derived artifacts required
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
					// Note: setFitResult may notify listeners depending on ACurve implementation.
					// In typical usage doFit is invoked on EDT during rendering, so this is acceptable.
					setFitResult(fr);
					success = (fr != null);
				}
				break;
			}

			default:
				// Unknown/unsupported method
				break;
			}

		} catch (Exception e) {
			// Fail soft: artifacts already cleared by clearComputedArtifacts()
		} finally {
			// Only clear dirty when we actually produced what we needed.
			if (success) {
				setDirty(false);
			}
		}
	}

	// --------------------------------------------------------------------
	// Immediate append API (mutates now + fires DATA change)
	// --------------------------------------------------------------------

	/**
	 * Append a point immediately and fire a DATA change event.
	 * <p>
	 * For Swing applications, prefer calling this on the EDT. For background producers,
	 * use {@link #enqueue(double, double)} and drain on the EDT.
	 * </p>
	 */
	public void add(double x, double y) {
		super.requireEdt("add(x, y)");
		synchronized (lock) {
			xData.add(x);
			yData.add(y);
			if (eData != null) {
				eData.add(0.0);
			}
			markDataChanged();
		}
	}

	/**
	 * Append a point with Y error immediately and fire a DATA change event.
	 *
	 * @throws IllegalStateException if this curve has no error column (eData is null)
	 */
	public void add(double x, double y, double ey) {
		super.requireEdt("add(x, y, e)");
		if (eData == null) {
			throw new IllegalStateException("This curve has no error column (eData is null).");
		}
		synchronized (lock) {
			xData.add(x);
			yData.add(y);
			eData.add(ey);
			markDataChanged();
		}
	}

	/**
	 * Append many points immediately and fire a single DATA change event.
	 */
	public void addAll(double[] x, double[] y) {
		super.requireEdt("addAll x[], y[]");
		Objects.requireNonNull(x, "x");
		Objects.requireNonNull(y, "y");
		if (x.length != y.length) {
			throw new IllegalArgumentException("x and y lengths differ: " + x.length + " vs " + y.length);
		}
		synchronized (lock) {
			for (int i = 0; i < x.length; i++) {
				xData.add(x[i]);
				yData.add(y[i]);
				if (eData != null) {
					eData.add(0.0);
				}
			}
			markDataChanged();
		}
	}

	/**
	 * Append many points with Y errors immediately and fire a single DATA change event.
	 *
	 * @throws IllegalStateException if this curve has no error column (eData is null)
	 */
	public void addAll(double[] x, double[] y, double[] ey) {
		super.requireEdt("addAll x[], y[], ey[]");
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
		synchronized (lock) {
			for (int i = 0; i < x.length; i++) {
				xData.add(x[i]);
				yData.add(y[i]);
				eData.add(ey[i]);
			}
			markDataChanged();
		}
	}

	/**
	 * Clear all data from this curve and fire a DATA change event.
	 * <p>
	 * This method synchronizes on {@link #lock} to avoid racing with {@link #snapshot()} or appends.
	 * </p>
	 */
	public void clearData() {
		super.requireEdt("clearData");
		synchronized (lock) {
			xData.clear();
			yData.clear();
			if (eData != null) {
				eData.clear();
			}
			markDataChanged();
		}
	}

	/**
	 * Obtain a consistent snapshot of the current data, suitable for plotting without further locking.
	 * <p>
	 * The returned arrays are copies of the internal data at the moment of the snapshot.
	 * </p>
	 */
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
	// Streaming / DAQ API (any thread enqueue, EDT drains in batches)
	// --------------------------------------------------------------------

	/**
	 * Enqueue a point for later application on the EDT.
	 * <p>
	 * Thread-safe: may be called from any thread. This method does not mutate the curve's internal
	 * data columns and does not fire any events.
	 * </p>
	 *
	 * <p>
	 * To apply queued points, call {@link #drainPendingOnEDT(int)} from the EDT (often via a Swing Timer),
	 * or use {@link #startPendingDrainTimer(int, int)} for a simple built-in drainer.
	 * </p>
	 */
	public void enqueue(double x, double y) {
		pending.offer(new PendingPoint(x, y));
		pendingCount.incrementAndGet();
	}

	/**
	 * Enqueue a point with Y error for later application on the EDT.
	 * <p>
	 * Thread-safe: may be called from any thread. This method does not mutate the curve's internal
	 * data columns and does not fire any events.
	 * </p>
	 *
	 * @throws IllegalStateException if this curve has no error column (eData is null)
	 */
	public void enqueue(double x, double y, double ey) {
		if (eData == null) {
			throw new IllegalStateException("This curve has no error column (eData is null).");
		}
		pending.offer(new PendingPoint(x, y, ey));
		pendingCount.incrementAndGet();
	}

	/**
	 * Approximate number of queued points awaiting drain.
	 * <p>
	 * This is intended for monitoring/backpressure. It is maintained separately because
	 * {@link ConcurrentLinkedQueue#size()} is O(n).
	 * </p>
	 */
	public long getPendingCount() {
		return pendingCount.get();
	}

	/**
	 * Drain queued points and apply them to the curve.
	 * <p>
	 * <b>This method must be called on the EDT.</b> It removes up to {@code max} points from the queue,
	 * appends them to the internal data columns, then fires a single DATA change event via
	 * {@link #markDataChanged()}.
	 * </p>
	 *
	 * <p>
	 * The {@code max} parameter prevents the EDT from being monopolized if producers temporarily outrun the UI.
	 * </p>
	 *
	 * @param max maximum number of points to apply; {@code max <= 0} drains none
	 * @return number of points applied
	 * @throws IllegalStateException if called off the EDT
	 */
	public int drainPendingOnEDT(int max) {
		requireEdt("drainPendingOnEDT");
		if (max <= 0) {
			return 0;
		}

		int drained = 0;
		PendingPoint p;

		// Poll without holding 'lock' to keep the critical section short.
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
			// One invalidation + one notification for the whole batch.
			markDataChanged();
		}

		return drained;
	}

	/**
	 * Start a Swing Timer that periodically drains queued points on the EDT.
	 * <p>
	 * This is a convenience for examples and small applications. If a timer is already running, it is
	 * stopped and replaced.
	 * </p>
	 *
	 * @param periodMs  timer period in milliseconds (typical 10–50). If {@code periodMs <= 0}, no timer is started.
	 * @param maxPerTick maximum points to drain per timer tick (prevents EDT starvation). Values <= 0 are treated as 1.
	 */
	public void startPendingDrainTimer(int periodMs, int maxPerTick) {
		startPendingDrainTimer(periodMs, maxPerTick, null);
	}
	
	/**
	 * Start a Swing Timer that periodically drains queued points on the EDT and
	 * reports how many points were applied per tick.
	 * <p>
	 * This is a convenience for streaming / DAQ-style applications that want
	 * to monitor accumulation or stop after a threshold.
	 * </p>
	 *
	 * @param periodMs  timer period in milliseconds (typical 10–50).
	 *                  If {@code periodMs <= 0}, no timer is started.
	 * @param maxPerTick maximum points to drain per timer tick
	 *                   (prevents EDT starvation). Values <= 0 are treated as 1.
	 * @param drainedCallback optional callback invoked on the EDT with the
	 *                        number of points drained this tick.
	 */
	public void startPendingDrainTimer(int periodMs,
	                                   int maxPerTick,
	                                   IntConsumer drainedCallback) {

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

	/** Stop the pending-drain Swing Timer if running. */
	public void stopPendingDrainTimer() {
		Timer t = pendingDrainTimer;
		pendingDrainTimer = null;
		if (t != null) {
			t.stop();
		}
	}

	/**
	 * Clear all queued (not-yet-applied) points.
	 * <p>
	 * This does not affect already-applied curve data; it only clears the staging queue.
	 * </p>
	 */
	public void clearPending() {
		pending.clear();
		pendingCount.set(0);
	}

	// --------------------------------------------------------------------
	// Internal append helpers (no notifications)
	// --------------------------------------------------------------------

	/**
	 * Append one point without firing any events.
	 * <p>
	 * Caller must hold {@link #lock}. This method only mutates data storage.
	 * </p>
	 */
	private void appendNoNotify(double x, double y) {
		xData.add(x);
		yData.add(y);
		if (eData != null) {
			eData.add(0.0);
		}
	}

	/**
	 * Append one point with error without firing any events.
	 * <p>
	 * Caller must hold {@link #lock}. This method only mutates data storage.
	 * </p>
	 *
	 * @throws IllegalStateException if this curve has no error column
	 */
	private void appendNoNotify(double x, double y, double ey) {
		if (eData == null) {
			throw new IllegalStateException("This curve has no error column (eData is null).");
		}
		xData.add(x);
		yData.add(y);
		eData.add(ey);
	}

	/**
	 * Immutable point record used for staging queue transport.
	 * <p>
	 * Instances are safe to share across threads.
	 * </p>
	 */
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
