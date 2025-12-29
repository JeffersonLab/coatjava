package cnuphys.splot.fit;

/**
 * Simple functional interface for objects that can return a scalar value as a function of one variable.
 */
public interface IValueGetter {

    /**
     * Evaluate the function at {@code x}.
     *
     * @param x the independent variable
     * @return the function value at {@code x}
     */
    double value(double x);
}