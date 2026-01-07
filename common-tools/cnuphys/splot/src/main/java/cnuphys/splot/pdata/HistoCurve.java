package cnuphys.splot.pdata;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.SwingUtilities;

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
 * <h2>Thread-safety</h2>
 * <p>
 * The {@link ACurve} contract requires that notifications (e.g. {@link #markDataChanged()})
 * occur on the Swing Event Dispatch Thread (EDT). This class preserves that contract while
 * allowing histogram filling from any thread:
 * </p>
 * <ul>
 *   <li>If {@link #add(double)} or {@link #addAll(double[])} are called on the EDT, they apply
 *       immediately and notify once.</li>
 *   <li>If called off the EDT, values are enqueued and a coalesced EDT drain is scheduled.
 *       The drain applies many samples in bulk and notifies once.</li>
 * </ul>
 *
 * @author heddle
 */
public class HistoCurve extends ACurve {

	/** Backing histogram data. */
	private final HistoData histoData;

	/** Thread-safe staging of samples from non-EDT threads. */
	private final PendingQueue<Double> pending = new PendingQueue<>();

	/**
	 * Coalescing latch: ensures we only post one drain runnable to the EDT at a time,
	 * no matter how many background threads call {@link #add(double)}.
	 */
	private final AtomicBoolean drainScheduled = new AtomicBoolean(false);

	/**
	 * Maximum number of pending samples to apply per single EDT drain pass.
	 * Keeps the EDT responsive under heavy producer rates.
	 */
	private static final int DEFAULT_DRAIN_MAX = 10_000;

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
	 * Add a value to the histogram.
	 * <p>
	 * Safe from any thread. Off-EDT calls enqueue and schedule a coalesced EDT drain.
	 * The {@link ACurve} notification contract is preserved: {@link #markDataChanged()}
	 * is invoked on the EDT only.
	 * </p>
	 *
	 * @param x the value to add
	 */
	public void add(double x) {
		if (!SwingUtilities.isEventDispatchThread()) {
			enqueue(x);
			scheduleDrain();
			return;
		}

		synchronized (lock) {
			histoData.add(x);
		}
		markDataChanged();
	}

	/**
	 * Add multiple values to the histogram.
	 * <p>
	 * Safe from any thread. Off-EDT calls enqueue and schedule a coalesced EDT drain.
	 * The {@link ACurve} notification contract is preserved: {@link #markDataChanged()}
	 * is invoked on the EDT only.
	 * </p>
	 *
	 * @param x the values to add (non-null)
	 */
	public void addAll(double[] x) {
		Objects.requireNonNull(x, "x");

		if (!SwingUtilities.isEventDispatchThread()) {
			enqueueAll(x);
			scheduleDrain();
			return;
		}

		synchronized (lock) {
			histoData.addAll(x);
		}
		markDataChanged();
	}

	/**
	 * Enqueue a value to be added to the histogram later on the EDT.
	 * This method should be used by background worker threads.
	 *
	 * @param x the value to enqueue
	 */
	private void enqueue(double x) {
		pending.enqueue(x);
	}

	/**
	 * Enqueue multiple values to be added to the histogram later on the EDT.
	 * This method should be used by background worker threads.
	 *
	 * @param x the values to enqueue (non-null)
	 */
	private void enqueueAll(double[] x) {
		Objects.requireNonNull(x, "x");
		for (double v : x) {
			pending.enqueue(v);
		}
	}

	/**
	 * Drain pending enqueued values on the EDT and apply them to the histogram.
	 * <p>
	 * This method is EDT-only because it ultimately fires {@link #markDataChanged()}.
	 * </p>
	 *
	 * @param max maximum number of values to apply in this drain pass
	 * @return number of drained values
	 */
	public int drainPendingOnEDT(int max) {
		requireEdt("HistoCurve.drainPendingOnEDT");
		return pending.drainPendingOnEDT(max, batch -> {
			synchronized (lock) {
				for (double v : batch) {
					histoData.add(v);
				}
			}
			markDataChanged();
		});
	}

	/**
	 * Schedule a coalesced drain pass on the EDT.
	 * <p>
	 * If multiple background threads call this repeatedly, only one drain runnable
	 * is posted until it begins execution.
	 * </p>
	 */
	private void scheduleDrain() {
		if (drainScheduled.compareAndSet(false, true)) {
			SwingUtilities.invokeLater(() -> {
				drainScheduled.set(false);

				// Apply a bounded chunk to keep EDT responsive.
				int drained = drainPendingOnEDT(DEFAULT_DRAIN_MAX);

				// If we likely hit the cap, there may be more pending; schedule another pass.
				if (drained >= DEFAULT_DRAIN_MAX) {
					scheduleDrain();
				}
			});
		}
	}

	/**
	 * Clear histogram contents and statistics.
	 * <p>
	 * EDT-only by default, preserving the existing behavior. If you decide you want
	 * this to be thread-safe later, it can follow the same enqueue/schedule pattern.
	 * </p>
	 */
	public void clearData() {
		requireEdt("HistoCurve.clearData");
		histoData.clear();
		markDataChanged();
	}

	@Override
	public Snapshot snapshot() {
		FitVectors fv = fitVectors();
		return new Snapshot(fv.x, fv.y, null);
	}
}
