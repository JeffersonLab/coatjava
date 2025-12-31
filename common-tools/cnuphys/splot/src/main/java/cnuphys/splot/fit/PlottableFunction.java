package cnuphys.splot.fit;

import java.util.Objects;

/**
 * Simple immutable implementation of {@link IPlottableFunction}.
 */
public final class PlottableFunction implements IPlottableFunction {

    private final IValueGetter f;
    private final double xmin;
    private final double xmax;

    public PlottableFunction(IValueGetter f, double xmin, double xmax) {
        if (f == null) {
            throw new IllegalArgumentException("IValueGetter is null");
        }
        if (!(xmax > xmin)) {
            throw new IllegalArgumentException("xmax must be > xmin");
        }
        this.f = f;
        this.xmin = xmin;
        this.xmax = xmax;
    }

    @Override
    public IValueGetter function() {
        return f;
    }

    @Override
    public double xmin() {
        return xmin;
    }

    @Override
    public double xmax() {
        return xmax;
    }
    
    /**
     * Convenience: wrap a getter as an {@link IPlottableFunction}.
     *
     * @param getter y(x) evaluator
     * @param xmin x minimum
     * @param xmax x maximum
     * @return plottable function
     */
    public static IPlottableFunction from(IValueGetter getter, double xmin, double xmax) {
        Objects.requireNonNull(getter, "getter");
        return new PlottableFunction(getter, xmin, xmax);
    }

    /**
     * Convenience: wrap a getter as an {@link IPlottableFunction} over the range of the data.
     * If the range is degenerate, pads by a tiny epsilon.
     *
     * @param getter y(x) evaluator
     * @param x x data array used to infer range
     * @return plottable function
     */
    public static IPlottableFunction overDataRange(IValueGetter getter, double[] x) {
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(x, "x");

        if (x.length == 0) {
            throw new IllegalArgumentException("x is empty");
        }

        double xmin = x[0], xmax = x[0];
        for (double v : x) {
            xmin = Math.min(xmin, v);
            xmax = Math.max(xmax, v);
        }

        if (!(xmax > xmin)) {
            double eps = (xmin == 0.0) ? 1.0 : Math.abs(xmin) * 1e-6;
            xmin -= eps;
            xmax += eps;
        }

        return from(getter, xmin, xmax);
    }
}
