package cnuphys.splot.pdata;

import java.util.Objects;

import cnuphys.splot.fit.CurveDrawingMethod;
import cnuphys.splot.fit.ErfFitter;
import cnuphys.splot.fit.ErfcFitter;
import cnuphys.splot.fit.FitResult;
import cnuphys.splot.fit.GaussianFitter;
import cnuphys.splot.fit.IFitter;
import cnuphys.splot.fit.MultiGaussianFitter;
import cnuphys.splot.fit.PolynomialFitter;
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
	
	// thread safety
	private final PendingQueue<Double> pending = new PendingQueue<>();


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
			return new PolynomialFitter(getFitOrder());

		case ERF:
			return new ErfFitter();

		case ERFC:
			return new ErfcFitter();

		case GAUSSIAN:
			return new GaussianFitter();

		case GAUSSIANS:
			// "order" interpreted as number of Gaussians
			return new MultiGaussianFitter(Math.max(1, getFitOrder()), true);

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
			case ERFC:
			case GAUSSIAN:
			case GAUSSIANS: {
				IFitter fitter = createFitterForCurrentMethod();
				if (fitter != null) {
					FitVectors v = fitVectors();
					FitResult fr = fitWithOptionalWeights(fitter, v);
					setFitResult(fr);
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
	 * Add a value to the histogram. This method must be called from the EDT.
	 * @param x the value to add
	 */
	public void add(double x) {
		requireEdt("HistoCurve.add");
		synchronized (lock) {
			histoData.add(x);
			markDataChanged();
		}
	}

	/**
	 * Add multiple values to the histogram. This method must be called from the EDT.
	 * @param x the values to add
	 */
	public void addAll(double[] x) {
		requireEdt("HistoCurve.addAll");
		synchronized (lock) {
			histoData.addAll(x);
			markDataChanged();
		}
	}

	/**
	 * Enqueue a value to be added to the histogram later on the EDT.
	 * This method should be used by background worker threads.
	 * @param x the value to enqueue
	 */
	public void enqueue(double x) {
		pending.enqueue(x);
	}

	/**
	 * Enqueue multiple values to be added to the histogram later on the EDT.
	 * This method should be used by background worker threads.
	 * @param x the values to enqueue
	 */
	public void enqueueAll(double[] x) {
		for (double v : x) {
			pending.enqueue(v);
		}
	}

	// EDT only
	public int drainPendingOnEDT(int max) {
		return pending.drainPendingOnEDT(max, batch -> {
			synchronized (lock) {
				for (double v : batch) {
					histoData.add(v);
				}
				markDataChanged();
			}
		});
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
		return new Snapshot(fv.x, fv.y, null);
	}
}
