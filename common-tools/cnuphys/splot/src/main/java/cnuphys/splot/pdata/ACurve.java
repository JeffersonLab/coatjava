package cnuphys.splot.pdata;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.IPlottableFunction;
import cnuphys.splot.fit.IValueGetter;
import cnuphys.splot.fit.PlottableFunction;
import cnuphys.splot.fit.apache.AbstractLeastSquaresFitter;
import cnuphys.splot.fit.apache.FitResult;
import cnuphys.splot.fit.apache.IFitter;
import cnuphys.splot.spline.CubicSpline;
import cnuphys.splot.style.Styled;

/**
 * A curve consisting of X, Y, and E data.
 *
 * @author heddle
 *
 */
public abstract class ACurve {

	/** Used to assign stable-ish style ids. */
	private static int styleCount = 0;

    /**
     * Default order/count used for methods requiring an integer order:
     * <ul>
     *   <li>{@link cnuphys.splot.fit.CurveDrawingMethod#GAUSSIANS}: number of Gaussians</li>
     *   <li>{@link cnuphys.splot.fit.CurveDrawingMethod#HARMONIC}: harmonic order</li>
     * </ul>
     */
    private int order = 2;

	// visibility of the curve for UI
	private boolean visible = true;

	// dirty flag (data added, fit must be redone, etc)
	private boolean dirty = true;

	// fit result, if any
	private FitResult fitResult;

	// name of the curve
	private String name;

	/** Drawing style  */
	private Styled style;

	/** Cached cubic spline for {@link CurveDrawingMethod#CUBICSPLINE}. */
	private CubicSpline cubicSpline;

	/** How the curve should be drawn for this column (primarily Y columns). */
	private CurveDrawingMethod curveMethod = CurveDrawingMethod.NONE;

    /** Default polynomial degree used when {@link #_curveMethod} is POLYNOMIAL. */
    private int polynomialDegree = 3;

    /** Default number of Gaussians for GAUSSIANS drawing method. */
    private static final int DEFAULT_MULTI_GAUSS_COUNT = 2;

    /** Default omega scan steps for HARMONIC drawing method (initial guess resolution). */
    private static final int DEFAULT_HARMONIC_OMEGA_STEPS = 300;


	/**
	 * Create a curve with the given data.
	 *
	 * @param name the name of the curve (for the legend)
	 * @param xData the X data
	 * @param yData the Y data
	 * @param eData the E (Y error bar) data
	 */
	public ACurve(String name) {
		this.name = name;
	}

	/**
	 * Perform a curve fit
	 *
	 * @param force <code>true</code> to force a refit even if not dirty
	 */
	public abstract void doCurveFit(boolean force);

	/**
	 * Get the length of the data.
	 *
	 * @return the length of the data
	 */
	public abstract int length();

	/**
	 * Get the name for a legend
	 * @return the name used in a legend
	 */
	public String name() {
		return name;
	}

	/**
	 * Set the fit result
	 * @param fitResult the fit result
	 */
	public void setFitResult(FitResult fitResult) {
		this.fitResult = fitResult;
	}

	/**
	 * Get the fit result
	 * @return the fit result
	 */
	public FitResult fitResult() {
		return fitResult;
	}
	
	/**
	 * Get an evaluator y(x) for the current fit result, suitable for drawing an overlay.
	 * Returns null if there is no fit result, the method is not fit-based, or an evaluator
	 * cannot be constructed.
	 */
	public final IValueGetter getFitValueGetter() {
	    if (fitResult == null) {
	        return null;
	    }

	    cnuphys.splot.fit.apache.IFitter fitter = createFitterForCurrentMethod();
	    if (fitter == null) {
	        return null;
	    }

	    // All your fitters already implement asValueGetter(FitResult).
	    // If IFitter doesn't declare it, either:
	    //   (a) add it to IFitter, OR
	    //   (b) cast to the common base type that declares it (recommended below).
	    return ((AbstractLeastSquaresFitter) fitter).asValueGetter(fitResult);
	}
	
	/**
	 * Get a plottable function y(x) for the current fit result, suitable for drawing an overlay
	 * over the data range. Returns null if there is no fit result, the method is not fit-based,
	 * or an evaluator cannot be constructed.
	 *
	 * @param x the x data array used to infer the data range
	 * @return plottable function over data range, or null
	 */
	public final IPlottableFunction getFitPlottableOverDataRange(double[] x) {
	    IValueGetter g = getFitValueGetter();
	    if (g == null) return null;
	    return PlottableFunction.overDataRange(g, x);
	}


	/**
	 * Is the curve visible
	 * @return <code>true</code> if the curve is visible
	 */
	public boolean isVisible() {
		return visible;
	}

	/**
	 * Set the visibility of the curve
	 * @param visible <code>true</code> to make the curve visible
	 */
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	/**
	 * Is the curve dirty
	 * @return <code>true</code> if the curve is dirty
	 */
	public boolean isDirty() {
		return dirty;
	}

	/**
	 * Set the dirty flag
	 * @param dirty <code>true</code> to set the curve dirty
	 */
	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	/**
	 * Get the drawing style
	 * @return the drawing style
	 */
	public Styled getStyle() {
		return style;
	}

	/**
	 * Set the drawing style
	 * @param style the drawing style
	 */
	public void setStyle(Styled style) {
		this.style = style;
	}

	/**
	 * Get the cached cubic spline
	 *
	 * @return the cached cubic spline
	 */
	public CubicSpline getCubicSpline() {
		return cubicSpline;
	}

	/**
	 * Set the cached cubic spline
	 *
	 * @param cubicSpline the cached cubic spline
	 */
	public void setCubicSpline(CubicSpline cubicSpline) {
		this.cubicSpline = cubicSpline;
	}


	/**
	 * Get the curve drawing method for this curve.
	 *
	 * @return curve drawing method (never null)
	 */
	public CurveDrawingMethod getCurveDrawingMethod() {
		return curveMethod;
	}

	/**
	 * Set the curve drawing method.
	 * <p>
	 * Marks the column dirty and clears cached curve artifacts. Also auto-adjusts
	 * the {@code order} knob to a sensible default for certain methods:
	 * </p>
	 * <ul>
	 *   <li>GAUSSIANS: if current order &lt; 1, set to 2</li>
	 *   <li>HARMONIC: if current order &lt; 10, set to 300 (omegaSteps)</li>
	 * </ul>
	 *
	 * @param method new method (null treated as {@link CurveDrawingMethod#NONE})
	 */
	public void setCurveDrawingMethod(CurveDrawingMethod method) {
		curveMethod = (method == null) ? CurveDrawingMethod.NONE : method;

		// Auto-default the "order" knob depending on method.
		// We only override when the current value is clearly unsuitable, so
		// user-set values are preserved across toggles.
		switch (curveMethod) {
		case GAUSSIANS:
			if (order < 1) {
				order = DEFAULT_MULTI_GAUSS_COUNT;
			}
			break;

		case HARMONIC:
			// HARMONIC uses "order" as omegaSteps; very small values are unhelpful.
			if (order < 10) {
				order = DEFAULT_HARMONIC_OMEGA_STEPS;
			}
			break;

		default:
			// leave _order unchanged
			break;
		}

		clearComputedArtifacts();
	}

	/**
	 * Create a fitter appropriate to the current curve method and knobs
	 * (e.g. polynomial degree, order). Returns null if the current method
	 * is not a fit-based method.
	 */
	protected abstract cnuphys.splot.fit.apache.IFitter createFitterForCurrentMethod();

	/**
	 * Clear cached computed artifacts (fit result, spline, etc).
	 */
	protected void clearComputedArtifacts() {
		dirty = true;
	    fitResult = null;
	    cubicSpline = null;
	}

	/**
	 * Get the polynomial degree used when the curve method is POLYNOMIAL.
	 *
	 * @return degree (>= 0)
	 */
	public int getPolynomialDegree() {
	    return polynomialDegree;
	}

	/**
	 * Set the polynomial degree used when the curve method is POLYNOMIAL.
	 * Marks the column dirty and clears cached fit result.
	 *
	 * @param degree degree (values < 0 are coerced to 0)
	 */
	public void setPolynomialDegree(int degree) {
	    polynomialDegree = Math.max(0, degree);
	    clearComputedArtifacts();
	}



	/**
	 * Common pattern: if weights exist, call the weighted fit overload; otherwise call fit(x,y).
	 * <p>
	 * This utility performs small sanity checks on {@link FitVectors} before delegating to the fitter.
	 * It intentionally does <em>not</em> interpret fitter-specific bounds or guesses; those remain
	 * in the concrete fitter API (or future UI wiring).
	 * </p>
	 *
	 * @param fitter a fitter instance (must not be null)
	 * @param v fit vectors (must not be null)
	 * @return fit result, or null if inputs are incomplete/invalid
	 */
	protected static FitResult fitWithOptionalWeights(IFitter fitter, FitVectors v) {
	    if (fitter == null || v == null || !v.isUsable()) {
	        return null;
	    }

	    // If weights exist but are all zero/invalid, fall back to unweighted fit.
	    if (v.hasWeights() && v.hasAnyPositiveWeight()) {
	        return fitter.fit(v.x, v.y, v.weights);
	    }

	    return fitter.fit(v.x, v.y);
	}


	/**
	 * Get the per-curve order/count parameter. Fit related.
	 * <p>
	 * Interpretation depends on {@link #getCurveDrawingMethod()}:
	 * <ul>
	 *   <li>GAUSSIANS: number of Gaussians</li>
	 *   <li>HARMONIC: omegaSteps used in the omega scan for initial guess</li>
	 * </ul>
	 * </p>
	 *
	 * @return order/count (>= 1)
	 */
	public int getOrder() {
	    return order;
	}

	/**
	 * Set the per-curve order/count parameter.
	 * Marks the column dirty and clears cached fit result.
	 *
	 * @param order order/count (values < 1 are coerced to 1)
	 */
	public void setOrder(int order) {
	    this.order = Math.max(1, order);
	    clearComputedArtifacts();
	}

	/**
	 * Initialize a default style. Intended to be called by {@link DataSet}
	 * after creating Y columns.
	 */
	protected void initStyle() {
		style = new Styled(styleCount++);
	}

	@Override
	public String toString() {
		return name();
	}

}
