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
 * Histogram-backed curve that plugs into the {@link ACurve} fitting/drawing-method
 * architecture.
 *
 * <p>A histogram is not stored as raw X/Y points; it is stored as bins + counts
 * in {@link HistoData}. When a fit (or spline) is requested, this class generates
 * temporary X/Y/(optional weights) vectors from the histogram:</p>
 *
 * <ul>
 *   <li>x[i] = bin center</li>
 *   <li>y[i] = count in bin</li>
 *   <li>optional weights are typically Poisson (w = 1/count for count&gt;0)</li>
 * </ul>
 *
 * <p>All model fitting is delegated to the existing Apache fitters via {@link IFitter}.
 * The resulting {@link FitResult} is cached in {@link ACurve} just like for an XYE {@code Curve}.</p>
 */
public class HistoCurve extends ACurve {

    /** Histogram storage (bins + counts). */
    private final HistoData histo;

    // --------------------------------------------------------------------
    // Fit-prep policy knobs
    // --------------------------------------------------------------------

    /** If false (default), bins with y==0 are excluded from fit vectors. */
    private boolean includeZeroBins = false;

    /** If true (default), use Poisson weights (w=1/count for count>0). */
    private boolean poissonWeights = true;

    /** Strategy for selecting which bins become fit vectors. */
    public enum FitWindowMode {
        /** Use the full histogram range (minX..maxX). */
        FULL_RANGE,

        /**
         * Use a window around the best peak with guarding (edge handling, minimum points).
         * This is often the best default for peak fitting.
         */
        AROUND_BEST_PEAK_GUARDED
    }

    private FitWindowMode fitWindowMode = FitWindowMode.FULL_RANGE;

    /** Half window size in bins for around-peak strategies. */
    private int halfWindowBins = 10;

    /**
     * Smoothing radius (in bins) used by guarded peak search.
     * 0 disables smoothing; small values like 1–3 are typical.
     */
    private int smoothRadius = 2;

    /**
     * If true, peak search ignores zero bins even if {@link #includeZeroBins} is true.
     * Usually you want this true.
     */
    private boolean ignoreZeroBinsInPeakSearch = true;

    /** Minimum number of points required by guarded peak fit vector prep. */
    private int minPoints = 6;

    /**
     * Create a histogram curve backed by the given histogram data.
     *
     * @param name  curve name (legend label)
     * @param histo histogram data (non-null)
     */
    public HistoCurve(String name, HistoData histo) {
        super(name);
        this.histo = Objects.requireNonNull(histo, "histo is null");
    }

    /** @return the backing histogram data. */
    public HistoData histoData() {
        return histo;
    }

    /** {@inheritDoc} */
    @Override
    public int length() {
        // Interpret "length" as number of histogram bins.
        return histo.getNumberBins();
    }

    // --------------------------------------------------------------------
    // Configuration setters (invalidate computed artifacts)
    // --------------------------------------------------------------------

    public boolean isIncludeZeroBins() {
        return includeZeroBins;
    }

    public void setIncludeZeroBins(boolean includeZeroBins) {
        this.includeZeroBins = includeZeroBins;
        clearComputedArtifacts(); // marks dirty too (per your refactor)
    }

    public boolean isPoissonWeights() {
        return poissonWeights;
    }

    public void setPoissonWeights(boolean poissonWeights) {
        this.poissonWeights = poissonWeights;
        clearComputedArtifacts();
    }

    public FitWindowMode getFitWindowMode() {
        return fitWindowMode;
    }

    public void setFitWindowMode(FitWindowMode mode) {
        this.fitWindowMode = (mode == null) ? FitWindowMode.FULL_RANGE : mode;
        clearComputedArtifacts();
    }

    public int getHalfWindowBins() {
        return halfWindowBins;
    }

    public void setHalfWindowBins(int halfWindowBins) {
        this.halfWindowBins = Math.max(1, halfWindowBins);
        clearComputedArtifacts();
    }

    public int getSmoothRadius() {
        return smoothRadius;
    }

    public void setSmoothRadius(int smoothRadius) {
        this.smoothRadius = Math.max(0, smoothRadius);
        clearComputedArtifacts();
    }

    public boolean isIgnoreZeroBinsInPeakSearch() {
        return ignoreZeroBinsInPeakSearch;
    }

    public void setIgnoreZeroBinsInPeakSearch(boolean ignoreZeroBinsInPeakSearch) {
        this.ignoreZeroBinsInPeakSearch = ignoreZeroBinsInPeakSearch;
        clearComputedArtifacts();
    }

    public int getMinPoints() {
        return minPoints;
    }

    public void setMinPoints(int minPoints) {
        this.minPoints = Math.max(2, minPoints);
        clearComputedArtifacts();
    }

    // --------------------------------------------------------------------
    // Fit/spline execution
    // --------------------------------------------------------------------

    /** Build fit vectors from the histogram using the configured fit window strategy. */
    private FitVectors buildFitVectorsFromHistogram() {

        switch (fitWindowMode) {

            case AROUND_BEST_PEAK_GUARDED: {
                HistoData.FitWindowData fw = histo.prepareForFitAroundBestPeakGuarded(
                        includeZeroBins,
                        halfWindowBins,
                        smoothRadius,
                        ignoreZeroBinsInPeakSearch,
                        poissonWeights,
                        minPoints);

                // Guarded method can return empty arrays if it can’t satisfy constraints.
                if (fw == null || fw.x == null || fw.x.length < 2) {
                    return null;
                }
                return new FitVectors(fw.x, fw.y, fw.weights);
            }

            case FULL_RANGE:
            default: {
                HistoData.FitData fd = histo.prepareForFit(
                        includeZeroBins,
                        histo.getMinX(),
                        histo.getMaxX(),
                        poissonWeights);

                if (fd == null || fd.x == null || fd.x.length < 2) {
                    return null;
                }
                return new FitVectors(fd.x, fd.y, fd.weights);
            }
        }
    }

    /** Create a fitter appropriate to the current drawing method and knobs stored in {@link ACurve}. */
    private IFitter createFitter(CurveDrawingMethod method) {
        switch (method) {

            case POLYNOMIAL:
                return new PolynomialFitter(getPolynomialDegree());

            case ERF:
                return new ErfErfcFitter(ErfErfcFitter.Kind.ERF);

            case GAUSSIAN:
                return new GaussianFitter();

            case GAUSSIANS:
                // "order" interpreted as number of Gaussians (>= 1)
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
     * Compute fit or spline artifacts as required by the current {@link CurveDrawingMethod}.
     *
     * @param force true to recompute even if not dirty
     */
    @Override
    public void doCurveFit(boolean force) {

        if (!force && !isDirty()) {
            return;
        }

        try {
            final CurveDrawingMethod method = getCurveDrawingMethod();

            // Always clear stale artifacts before recompute
            clearComputedArtifacts();

            switch (method) {

                // Pure drawing styles: nothing to compute
                case NONE:
                case CONNECT:
                case STAIRS:
                    break;

                case CUBICSPLINE: {
                    // Spline over bin centers/counts (unweighted)
                    HistoData.FitData fd = histo.prepareForFit(
                            includeZeroBins,
                            histo.getMinX(),
                            histo.getMaxX(),
                            false);

                    if (fd != null && fd.x != null && fd.x.length >= 2) {
                        setCubicSpline(new CubicSpline(fd.x, fd.y));
                    }
                    break;
                }

                // Fit-based styles
                case POLYNOMIAL:
                case ERF:
                case GAUSSIANS:
                case HARMONIC:
                case GAUSSIAN: {

                    IFitter fitter = createFitter(method);
                    if (fitter == null) {
                        break;
                    }

                    FitVectors v = buildFitVectorsFromHistogram();
                    if (v == null) {
                        setFitResult(null);
                        break;
                    }

                    FitResult fr = fitWithOptionalWeights(fitter, v);
                    setFitResult(fr);
                    break;
                }

                default:
                    break;
            }
        }
        catch (Exception e) {
            // Fail soft: leave artifacts null
            setFitResult(null);
            setCubicSpline(null);
        }
        finally {
            setDirty(false);
        }
    }
}
