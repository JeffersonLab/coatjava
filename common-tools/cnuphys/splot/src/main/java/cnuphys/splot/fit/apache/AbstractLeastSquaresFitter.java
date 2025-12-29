package cnuphys.splot.fit.apache;

import java.util.Objects;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.linear.RealMatrix;

/**
 * Base class for least-squares fitters built on Apache Commons Math 3.x
 * {@code fitting.leastsquares}.
 *
 * <p>This class centralizes:
 * <ul>
 *   <li>x/y validation</li>
 *   <li>weights-from-sigma helper</li>
 *   <li>safe covariance extraction</li>
 *   <li>uniform initial-guess plumbing via {@link IInitialGuess}</li>
 *   <li>standard {@link FitResult} diagnostics: cost, chi-square, dof, reduced chi-square, RMS</li>
 * </ul>
 *
 * <h3>Chi-square note (Commons Math 3.6.1)</h3>
 * {@code Optimum.getCost()} returns {@code sqrt(sum r_i^2)}. Therefore:
 * <pre>
 *   chiSquare = cost^2
 * </pre>
 * If you supply weights as {@code 1/sigmaY^2} in a diagonal weight matrix, this is the conventional
 * weighted chi-square.
 */
public abstract class AbstractLeastSquaresFitter {

    /** Optimizer used for the fit (often Levenberg-Marquardt). */
    protected final LeastSquaresOptimizer optimizer;

    /** Initial-guess strategy used when the caller does not provide an explicit guess. */
    protected final IInitialGuess initialGuesser;

    protected AbstractLeastSquaresFitter(LeastSquaresOptimizer optimizer, IInitialGuess initialGuesser) {
        this.optimizer = Objects.requireNonNull(optimizer, "optimizer");
        this.initialGuesser = Objects.requireNonNull(initialGuesser, "initialGuesser");
    }

    /**
     * Validate x/y arrays.
     *
     * @param x x values
     * @param y y values
     * @param minPoints minimum required number of points
     */
    protected static void validateXY(double[] x, double[] y, int minPoints) {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");

        if (x.length != y.length) {
            throw new IllegalArgumentException("x and y must have the same length");
        }
        if (x.length < minPoints) {
            throw new IllegalArgumentException("need at least " + minPoints + " points");
        }

        for (int i = 0; i < x.length; i++) {
            if (!Double.isFinite(x[i]) || !Double.isFinite(y[i])) {
                throw new IllegalArgumentException("x/y must be finite; bad value at index " + i);
            }
        }
    }

    /**
     * Convenience: build weights from y-uncertainties.
     * <p>{@code weights[i] = 1/(sigmaY[i]^2)}.
     *
     * @param sigmaY y uncertainties, all > 0
     * @return weights array
     */
    public static double[] weightsFromSigmaY(double[] sigmaY) {
        Objects.requireNonNull(sigmaY, "sigmaY");
        double[] w = new double[sigmaY.length];
        for (int i = 0; i < sigmaY.length; i++) {
            double s = sigmaY[i];
            if (!(s > 0.0) || !Double.isFinite(s)) {
                throw new IllegalArgumentException("sigmaY must be finite and > 0 at index " + i);
            }
            w[i] = 1.0 / (s * s);
        }
        return w;
    }

    /**
     * Best-effort covariance extraction; returns null if unavailable (singular, ill-conditioned, etc.).
     */
    protected static RealMatrix safeCovariances(LeastSquaresOptimizer.Optimum opt, double threshold) {
        try {
            return opt.getCovariances(threshold);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create a {@link FitResult} from an Apache least-squares optimum.
     *
     * @param modelName label for FitResult
     * @param params fitted parameters
     * @param covariance covariance matrix or null
     * @param nPoints number of data points
     * @param nParams number of fitted parameters
     * @param opt optimizer optimum
     * @return FitResult
     */
    protected static FitResult buildFitResult(String modelName,
                                              double[] params,
                                              RealMatrix covariance,
                                              int nPoints,
                                              int nParams,
                                              LeastSquaresOptimizer.Optimum opt) {

        final double cost = opt.getCost();
        final double chiSq = cost * cost;

        final int dof = Math.max(1, nPoints - nParams);
        final double chiSqReduced = chiSq / dof;

        final double rms = opt.getRMS();

        return new FitResult(
                modelName,
                params,
                covariance,
                cost,
                chiSq,
                dof,
                chiSqReduced,
                rms,
                opt.getIterations(),
                opt.getEvaluations()
        );
    }

    /**
     * Uniform initial-guess selection:
     * <ul>
     *   <li>If {@code explicitGuess != null}, returns a defensive copy.</li>
     *   <li>Otherwise delegates to {@link #initialGuesser}.</li>
     * </ul>
     */
    protected double[] selectInitialGuess(double[] x, double[] y, double[] weights, double[] explicitGuess) {
        if (explicitGuess != null) {
            return explicitGuess.clone();
        }
        return initialGuesser.guess(x, y, weights);
    }
}
