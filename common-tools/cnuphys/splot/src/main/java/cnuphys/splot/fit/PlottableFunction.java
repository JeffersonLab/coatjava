package cnuphys.splot.fit;

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
}
