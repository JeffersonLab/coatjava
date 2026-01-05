package cnuphys.splot.pdata;

import java.util.Objects;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.ErfErfcFitter;
import cnuphys.splot.fit.FitResult;
import cnuphys.splot.fit.GaussianFitter;
import cnuphys.splot.fit.IFitter;
import cnuphys.splot.fit.MultiGaussianFitter;
import cnuphys.splot.fit.PolynomialFitter;
import cnuphys.splot.spline.CubicSpline;

/**
 * A standard XY curve consisting of X, Y, and (optional) E (y-error) data.
 *
 * @author heddle
 */
public class Curve extends ACurve {

	// Data columns for x, y, and (optional) e (y error bars)
	private final DataColumn xData;
	private final DataColumn yData;
	private final DataColumn eData;

	/**
	 * Create a standard XY curve.
	 *
	 * @param name  the curve name
	 * @param xData the x data column
	 * @param yData the y data column
	 * @param eData the (optional) y-error data column (may be null)
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
	
	
	// Check that x, y, (e) data lengths are consistent
	private boolean consistentData() {
		int n = xData.size();
		return yData.size() == n && (eData == null || eData.size() == n);
	}

	@Override
	public int length() {
		return xData.size();
	}

	/**
	 * Get the x data column.
	 *
	 * @return the x data column
	 */
	public DataColumn xData() {
		return xData;
	}

	/**
	 * Get the y data column.
	 *
	 * @return the y data column
	 */
	public DataColumn yData() {
		return yData;
	}
	
	/**
	 * Get the (optional) y-error data column.
	 *
	 * @return the y-error data column (may be null)
	 */
	public DataColumn eData() {
		return eData;
	}

	@Override
	protected IFitter createFitterForCurrentMethod() {

		switch (getCurveDrawingMethod()) {

		case POLYNOMIAL:
			return new PolynomialFitter(getFitOrder());

		case ERF:
			return new ErfErfcFitter(ErfErfcFitter.Kind.ERF);

		case GAUSSIAN:
			return new GaussianFitter();

		case GAUSSIANS:
			// "order" interpreted as number of Gaussians. (Constructor: (count, includeBaseline))
			return new MultiGaussianFitter(Math.max(1, getFitOrder()), true);

		default:
			return null;
		}
	}

	@Override
	public void doFit(boolean force) {

		if (!force && !isDirty()) {
			return;
		}

		try {
			final CurveDrawingMethod method = getCurveDrawingMethod();

			// Clear stale artifacts first (fitResult, fitValueGetter, spline, etc.)
			clearComputedArtifacts();

			switch (method) {

			case NONE:
			case CONNECT:
			case STAIRS:
				break;

			case CUBICSPLINE: {
				FitVectors v = new FitVectors(xData, yData, eData);
				if (v != null && v.length() >= 2) {
					//note a CubicSpline is just an interpolator, no fitting involved
					//it implements IValurGetter
					setCubicSpline(new CubicSpline(v.x, v.y));
				}
				break;
			}

			case POLYNOMIAL:
			case ERF:
			case GAUSSIAN:
			case GAUSSIANS: {
				IFitter fitter = createFitterForCurrentMethod();
				if (fitter != null) {
					FitVectors v = new FitVectors(xData, yData, eData);
					FitResult fr = fitWithOptionalWeights(fitter, v);
					setFitResult(fr);
				}
				break;
			}

			default:
				break;
			}

		} catch (Exception e) {
			// Fail soft: artifacts already cleared by clearComputedArtifacts()
		} finally {
			setDirty(false);
		}
	}

	// ------------------------------------------------------------
	// Data append helpers
	// ------------------------------------------------------------

	public void add(double x, double y) {
		synchronized (lock) {
			xData.add(x);
			yData.add(y);
			if (eData != null) {
				eData.add(0.0);
			}
		}
		markDataChanged();
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
			return new Snapshot(xData.values(), yData.values(), eData == null ? null : eData.values());
		}
	}


	public void add(double x, double y, double ey) {
		if (eData == null) {
			throw new IllegalStateException("This curve has no error column (eData is null).");
		}
		synchronized (lock) {
			xData.add(x);
			yData.add(y);
			eData.add(ey);
		}
		markDataChanged();
	}

	public void addAll(double[] x, double[] y) {
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
		}
		markDataChanged();
	}

	public void addAll(double[] x, double[] y, double[] ey) {
		if (eData == null) {
			throw new IllegalStateException("This curve has no error column (eData is null).");
		}
		Objects.requireNonNull(x, "x");
		Objects.requireNonNull(y, "y");
		Objects.requireNonNull(ey, "ey");
		if (x.length != y.length || x.length != ey.length) {
			throw new IllegalArgumentException("lengths differ: x=" + x.length + " y=" + y.length + " ey=" + ey.length);
		}
		for (int i = 0; i < x.length; i++) {
			xData.add(x[i]);
			yData.add(y[i]);
			eData.add(ey[i]);
		}
		markDataChanged();
	}

	public void clearData() {
		xData.clear();
		yData.clear();
		if (eData != null) {
			eData.clear();
		}
		markDataChanged();
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
}
