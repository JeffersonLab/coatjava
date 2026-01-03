package cnuphys.splot.fit;

/**
 * Provides an initial parameter guess for a curve fitter.
 * Implementations should be fast and side-effect free.
 */
@FunctionalInterface
public interface IInitialGuess {

    /**
     * Compute an initial parameter guess.
     *
     * @param x       x data
     * @param y       y data
     * @param weights optional weights (may be null)
     * @return initial parameters (model-specific ordering/length)
     */
    double[] guess(double[] x, double[] y, double[] weights);
}
