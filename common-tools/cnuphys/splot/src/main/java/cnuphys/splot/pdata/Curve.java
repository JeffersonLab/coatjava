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
 * A standard XY curve consisting of X, Y, and (optional) E (y-error) data.
 *
 * @author heddle
 */
public class Curve extends ACurve {

	private final DataColumn xData;
	private final DataColumn yData;
	private final DataColumn eData;

	public Curve(String name, DataColumn xData, DataColumn yData, DataColumn eData) throws PlotDataException {

		super(name);
		this.xData = Objects.requireNonNull(xData, "xData");
		this.yData = Objects.requireNonNull(yData, "yData");
		this.eData = eData;

		if (!consistentData()) {
			throw new PlotDataException("Inconsistent data lengths in curve: " + name);
		}
	}

	private boolean consistentData() {
		int n = xData.size();
		return yData.size() == n && (eData == null || eData.size() == n);
	}

	@Override
	public int length() {
		return xData.size();
	}

	public DataColumn xData() {
		return xData;
	}

	public DataColumn yData() {
		return yData;
	}

	public DataColumn eData() {
		return eData;
	}

	@Override
	protected IFitter createFitterForCurrentMethod() {

		switch (getCurveDrawingMethod()) {

		case POLYNOMIAL:
			return new PolynomialFitter(getPolynomialDegree());

		case ERF:
			return new ErfErfcFitter(ErfErfcFitter.Kind.ERF);

		case GAUSSIAN:
			return new GaussianFitter();

		case GAUSSIANS:
			// "order" interpreted as number of Gaussians. (Constructor: (count, includeBaseline))
			return new MultiGaussianFitter(Math.max(1, getOrder()), true);

		case HARMONIC:
			// HarmonicFitter constructors don't take an order. Use offset form as default.
			return new HarmonicFitter(true);

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
					setCubicSpline(new CubicSpline(v.x, v.y));
				}
				break;
			}

			case POLYNOMIAL:
			case ERF:
			case GAUSSIAN:
			case GAUSSIANS:
			case HARMONIC: {
				IFitter fitter = createFitterForCurrentMethod();
				if (fitter != null) {
					FitVectors v = new FitVectors(xData, yData, eData);
					FitResult fr = fitWithOptionalWeights(fitter, v);
					setFitArtifacts(fr, (fr == null) ? null : fitter.asValueGetter(fr));
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
		xData.add(x);
		yData.add(y);
		if (eData != null) {
			eData.add(0.0);
		}
		markDataChanged();
	}

	public void add(double x, double y, double ey) {
		if (eData == null) {
			throw new IllegalStateException("This curve has no error column (eData is null).");
		}
		xData.add(x);
		yData.add(y);
		eData.add(ey);
		markDataChanged();
	}

	public void addAll(double[] x, double[] y) {
		Objects.requireNonNull(x, "x");
		Objects.requireNonNull(y, "y");
		if (x.length != y.length) {
			throw new IllegalArgumentException("x and y lengths differ: " + x.length + " vs " + y.length);
		}
		for (int i = 0; i < x.length; i++) {
			xData.add(x[i]);
			yData.add(y[i]);
			if (eData != null) {
				eData.add(0.0);
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
}
