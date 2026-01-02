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
 * Histogram-backed curve that integrates {@link HistoData} into the {@link ACurve}
 * fitting and drawing framework.
 *
 * @author heddle
 */
public class HistoCurve extends ACurve {

	/** Backing histogram data. */
	private final HistoData histoData;

	/**
	 * Create a histogram-backed curve.
	 *
	 * @param name      curve name (legend label)
	 * @param histoData backing histogram data (non-null)
	 */
	public HistoCurve(String name, HistoData histoData) {
		super(name);
		this.histoData = Objects.requireNonNull(histoData, "histoData");
	}

	/** @return the backing histogram data */
	public HistoData getHistoData() {
		return histoData;
	}

	/**
	 * Length is defined as the number of bins.
	 */
	@Override
	public int length() {
		return histoData.getNumberBins();
	}

	/**
	 * Build fit vectors from histogram bin centers and bin counts.
	 * <p>
	 * Bin centers are computed from the histogram grid edges:
	 * {@code center[i] = 0.5*(grid[i] + grid[i+1])}.
	 * </p>
	 */
	private FitVectors fitVectors() {
		final int n = histoData.getNumberBins();
		if (n < 1) {
			return new FitVectors(new double[0], new double[0], null);
		}

		final double[] grid = histoData.getGridCopy(); // length n+1
		final long[] counts = histoData.getCountsCopy(); // length n

		// Defensive: if something is inconsistent, fail soft with empty vectors.
		if (grid == null || grid.length != n + 1 || counts == null || counts.length != n) {
			return new FitVectors(new double[0], new double[0], null);
		}

		final double[] x = new double[n];
		final double[] y = new double[n];

		for (int i = 0; i < n; i++) {
			x[i] = 0.5 * (grid[i] + grid[i + 1]);
			y[i] = (double) counts[i];
		}

		return new FitVectors(x, y, null);
	}

	/**
	 * Create a fitter appropriate for the current curve drawing method.
	 */
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
			// "order" interpreted as number of Gaussians
			return new MultiGaussianFitter(Math.max(1, getOrder()), true);

		case HARMONIC:
			// HarmonicFitter has no order parameter; default to offset form
			return new HarmonicFitter(true);

		default:
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
	public void doFit(boolean force) {

		if (!force && !isDirty()) {
			return;
		}

		try {
			final CurveDrawingMethod method = getCurveDrawingMethod();

			// Clear stale artifacts first
			clearComputedArtifacts();

			switch (method) {

			case NONE:
			case CONNECT:
			case STAIRS:
				break;

			case CUBICSPLINE: {
				FitVectors v = fitVectors();
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
					FitVectors v = fitVectors();
					FitResult fr = fitWithOptionalWeights(fitter, v);
					setFitArtifacts(fr, (fr == null) ? null : fitter.asValueGetter(fr));
				}
				break;
			}

			default:
				break;
			}

		} catch (Exception e) {
			// Fail soft: artifacts already cleared
		} finally {
			setDirty(false);
		}
	}

	

	/**
	 * {@inheritDoc}
	 */
	@Override
	public double xMin() {
		return histoData == null ? Double.NaN : histoData.getMinX();
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public double xMax() {
		return histoData == null ? Double.NaN : histoData.getMaxX();
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public double yMin() {
		return histoData == null ? Double.NaN : histoData.getMinY();
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public double yMax() {
		return histoData == null ? Double.NaN : histoData.getMaxY();
	}
	// ------------------------------------------------------------
	// Histogram mutation API
	// ------------------------------------------------------------

	/**
	 * Fill the histogram with a single sample.
	 *
	 * @param x sample value
	 */
	public void add(double x) {
		synchronized (lock) {
			histoData.add(x);
		}
		markDataChanged();
	}

	/**
	 * Clear histogram contents and statistics.
	 */
	public void clearData() {
		histoData.clear();
		markDataChanged();
	}

	@Override
	public Snapshot snapshot() {
		FitVectors fv = fitVectors();
		return new Snapshot(fv.x, fv.y);
	}
}
