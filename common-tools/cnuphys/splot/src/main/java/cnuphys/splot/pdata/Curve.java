package cnuphys.splot.pdata;

import java.util.Objects;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.apache.ErfErfcFitter;
import cnuphys.splot.fit.apache.FitResult;
import cnuphys.splot.fit.apache.GaussianFitter;
import cnuphys.splot.fit.apache.HarmonicFitter;
import cnuphys.splot.fit.apache.IFitter;
import cnuphys.splot.fit.apache.MultiGaussianFitter;
import cnuphys.splot.fit.apache.PolynomialFitter;
import cnuphys.splot.spline.CubicSpline;

/**
 * A standard XY curve consisting of X, Y, and (optional) E data.
 *
 * <p>
 * This concrete curve supplies the data columns and delegates fitting to
 * Apache-based fitters via {@link IFitter}. All fit state is owned by
 * {@link ACurve}.
 * </p>
 *
 * @author heddle
 */
public class Curve extends ACurve {

	// X data
	private final DataColumn xData;

	// Y data
	private final DataColumn yData;

	// E (Y error bar) data (may be null)
	private final DataColumn eData;

	/**
	 * Create a curve with the given data.
	 *
	 * @param name  the name of the curve (for the legend)
	 * @param xData the X data
	 * @param yData the Y data
	 * @param eData the E (Y error bar) data (can be null)
	 * @throws DataSetException if data lengths are inconsistent
	 */
	public Curve(String name, DataColumn xData, DataColumn yData, DataColumn eData) throws DataSetException {

		super(name);
		this.xData = Objects.requireNonNull(xData, "X data column is null");
		this.yData = Objects.requireNonNull(yData, "Y data column is null");
		this.eData = eData;

		if (!consistentData()) {
			throw new DataSetException("Inconsistent data lengths in curve: " + name);
		}
	}

	/** Check data length consistency. */
	private boolean consistentData() {
		int n = xData.size();
		return yData.size() == n && (eData == null || eData.size() == n);
	}

	/** {@inheritDoc} */
	@Override
	public int length() {
		return xData.size();
	}

	/** Build fit vectors from the current data columns. */
	private FitVectors buildFitVectors() {
		return new FitVectors(xData, yData, eData);
	}

	/**
	 * Factory for creating a fitter appropriate to the current drawing method.
	 *
	 * @param method curve drawing method
	 * @return a fitter, or {@code null} if the method is not a fit
	 */
	private IFitter createFitter(CurveDrawingMethod method) {

		switch (method) {

		case POLYNOMIAL:
			return new PolynomialFitter(getPolynomialDegree());

		case ERF:
			return new ErfErfcFitter(ErfErfcFitter.Kind.ERF);

		case GAUSSIAN:
			return new GaussianFitter();

		case GAUSSIANS:
			// "order" interpreted as number of Gaussians
			return new MultiGaussianFitter(Math.max(1, getOrder()), true);

		case HARMONIC:
			return new HarmonicFitter(true);

		default:
			return null;
		}
	}

	/**
	 * Create a fitter appropriate to the current {@link CurveDrawingMethod} and
	 * knobs (polynomial degree, order).
	 *
	 * <p>
	 * Returns {@code null} for non-fit drawing methods.
	 * </p>
	 */
	@Override
	protected IFitter createFitterForCurrentMethod() {

		final CurveDrawingMethod method = getCurveDrawingMethod();

		switch (method) {

		case POLYNOMIAL:
			// degree is stored in ACurve
			return new PolynomialFitter(getPolynomialDegree());

		case ERF:
			// If you later add ERFC as a separate method, map it here
			return new ErfErfcFitter(ErfErfcFitter.Kind.ERF);

		case GAUSSIAN:
			return new GaussianFitter();

		case GAUSSIANS:
			// "order" interpreted as number of gaussians
			return new MultiGaussianFitter(Math.max(1, getOrder()), true);

		case HARMONIC:
			// If your HarmonicFitter has more knobs (omega scan steps, etc.),
			// wire them here using getOrder() or a dedicated getter.
			return new HarmonicFitter(true);

		default:
			// NONE, CONNECT, STAIRS, CUBICSPLINE, etc.
			return null;
		}
	}

	/**
	 * Perform a curve computation (fit or spline) depending on the
	 * {@link CurveDrawingMethod}.
	 *
	 * @param force {@code true} to force recomputation even if not dirty
	 */
	@Override
	public void doCurveFit(boolean force) {

		if (!force && !isDirty()) {
			return;
		}

		try {
			final CurveDrawingMethod method = getCurveDrawingMethod();

			// Always clear stale artifacts first
			clearComputedArtifacts();

			switch (method) {

			// Pure drawing modes: nothing to compute
			case NONE:
			case CONNECT:
			case STAIRS:
				break;

			case CUBICSPLINE:
				if (length() >= 2) {
					setCubicSpline(new CubicSpline(xData.values(), yData.values()));
				}
				break;

			// All fit-based modes
			case POLYNOMIAL:
			case ERF:
			case GAUSSIANS:
			case HARMONIC:
			case GAUSSIAN:
				IFitter fitter = createFitterForCurrentMethod();
				if (fitter != null) {
					FitVectors v = new FitVectors(xData, yData, eData);
					setFitResult(fitWithOptionalWeights(fitter, v));
				}
				break;

			}
		} catch (Exception e) {
			// Fail soft: leave fit artifacts null
			clearComputedArtifacts();
		} finally {
			setDirty(false);
		}
	}

	/** @return the X data column. */
	public DataColumn xData() {
		return xData;
	}

	/** @return the Y data column. */
	public DataColumn yData() {
		return yData;
	}

	/** @return the E (Y error bar) column, or null. */
	public DataColumn eData() {
		return eData;
	}
}