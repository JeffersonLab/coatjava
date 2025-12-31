package cnuphys.splot.pdata;

/**
 * A named numeric column used by sPlot datasets.
 *
 * <p>This is currently a thin extension of {@link DataList} that provides basic statistics.</p>
 */
@SuppressWarnings("serial")
public class DataColumn extends DataList {

    /**
     * Create a data column with the given name.
     *
     * @param name column name
     */
    public DataColumn(String name) {
        super(name);
    }

    /**
     * Get mean of the data in this column.
     *
     * @return mean, or NaN if empty
     */
    public double getMean() {
        int n = size();
        if (n <= 0) {
            return Double.NaN;
        }
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += super.get(i);
        }
        return sum / n;
    }

    /**
     * Get (population) variance of the data in this column.
     *
     * @return variance, or NaN if empty
     */
    public double getVariance() {
        int n = size();
        if (n <= 0) {
            return Double.NaN;
        }
        if (n == 1) {
            return 0.0;
        }
        double mean = getMean();
        double ss = 0.0;
        for (int i = 0; i < n; i++) {
            double d = super.get(i) - mean;
            ss += d * d;
        }
        return ss / n;
    }

    /**
     * Standard deviation of the data in this column.
     *
     * @return standard deviation, or NaN if empty
     */
    public double getStandardDeviation() {
        double var = getVariance();
        return Double.isNaN(var) ? Double.NaN : Math.sqrt(Math.max(0.0, var));
    }
}
