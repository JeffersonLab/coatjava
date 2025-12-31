package cnuphys.splot.pdata;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Vector;

import javax.swing.event.EventListenerList;
import javax.swing.table.DefaultTableModel;

import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.apache.ErfErfcFitter;
import cnuphys.splot.fit.apache.FitResult;
import cnuphys.splot.fit.apache.GaussianFitter;
import cnuphys.splot.fit.apache.HarmonicFitter;
import cnuphys.splot.fit.apache.IInitialGuess;
import cnuphys.splot.fit.apache.MultiGaussianFitter;
import cnuphys.splot.fit.apache.PolynomialFitter;
import cnuphys.splot.plot.DoubleFormat;
import cnuphys.splot.style.IStyled;
import cnuphys.splot.style.Styled;
import cnuphys.splot.style.SymbolType;

/**
 * A {@code DataSet} is a table model backed by a list of {@link OldDataColumn}s.
 * <p>
 * DataSet supports multiple structural layouts defined by {@link DataSetType}:
 * </p>
 * <ul>
 *   <li>{@link DataSetType#XYXY}: repeated (X,Y) column pairs</li>
 *   <li>{@link DataSetType#XYEXYE}: repeated (X,Y,YERR) column triplets</li>
 *   <li>{@link DataSetType#H1D}: one or more 1D histograms (each histogram is a Y column)</li>
 *   <li>{@link DataSetType#STRIP}: a simple strip chart dataset (X,Y)</li>
 * </ul>
 *
 * <h3>Curve drawing + fitting</h3>
 * Each Y column (also called a "curve") has a {@link CurveDrawingMethod}.
 * When the method requires a fit or spline, {@link #doCurveFits(boolean)} computes and
 * caches the result on the corresponding {@link OldDataColumn}.
 *
 * <p>
 * <strong>MVC note:</strong> This class currently performs fitting directly (violates MVC),
 * intentionally, to get everything functional. You can later refactor fitting into a controller/service.
 * </p>
 *
 * @author heddle
 */
@SuppressWarnings("serial")
public class DataSet extends DefaultTableModel {


	// ------------------------------------------------------------------------
	// Core data
	// ------------------------------------------------------------------------

	/** Column storage. */
	private final Vector<OldDataColumn> _columns = new Vector<>();

	/** Dataset change listeners. */
	private final EventListenerList _listenerList = new EventListenerList();

	/** Dataset type. */
	private final DataSetType _type;

	// ------------------------------------------------------------------------
	// Constructors
	// ------------------------------------------------------------------------

	/**
	 * Construct a 1D histogram dataset from one or more {@link HistoData} objects.
	 * Each histogram becomes a Y column.
	 *
	 * @param histos histogram data objects (must be non-null and length &gt;= 1)
	 * @throws DataSetException if no histograms are supplied
	 */
	public DataSet(HistoData... histos) throws DataSetException {
		if (histos == null || histos.length < 1) {
			throw new DataSetException("Must supply at least one histogram data object.");
		}
		_type = DataSetType.H1D;

		for (int i = 0; i < histos.length; i++) {
			HistoData hd = histos[i];
			if (hd != null) {
				OldDataColumn y = new OldDataColumn(DataColumnType.Y, hd.getName());
				y.setHistoData(hd);
				y.initStyle();
				y.getStyle().setSymbolType(SymbolType.NOSYMBOL);
				_columns.add(y);
			}
		}
	}

	/**
	 * Create a dataset for a simple strip chart (X,Y) and attach it to the provided
	 * {@link StripData} object.
	 *
	 * @param stripData the strip chart backing data
	 * @param colNames  column names (expected length 2: xName, yName)
	 */
	public DataSet(StripData stripData, String... colNames) {
		super(colNames, 0);
		_type = DataSetType.STRIP;

		_columns.add(new OldDataColumn(DataColumnType.X, colNames[0]));
		_columns.add(new OldDataColumn(DataColumnType.Y, colNames[1]));

		// default curve style + drawing method for strip chart
		getColumn(1).initStyle();
		getColumn(1).setCurveDrawingMethod(CurveDrawingMethod.STAIRS);

		if (stripData != null) {
			stripData.setDataSet(this);
		}
	}

	/**
	 * Create a dataset of the given structural type using column names.
	 *
	 * @param type     dataset type
	 * @param colNames column names; must match the structural expectations of {@code type}
	 * @throws DataSetException if the number of columns is incompatible with {@code type}
	 */
	public DataSet(DataSetType type, String... colNames) throws DataSetException {
		super(colNames, 0);

		if (type == null) {
			throw new DataSetException("DataSetType is null.");
		}
		_type = type;

		final int colCount = (colNames == null) ? 0 : colNames.length;

		switch (type) {

		case XYXY:
			if ((colCount % 2) != 0) {
				throw new DataSetException("The number of columns " + colCount + " is not divisible by 2.");
			}
			for (int i = 0; i < colCount / 2; i++) {
				int j = i * 2;
				_columns.add(new OldDataColumn(DataColumnType.X, colNames[j]));
				_columns.add(new OldDataColumn(DataColumnType.Y, colNames[j + 1]));
				getColumn(j + 1).initStyle();
			}
			break;

		case XYEXYE:
			if ((colCount % 3) != 0) {
				throw new DataSetException("The number of columns for type XYEXYE " + colCount + " is not divisible by 3.");
			}
			for (int i = 0; i < colCount / 3; i++) {
				int j = i * 3;
				_columns.add(new OldDataColumn(DataColumnType.X, colNames[j]));
				_columns.add(new OldDataColumn(DataColumnType.Y, colNames[j + 1]));
				_columns.add(new OldDataColumn(DataColumnType.YERR, colNames[j + 2]));
				getColumn(j + 1).initStyle();
			}
			break;

		case H1D:
			throw new DataSetException("Use DataSet(HistoData...) constructor for 1D histograms.");

		case STRIP:
			// handled by strip ctor
			throw new DataSetException("Use DataSet(StripData, ...) constructor for STRIP data.");
		}
	}

	// ------------------------------------------------------------------------
	// Curve fitting (Apache fitters)
	// ------------------------------------------------------------------------

	/**
	 * Perform curve fits / spline construction for all Y columns.
	 * <p>
	 * What is computed depends on each Y column's {@link CurveDrawingMethod}.
	 * Results are cached on the {@link OldDataColumn}:
	 * </p>
	 * <ul>
	 *   <li>{@link CurveDrawingMethod#CUBICSPLINE}: sets {@link OldDataColumn#setCubicSpline(CubicSpline)}</li>
	 *   <li>Fit-based methods: sets {@link OldDataColumn#setFitResult(FitResult)}</li>
	 * </ul>
	 *
	 * <p>
	 * For histogram datasets ({@link DataSetType#H1D}), fitting uses bin centers and counts
	 * as (x,y) via {@link HistoData#prepareForFit(boolean, double, double, boolean)} which
	 * also provides weights.
	 * </p>
	 *
	 * @param force if true, recompute even if column is not marked dirty
	 */
	public void doCurveFits(boolean force) {

		for (OldDataColumn ycol : _columns) {
			if (ycol.getType() != DataColumnType.Y) {
				continue;
			}
			if (!force && !ycol.isDirty()) {
				continue;
			}

			// clear cached artifacts
			ycol.setFitResult(null);
			ycol.setCubicSpline(null);

			try {
				final CurveDrawingMethod method = ycol.getCurveDrawingMethod();

				switch (method) {

				case NONE:
				case CONNECT:
				case STAIRS:
					// nothing to compute
					break;

				case CUBICSPLINE: {
					FitVectors v = buildFitVectors(ycol);
					if (v != null && v.x.length >= 2) {
						ycol.setCubicSpline(new cnuphys.splot.spline.CubicSpline(v.x, v.y));
					}
					break;
				}

				case POLYNOMIAL: {
				    FitVectors v = buildFitVectors(ycol);
				    if (v != null && v.x.length >= 2) {
				        int degree = ycol.getPolynomialDegree();
				        PolynomialFitter pf = new PolynomialFitter(degree);
				        ycol.setFitResult(fitWithOptionalWeights(pf, v));
				    }
				    break;
				}

				case GAUSSIAN: {
					FitVectors v = buildFitVectors(ycol);
					if (v != null && v.x.length >= 2) {
						GaussianFitter gf = new GaussianFitter();
						ycol.setFitResult(fitWithOptionalWeights(gf, v));
					}
					break;
				}

				case GAUSSIANS: {
				    FitVectors v = buildFitVectors(ycol);
				    if (v != null && v.x.length >= 2) {
				        int nGauss = ycol.getOrder();
				        MultiGaussianFitter mg = new MultiGaussianFitter(nGauss);
				        ycol.setFitResult(fitWithOptionalWeights(mg, v));
				    }
				    break;
				}

				case HARMONIC: {
				    FitVectors v = buildFitVectors(ycol);
				    if (v != null && v.x.length >= 2) {

				        // Interpret per-curve "order" as omega scan resolution (omegaSteps).
				        // If user leaves it small (e.g., 2 default used for multi-gauss), fall back to fitter default.
				        int omegaSteps = ycol.getOrder();
				        if (omegaSteps < 10) {
				            omegaSteps = HarmonicFitter.DEFAULT_OMEGA_STEPS;
				        }

				        IInitialGuess guesser =
				                HarmonicFitter.defaultGuesser(
				                        true, // withOffset (matches HarmonicFitter() default)
				                        omegaSteps,
				                        HarmonicFitter.DEFAULT_MIN_CYCLES_OVER_SPAN,
				                        HarmonicFitter.DEFAULT_MAX_CYCLES_OVER_SPAN);

				        HarmonicFitter hf = new HarmonicFitter(
				                true,
				                new LevenbergMarquardtOptimizer(),
				                guesser);

				        ycol.setFitResult(fitWithOptionalWeights(hf, v));
				    }
				    break;
				}

				case ERF: {
					FitVectors v = buildFitVectors(ycol);
					if (v != null && v.x.length >= 2) {
						ErfErfcFitter ef = new ErfErfcFitter(ErfErfcFitter.Kind.ERF);
						ycol.setFitResult(fitWithOptionalWeights(ef, v));
					}
					break;
				}

				case ERFC: {
					FitVectors v = buildFitVectors(ycol);
					if (v != null && v.x.length >= 2) {
						ErfErfcFitter ef = new ErfErfcFitter(ErfErfcFitter.Kind.ERFC);
						ycol.setFitResult(fitWithOptionalWeights(ef, v));
					}
					break;
				}
				}
			}
			catch (Exception e) {
				// fail soft: leave artifacts null
				ycol.setFitResult(null);
				ycol.setCubicSpline(null);
			}
			finally {
				ycol.setDirty(false);
			}
		}
	}

	/**
	 * Common pattern: if weights exist, call the weighted fit overload; otherwise call fit(x,y).
	 * <p>
	 * Each fitter defines its own {@code ParameterBounds} inner type, so we do not try to share
	 * a bounds object here. For now we pass {@code null} bounds and {@code null} initial guesses.
	 * These can be wired in later (per-fitter) when you add UI controls.
	 * </p>
	 *
	 * @param fitter a concrete fitter instance
	 * @param v fit vectors
	 * @return fit result or null
	 */
	private static FitResult fitWithOptionalWeights(Object fitter, FitVectors v) {
		if (v == null) {
			return null;
		}

		// Unweighted overloads
		if (v.weights == null) {
			if (fitter instanceof PolynomialFitter) {
				return ((PolynomialFitter) fitter).fit(v.x, v.y);
			}
			if (fitter instanceof GaussianFitter) {
				return ((GaussianFitter) fitter).fit(v.x, v.y);
			}
			if (fitter instanceof MultiGaussianFitter) {
				return ((MultiGaussianFitter) fitter).fit(v.x, v.y);
			}
			if (fitter instanceof HarmonicFitter) {
				return ((HarmonicFitter) fitter).fit(v.x, v.y);
			}
			if (fitter instanceof ErfErfcFitter) {
				return ((ErfErfcFitter) fitter).fit(v.x, v.y);
			}
			return null;
		}

		// Weighted overloads: bounds + initialGuess are fitter-specific, so pass null for now.
		double[] initialGuess = null;

		if (fitter instanceof PolynomialFitter) {
			return ((PolynomialFitter) fitter).fit(v.x, v.y, v.weights,
					(PolynomialFitter.ParameterBounds) null, initialGuess);
		}
		if (fitter instanceof GaussianFitter) {
			return ((GaussianFitter) fitter).fit(v.x, v.y, v.weights,
					(GaussianFitter.ParameterBounds) null, initialGuess);
		}
		if (fitter instanceof MultiGaussianFitter) {
			return ((MultiGaussianFitter) fitter).fit(v.x, v.y, v.weights,
					(MultiGaussianFitter.ParameterBounds) null, initialGuess);
		}
		if (fitter instanceof HarmonicFitter) {
			return ((HarmonicFitter) fitter).fit(v.x, v.y, v.weights,
					(HarmonicFitter.ParameterBounds) null, initialGuess);
		}
		if (fitter instanceof ErfErfcFitter) {
			return ((ErfErfcFitter) fitter).fit(v.x, v.y, v.weights,
					(ErfErfcFitter.ParameterBounds) null, initialGuess);
		}

		return null;
	}


	// ------------------------------------------------------------------------
	// Fit vector preparation
	// ------------------------------------------------------------------------

	/**
	 * Small container for fit vectors.
	 */
	private static final class FitVectors {
		final double[] x;
		final double[] y;
		final double[] weights; // may be null

		FitVectors(double[] x, double[] y, double[] weights) {
			this.x = x;
			this.y = y;
			this.weights = weights;
		}
	}

	/**
	 * Build x/y/(optional weights) vectors for fitting a Y column.
	 * <p>
	 * Histogram datasets use {@link HistoData#prepareForFit} (x=bin centers, y=counts).
	 * XYEXYE datasets convert YERR into weights using w=1/sigma^2.
	 * Other datasets use unit weights (null weights).
	 * </p>
	 *
	 * @param ycol a Y column
	 * @return vectors, or null if insufficient data
	 */
	private FitVectors buildFitVectors(OldDataColumn ycol) {
		if (ycol == null) {
			return null;
		}

		// Histogram case
		if (is1DHistoSet()) {
			HistoData hd = ycol.getHistoData();
			if (hd == null) {
				return null;
			}
			HistoData.FitData fd = hd.prepareForFit(false, hd.getMinX(), hd.getMaxX(), true);
			if (fd == null || fd.x == null || fd.y == null || fd.x.length < 2) {
				return null;
			}
			return new FitVectors(fd.x, fd.y, fd.weights);
		}

		// XY/STRIP case
		OldDataColumn xcol = getCorrespondingXColumn(ycol);
		if (xcol == null) {
			return null;
		}

		double[] x = xcol.getMinimalCopy();
		double[] y = ycol.getMinimalCopy();
		if (x == null || y == null) {
			return null;
		}

		int n = Math.min(x.length, y.length);
		if (n < 2) {
			return null;
		}

		double[] weights = null;

		// XYEXYE: convert sigma (yerr) to weights
		if (_type == DataSetType.XYEXYE) {
			OldDataColumn eCol = getCorrespondingYErrColumn(ycol);
			if (eCol != null) {
				double[] sigma = eCol.getMinimalCopy();
				if (sigma != null) {
					n = Math.min(n, sigma.length);
					if (n < 2) {
						return null;
					}
					weights = sigmaToWeights(sigma, n);
				}
			}
		}

		// trim to n
		if (x.length != n) {
			double[] xt = new double[n];
			System.arraycopy(x, 0, xt, 0, n);
			x = xt;
		}
		if (y.length != n) {
			double[] yt = new double[n];
			System.arraycopy(y, 0, yt, 0, n);
			y = yt;
		}

		return new FitVectors(x, y, weights);
	}

	private static double[] sigmaToWeights(double[] sigma, int n) {
		double[] w = new double[n];
		for (int i = 0; i < n; i++) {
			double s = sigma[i];
			if (!(s > 0.0)) {
				w[i] = 1.0;
			}
			else {
				double inv = 1.0 / s;
				w[i] = inv * inv;
			}
		}
		return w;
	}

	// ------------------------------------------------------------------------
	// Column correspondence
	// ------------------------------------------------------------------------

	/**
	 * Get the corresponding X column for a given Y column based on dataset type.
	 *
	 * @param yColumn a Y column
	 * @return the corresponding X column, or null if not applicable
	 */
	public OldDataColumn getCorrespondingXColumn(OldDataColumn yColumn) {
		if (yColumn == null || yColumn.getType() != DataColumnType.Y) {
			return null;
		}

		int yIndex = _columns.indexOf(yColumn);
		if (yIndex < 0) {
			return null;
		}

		switch (_type) {
		case XYXY:
			// (X,Y) pairs: Y is odd index
			return ((yIndex % 2) == 1) ? _columns.get(yIndex - 1) : null;

		case XYEXYE:
			// (X,Y,YERR) triplets: Y is index % 3 == 1
			return ((yIndex % 3) == 1) ? _columns.get(yIndex - 1) : null;

		case STRIP:
			// fixed X,Y
			return _columns.isEmpty() ? null : _columns.get(0);

		case H1D:
		default:
			return null;
		}
	}

	/**
	 * Get the corresponding YERR column for a given Y column (only for XYEXYE datasets).
	 *
	 * @param yColumn a Y column
	 * @return the corresponding YERR column, or null
	 */
	private OldDataColumn getCorrespondingYErrColumn(OldDataColumn yColumn) {
		if (_type != DataSetType.XYEXYE || yColumn == null || yColumn.getType() != DataColumnType.Y) {
			return null;
		}
		int yIndex = _columns.indexOf(yColumn);
		if (yIndex < 0) {
			return null;
		}

		// triplets: X,Y,YERR
		if ((yIndex % 3) == 1) {
			int eIndex = yIndex + 1;
			if (eIndex < _columns.size() && _columns.get(eIndex).getType() == DataColumnType.YERR) {
				return _columns.get(eIndex);
			}
		}
		return null;
	}

	// ------------------------------------------------------------------------
	// Basic dataset info
	// ------------------------------------------------------------------------

	/**
	 * Number of points (rows) as implied by the first column.
	 * Histogram datasets report total count of the first histogram.
	 *
	 * @return data count, or -1 if no columns exist
	 */
	public long size() {
		if (getColumnCount() == 0) {
			return -1;
		}
		OldDataColumn dc = _columns.firstElement();
		if (is1DHistoSet()) {
			HistoData hd = dc.getHistoData();
			return (hd == null) ? -1 : hd.getTotalCount();
		}
		return dc.size();
	}

	/**
	 * @return true if this dataset is a 1D histogram dataset
	 */
	public boolean is1DHistoSet() {
		return _type == DataSetType.H1D;
	}

	/**
	 * Mark all Y columns as dirty (forces refit/spline on next {@link #doCurveFits(boolean)}).
	 */
	public void setAllFitsDirty() {
		for (OldDataColumn dc : _columns) {
			if (dc.getType() == DataColumnType.Y) {
				dc.setDirty(true);
			}
		}
	}

	/**
	 * Get a curve (Y column) by curve index (0..numCurves-1).
	 * For XYXY and XYEXYE, curves are the Y columns in order.
	 * For H1D, each histogram is a curve.
	 *
	 * @param index curve index
	 * @return the Y column
	 */
	public OldDataColumn getCurve(int index) {
		if (is1DHistoSet()) {
			return _columns.get(index);
		}

		if (_type == DataSetType.XYXY) {
			return _columns.get(2 * index + 1);
		}
		// XYEXYE: X,Y,YERR
		return _columns.get(3 * index + 1);
	}

	/**
	 * Get the X column corresponding to a given curve index.
	 * Not meaningful for H1D datasets.
	 *
	 * @param index curve index
	 * @return x column or null
	 */
	public OldDataColumn getXColumn(int index) {
		if (is1DHistoSet()) {
			return null;
		}
		if (_type == DataSetType.XYXY) {
			return _columns.get(2 * index);
		}
		// XYEXYE
		return _columns.get(3 * index);
	}

	/**
	 * Convenience: style for a curve index.
	 *
	 * @param index curve index
	 * @return the curve's style
	 */
	public IStyled getCurveStyle(int index) {
		return getCurve(index).getStyle();
	}

	/**
	 * Compute standard deviation of a primitive array.
	 *
	 * @param x array
	 * @return standard deviation (population)
	 */
	public static double standardDev(double x[]) {
		if (x == null || x.length < 2) {
			return 0.0;
		}
		double sum = 0.0;
		for (double v : x) {
			sum += v;
		}
		double mean = sum / x.length;

		double ss = 0.0;
		for (double v : x) {
			double d = v - mean;
			ss += d * d;
		}
		double var = ss / x.length;
		return (var <= 0.0) ? 0.0 : Math.sqrt(var);
	}

	/**
	 * @return dataset type
	 */
	public DataSetType getType() {
		return _type;
	}

	/**
	 * Get minimum of a column by index.
	 *
	 * @param index column index
	 * @return minimum value
	 */
	public double getColumnMin(int index) {
		return getColumn(index).getMinValue();
	}

	/**
	 * Get maximum of a column by index.
	 *
	 * @param index column index
	 * @return maximum value
	 */
	public double getColumnMax(int index) {
		return getColumn(index).getMaxValue();
	}

	/**
	 * @return all visible curves (Y columns with visible=true)
	 */
	public Collection<OldDataColumn> getAllVisibleCurves() {
		Vector<OldDataColumn> curves = new Vector<>();
		for (OldDataColumn dc : _columns) {
			if (dc.getType() == DataColumnType.Y && dc.isVisible()) {
				curves.add(dc);
			}
		}
		return curves;
	}

	// ------------------------------------------------------------------------
	// Data min/max convenience (legacy behavior retained)
	// ------------------------------------------------------------------------

	/**
	 * Convenience: get overall minimum X value among X columns.
	 *
	 * @return minimum x value
	 */
	public double getXmin() {
		return getDataMin(DataColumnType.X);
	}

	/**
	 * Convenience: get overall maximum X value among X columns.
	 *
	 * @return maximum x value
	 */
	public double getXmax() {
		return getDataMax(DataColumnType.X);
	}

	/**
	 * Convenience: get overall minimum Y value among Y columns.
	 *
	 * @return minimum y value
	 */
	public double getYmin() {
		return getDataMin(DataColumnType.Y);
	}

	/**
	 * Convenience: get overall maximum Y value among Y columns.
	 *
	 * @return maximum y value
	 */
	public double getYmax() {
		return getDataMax(DataColumnType.Y);
	}

	private double getDataMin(DataColumnType type) {
		double min = Double.POSITIVE_INFINITY;
		for (OldDataColumn dc : _columns) {
			if (dc.getType() == type) {
				min = Math.min(min, dc.getMinValue());
			}
		}
		return min;
	}

	private double getDataMax(DataColumnType type) {
		double max = Double.NEGATIVE_INFINITY;
		for (OldDataColumn dc : _columns) {
			if (dc.getType() == type) {
				max = Math.max(max, dc.getMaxValue());
			}
		}
		return max;
	}

	// ------------------------------------------------------------------------
	// Table / mutation API (legacy signatures retained)
	// ------------------------------------------------------------------------

	/**
	 * Get the minimal array for a column (a tight copy of active data).
	 *
	 * @param index column index
	 * @return minimal array (may be null/empty depending on column implementation)
	 */
	public double[] getMinimalArray(int index) {
		return getColumn(index).getMinimalCopy();
	}

	/**
	 * Size of real data (same as row count for normal datasets).
	 *
	 * @return number of rows
	 */
	public int getSize() {
		return getRowCount();
	}

	/**
	 * Add one row of values.
	 * <p>
	 * For XYXY / XYEXYE / STRIP the number of vals must match column count.
	 * For H1D this is not used (histograms are updated via {@link OldDataColumn#add(double)}).
	 * </p>
	 *
	 * @param vals row values
	 * @throws DataSetException if wrong number of values
	 */
	public void add(double... vals) throws DataSetException {

		if (vals == null) {
			throw new DataSetException("Null add(double... vals).");
		}
		if (vals.length != getColumnCount()) {
			throw new DataSetException("Wrong number of values: got " + vals.length + " expected " + getColumnCount());
		}

		for (int i = 0; i < vals.length; i++) {
			getColumn(i).add(vals[i]);
		}

		// notify table + listeners
		fireTableDataChanged();
		notifyListeners();
	}

	/**
	 * Add a single value to a given column (used by some live-update modes).
	 *
	 * @param column column index
	 * @param val value to add
	 * @throws DataSetException if column is invalid
	 */
	public void add(int column, double val) throws DataSetException {
		if (column < 0 || column >= getColumnCount()) {
			throw new DataSetException("Bad column index: " + column);
		}
		getColumn(column).add(val);
		fireTableDataChanged();
		notifyListeners();
	}

	/**
	 * Clear all columns and notify listeners.
	 */
	public void clear() {
		for (OldDataColumn dc : _columns) {
			dc.clear();
		}
		fireTableDataChanged();
		notifyListeners();
	}

	/**
	 * Notify {@link DataChangeListener}s that data changed.
	 */
	public void notifyListeners() {
		Object[] listeners = _listenerList.getListenerList();
		for (int i = listeners.length - 2; i >= 0; i -= 2) {
			if (listeners[i] == DataChangeListener.class) {
				((DataChangeListener) listeners[i + 1]).dataSetChanged(this);
			}
		}
	}

	/**
	 * Add a dataset change listener.
	 *
	 * @param listener listener to add
	 */
	public void addDataChangeListener(DataChangeListener listener) {
		if (listener != null) {
			_listenerList.add(DataChangeListener.class, listener);
		}
	}

	/**
	 * Remove a dataset change listener.
	 *
	 * @param listener listener to remove
	 */
	public void removeDataChangeListener(DataChangeListener listener) {
		if (listener != null) {
			_listenerList.remove(DataChangeListener.class, listener);
		}
	}

	/**
	 * Convenience: style for a column.
	 *
	 * @param index column index
	 * @return style (may be null if not initialized)
	 */
	public Styled getColumnStyle(int index) {
		return getColumn(index).getStyle();
	}

	/**
	 * @return all columns in this dataset
	 */
	public Collection<OldDataColumn> getColumns() {
		return _columns;
	}

	/**
	 * Get a column by index.
	 *
	 * @param index column index
	 * @return data column
	 */
	public OldDataColumn getColumn(int index) {
		return _columns.get(index);
	}

	// ------------------------------------------------------------------------
	// DefaultTableModel overrides (kept compatible with existing UI behavior)
	// ------------------------------------------------------------------------

	@Override
	public int getRowCount() {
		if (_columns == null || _columns.isEmpty()) {
			return 0;
		}
		// For histogram datasets, table view isn't a conventional row model;
		// keep legacy behavior: report size of first column.
		OldDataColumn dc = _columns.firstElement();
		return dc.size();
	}

	@Override
	public int getColumnCount() {
		return (_columns == null) ? 0 : _columns.size();
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return Double.class;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		// keep legacy behavior: editable
		return true;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		OldDataColumn dc = getColumn(columnIndex);
		double val = dc.get(rowIndex);

		// mimic legacy formatting: integers show as int, others as ~4 decimals
		double[] ifrac = intFract(val);
		boolean asInt = Math.abs(ifrac[1]) < 1.0e-12;
		String s = asInt ? DoubleFormat.doubleFormat(val, 0) : DoubleFormat.doubleFormat(val, 4, 2);
		return Double.valueOf(s);
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		OldDataColumn dc = getColumn(columnIndex);
		dc.set(rowIndex, (Double) aValue);
	}

	private static double[] intFract(double d) {
		BigDecimal bd = BigDecimal.valueOf(d);
		return new double[] { bd.intValue(), bd.remainder(BigDecimal.ONE).doubleValue() };
	}
}
