package cnuphys.splot.fit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility methods for sampling {@link Evaluator} functions
 * for plotting or further analysis.
 */
public final class ValueSampler {

    private ValueSampler() {
        // no instances
    }

    /**
     * Sample a function uniformly over [xmin, xmax].
     *
     * @param f     function to evaluate
     * @param xmin  minimum x
     * @param xmax  maximum x
     * @param n     number of sample points (n >= 2)
     * @return array { xs, ys } each of length n
     */
    public static double[][] sampleUniform(Evaluator f,
                                           double xmin,
                                           double xmax,
                                           int n) {

       	Objects.requireNonNull(f, "Evaluator is null");
        if (n < 2) {
            throw new IllegalArgumentException("n must be >= 2");
        }
        if (!(xmax > xmin)) {
            throw new IllegalArgumentException("xmax must be > xmin");
        }

        double[] xs = new double[n];
        double[] ys = new double[n];

        double dx = (xmax - xmin) / (n - 1);

        for (int i = 0; i < n; i++) {
            double x = xmin + i * dx;
            xs[i] = x;
            ys[i] = f.value(x);
        }

        return new double[][] { xs, ys };
    }

    /**
     * Sample a function at explicitly supplied x values.
     *
     * @param f  function to evaluate
     * @param xs x values
     * @return y values (same length as xs)
     */
    public static double[] sampleAt(Evaluator f, double[] xs) {
    	Objects.requireNonNull(xs, "xs is null");
    	Objects.requireNonNull(f, "Evaluator is null");

        double[] ys = new double[xs.length];
        for (int i = 0; i < xs.length; i++) {
            ys[i] = f.value(xs[i]);
        }
        return ys;
    }

    /**
     * Sample uniformly, skipping non-finite results (NaN/Inf).
     * Useful when plotting functions that may blow up at the edges.
     *
     * @param f     function
     * @param xmin  minimum x
     * @param xmax  maximum x
     * @param n     nominal number of samples
     * @return array { xs, ys } with only finite points retained
     */
    public static double[][] sampleUniformFinite(Evaluator f,
                                                 double xmin,
                                                 double xmax,
                                                 int n) {

        double[][] raw = sampleUniform(f, xmin, xmax, n);

        List<Double> xList = new ArrayList<>();
        List<Double> yList = new ArrayList<>();

        for (int i = 0; i < raw[0].length; i++) {
            double y = raw[1][i];
            if (Double.isFinite(y)) {
                xList.add(raw[0][i]);
                yList.add(y);
            }
        }

        double[] xs = new double[xList.size()];
        double[] ys = new double[yList.size()];

        for (int i = 0; i < xs.length; i++) {
            xs[i] = xList.get(i);
            ys[i] = yList.get(i);
        }

        return new double[][] { xs, ys };
    }
}
