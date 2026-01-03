package cnuphys.splot.pdata;

import javax.swing.event.EventListenerList;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.Evaluator;
import cnuphys.splot.fit.FitResult;
import cnuphys.splot.fit.IFitter;
import cnuphys.splot.spline.CubicSpline;
import cnuphys.splot.style.Styled;

/**
 * Base class for all plottable curves (XY curves, histogram curves, strip-chart curves, etc.).
 * <p>
 * An {@code ACurve} supports:
 * <ul>
 *   <li>Visibility and style</li>
 *   <li>Curve drawing methods (fit-based, spline, etc.)</li>
 *   <li>Curve-level change notifications (data/style/fit)</li>
 *   <li>Batched updates via {@link #beginUpdate()} / {@link #endUpdate()}</li>
 * </ul>
 *
 * <h3>Fit evaluator</h3>
 * {@link FitResult} intentionally does not expose the fitter that produced it.
 * Therefore, when a fit is performed, the curve caches the corresponding
 * {@link Evaluator} produced at fit-time.
 *
 * @author heddle
 */
public abstract class ACurve {
	
	/** Synchronizes mutations and snapshots. */
	protected final Object lock = new Object();

	/** Used to assign stable-ish style ids. */
	private static int styleCount = 0;

	/** Per-curve order/count knob for fit methods that need an integer order. */
	private int order = 2;

	/** Default polynomial degree used when method is POLYNOMIAL. */
	private int polynomialDegree = 3;

	/** Visibility flag (UI). */
	private boolean visible = true;

	/** Dirty flag: data changed and cached artifacts are invalid. */
	private boolean dirty = true;

	/** Latest fit result (may be null). */
	private FitResult fitResult;

	/** Curve name (legend label). */
	private String name;

	/** Style for drawing. */
	private Styled style;

	/** Cached cubic spline for {@link CurveDrawingMethod#CUBICSPLINE}. */
	private CubicSpline cubicSpline;

	/** How the curve should be drawn. */
	private CurveDrawingMethod curveMethod = CurveDrawingMethod.NONE;

	/** Curve change listeners. */
	private final EventListenerList curveListenerList = new EventListenerList();

	/** Update batching depth. Default value of 0 means no batching
	 * of curve change notifications */
	private int updateDepth;

	/** Pending change flags while batching. */
	private boolean pendingData;
	private boolean pendingStyle;
	private boolean pendingFit;

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
	 * Perform a curve fit (or compute derived artifacts like splines), depending on
	 * the configured curve drawing method.
	 *
	 * @param force true to force a refit even if not dirty
	 */
	public abstract void doFit(boolean force);

	/**
	 * Get curve length (number of points or effective points
	 * in the data columns).
	 *
	 * @return curve length
	 */
	public abstract int length();

	/**
	 * Hook: subclasses that support fit-based curve methods may override this to provide
	 * the appropriate fitter for the current drawing method and per-curve knobs.
	 * <p>
	 * Default implementation returns {@code null}.
	 * </p>
	 *
	 * @return a fitter for the current method, or null if not applicable
	 */
	protected IFitter createFitterForCurrentMethod() {
		return null;
	}

	/** The curve name. 
	 * @return the curve name
	 */
	public final String name() {
		return name;
	}

	/**
	 * Set curve name.
	 *
	 * @param name curve name
	 */
	public final void setName(String name) {
		this.name = name;
		fireCurveChanged(CurveChangeType.STYLE);
	}

	/** @return true if visible */
	public boolean isVisible() {
		return visible;
	}

	/** Set curve visibility. */
	public void setVisible(boolean visible) {
		this.visible = visible;
		fireCurveChanged(CurveChangeType.STYLE);
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
		if (fitResult == null) {
			return null;
		}
		return fitResult.evaluator;
	}

	/**
	 * Set the fit result 
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

	/** Set the curve style. */
	public void setStyle(Styled style) {
		this.style = style;
		fireCurveChanged(CurveChangeType.STYLE);
	}

	/** @return true if curve is dirty */
	public boolean isDirty() {
		return dirty;
	}

	/** Set dirty flag. */
	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	/** @return cached cubic spline (may be null) */
	public CubicSpline getCubicSpline() {
		return cubicSpline;
	}

	/** Set cached cubic spline. */
	public void setCubicSpline(CubicSpline cubicSpline) {
		this.cubicSpline = cubicSpline;
	}

	/** @return curve drawing method */
	public CurveDrawingMethod getCurveDrawingMethod() {
		return curveMethod;
	}

	/**
	 * Set curve drawing method and mark style changed.
	 *
	 * @param method method (null treated as NONE)
	 */
	public void setCurveDrawingMethod(CurveDrawingMethod method) {
		curveMethod = (method == null) ? CurveDrawingMethod.NONE : method;
		markStyleChanged();
	}

	/** @return per-curve order/count (>= 1) */
	public int getOrder() {
		return order;
	}

	/** Set per-curve order/count. */
	public void setOrder(int order) {
		this.order = Math.max(1, order);
		markStyleChanged();
	}

	/** @return polynomial degree (>= 0) */
	public int getPolynomialDegree() {
		return polynomialDegree;
	}

	/** Set polynomial degree. */
	public void setPolynomialDegree(int degree) {
		polynomialDegree = Math.max(0, degree);
		markStyleChanged();
	}

	/**
	 * Common pattern: if weights exist, call the weighted fit overload; otherwise call fit(x,y).
	 * <p>
	 * This utility performs small sanity checks on {@link FitVectors} before delegating to the fitter.
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
	 * Clear computed artifacts (fit result, fit evaluator, spline cache) and mark dirty.
	 * <p>
	 * This does <b>not</b> notify listeners. Use {@link #markDataChanged()} or
	 * {@link #markStyleChanged()} when you want notifications.
	 * </p>
	 */
	void clearComputedArtifacts() {
		dirty = true;
		fitResult = null;
		cubicSpline = null;
	}

	/** Mark data changed: clear computed artifacts and notify listeners. */
	protected final void markDataChanged() {
		clearComputedArtifacts();
		fireCurveChanged(CurveChangeType.DATA);
	}
	
	/** Notify listeners that data has changed. */
	public final void dataChanged() {
		markDataChanged();
	}

	/** Mark style changed: clear computed artifacts and notify listeners. */
	protected final void markStyleChanged() {
		dirty = true; //other artifacts unchanged
		fireCurveChanged(CurveChangeType.STYLE);
	}

	/** Notify listeners that style has changed. */
	public final void styleChanged() {
		markStyleChanged();
	}
	
	/** Mark fit changed: notify listeners. */
	protected final void markFitChanged() {
		fireCurveChanged(CurveChangeType.FIT);
	}

	/** For batching */
	public final void beginUpdate() {
		updateDepth++;
	}

	/** For batching */
	public final void endUpdate() {
		if (updateDepth > 0) {
			updateDepth--;
		}
		
		//batching complete
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
	 * @param listener
	 */
	public final void addCurveChangeListener(CurveChangeListener listener) {
		if (listener != null) {
			curveListenerList.add(CurveChangeListener.class, listener);
		}
	}

	/** Remove a curve change listener. 
	 * @param listener the listener to remove
	 */
	public final void removeCurveChangeListener(CurveChangeListener listener) {
		if (listener != null) {
			curveListenerList.remove(CurveChangeListener.class, listener);
		}
	}

	/** Notify listeners of curve change. */
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

	/** Flush any pending changes after batching. */
	private void flushPendingChanges() {
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

	/** Initialize style with unique style ID. */
	protected void initStyle() {
		style = new Styled(styleCount++);
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
	public abstract Snapshot snapshot();

	@Override
	public String toString() {
		return name();
	}
}
