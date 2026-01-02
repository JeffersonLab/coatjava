package cnuphys.splot.pdata;

import javax.swing.event.EventListenerList;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.IValueGetter;
import cnuphys.splot.fit.apache.FitResult;
import cnuphys.splot.fit.apache.IFitter;
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
 * {@link IValueGetter} produced by {@link IFitter#asValueGetter(FitResult)} at fit-time.
 *
 * @author heddle
 */
public abstract class ACurve {

	/** Used to assign stable-ish style ids. */
	private static int styleCount = 0;

	/** Per-curve order/count knob for methods that need an integer order. */
	private int order = 2;

	/** Default polynomial degree used when method is POLYNOMIAL. */
	private int polynomialDegree = 3;

	/** Visibility flag (UI). */
	private boolean visible = true;

	/** Dirty flag: data changed and cached artifacts are invalid. */
	private boolean dirty = true;

	/** Latest fit result (may be null). */
	private FitResult fitResult;

	/**
	 * Cached fit evaluator. This is stored at fit-time because FitResult does not
	 * expose the fitter that produced it.
	 */
	private IValueGetter fitValueGetter;

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

	/** Update batching depth. */
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
	 * Get curve length (number of points or effective points).
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

	/** @return curve name */
	public final String getName() {
		return name;
	}

	/** Convenience alias (older code). */
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
	public final IValueGetter getFitValueGetter() {
		return fitValueGetter;
	}

	/**
	 * Set the fit result only.
	 * <p>
	 * This clears the cached evaluator, since without the fitter we cannot rebuild it.
	 * Normally callers should use {@link #setFitArtifacts(FitResult, IValueGetter)}.
	 * </p>
	 *
	 * @param fitResult the fit result (may be null)
	 */
	public void setFitResult(FitResult fitResult) {
		this.fitResult = fitResult;
		this.fitValueGetter = null;
		markFitChanged();
	}

	/**
	 * Set both the fit result and the corresponding cached evaluator (preferred).
	 *
	 * @param fitResult      fit result (may be null)
	 * @param fitValueGetter evaluator corresponding to {@code fitResult} (may be null)
	 */
	public final void setFitArtifacts(FitResult fitResult, IValueGetter fitValueGetter) {
		this.fitResult = fitResult;
		this.fitValueGetter = fitValueGetter;
		markFitChanged();
	}

	/** @return curve style */
	public Styled getStyle() {
		return style;
	}

	/** Set curve style. */
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
		fitValueGetter = null;
		cubicSpline = null;
	}

	protected final void markDataChanged() {
		clearComputedArtifacts();
		fireCurveChanged(CurveChangeType.DATA);
	}

	public final void dataChanged() {
		markDataChanged();
	}

	protected final void markStyleChanged() {
		clearComputedArtifacts();
		fireCurveChanged(CurveChangeType.STYLE);
	}

	public final void styleChanged() {
		markStyleChanged();
	}

	protected final void markFitChanged() {
		fireCurveChanged(CurveChangeType.FIT);
	}

	public final void beginUpdate() {
		updateDepth++;
	}

	public final void endUpdate() {
		if (updateDepth > 0) {
			updateDepth--;
		}
		if (updateDepth == 0) {
			flushPendingChanges();
		}
	}

	public final void addCurveChangeListener(CurveChangeListener listener) {
		if (listener != null) {
			curveListenerList.add(CurveChangeListener.class, listener);
		}
	}

	public final void removeCurveChangeListener(CurveChangeListener listener) {
		if (listener != null) {
			curveListenerList.remove(CurveChangeListener.class, listener);
		}
	}

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

	protected void initStyle() {
		style = new Styled(styleCount++);
	}

	@Override
	public String toString() {
		return name();
	}
}
