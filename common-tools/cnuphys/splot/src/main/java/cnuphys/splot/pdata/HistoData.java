package cnuphys.splot.pdata;

import java.awt.Point;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import cnuphys.splot.plot.DoubleFormat;
import cnuphys.splot.plot.PlotCanvas;
import cnuphys.splot.plot.PlotParameters;
import cnuphys.splot.plot.UnicodeSupport;

/**
 * Container class for 1D histogram data.
 * <p>
 * Bin edges are stored in {@code grid[]} of length (numBins + 1). Counts are stored in
 * {@code counts[]} of length numBins.
 *
 * <h3>Fit preparation helpers</h3>
 * This class includes helpers to build x/y vectors for least-squares fitting:
 * <ul>
 *   <li>x[i] = bin center</li>
 *   <li>y[i] = bin count</li>
 *   <li>optional Poisson weights: w[i] = 1/count for count&gt;0</li>
 * </ul>
 * It also includes several peak-finding strategies and "prepare around peak" convenience methods,
 * including guarded versions that deal with edge proximity and minimum-point constraints.
 *
 * @author heddle
 */
public class HistoData {

    /** Sentinel returned by {@link #getBin(double)} for values below the histogram range. */
    private static final int UNDERFLOW = -200;

    /** Sentinel returned by {@link #getBin(double)} for values above the histogram range. */
    private static final int OVERFLOW = -100;

    /**
     * Cached statistical results: mean in index 0, standard deviation in index 1, rms in index 2.
     * Null means cache is invalid and must be recomputed.
     */
    private double[] stats;

    /** Histogram name (used as curve name). */
    private String name;

    /** Underflow and overflow counts (values outside the range). */
    private long underCount;
    private long overCount;

    /** Use rms or sigma in legend. */
    private boolean rmsInHistoLegend = true;

    /** Draw sqrt(n) statistical errors. */
    private boolean statErrors;

    /** Bin edges (length = numBins + 1). Must be strictly ascending. */
    private final double[] grid;

    /** Bin counts (length = numBins). */
    private final long[] counts;

    /**
     * The data for a 1D histogram where the bin spacing is uniform.
     *
     * @param name    histogram name
     * @param valMin  range minimum
     * @param valMax  range maximum
     * @param numBins number of bins (>= 1)
     */
    public HistoData(String name, double valMin, double valMax, int numBins) {
        this(name, evenBins(valMin, valMax, numBins));
    }

    /**
     * The data for a 1D histogram where the bin spacing is arbitrary (i.e., not uniform).
     *
     * @param name histogram name
     * @param grid bin edge array in strictly ascending order (length >= 2)
     */
    public HistoData(String name, double[] grid) {
        this.name = name;
        this.grid = validateAndCopyGrid(grid);
        this.counts = new long[getNumberBins()];
        clear();
    }

    /** @return the name of the histogram. */
    public String name() {
        return name;
    }

    /** Set the histogram name. */
    public void setName(String name) {
        this.name = name;
    }

    /** Reset counts and cached statistics. */
    private void reset() {
        underCount = 0L;
        overCount = 0L;
        stats = null;
        Arrays.fill(counts, 0L);
    }

    /** Clear histogram data (same as reset). */
    public void clear() {
        reset();
    }

    /** @return number of bins. */
    public int getNumberBins() {
        return grid.length - 1;
    }

    /** @return a defensive copy of the bin-edge grid. */
    public double[] getGridCopy() {
        return grid.clone();
    }

    /** @return the counts array (live). */
    public long[] getCounts() {
        return counts;
    }

    /** @return a defensive copy of the counts array. */
    public long[] getCountsCopy() {
        return counts.clone();
    }

    /** Get the count for a given bin. */
    public long getCount(int bin) {
        if (bin < 0 || bin >= counts.length) {
            return 0L;
        }
        return counts[bin];
    }

    /**
     * Get the number of entries in the histogram (excluding underflows and overflows).
     *
     * @return total in-range count
     */
    public long getGoodCount() {
        long sum = 0L;
        for (long c : counts) {
            sum += c;
        }
        return sum;
    }

    /** @return total count including under/overflows. */
    public long getTotalCount() {
        return getGoodCount() + getUnderCount() + getOverCount();
    }

    /** @return underflow count. */
    public long getUnderCount() {
        return underCount;
    }

    /** @return overflow count. */
    public long getOverCount() {
        return overCount;
    }

    /** @return minimum x value (left edge of first bin). */
    public double getMinX() {
        return grid[0];
    }

    /** @return maximum x value (right edge of last bin). */
    public double getMaxX() {
        return grid[grid.length - 1];
    }

    /** @return minimum y value (always 0 for histograms). */
    public double getMinY() {
        return 0.0;
    }

    /** @return maximum y value (max bin count, at least 1). */
    public double getMaxY() {
        long max = 1L;
        for (long c : counts) {
            max = Math.max(max, c);
        }
        return max;
    }

    /** Add one value to the histogram. */
    public void add(double value) {
        stats = null;
        int bin = getBin(value);
        if (bin == UNDERFLOW) {
            underCount++;
        } else if (bin == OVERFLOW) {
            overCount++;
        } else {
            counts[bin]++;
        }
    }

    /**
     * Set a bin to a given count (value determines which bin).
     * Counts outside range are accumulated into under/over counts.
     */
    public void setCount(double val, int count) {
        stats = null;
        int bin = getBin(val);
        if (bin == UNDERFLOW) {
            underCount += count;
        } else if (bin == OVERFLOW) {
            overCount += count;
        } else {
            counts[bin] = count;
        }
    }

    /** @return bin midpoint x value. */
    public double getBinMidValue(int bin) {
        if (bin < 0 || bin >= getNumberBins()) {
            return Double.NaN;
        }
        return 0.5 * (grid[bin] + grid[bin + 1]);
    }

    /** @return left edge of bin. */
    public double getBinMinX(int bin) {
        if (bin < 0 || bin >= getNumberBins()) {
            return Double.NaN;
        }
        return grid[bin];
    }

    /** @return right edge of bin. */
    public double getBinMaxX(int bin) {
        if (bin < 0 || bin >= getNumberBins()) {
            return Double.NaN;
        }
        return grid[bin + 1];
    }

    /** @return bin width (right-left). */
    public double getBinWidth(int bin) {
        if (bin < 0 || bin >= getNumberBins()) {
            return Double.NaN;
        }
        return grid[bin + 1] - grid[bin];
    }

    /**
     * Get the bin for a given value.
     *
     * @param val the value
     * @return bin index in [0..numBins-1], or {@link #UNDERFLOW}/{@link #OVERFLOW}
     */
    public int getBin(double val) {
        if (val < getMinX()) {
            return UNDERFLOW;
        }
        if (val > getMaxX()) {
            return OVERFLOW;
        }

        int index = Arrays.binarySearch(grid, val);
        if (index < 0) {
            index = -(index + 1); // insertion point
        }
        int bin = index - 1;
        return Math.max(0, Math.min(grid.length - 2, bin));
    }

    /**
     * Get mean, standard deviation, and rms.
     *
     * @return array: [mean, stdDev, rms]
     */
    public double[] getBasicStatistics() {
        if (stats != null) {
            return stats;
        }

        stats = new double[] { Double.NaN, Double.NaN, Double.NaN };

        int nbin = getNumberBins();
        long tot = getGoodCount();
        if (nbin > 0 && tot > 0) {
            double sum = 0.0;
            double sumsq = 0.0;

            for (int bin = 0; bin < nbin; bin++) {
                double x = getBinMidValue(bin);
                double w = counts[bin];
                sum += w * x;
                sumsq += w * x * x;
            }

            double mean = sum / tot;
            double avgSq = sumsq / tot;

            stats[0] = mean;
            stats[1] = Math.sqrt(Math.max(0.0, avgSq - mean * mean));
            stats[2] = Math.sqrt(Math.max(0.0, avgSq));
        }

        return stats;
    }

    /**
     * A string displaying mean and either rms or sigma plus under/over counts.
     *
     * @return stats string
     */
    public String statStr() {
        double[] res = getBasicStatistics();
        if (rmsInHistoLegend) {
            return String.format(UnicodeSupport.SMALL_MU + ": %-4.2g rms: %-4.2g under: %d over: %d",
                    res[0], res[2], underCount, overCount);
        }
        return String.format(UnicodeSupport.SMALL_MU + ": %-4.2g " + UnicodeSupport.SMALL_SIGMA + ": %-4.2g under: %d over: %d",
                res[0], res[1], underCount, overCount);
    }

    /** Set whether we use rms or sigma in histogram legends. */
    public void setRmsInHistoLegend(boolean useRMS) {
        this.rmsInHistoLegend = useRMS;
    }

    /** @return true to use rms, false to use sigma. */
    public boolean useRmsInHistoLegend() {
        return rmsInHistoLegend;
    }

    /** Set whether we draw sqrt(n) statistical errors. */
    public void setDrawStatisticalErrors(boolean statErr) {
        this.statErrors = statErr;
    }

    /** @return true if statistical errors are drawn. */
    public boolean drawStatisticalErrors() {
        return statErrors;
    }

    /**
     * Return a string describing the max bin(s) (1-based indices).
     */
    public String maxBinString() {
        long maxCount = -1;
        for (long lv : counts) {
            maxCount = Math.max(maxCount, lv);
        }
        if (maxCount < 1) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Max count: ").append(maxCount).append(" in 1-based bin(s):");
        for (int bin = 0; bin < getNumberBins(); bin++) {
            if (counts[bin] == maxCount) {
                sb.append(' ').append(bin + 1);
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------
    // "Prepare for fit" helper
    // ------------------------------------------------------------------------

    /**
     * Lightweight container for (x,y[,w]) vectors prepared from histogram bins.
     * <p>
     * If {@code weights} is null, the caller can treat it as "unit weights".
     */
    public static class FitData {
        public final double[] x;
        public final double[] y;
        public final double[] weights; // may be null

        public FitData(double[] x, double[] y, double[] weights) {
            this.x = x;
            this.y = y;
            this.weights = weights;
        }
    }

    /**
     * Fit prep result that also reports which peak bin and window were used.
     * Useful for status/debug strings in the UI.
     */
    public static final class FitWindowData extends FitData {
        public final int peakBin;
        public final int bin0;
        public final int bin1;
        public final int halfWindowBinsUsed;

        public FitWindowData(double[] x, double[] y, double[] weights,
                             int peakBin, int bin0, int bin1, int halfWindowBinsUsed) {
            super(x, y, weights);
            this.peakBin = peakBin;
            this.bin0 = bin0;
            this.bin1 = bin1;
            this.halfWindowBinsUsed = halfWindowBinsUsed;
        }
    }

    /** Prepare fit vectors over full x-range; optionally skip empty bins. */
    public FitData prepareForFit(boolean includeZeroBins) {
        return prepareForFit(includeZeroBins, getMinX(), getMaxX(), false);
    }

    /**
     * Prepare arrays suitable for the fitters:
     * <ul>
     *   <li>{@code x[i]} = bin center</li>
     *   <li>{@code y[i]} = count in bin (as double)</li>
     *   <li>{@code weights[i]} (optional) = 1/sigmaY^2 using Poisson sigmaY = sqrt(count)</li>
     * </ul>
     */
    public FitData prepareForFit(boolean includeZeroBins, double xmin, double xmax, boolean poissonWeights) {
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        List<Double> ws = poissonWeights ? new ArrayList<>() : null;

        int nbin = getNumberBins();
        for (int bin = 0; bin < nbin; bin++) {
            long c = counts[bin];
            if (!includeZeroBins && c == 0L) {
                continue;
            }

            double xc = getBinMidValue(bin);
            if (xc < xmin || xc > xmax) {
                continue;
            }

            xs.add(xc);
            ys.add((double) c);

            if (poissonWeights) {
                ws.add(poissonWeightForCount(c));
            }
        }

        double[] xArr = toDoubleArray(xs);
        double[] yArr = toDoubleArray(ys);
        double[] wArr = (ws == null) ? null : toDoubleArray(ws);

        return new FitData(xArr, yArr, wArr);
    }

    /**
     * Prepare arrays suitable for the fitters using an inclusive bin-index range.
     */
    public FitData prepareForFit(boolean includeZeroBins, int bin0, int bin1, boolean poissonWeights) {
        int nbin = getNumberBins();
        if (nbin <= 0) {
            return new FitData(new double[0], new double[0], poissonWeights ? new double[0] : null);
        }

        int b0 = clampBin(bin0, nbin);
        int b1 = clampBin(bin1, nbin);
        if (b0 > b1) {
            int tmp = b0;
            b0 = b1;
            b1 = tmp;
        }

        int keep = 0;
        for (int bin = b0; bin <= b1; bin++) {
            long c = counts[bin];
            if (!includeZeroBins && c == 0L) {
                continue;
            }
            keep++;
        }

        double[] xArr = new double[keep];
        double[] yArr = new double[keep];
        double[] wArr = poissonWeights ? new double[keep] : null;

        int j = 0;
        for (int bin = b0; bin <= b1; bin++) {
            long c = counts[bin];
            if (!includeZeroBins && c == 0L) {
                continue;
            }

            xArr[j] = getBinMidValue(bin);
            yArr[j] = (double) c;
            if (poissonWeights) {
                wArr[j] = poissonWeightForCount(c);
            }
            j++;
        }

        return new FitData(xArr, yArr, wArr);
    }

    /** Convenience overload: inclusive bin range, unit weights. */
    public FitData prepareForFit(boolean includeZeroBins, int bin0, int bin1) {
        return prepareForFit(includeZeroBins, bin0, bin1, false);
    }

    /**
     * Prepare arrays suitable for fitters from a symmetric window around a peak bin.
     */
    public FitData prepareForFitAroundPeak(boolean includeZeroBins, int peakBin, int halfWindowBins, boolean poissonWeights) {
        if (halfWindowBins < 0) {
            throw new IllegalArgumentException("halfWindowBins must be >= 0");
        }
        int b0 = peakBin - halfWindowBins;
        int b1 = peakBin + halfWindowBins;
        return prepareForFit(includeZeroBins, b0, b1, poissonWeights);
    }

    /** Convenience overload: unit weights, symmetric window around a peak bin. */
    public FitData prepareForFitAroundPeak(boolean includeZeroBins, int peakBin, int halfWindowBins) {
        return prepareForFitAroundPeak(includeZeroBins, peakBin, halfWindowBins, false);
    }

    /**
     * Convenience: find global raw peak and prepare arrays around it.
     */
    public FitData prepareForFitAroundPeak(boolean includeZeroBins, int halfWindowBins, boolean poissonWeights) {
        int peak = findPeakBin();
        if (peak < 0) {
            return new FitData(new double[0], new double[0], poissonWeights ? new double[0] : null);
        }
        return prepareForFitAroundPeak(includeZeroBins, peak, halfWindowBins, poissonWeights);
    }

    // ------------------------------------------------------------------------
    // Peak finders
    // ------------------------------------------------------------------------

    /**
     * Find the (0-based) bin index with the maximum count (raw).
     * If there are multiple maxima, returns the first.
     *
     * @return peak bin index, or -1 if histogram has no bins
     */
    public int findPeakBin() {
        int nbin = getNumberBins();
        if (nbin <= 0) {
            return -1;
        }
        int peak = 0;
        long best = counts[0];
        for (int bin = 1; bin < nbin; bin++) {
            long c = counts[bin];
            if (c > best) {
                best = c;
                peak = bin;
            }
        }
        return peak;
    }

    /**
     * Find the (0-based) bin index with the maximum count within an inclusive bin range.
     * If there are multiple maxima, returns the first.
     */
    public int findPeakBin(int bin0, int bin1) {
        int nbin = getNumberBins();
        if (nbin <= 0) {
            return -1;
        }

        int b0 = clampBin(bin0, nbin);
        int b1 = clampBin(bin1, nbin);
        if (b0 > b1) {
            int tmp = b0;
            b0 = b1;
            b1 = tmp;
        }

        int peak = b0;
        long best = counts[b0];
        for (int bin = b0 + 1; bin <= b1; bin++) {
            long c = counts[bin];
            if (c > best) {
                best = c;
                peak = bin;
            }
        }
        return peak;
    }

    /**
     * Find the peak bin using a flat moving-average smoothing.
     */
    public int findPeakBinSmoothed(int bin0, int bin1, int radius, boolean ignoreZeroBins) {
        int nbin = getNumberBins();
        if (nbin <= 0) {
            return -1;
        }
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be >= 0");
        }

        int b0 = clampBin(bin0, nbin);
        int b1 = clampBin(bin1, nbin);
        if (b0 > b1) {
            int tmp = b0;
            b0 = b1;
            b1 = tmp;
        }

        int bestBin = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int i = b0; i <= b1; i++) {
            if (ignoreZeroBins && counts[i] == 0L) {
                continue;
            }

            int lo = Math.max(0, i - radius);
            int hi = Math.min(nbin - 1, i + radius);

            long sum = 0L;
            int cnt = 0;
            for (int j = lo; j <= hi; j++) {
                sum += counts[j];
                cnt++;
            }

            double avg = (cnt > 0) ? ((double) sum / cnt) : Double.NEGATIVE_INFINITY;

            if (avg > bestScore) {
                bestScore = avg;
                bestBin = i;
            } else if (avg == bestScore && bestBin >= 0) {
                if (counts[i] > counts[bestBin]) {
                    bestBin = i;
                }
            }
        }

        return (bestBin >= 0) ? bestBin : findPeakBin(b0, b1);
    }

    /** Convenience: full-range flat-smoothed peak. */
    public int findPeakBinSmoothed(int radius, boolean ignoreZeroBins) {
        return findPeakBinSmoothed(0, getNumberBins() - 1, radius, ignoreZeroBins);
    }

    /**
     * Find the peak bin using a triangular-kernel smoothing.
     */
    public int findPeakBinTriangularSmoothed(int bin0, int bin1, int radius, boolean ignoreZeroBins) {
        int nbin = getNumberBins();
        if (nbin <= 0) {
            return -1;
        }
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be >= 0");
        }

        int b0 = clampBin(bin0, nbin);
        int b1 = clampBin(bin1, nbin);
        if (b0 > b1) {
            int tmp = b0;
            b0 = b1;
            b1 = tmp;
        }

        int bestBin = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int i = b0; i <= b1; i++) {
            if (ignoreZeroBins && counts[i] == 0L) {
                continue;
            }

            double score = triangularSmoothedScore(i, radius, nbin);

            if (score > bestScore) {
                bestScore = score;
                bestBin = i;
            } else if (score == bestScore && bestBin >= 0) {
                long ci = counts[i];
                long cb = counts[bestBin];
                if (ci > cb) {
                    bestBin = i;
                } else if (ci == cb) {
                    int mid = (b0 + b1) / 2;
                    if (Math.abs(i - mid) < Math.abs(bestBin - mid)) {
                        bestBin = i;
                    }
                }
            }
        }

        return (bestBin >= 0) ? bestBin : findPeakBin(b0, b1);
    }

    /** Convenience: full-range triangular-smoothed peak. */
    public int findPeakBinTriangularSmoothed(int radius, boolean ignoreZeroBins) {
        return findPeakBinTriangularSmoothed(0, getNumberBins() - 1, radius, ignoreZeroBins);
    }

    /**
     * Best-of-both-worlds peak:
     * <ul>
     *   <li>Primary: maximize triangular-smoothed score</li>
     *   <li>If multiple adjacent bins tie (plateau), pick the most sensible raw bin within plateau</li>
     * </ul>
     */
    public int findPeakBinBest(int bin0, int bin1, int smoothRadius, boolean ignoreZeroBins) {
        int nbin = getNumberBins();
        if (nbin <= 0) {
            return -1;
        }
        if (smoothRadius < 0) {
            throw new IllegalArgumentException("smoothRadius must be >= 0");
        }

        int b0 = clampBin(bin0, nbin);
        int b1 = clampBin(bin1, nbin);
        if (b0 > b1) {
            int tmp = b0;
            b0 = b1;
            b1 = tmp;
        }

        if (smoothRadius == 0) {
            return rawPeakWithPlateauHandling(b0, b1, ignoreZeroBins);
        }

        double bestScore = Double.NEGATIVE_INFINITY;
        int p0 = -1;
        int p1 = -1;

        for (int i = b0; i <= b1; i++) {
            if (ignoreZeroBins && counts[i] == 0L) {
                continue;
            }

            double score = triangularSmoothedScore(i, smoothRadius, nbin);

            if (score > bestScore) {
                bestScore = score;
                p0 = i;
                p1 = i;
            } else if (score == bestScore && p0 >= 0) {
                if (i == p1 + 1) {
                    p1 = i;
                } else {
                    // Non-contiguous tie: keep the plateau segment whose chosen raw representative is better.
                    int oldChoice = chooseBestInPlateau(p0, p1, b0, b1);
                    int newChoice = i;
                    if (counts[newChoice] > counts[oldChoice]) {
                        p0 = i;
                        p1 = i;
                    } else if (counts[newChoice] == counts[oldChoice]) {
                        int mid = (b0 + b1) / 2;
                        if (Math.abs(newChoice - mid) < Math.abs(oldChoice - mid)) {
                            p0 = i;
                            p1 = i;
                        }
                    }
                }
            }
        }

        if (p0 < 0) {
            return findPeakBin(b0, b1);
        }
        if (p0 == p1) {
            return p0;
        }
        return chooseBestInPlateau(p0, p1, b0, b1);
    }

    /** Convenience: full-range best peak. */
    public int findPeakBinBest(int smoothRadius, boolean ignoreZeroBins) {
        return findPeakBinBest(0, getNumberBins() - 1, smoothRadius, ignoreZeroBins);
    }

    // ------------------------------------------------------------------------
    // Peak-based fit-prep conveniences
    // ------------------------------------------------------------------------

    public FitData prepareForFitAroundSmoothedPeak(boolean includeZeroBins,
                                                  int halfWindowBins,
                                                  int smoothRadius,
                                                  boolean ignoreZeroBins,
                                                  boolean poissonWeights) {
        int peak = findPeakBinSmoothed(smoothRadius, ignoreZeroBins);
        if (peak < 0) {
            return new FitData(new double[0], new double[0], poissonWeights ? new double[0] : null);
        }
        return prepareForFitAroundPeak(includeZeroBins, peak, halfWindowBins, poissonWeights);
    }

    public FitData prepareForFitAroundSmoothedPeak(boolean includeZeroBins,
                                                  int bin0,
                                                  int bin1,
                                                  int halfWindowBins,
                                                  int smoothRadius,
                                                  boolean ignoreZeroBins,
                                                  boolean poissonWeights) {
        int peak = findPeakBinSmoothed(bin0, bin1, smoothRadius, ignoreZeroBins);
        if (peak < 0) {
            return new FitData(new double[0], new double[0], poissonWeights ? new double[0] : null);
        }
        return prepareForFitAroundPeak(includeZeroBins, peak, halfWindowBins, poissonWeights);
    }

    public FitData prepareForFitAroundTriangularSmoothedPeak(boolean includeZeroBins,
                                                            int halfWindowBins,
                                                            int smoothRadius,
                                                            boolean ignoreZeroBins,
                                                            boolean poissonWeights) {
        int peak = findPeakBinTriangularSmoothed(smoothRadius, ignoreZeroBins);
        if (peak < 0) {
            return new FitData(new double[0], new double[0], poissonWeights ? new double[0] : null);
        }
        return prepareForFitAroundPeak(includeZeroBins, peak, halfWindowBins, poissonWeights);
    }

    public FitData prepareForFitAroundTriangularSmoothedPeak(boolean includeZeroBins,
                                                            int bin0,
                                                            int bin1,
                                                            int halfWindowBins,
                                                            int smoothRadius,
                                                            boolean ignoreZeroBins,
                                                            boolean poissonWeights) {
        int peak = findPeakBinTriangularSmoothed(bin0, bin1, smoothRadius, ignoreZeroBins);
        if (peak < 0) {
            return new FitData(new double[0], new double[0], poissonWeights ? new double[0] : null);
        }
        return prepareForFitAroundPeak(includeZeroBins, peak, halfWindowBins, poissonWeights);
    }

    public FitData prepareForFitAroundBestPeak(boolean includeZeroBins,
                                              int halfWindowBins,
                                              int smoothRadius,
                                              boolean ignoreZeroBins,
                                              boolean poissonWeights) {
        int peak = findPeakBinBest(smoothRadius, ignoreZeroBins);
        if (peak < 0) {
            return new FitData(new double[0], new double[0], poissonWeights ? new double[0] : null);
        }
        return prepareForFitAroundPeak(includeZeroBins, peak, halfWindowBins, poissonWeights);
    }

    public FitData prepareForFitAroundBestPeak(boolean includeZeroBins,
                                              int bin0,
                                              int bin1,
                                              int halfWindowBins,
                                              int smoothRadius,
                                              boolean ignoreZeroBins,
                                              boolean poissonWeights) {
        int peak = findPeakBinBest(bin0, bin1, smoothRadius, ignoreZeroBins);
        if (peak < 0) {
            return new FitData(new double[0], new double[0], poissonWeights ? new double[0] : null);
        }
        return prepareForFitAroundPeak(includeZeroBins, peak, halfWindowBins, poissonWeights);
    }

    // ------------------------------------------------------------------------
    // Guarded best-peak fit-prep (edge sanity + min-points safety)
    // ------------------------------------------------------------------------

    /**
     * Prepare fit arrays around a robust peak, with:
     * <ul>
     *   <li>edge sanity: prefer peaks that can support the requested window fully</li>
     *   <li>auto-shrink if peak is near edges</li>
     *   <li>min-points safety after filtering; expand window to max if needed; optionally include zeros as last resort</li>
     * </ul>
     *
     * @param includeZeroBins include bins with count==0 in returned fit vectors (if false, zeros are skipped)
     * @param searchBin0 inclusive start bin to search for peak (0-based; clamped)
     * @param searchBin1 inclusive end bin to search for peak (0-based; clamped)
     * @param halfWindowBins requested half-window size (>= 0)
     * @param smoothRadius smoothing radius for {@link #findPeakBinBest(int, int, int, boolean)} (>= 0)
     * @param ignoreZeroBins if true, zero-count bins are skipped as PEAK candidates
     * @param poissonWeights if true, include Poisson weights
     * @param minPoints minimum number of points required after filtering (>= 1)
     */
    public FitWindowData prepareForFitAroundBestPeakGuarded(boolean includeZeroBins,
                                                           int searchBin0,
                                                           int searchBin1,
                                                           int halfWindowBins,
                                                           int smoothRadius,
                                                           boolean ignoreZeroBins,
                                                           boolean poissonWeights,
                                                           int minPoints) {
        int nbin = getNumberBins();
        if (nbin <= 0) {
            return new FitWindowData(new double[0], new double[0], poissonWeights ? new double[0] : null,
                                     -1, 0, -1, 0);
        }
        if (halfWindowBins < 0) {
            throw new IllegalArgumentException("halfWindowBins must be >= 0");
        }
        if (minPoints < 1) {
            throw new IllegalArgumentException("minPoints must be >= 1");
        }

        int s0 = clampBin(searchBin0, nbin);
        int s1 = clampBin(searchBin1, nbin);
        if (s0 > s1) {
            int tmp = s0;
            s0 = s1;
            s1 = tmp;
        }

        // Prefer peaks that can support the requested window fully.
        int inner0 = s0 + halfWindowBins;
        int inner1 = s1 - halfWindowBins;

        int peak;
        if (inner0 <= inner1) {
            peak = findPeakBinBest(inner0, inner1, smoothRadius, ignoreZeroBins);
        } else {
            peak = findPeakBinBest(s0, s1, smoothRadius, ignoreZeroBins);
        }

        if (peak < 0) {
            return new FitWindowData(new double[0], new double[0], poissonWeights ? new double[0] : null,
                                     -1, 0, -1, 0);
        }

        int maxHalf = maxHalfWindowAround(peak, s0, s1, nbin);
        int halfUsed = Math.min(halfWindowBins, maxHalf);

        FitWindowData out = buildWindow(includeZeroBins, peak, halfUsed, s0, s1, poissonWeights);
        if (out.x.length >= minPoints) {
            return out;
        }

        // Expand to maximum possible window.
        if (halfUsed < maxHalf) {
            halfUsed = maxHalf;
            out = buildWindow(includeZeroBins, peak, halfUsed, s0, s1, poissonWeights);
            if (out.x.length >= minPoints) {
                return out;
            }
        }

        // Last resort: if user excluded zeros, try including them to satisfy minPoints.
        if (!includeZeroBins) {
            FitWindowData out2 = buildWindow(true, peak, halfUsed, s0, s1, poissonWeights);
            if (out2.x.length >= minPoints) {
                return out2;
            }
        }

        // Still insufficient: return empty but keep metadata.
        int b0 = Math.max(s0, peak - halfUsed);
        int b1 = Math.min(s1, peak + halfUsed);
        return new FitWindowData(new double[0], new double[0], poissonWeights ? new double[0] : null,
                                 peak, b0, b1, halfUsed);
    }

    /** Convenience: guarded best-peak search over whole histogram. */
    public FitWindowData prepareForFitAroundBestPeakGuarded(boolean includeZeroBins,
                                                           int halfWindowBins,
                                                           int smoothRadius,
                                                           boolean ignoreZeroBins,
                                                           boolean poissonWeights,
                                                           int minPoints) {
        return prepareForFitAroundBestPeakGuarded(includeZeroBins,
                                                  0, getNumberBins() - 1,
                                                  halfWindowBins,
                                                  smoothRadius,
                                                  ignoreZeroBins,
                                                  poissonWeights,
                                                  minPoints);
    }

    // ------------------------------------------------------------------------
    // UI helpers: status string and polygon
    // ------------------------------------------------------------------------

    /**
     * Get the status string.
     *
     * @param canvas     plot canvas
     * @param histo      histogram data
     * @param mousePoint current mouse point (local coords)
     * @param wp         mouse point in world coords
     * @return status string or null if not inside histogram polygon
     */
    public static String statusString(PlotCanvas canvas, HistoData histo, Point mousePoint, Point.Double wp) {
        String s = null;

        Polygon poly = GetPolygon(canvas, histo);
        if (poly.contains(mousePoint)) {
            int bin = histo.getBin(wp.x);

            PlotParameters params = canvas.getParameters();
            String minstr = DoubleFormat.doubleFormat(histo.getBinMinX(bin), params.getNumDecimalX(), params.getMinExponentX());
            String maxstr = DoubleFormat.doubleFormat(histo.getBinMaxX(bin), params.getNumDecimalX(), params.getMinExponentX());

            String name = histo.name();
            if (name != null && !name.isEmpty()) {
                name = "[" + name + "]";
            } else {
                name = "";
            }

            s = name + " bin: " + bin + " [" + minstr + " - " + maxstr + "]";
            s += " counts: " + histo.getCount(bin);
        }

        return s;
    }

    /**
     * Get the drawing polygon.
     *
     * @param canvas plot canvas
     * @param histo  histogram data
     * @return polygon outlining the drawn histogram
     */
    public static Polygon GetPolygon(PlotCanvas canvas, HistoData histo) {
        Polygon poly = new Polygon();
        long[] counts = histo.getCounts();
        Point pp = new Point();
        Point.Double wp = new Point.Double();

        for (int bin = 0; bin < histo.getNumberBins(); bin++) {
            double xmin = histo.getBinMinX(bin);
            double xmax = histo.getBinMaxX(bin);
            double y = counts[bin];

            if (bin == 0) {
                wp.setLocation(xmin, 0);
                canvas.worldToLocal(pp, wp);
                poly.addPoint(pp.x, pp.y);
            }

            wp.setLocation(xmin, y);
            canvas.worldToLocal(pp, wp);
            poly.addPoint(pp.x, pp.y);
            wp.setLocation(xmax, y);
            canvas.worldToLocal(pp, wp);
            poly.addPoint(pp.x, pp.y);

            if (bin == (histo.getNumberBins() - 1)) {
                wp.setLocation(xmax, 0);
                canvas.worldToLocal(pp, wp);
                poly.addPoint(pp.x, pp.y);
            }
        }
        return poly;
    }

    // ------------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------------

    private static double[] toDoubleArray(List<Double> list) {
        double[] a = new double[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        return a;
    }

    private static int clampBin(int bin, int nbin) {
        if (bin < 0) return 0;
        if (bin >= nbin) return nbin - 1;
        return bin;
    }

    private static double poissonWeightForCount(long c) {
        // weight = 1/sigma^2, Poisson sigma=sqrt(c) => weight=1/c for c>0
        // If c==0 and we included it, use a gentle finite weight (1.0).
        return (c > 0L) ? (1.0 / c) : 1.0;
    }

    private double triangularSmoothedScore(int i, int radius, int nbin) {
        int lo = Math.max(0, i - radius);
        int hi = Math.min(nbin - 1, i + radius);

        long weightedSum = 0L;
        long weightSum = 0L;
        for (int j = lo; j <= hi; j++) {
            int d = Math.abs(j - i);
            int w = (radius + 1 - d); // >= 1
            weightedSum += (long) w * counts[j];
            weightSum += w;
        }
        return (weightSum > 0L) ? ((double) weightedSum / weightSum) : Double.NEGATIVE_INFINITY;
    }

    private int rawPeakWithPlateauHandling(int b0, int b1, boolean ignoreZeroBins) {
        long best = Long.MIN_VALUE;
        int p0 = -1;
        int p1 = -1;

        for (int i = b0; i <= b1; i++) {
            long c = counts[i];
            if (ignoreZeroBins && c == 0L) {
                continue;
            }

            if (c > best) {
                best = c;
                p0 = i;
                p1 = i;
            } else if (c == best && p0 >= 0) {
                if (i == p1 + 1) {
                    p1 = i;
                } else {
                    int mid = (b0 + b1) / 2;
                    int oldChoice = chooseBestInPlateau(p0, p1, b0, b1);
                    int newChoice = i;
                    if (Math.abs(newChoice - mid) < Math.abs(oldChoice - mid)) {
                        p0 = i;
                        p1 = i;
                    }
                }
            }
        }

        if (p0 < 0) {
            return findPeakBin(b0, b1);
        }
        if (p0 == p1) {
            return p0;
        }
        return chooseBestInPlateau(p0, p1, b0, b1);
    }

    private int chooseBestInPlateau(int p0, int p1, int b0, int b1) {
        long bestRaw = Long.MIN_VALUE;
        for (int i = p0; i <= p1; i++) {
            bestRaw = Math.max(bestRaw, counts[i]);
        }

        int plateauMid = (p0 + p1) / 2;
        int rangeMid = (b0 + b1) / 2;

        int bestBin = p0;
        int bestDistPlateau = Integer.MAX_VALUE;
        int bestDistRange = Integer.MAX_VALUE;

        for (int i = p0; i <= p1; i++) {
            if (counts[i] != bestRaw) {
                continue;
            }
            int dP = Math.abs(i - plateauMid);
            int dR = Math.abs(i - rangeMid);

            if (dP < bestDistPlateau) {
                bestBin = i;
                bestDistPlateau = dP;
                bestDistRange = dR;
            } else if (dP == bestDistPlateau) {
                if (dR < bestDistRange) {
                    bestBin = i;
                    bestDistRange = dR;
                }
            }
        }

        return bestBin;
    }

    private int maxHalfWindowAround(int peak, int s0, int s1, int nbin) {
        int maxLeft = peak - 0;
        int maxRight = (nbin - 1) - peak;
        int maxHalf = Math.min(maxLeft, maxRight);
        maxHalf = Math.min(maxHalf, peak - s0);
        maxHalf = Math.min(maxHalf, s1 - peak);
        return Math.max(0, maxHalf);
    }

    private FitWindowData buildWindow(boolean includeZeroBins,
                                      int peak,
                                      int halfUsed,
                                      int s0,
                                      int s1,
                                      boolean poissonWeights) {
        int b0 = Math.max(s0, peak - halfUsed);
        int b1 = Math.min(s1, peak + halfUsed);
        FitData fd = prepareForFit(includeZeroBins, b0, b1, poissonWeights);
        return new FitWindowData(fd.x, fd.y, fd.weights, peak, b0, b1, halfUsed);
    }

    private static double[] evenBins(double vmin, double vmax, int numBins) {
        if (numBins <= 0) {
            throw new IllegalArgumentException("numBins must be >= 1");
        }
        if (!(vmax > vmin)) {
            throw new IllegalArgumentException("valMax must be > valMin");
        }

        double[] grid = new double[numBins + 1];
        double del = (vmax - vmin) / numBins;

        grid[0] = vmin;
        for (int i = 1; i < numBins; i++) {
            grid[i] = vmin + i * del;
        }
        grid[numBins] = vmax;

        return grid;
    }

    private static double[] validateAndCopyGrid(double[] grid) {
        Objects.requireNonNull(grid, "grid");
        if (grid.length < 2) {
            throw new IllegalArgumentException("grid must have length >= 2");
        }

        double[] g = grid.clone();
        for (int i = 1; i < g.length; i++) {
            if (!(g[i] > g[i - 1])) {
                throw new IllegalArgumentException(
                        "grid must be strictly ascending (duplicate or decreasing at index " + i + ")");
            }
        }
        return g;
    }
    
    @Override
    public String toString() {
        String nm = (name == null) ? "" : name.trim();

        int nbin = getNumberBins();
        long good = getGoodCount();
        long total = getTotalCount();

        double xmin = (grid != null && grid.length > 0) ? getMinX() : Double.NaN;
        double xmax = (grid != null && grid.length > 0) ? getMaxX() : Double.NaN;

        double[] st = getBasicStatistics(); // safe; caches
        double mean = (st != null && st.length > 0) ? st[0] : Double.NaN;
        double sigma = (st != null && st.length > 1) ? st[1] : Double.NaN;
        double rms = (st != null && st.length > 2) ? st[2] : Double.NaN;

        int peak = (nbin > 0) ? findPeakBin() : -1;
        long peakCount = (peak >= 0) ? getCount(peak) : 0L;
        double peakX = (peak >= 0) ? getBinMidValue(peak) : Double.NaN;

        StringBuilder sb = new StringBuilder(160);
        sb.append("HistoData");
        if (!nm.isEmpty()) {
            sb.append("[").append(nm).append("]");
        }
        sb.append("{bins=").append(nbin)
          .append(", x=[").append(xmin).append(", ").append(xmax).append("]")
          .append(", good=").append(good)
          .append(", under=").append(underCount)
          .append(", over=").append(overCount)
          .append(", total=").append(total);

        // Only add stats if they’re meaningful
        if (!Double.isNaN(mean)) {
            sb.append(", mean=").append(mean);
        }
        if (!Double.isNaN(sigma)) {
            sb.append(", sigma=").append(sigma);
        }
        if (!Double.isNaN(rms)) {
            sb.append(", rms=").append(rms);
        }

        if (peak >= 0) {
            sb.append(", peakBin=").append(peak)
              .append(", peakX=").append(peakX)
              .append(", peakCount=").append(peakCount);
        }

        sb.append("}");
        return sb.toString();
    }

}
