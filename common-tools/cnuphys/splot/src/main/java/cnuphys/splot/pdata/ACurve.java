package cnuphys.splot.pdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.EventListenerList;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.ErfFitter;
import cnuphys.splot.fit.ErfcFitter;
import cnuphys.splot.fit.Evaluator;
import cnuphys.splot.fit.FitResult;
import cnuphys.splot.fit.GaussianFitter;
import cnuphys.splot.fit.IFitter;
import cnuphys.splot.fit.MultiGaussianFitter;
import cnuphys.splot.fit.PolynomialFitter;
import cnuphys.splot.spline.CubicSpline;
import cnuphys.splot.style.Styled;

/**
 * Base class for all plottable curves (XY curves, histogram curves, strip-chart curves, etc.).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li><b>State</b>: name, visibility, style, drawing method, per-curve fit “order” knob.</li>
 *   <li><b>Computed artifacts</b>: fit results/evaluator and spline cache.</li>
 *   <li><b>Notifications</b>: curve-level change events for data/style/fit.</li>
 *   <li><b>Batching</b>: coalescing multiple changes into a single notification burst.</li>
 * </ul>
 *
 * <h2>Thread-safety policy</h2>
 * <p>
 * This library is Swing-based. Curve change notifications commonly lead (directly or indirectly)
 * to {@code repaint()} on a plot canvas. Swing requires UI updates to occur on the
 * <b>Event Dispatch Thread (EDT)</b>. Therefore:
 * </p>
 * <ul>
 *   <li>All methods that <b>notify listeners</b> must be called on the EDT.</li>
 *   <li>Data integrity within a curve is typically protected by {@link #lock} in subclasses,
 *       but {@link #lock} does not make Swing notifications thread-safe by itself.</li>
 * </ul>
 *
 * <p>
 * For streaming / DAQ scenarios, the recommended pattern is:
 * </p>
 * <pre>
 * producer threads → enqueue points (lock-free queue) → EDT drains → curve.add(...)
 * </pre>
 *
 * <h2>Dirty flag and cache invalidation</h2>
 * <p>
 * The curve may cache computed artifacts such as a {@link FitResult} or {@link CubicSpline}.
 * When data or relevant rendering parameters change, those artifacts must be invalidated and
 * the curve marked {@linkplain #isDirty() dirty}.
 * </p>
 *
 * <p>
 * Use:
 * </p>
 * <ul>
 *   <li>{@link #markDataChanged()} when the underlying data values have changed.</li>
 *   <li>{@link #markStyleChanged()} when appearance or drawing configuration has changed.</li>
 *   <li>{@link #markFitChanged()} when a fit result has been updated (e.g., after {@link #doFit(boolean)}).</li>
 * </ul>
 *
 * @author heddle
 */
public abstract class ACurve {

	/**
	 * Lock object intended for subclasses to synchronize data mutation and snapshot creation.
	 * <p>
	 * This lock is about <b>data consistency</b> (e.g., keeping x/y arrays aligned). It does not
	 * automatically make Swing notifications safe; notifications are enforced to be EDT-only.
	 * </p>
	 */
	protected final Object lock = new Object();

	/** Used to assign stable-ish style ids. */
	private static int styleCount = 0;

	/**
	 * Per-curve order/count knob for fit methods that need an integer order.
	 * <ul>
	 *   <li>Polynomial: polynomial degree</li>
	 *   <li>Multi-Gaussian: number of Gaussians</li>
	 * </ul>
	 */
	private int fitOrder = 2;

	/** Visibility flag (UI). */
	private boolean visible = true;

	/**
	 * Dirty flag: indicates that cached artifacts (fit/spline) are invalid and should be recomputed
	 * before rendering if the current drawing method requires them.
	 */
	private boolean dirty = true;

	/** Latest fit result (may be null). */
	private FitResult fitResult;

	/** Curve name (legend label). */
	private String name;

	/** Style for drawing. */
	private Styled style;

	/** Cached cubic spline for {@link CurveDrawingMethod#CUBICSPLINE} (may be null). */
	private CubicSpline cubicSpline;

	/** How the curve should be drawn. */
	private CurveDrawingMethod curveMethod = CurveDrawingMethod.NONE;

	/** Curve change listeners. */
	private final EventListenerList curveListenerList = new EventListenerList();

	/**
	 * Update batching depth. A value of 0 means notifications are delivered immediately.
	 * <p>
	 * Batching is intended for EDT use.
	 * </p>
	 */
	private int updateDepth;

	/** Pending change flags while batching. */
	private boolean pendingData;
	private boolean pendingStyle;
	private boolean pendingFit;
	
	/**
	 * Latch used by {@link #scheduleDrainOnce(Runnable)} to ensure we post at most
	 * one drain runnable to the EDT at a time.
	 */
	private final AtomicBoolean drainScheduled = new AtomicBoolean(false);

	/**
	 * Create a curve with the given name.
	 *
	 * @param name curve name (legend label)
	 */
	public ACurve(String name) {
		this.name = name;
		initStyle();
	}

	
	/**
	 * Perform a curve fit (or compute derived artifacts like splines), depending on the configured
	 * curve drawing method.
	 *
	 * @param force true to force a refit even if not dirty
	 */
	public abstract void doFit(boolean force);

	/**
	 * Get curve length (number of points or effective points in the data columns).
	 *
	 * @return curve length
	 */
	public abstract int length();

	/**
	 * Hook: subclasses may override to provide an appropriate fitter for the current drawing method
	 * and per-curve knobs.
	 *
	 * @return a fitter for the current method, or null if not applicable
	 */
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
		}	}

	/** @return the curve name (legend label) */
	public final String name() {
		return name;
	}

	/**
	 * Set curve name.
	 * <p>
	 * This is a UI/style-affecting change. Must be called on the EDT.
	 * </p>
	 *
	 * @param name curve name
	 */
	public final void setName(String name) {
		this.name = name;
		markStyleChanged();
	}

	/** @return true if visible */
	public boolean isVisible() {
		return visible;
	}

	/**
	 * Set curve visibility.
	 * <p>
	 * This is a UI/style-affecting change. Must be called on the EDT.
	 * </p>
	 *
	 * @param visible visibility flag
	 */
	public void setVisible(boolean visible) {
		this.visible = visible;
		markStyleChanged();
	}

	/** @return the current fit result (may be null) */
	public FitResult fitResult() {
		return fitResult;
	}

	/**
	 * Get the cached fit evaluator y(x) for the current fit.
	 *
	 * @return evaluator for the current fit, or null if none
	 */
	public final Evaluator getFitValueGetter() {
		return (fitResult == null) ? null : fitResult.evaluator;
	}

	/**
	 * Set the fit result and notify listeners that the fit has changed.
	 * <p>
	 * Must be called on the EDT.
	 * </p>
	 *
	 * @param fitResult fit result (may be null)
	 */
	public final void setFitResult(FitResult fitResult) {
		this.fitResult = fitResult;
		markFitChanged();
	}

	/** @return the curve style */
	public Styled getStyle() {
		return style;
	}

	/**
	 * Set the curve style.
	 * <p>
	 * This is a UI/style-affecting change. Must be called on the EDT.
	 * </p>
	 *
	 * @param style new style
	 */
	public void setStyle(Styled style) {
		this.style = style;
		markStyleChanged();
	}

	/** @return true if curve is dirty */
	public boolean isDirty() {
		return dirty;
	}

	/**
	 * Set dirty flag.
	 * <p>
	 * This method does not notify listeners. Prefer {@link #markDataChanged()} or
	 * {@link #markStyleChanged()} when you want notifications.
	 * </p>
	 */
	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	/** @return cached cubic spline (may be null) */
	public CubicSpline getCubicSpline() {
		return cubicSpline;
	}

	/**
	 * Set cached cubic spline.
	 * <p>
	 * This does not notify listeners; it is typically called during {@link #doFit(boolean)}.
	 * </p>
	 */
	public void setCubicSpline(CubicSpline cubicSpline) {
		this.cubicSpline = cubicSpline;
	}

	/** @return curve drawing method */
	public CurveDrawingMethod getCurveDrawingMethod() {
		return curveMethod;
	}

	/**
	 * Convenience method to determine if this curve is a histogram curve.
	 *
	 * @return true if histogram curve
	 */
	public boolean isHistogram() {
		return this instanceof HistoCurve;
	}
	
	/**
	 * Convenience method to determine if this curve is a strip chart curve.
	 *
	 * @return true if strip chart curve
	 */
	public boolean isStripChart() {
		return this instanceof StripChartCurve;
	}
	
	/**
	 * Convenience method to determine if this curve is an XY curve.
	 *
	 * @return true if XY curve
	 */
	public boolean isXYCurve() {
		return this instanceof Curve;
	}

	/**
	 * Set curve drawing method.
	 * <p>
	 * This is a style/configuration change and invalidates computed artifacts.
	 * Must be called on the EDT.
	 * </p>
	 *
	 * @param method method (null treated as NONE)
	 */
	public void setCurveMethod(CurveDrawingMethod method) {
		curveMethod = (method == null) ? CurveDrawingMethod.NONE : method;
		markStyleChanged();
	}

	/**
	 * Get the per-curve order/count knob for fit methods that need an integer order.
	 *
	 * @return per-curve order/count (>= 1)
	 */
	public int getFitOrder() {
		return fitOrder;
	}

	/**
	 * Set the per-curve order/count knob for fit methods that need an integer order.
	 * <p>
	 * This affects fitting/drawing behavior, so it is treated as a style/config change and
	 * invalidates computed artifacts. Must be called on the EDT.
	 * </p>
	 *
	 * @param order per-curve order/count (>= 1)
	 */
	public void setFitOrder(int order) {
		this.fitOrder = Math.max(1, order);
		markStyleChanged();
	}

	/**
	 * Common pattern: if weights exist, call the weighted fit overload; otherwise call fit(x,y).
	 * <p>
	 * This utility performs small sanity checks on {@link FitVectors} before delegating to the fitter.
	 * </p>
	 */
	protected FitResult fitWithOptionalWeights(IFitter fitter, FitVectors v) {
		if (fitter == null || v == null || v.length() < 2) {
			return null;
		}

		final double[] x = v.x;
		final double[] y = v.y;
		final double[] w = v.w;

		if (x == null || y == null || x.length != y.length || x.length < 2) {
			return null;
		}

		if (w != null && w.length == x.length) {
			return fitter.fit(x, y, w);
		}
		return fitter.fit(x, y);
	}

	/**
	 * Clear computed artifacts (fit result and spline cache) and mark dirty.
	 * <p>
	 * This method does <b>not</b> notify listeners. Use {@link #markDataChanged()} or
	 * {@link #markStyleChanged()} when you want notifications.
	 * </p>
	 */
	void clearComputedArtifacts() {
		fitResult = null;
		cubicSpline = null;
		setDirty(true);
	}

	/**
	 * Mark data changed: invalidate computed artifacts and notify listeners.
	 * <p>
	 * Must be called on the EDT (notifications typically lead to repaint).
	 * </p>
	 */
	protected final void markDataChanged() {
		requireEdt("markDataChanged");
		clearComputedArtifacts();
		fireCurveChanged(CurveChangeType.DATA);
	}

	/** Notify listeners that data has changed (EDT-only). */
	public final void dataChanged() {
		markDataChanged();
	}

	/**
	 * Mark style/configuration changed: invalidate computed artifacts and notify listeners.
	 * <p>
	 * Must be called on the EDT. Style/config changes may affect derived artifacts (fit/spline),
	 * so this method clears computed artifacts as well.
	 * </p>
	 */
	protected final void markStyleChanged() {
		requireEdt("markStyleChanged");
		clearComputedArtifacts();
		fireCurveChanged(CurveChangeType.STYLE);
	}

	/** Notify listeners that style has changed (EDT-only). */
	public final void styleChanged() {
		markStyleChanged();
	}

	/**
	 * Mark fit changed: notify listeners.
	 * <p>
	 * Must be called on the EDT.
	 * </p>
	 */
	protected final void markFitChanged() {
		requireEdt("markFitChanged");
		fireCurveChanged(CurveChangeType.FIT);
	}

	/**
	 * Begin batching curve change notifications.
	 * <p>
	 * While batching (depth &gt; 0), calls to {@link #fireCurveChanged(CurveChangeType)} do not notify
	 * listeners immediately; instead they set pending flags. When {@link #endUpdate()} returns the
	 * depth to 0, pending flags are flushed.
	 * </p>
	 * <p>
	 * Must be called on the EDT.
	 * </p>
	 */
	public final void beginUpdate() {
		requireEdt("beginUpdate");
		updateDepth++;
	}

	/**
	 * End batching curve change notifications.
	 * <p>
	 * When the batch depth returns to 0, pending changes are flushed.
	 * Must be called on the EDT.
	 * </p>
	 */
	public final void endUpdate() {
		requireEdt("endUpdate");
		if (updateDepth > 0) {
			updateDepth--;
		}
		if (updateDepth == 0) {
			flushPendingChanges();
		}
	}

	/** @return the minimum x value for this curve */
	public abstract double xMin();

	/** @return the maximum x value for this curve */
	public abstract double xMax();

	/** @return the minimum y value for this curve */
	public abstract double yMin();

	/** @return the maximum y value for this curve */
	public abstract double yMax();

	/**
	 * Add a curve change listener.
	 *
	 * @param listener listener to add (ignored if null)
	 */
	public final void addCurveChangeListener(CurveChangeListener listener) {
		if (listener != null) {
			curveListenerList.add(CurveChangeListener.class, listener);
		}
	}

	/**
	 * Remove a curve change listener.
	 *
	 * @param listener listener to remove (ignored if null)
	 */
	public final void removeCurveChangeListener(CurveChangeListener listener) {
		if (listener != null) {
			curveListenerList.remove(CurveChangeListener.class, listener);
		}
	}

	/**
	 * Notify listeners of a curve change.
	 * <p>
	 * This method assumes EDT usage; higher-level methods enforce EDT.
	 * When batching is active (updateDepth &gt; 0), the change is recorded as pending.
	 * </p>
	 *
	 * @param type change type (ignored if null)
	 */
	protected final void fireCurveChanged(CurveChangeType type) {
		if (type == null) {
			return;
		}

		if (updateDepth > 0) {
			switch (type) {
			case DATA:
				pendingData = true;
				break;
			case STYLE:
				pendingStyle = true;
				break;
			case FIT:
				pendingFit = true;
				break;
			}
			return;
		}

		Object[] listeners = curveListenerList.getListenerList();
		for (int i = listeners.length - 2; i >= 0; i -= 2) {
			if (listeners[i] == CurveChangeListener.class) {
				((CurveChangeListener) listeners[i + 1]).curveChanged(this, type);
			}
		}
	}

	/**
	 * Flush any pending changes after batching completes.
	 * <p>
	 * Must be called on the EDT.
	 * </p>
	 */
	private void flushPendingChanges() {
		// Chosen ordering: DATA, FIT, STYLE (consistent and predictable)
		if (pendingData) {
			pendingData = false;
			fireCurveChanged(CurveChangeType.DATA);
		}
		if (pendingFit) {
			pendingFit = false;
			fireCurveChanged(CurveChangeType.FIT);
		}
		if (pendingStyle) {
			pendingStyle = false;
			fireCurveChanged(CurveChangeType.STYLE);
		}
	}

	/** Initialize style with a unique style ID. */
	protected void initStyle() {
		style = new Styled(styleCount++);
	}

	/**
	 * Obtain a consistent snapshot of the current data, suitable for plotting without locking.
	 * <p>
	 * Subclasses should return copies of their internal primitive arrays (or equivalent immutable state),
	 * typically while synchronizing on {@link #lock}.
	 * </p>
	 *
	 * @return snapshot of plot-ready data
	 */
	public abstract Snapshot snapshot();

	@Override
	public String toString() {
		return name();
	}

	/**
	 * Ensure that the current thread is the Swing EDT.
	 *
	 * @param operation operation name used in the exception message
	 * @throws IllegalStateException if not on EDT
	 */
	protected static final void requireEdt(String operation) {
		if (!SwingUtilities.isEventDispatchThread()) {
			throw new IllegalStateException(operation + " must be called on the Swing EDT. "
					+ "For background threads, use enqueue(...) + drainPendingOnEDT(...).");
		}
	}
	
	// ====================================================================
	// Pending-queue infrastructure for background producers
	// ====================================================================
	
	/**
	 * Schedule a drain operation to run later on the Swing EDT, coalescing multiple
	 * requests into a single posted runnable.
	 * <p>
	 * This is intended for curves whose {@code add(...)} methods are safe to call
	 * from any thread: background calls enqueue data, then call this method to
	 * ensure the queued data is applied on the EDT.
	 * </p>
	 *
	 * <p>
	 * If many threads call this repeatedly, only the first call posts a runnable;
	 * subsequent calls are coalesced until the posted runnable begins execution.
	 * </p>
	 *
	 * @param drainOnEdt code that must execute on the EDT (typically calls
	 *                   {@code drainPendingOnEDT(...)} in the subclass)
	 */
	protected final void scheduleDrainOnce(Runnable drainOnEdt) {
		Objects.requireNonNull(drainOnEdt, "drainOnEdt");
		if (drainScheduled.compareAndSet(false, true)) {
			SwingUtilities.invokeLater(() -> {
				drainScheduled.set(false);
				drainOnEdt.run();
			});
		}
	}

	/**
	 * A reusable pending-queue helper for streaming/DAQ scenarios.
	 * <p>
	 * Producer threads enqueue items lock-free. Application occurs later on the EDT
	 * via {@link #drainPendingOnEDT(int, Consumer)}.
	 * </p>
	 *
	 * @param <T> pending item payload type (e.g. a point, a sample value, etc.)
	 */
	protected static final class PendingQueue<T> {

		private final ConcurrentLinkedQueue<T> queue = new ConcurrentLinkedQueue<>();
		private final AtomicLong pendingCount = new AtomicLong(0);
		private final AtomicLong totalEnqueued = new AtomicLong(0);
		private volatile Timer timer;

		/** Enqueue an item (thread-safe, lock-free). */
		public void enqueue(T item) {
			Objects.requireNonNull(item, "item");
			queue.add(item);
			pendingCount.incrementAndGet();
			totalEnqueued.incrementAndGet();
		}

		/**
		 * Enqueue multiple items (thread-safe, lock-free).
		 *
		 * @param items items to enqueue (non-null, no null elements)
		 */
		public void enqueueAll(List<? extends T> items) {
			Objects.requireNonNull(items, "items");
			for (T it : items) {
				enqueue(it);
			}
		}

		/** Clear any queued (not-yet-applied) items. */
		public void clear() {
			queue.clear();
			pendingCount.set(0);
		}

		/** @return approximate number of pending items */
		public long getPendingCount() {
			return pendingCount.get();
		}

		/** @return total number of items ever enqueued (monotonic) */
		public long getTotalEnqueued() {
			return totalEnqueued.get();
		}

		/**
		 * Drain up to {@code max} items on the EDT and apply them in one batch.
		 *
		 * @param max maximum number of items to drain
		 * @param applier called on the EDT with a non-empty batch of items
		 * @return number of drained items
		 * @throws IllegalStateException if called off the Swing EDT
		 */
		public int drainPendingOnEDT(int max, Consumer<List<T>> applier) {
			if (!SwingUtilities.isEventDispatchThread()) {
				throw new IllegalStateException("drainPendingOnEDT must be called on the Swing EDT.");
			}
			Objects.requireNonNull(applier, "applier");
			if (max <= 0) {
				return 0;
			}

			final ArrayList<T> batch = new ArrayList<>(Math.min(max, 256));

			T item;
			int drained = 0;
			while (drained < max && (item = queue.poll()) != null) {
				pendingCount.decrementAndGet();
				batch.add(item);
				drained++;
			}

			if (!batch.isEmpty()) {
				applier.accept(batch);
			}
			return drained;
		}

		/**
		 * Start a Swing {@link Timer} that periodically drains queued items on the EDT.
		 *
		 * @param periodMs timer period in milliseconds; if {@code periodMs <= 0}, no timer is started
		 * @param maxPerTick maximum number of items to drain per tick (prevents EDT starvation)
		 * @param drainAction action that performs the drain and returns drained count (EDT)
		 * @param drainedCallback optional callback invoked on the EDT with the drained count
		 */
		public void startDrainTimer(int periodMs,
				int maxPerTick,
				IntSupplier drainAction,
				IntConsumer drainedCallback) {

			stopDrainTimer();
			if (periodMs <= 0) {
				return;
			}
			Objects.requireNonNull(drainAction, "drainAction");
			
			timer = new Timer(periodMs, e -> {
				int drained = drainAction.getAsInt();
				if (drainedCallback != null) {
					drainedCallback.accept(drained);
				}
			});

			timer.setCoalesce(true);
			timer.start();
		}

		/** Stop the drain timer if running. */
		public void stopDrainTimer() {
			Timer t = timer;
			timer = null;
			if (t != null) {
				t.stop();
			}
		}
	}

}
