package cnuphys.splot.pdata;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.apache.FitResult;
import cnuphys.splot.spline.CubicSpline;
import cnuphys.splot.style.Styled;

/**
 * A single named numeric column in a {@link DataSet}.
 * <p>
 * A {@code DataColumn} is a primitive {@code double} container (via {@link GrowableArray})
 * plus metadata used by sPlot:
 * </p>
 * <ul>
 *   <li>Column type: X, Y, or YERR</li>
 *   <li>A display name</li>
 *   <li>Visibility (primarily for Y columns / curves)</li>
 *   <li>A drawing style ({@link Styled})</li>
 *   <li>A selected curve drawing method ({@link CurveDrawingMethod})</li>
 *   <li>Cached curve artifacts computed by {@link DataSet#doCurveFits(boolean)}:
 *       a {@link FitResult} for fit-based methods, or a {@link CubicSpline} for splines</li>
 * </ul>
 *
 * <h3>Histogram mode</h3>
 * For 1D histogram datasets ({@link DataSetType#H1D}), a Y column may be backed by a
 * {@link HistoData} object. In that case:
 * <ul>
 *   <li>{@link #add(double)} adds a raw sample to the histogram (not a y-value)</li>
 *   <li>curve fitting uses bin centers and counts from {@link HistoData#prepareForFit}</li>
 * </ul>
 *
 * <p>
 * This class intentionally participates in curve fitting and caching (MVC-violating),
 * because the current goal is “fully functional first,” and refactoring can follow.
 * </p>
 *
 * @author heddle
 */
public class OldDataColumn extends GrowableArray {

	/** Used to assign stable-ish style ids. */
	private static int _count = 0;

	/** Drawing style (typically meaningful for Y columns). */
	private Styled _style;

	/** Column name shown in UI. */
	protected String _name;

	/** Visibility flag (primarily for curves / Y columns). */
	protected boolean _visible = true;

	// ------------------------------------------------------------------------
	// Histogram support
	// ------------------------------------------------------------------------

	/** Backing histogram (only for DataSetType.H1D). */
	private HistoData _histoData1D;

	/** True if this column is backed by a 1D histogram. */
	private boolean _isHisto1D = false;

	// ------------------------------------------------------------------------
	// Dirty flag + curve caching
	// ------------------------------------------------------------------------

	/** True if cached fit/spline should be recomputed. */
	private boolean _dirty = true;

	/** How the curve should be drawn for this column (primarily Y columns). */
	private CurveDrawingMethod _curveMethod = CurveDrawingMethod.NONE;

	/** Cached fit result for fit-based drawing methods. */
	private FitResult _fitResult;

	/** Cached cubic spline for {@link CurveDrawingMethod#CUBICSPLINE}. */
	private CubicSpline _cubicSpline;
	
    /** Default polynomial degree used when {@link #_curveMethod} is POLYNOMIAL. */
    private int _polynomialDegree = 3;
    
    /** Default number of Gaussians for GAUSSIANS drawing method. */
    private static final int DEFAULT_MULTI_GAUSS_COUNT = 2;

    /** Default omega scan steps for HARMONIC drawing method (initial guess resolution). */
    private static final int DEFAULT_HARMONIC_OMEGA_STEPS = 300;


    /**
     * Default order/count used for methods requiring an integer order:
     * <ul>
     *   <li>{@link cnuphys.splot.fit.CurveDrawingMethod#GAUSSIANS}: number of Gaussians</li>
     *   <li>{@link cnuphys.splot.fit.CurveDrawingMethod#HARMONIC}: harmonic order</li>
     * </ul>
     */
    private int _order = 2;


	// ------------------------------------------------------------------------
	// (Optional) running stats for histogram-sample mode
	// ------------------------------------------------------------------------



	/**
	 * Running mean accumulator (Welford) for histogram-backed sample adds.
	 * Only meaningful when {@link #_isHisto1D} is true.
	 */
	private double _M;

	/**
	 * Running Q accumulator (Welford) for histogram-backed sample adds.
	 * Only meaningful when {@link #_isHisto1D} is true.
	 */
	private double _Q;

	/**
	 * Create a DataColumn.
	 *
	 * @param name      the display name
	 */
	public OldDataColumn(String name) {
		super();
		_name = name;
		_dirty = true;
		resetHistoRunningStats();
	}

	/**
	 * If this column is histogram-backed, returns the histogram; otherwise null.
	 *
	 * @return histogram backing store, or null
	 */
	public HistoData getHistoData() {
		return _histoData1D;
	}

	/**
	 * Assign histogram backing for a 1D histogram dataset.
	 * Intended to be called by {@link DataSet} constructors.
	 *
	 * @param histo histogram backing store (may be null)
	 */
	protected void setHistoData(HistoData histo) {
		_histoData1D = histo;
		_isHisto1D = (histo != null);
		_dirty = true;
		resetHistoRunningStats();
	}

	/**
	 * Whether this column is backed by a 1D histogram.
	 *
	 * @return true if histogram-backed
	 */
	public boolean isHistogram1D() {
		return _isHisto1D;
	}

	/**
	 * Visibility is primarily relevant for Y columns (curves).
	 *
	 * @return true if visible
	 */
	public boolean isVisible() {
		return _visible;
	}

	/**
	 * Set the display name. If histogram-backed, also sets the histogram name.
	 *
	 * @param name new name (null is treated as empty)
	 */
	public void setName(String name) {
		_name = name;
		if (_isHisto1D && _histoData1D != null) {
			_histoData1D.setName(getName());
		}
	}

	/**
	 * Get the display name.
	 *
	 * @return non-null name string
	 */
	public String getName() {
		return (_name != null) ? _name : "";
	}

	/**
	 * Set visibility (primarily for Y/curve columns).
	 *
	 * @param visible visibility flag
	 */
	public void setVisible(boolean visible) {
		_visible = visible;
	}
	
	/**
	 * Get the polynomial degree used when the curve method is POLYNOMIAL.
	 *
	 * @return degree (>= 0)
	 */
	public int getPolynomialDegree() {
	    return _polynomialDegree;
	}

	/**
	 * Set the polynomial degree used when the curve method is POLYNOMIAL.
	 * Marks the column dirty and clears cached fit result.
	 *
	 * @param degree degree (values < 0 are coerced to 0)
	 */
	public void setPolynomialDegree(int degree) {
	    _polynomialDegree = Math.max(0, degree);
	    _dirty = true;
	    _fitResult = null;
	}

	/**
	 * Get the per-curve order/count parameter.
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
	    return _order;
	}

	/**
	 * Set the per-curve order/count parameter.
	 * Marks the column dirty and clears cached fit result.
	 *
	 * @param order order/count (values < 1 are coerced to 1)
	 */
	public void setOrder(int order) {
	    _order = Math.max(1, order);
	    _dirty = true;
	    _fitResult = null;
	}
	
	/**
	 * Initialize a default style. Intended to be called by {@link DataSet}
	 * after creating Y columns.
	 */
	protected void initStyle() {
		_style = new Styled(_count++);
	}

	/**
	 * Set style explicitly (used by style editing UI).
	 *
	 * @param style a style instance
	 */
	protected void setStyle(Styled style) {
		_style = style;
	}

	/**
	 * Get the style for this column.
	 *
	 * @return the style (may be null if not initialized yet)
	 */
	public Styled getStyle() {
		return _style;
	}

	@Override
	public String toString() {
		return getName();
	}

	/**
	 * Add a value.
	 * <ul>
	 *   <li>For normal columns: appends to the internal buffer.</li>
	 *   <li>For histogram-backed columns: adds a raw sample to the histogram.</li>
	 * </ul>
	 *
	 * <p>
	 * Marks this column dirty so cached curve artifacts are recomputed.
	 * </p>
	 *
	 * @param val value to add
	 */
	@Override
	public void add(double val) {
		_dirty = true;

		if (_isHisto1D) {
			if (_histoData1D != null) {
				_histoData1D.add(val);
			}
			updateHistoRunningStats(val);
		}
		else {
			super.add(val);
		}
	}

	/**
	 * Get mean of the data in this column.
	 * <p>
	 * For histogram-backed columns, this returns the mean of the <em>samples added</em>
	 * (not bin-center weighted mean).
	 * </p>
	 *
	 * @return mean, or NaN if empty
	 */
	public double getMean() {
		if (_isHisto1D) {
			long n = (_histoData1D == null) ? 0 : _histoData1D.getTotalCount();
			return (n > 0) ? _M : Double.NaN;
		}

		int n = size();
		if (n <= 0) {
			return Double.NaN;
		}
		double sum = 0.0;
		for (int i = 0; i < n; i++) {
			sum += super.get(i);
		}
		return sum / n;
	}

	/**
	 * Get (population) variance of the data in this column.
	 * <p>
	 * For histogram-backed columns, this returns the variance of the <em>samples added</em>.
	 * </p>
	 *
	 * @return variance, or NaN if empty
	 */
	public double getVariance() {
		if (_isHisto1D) {
			long n = (_histoData1D == null) ? 0 : _histoData1D.getTotalCount();
			if (n <= 0) {
				return Double.NaN;
			}
			if (n == 1) {
				return 0.0;
			}
			return _Q / n; // population variance consistent with original behavior
		}

		int n = size();
		if (n <= 0) {
			return Double.NaN;
		}
		if (n == 1) {
			return 0.0;
		}
		double mean = getMean();
		double ss = 0.0;
		for (int i = 0; i < n; i++) {
			double d = super.get(i) - mean;
			ss += d * d;
		}
		return ss / n;
	}

	/**
	 * Standard deviation of the data in this column.
	 *
	 * @return standard deviation, or NaN if empty
	 */
	public double getStandardDeviation() {
		double var = getVariance();
		return Double.isNaN(var) ? Double.NaN : Math.sqrt(Math.max(0.0, var));
	}

	/**
	 * Clear the data.
	 * <p>
	 * For histogram-backed columns, this clears the underlying histogram.
	 * </p>
	 */
	@Override
	public void clear() {
		_dirty = true;
		_fitResult = null;
		_cubicSpline = null;

		if (_isHisto1D) {
			if (_histoData1D != null) {
				_histoData1D.clear();
			}
			resetHistoRunningStats();
		}
		else {
			super.clear();
		}
	}

	/**
	 * Set a value at index (normal columns only).
	 * <p>
	 * For histogram-backed columns, {@code set} does not have a meaningful
	 * interpretation and will throw {@link UnsupportedOperationException}.
	 * </p>
	 *
	 * @param index index in [0, size)
	 * @param val new value
	 */
	@Override
	public void set(int index, double val) {
		if (_isHisto1D) {
			throw new UnsupportedOperationException("set(index,val) not supported for histogram-backed columns.");
		}
		super.set(index, val);
		_dirty = true;
	}

	/**
	 * Whether this column needs its curve artifacts recomputed.
	 *
	 * @return true if dirty
	 */
	public boolean isDirty() {
		return _dirty;
	}

	/**
	 * Set dirty flag. Typically used internally by {@link DataSet}.
	 *
	 * @param dirty dirty flag
	 */
	public void setDirty(boolean dirty) {
		_dirty = dirty;
	}

	/**
	 * Get the curve drawing method for this column (primarily Y columns).
	 *
	 * @return curve drawing method (never null)
	 */
	public CurveDrawingMethod getCurveDrawingMethod() {
		return _curveMethod;
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
		_curveMethod = (method == null) ? CurveDrawingMethod.NONE : method;

		// Auto-default the "order" knob depending on method.
		// We only override when the current value is clearly unsuitable, so
		// user-set values are preserved across toggles.
		switch (_curveMethod) {
		case GAUSSIANS:
			if (_order < 1) {
				_order = DEFAULT_MULTI_GAUSS_COUNT;
			}
			break;

		case HARMONIC:
			// HARMONIC uses "order" as omegaSteps; very small values are unhelpful.
			if (_order < 10) {
				_order = DEFAULT_HARMONIC_OMEGA_STEPS;
			}
			break;

		default:
			// leave _order unchanged
			break;
		}

		_dirty = true;
		_fitResult = null;
		_cubicSpline = null;
	}

	/**
	 * Get cached fit result (only meaningful for fit-based curve methods).
	 *
	 * @return fit result, or null if none/failed/not applicable
	 */
	public FitResult getFitResult() {
		return _fitResult;
	}

	/**
	 * Set cached fit result.
	 *
	 * @param fitResult fit result (may be null)
	 */
	public void setFitResult(FitResult fitResult) {
		_fitResult = fitResult;
	}

	/**
	 * Get cached cubic spline (only meaningful for {@link CurveDrawingMethod#CUBICSPLINE}).
	 *
	 * @return spline, or null if none/failed/not applicable
	 */
	public CubicSpline getCubicSpline() {
		return _cubicSpline;
	}

	/**
	 * Set cached cubic spline.
	 *
	 * @param cubicSpline spline (may be null)
	 */
	public void setCubicSpline(CubicSpline cubicSpline) {
		_cubicSpline = cubicSpline;
	}

	// ------------------------------------------------------------------------
	// Histogram running stats helpers (sample-based)
	// ------------------------------------------------------------------------

	private void resetHistoRunningStats() {
		_M = 0.0;
		_Q = 0.0;
	}

	private void updateHistoRunningStats(double val) {

		long n = (_histoData1D == null) ? 0 : _histoData1D.getTotalCount();
		if (n <= 0) {
			return;
		}
		if (n == 1) {
			_M = val;
			_Q = 0.0;
		}
		else {
			double fac = (val - _M);
			double fac2 = fac / n;
			_M = _M + fac2;
			_Q = _Q + (n - 1) * fac * fac2;
		}
	}
}
