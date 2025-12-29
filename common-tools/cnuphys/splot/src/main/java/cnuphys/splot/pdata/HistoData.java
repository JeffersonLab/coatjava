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
 * @author heddle
 */
public class HistoData {

    /** The XML root element name. */
    public static final String XmlRootElementName = "HistoData";

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
    public String getName() {
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
    public static final class FitData {
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
     * Prepare arrays suitable for the fitters:
     * <ul>
     *   <li>{@code x[i]} = bin center</li>
     *   <li>{@code y[i]} = count in bin (as double)</li>
     * </ul>
     *
     * @param includeZeroBins if false, bins with count==0 are skipped
     * @return fit data with unit weights (weights == null)
     */
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
     *
     * <p>Poisson weights behavior:
     * <ul>
     *   <li>count &gt; 0: sigma = sqrt(count), weight = 1/count</li>
     *   <li>count == 0: if included, weight is set to 1 (gentle) rather than infinite</li>
     * </ul>
     *
     * @param includeZeroBins include bins with count==0 if true
     * @param xmin inclusive x-range (bin center) filter
     * @param xmax inclusive x-range (bin center) filter
     * @param poissonWeights if true, include weights suitable for count data
     * @return fit data; weights may be null if poissonWeights=false
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
                // weight = 1/sigma^2; sigma=sqrt(c) => weight=1/c for c>0
                double w = (c > 0L) ? (1.0 / c) : 1.0;
                ws.add(w);
            }
        }

        double[] xArr = toDoubleArray(xs);
        double[] yArr = toDoubleArray(ys);
        double[] wArr = (ws == null) ? null : toDoubleArray(ws);

        return new FitData(xArr, yArr, wArr);
    }

    private static double[] toDoubleArray(List<Double> list) {
        double[] a = new double[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        return a;
    }
    
    /**
     * Prepare arrays suitable for the fitters using an inclusive bin-index range.
     * <p>
     * The returned arrays are built from bin centers and bin counts:
     * <ul>
     *   <li>{@code x[i]} = center of bin</li>
     *   <li>{@code y[i]} = count in bin (as double)</li>
     *   <li>{@code weights[i]} (optional) = 1/sigmaY^2 using Poisson sigmaY = sqrt(count)</li>
     * </ul>
     *
     * @param includeZeroBins include bins with count==0 if true
     * @param bin0 inclusive starting bin index (0-based). Values outside range are clamped.
     * @param bin1 inclusive ending bin index (0-based). Values outside range are clamped.
     * @param poissonWeights if true, include weights suitable for count data
     * @return fit data; weights may be null if poissonWeights=false
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

        // Count how many bins will be included
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

            double xc = getBinMidValue(bin);
            xArr[j] = xc;
            yArr[j] = (double) c;

            if (poissonWeights) {
                // weight = 1/sigma^2; sigma=sqrt(c) => weight=1/c for c>0
                // for c==0 (if included), use a gentle finite weight
                wArr[j] = (c > 0L) ? (1.0 / c) : 1.0;
            }

            j++;
        }

        return new FitData(xArr, yArr, wArr);
    }

    /**
     * Convenience overload: prepare fit vectors for an inclusive bin range with unit weights.
     */
    public FitData prepareForFit(boolean includeZeroBins, int bin0, int bin1) {
        return prepareForFit(includeZeroBins, bin0, bin1, false);
    }

    private static int clampBin(int bin, int nbin) {
        if (bin < 0) return 0;
        if (bin >= nbin) return nbin - 1;
        return bin;
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

            String name = histo.getName();
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
                throw new IllegalArgumentException("grid must be strictly ascending (duplicate or decreasing at index " + i + ")");
            }
        }
        return g;
    }
}
