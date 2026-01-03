package cnuphys.splot.fit;

import java.util.Objects;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.fitting.leastsquares.ParameterValidator;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.optim.SimpleVectorValueChecker;

import cnuphys.splot.plot.UnicodeSupport;

/**
 * Base class for least-squares fitters built on Apache Commons Math 3.x
 * {@code fitting.leastsquares}.
 *
 * <p>This class centralizes the common fit pipeline:
 * <ul>
 *   <li>x/y/weights validation</li>
 *   <li>weights-from-sigma helper</li>
 *   <li>least-squares problem builder wiring</li>
 *   <li>optimizer execution</li>
 *   <li>best-effort covariance extraction</li>
 *   <li>uniform {@link FitResult} creation</li>
 * </ul>
 *
 * <p>Concrete fitters typically only implement:
 * <ul>
 *   <li>{@link #getParameterCount()}</li>
 *   <li>{@link #model(double[])}</li>
 *   <li>optionally {@link #defaultInitialGuess(double[], double[], double[])}</li>
 *   <li>optionally {@link #defaultValidator()}</li>
 *   <li>optionally {@link #getModelName()}</li>
 * </ul>
 *
 * <h3>Chi-square note (Commons Math 3.6.1)</h3>
 * {@code Optimum.getCost()} returns {@code sqrt(sum r_i^2)}. Therefore:
 * <pre>
 *   chiSquare = cost^2
 * </pre>
 * If you supply weights as {@code 1/sigmaY^2} in a diagonal weight matrix, this corresponds
 * to the conventional weighted chi-square.
 */
public abstract class ALeastSquaresFitter implements IFitter, IFitStringGetter {
	
	//helpers from unicode
	public static final String SUB0 = UnicodeSupport.SUB0;
	public static final String SUB1 = UnicodeSupport.SUB1;
	public static final String SUB2 = UnicodeSupport.SUB2;
	public static final String SUBN = UnicodeSupport.SUBN;
	public static final String SUP0 = UnicodeSupport.SUPER0;
	public static final String SUP1 = UnicodeSupport.SUPER1;
	public static final String SUP2 = UnicodeSupport.SUPER2;
	public static final String SUPN = UnicodeSupport.SUPERN;
	public static final String MU = UnicodeSupport.SMALL_MU;
	public static final String SMALLSIG = UnicodeSupport.SMALL_SIGMA;
	public static final String CAPSIG = UnicodeSupport.CAPITAL_SIGMA;
	public static final String OMEGA = UnicodeSupport.SMALL_OMEGA;
	public static final String PHI = UnicodeSupport.SMALL_PHI;
	
	public String[] subArray = {SUB0, SUB1, SUB2, UnicodeSupport.SUB3,
			                   UnicodeSupport.SUB4, UnicodeSupport.SUB5,
			                   UnicodeSupport.SUB6, UnicodeSupport.SUB7,
			                   UnicodeSupport.SUB8, UnicodeSupport.SUB9};
	
	

    /** Optimizer used for the fit (often Levenberg-Marquardt). */
    protected final LeastSquaresOptimizer optimizer;

    /** Initial-guess strategy used when the caller does not provide an explicit guess. */
    protected final IInitialGuess initialGuesser;

    /**
     * Maximum iterations used by the least-squares problem (Commons Math builder).
     * Subclasses may override via {@link #getMaxIterations()}.
     */
    protected final int maxIterations;

    /**
     * Maximum evaluations used by the least-squares problem (Commons Math builder).
     * Subclasses may override via {@link #getMaxEvaluations()}.
     */
    protected final int maxEvaluations;

    /**
     * Absolute/relative thresholds used by the default convergence checker.
     * Subclasses may override via {@link #getChecker()}.
     */
    protected final SimpleVectorValueChecker checker;

    /**
     * Covariance extraction threshold passed to {@code Optimum.getCovariances(threshold)}.
     * Subclasses may override via {@link #getCovarianceThreshold()}.
     */
    protected final double covarianceThreshold;

    /**
     * Construct a fitter with default iteration/evaluation/checker settings.
     *
     * @param optimizer optimizer instance (e.g. Levenberg-Marquardt)
     * @param initialGuesser fallback initial-guess strategy (non-null)
     */
    protected ALeastSquaresFitter(LeastSquaresOptimizer optimizer, IInitialGuess initialGuesser) {
        this(optimizer, initialGuesser,
                2000,
                2000,
                new SimpleVectorValueChecker(1e-12, 1e-12),
                1e-14);
    }

    /**
     * Construct a fitter with explicit common configuration.
     *
     * @param optimizer optimizer instance
     * @param initialGuesser fallback initial guess strategy
     * @param maxIterations maximum iterations for the least-squares builder
     * @param maxEvaluations maximum evaluations for the least-squares builder
     * @param checker convergence checker (may be null for none)
     * @param covarianceThreshold threshold passed to getCovariances
     */
    protected ALeastSquaresFitter(LeastSquaresOptimizer optimizer,
                                        IInitialGuess initialGuesser,
                                        int maxIterations,
                                        int maxEvaluations,
                                        SimpleVectorValueChecker checker,
                                        double covarianceThreshold) {
        this.optimizer = Objects.requireNonNull(optimizer, "optimizer");
        this.initialGuesser = Objects.requireNonNull(initialGuesser, "initialGuesser");
        this.maxIterations = maxIterations;
        this.maxEvaluations = maxEvaluations;
        this.checker = checker;
        this.covarianceThreshold = covarianceThreshold;
    }

    /** @return number of parameters for this fitter. */
    protected abstract int getParameterCount();

    /**
     * Build the model + analytic (or numeric) Jacobian for this fitter, given x values.
     *
     * @param x x data (length n)
     * @return a {@link MultivariateJacobianFunction} compatible with Commons Math least-squares
     */
    protected abstract MultivariateJacobianFunction model(double[] x);

    /**
     * Default initial guess used if the caller does not provide an explicit override.
     * The default implementation delegates to the {@link #initialGuesser}.
     *
     * <p>Subclasses may override if they have a better model-specific guess.
     *
     * @param x x data
     * @param y y data
     * @param weights optional weights (may be null)
     * @return initial parameter vector of length {@link #getParameterCount()}
     */
    protected double[] defaultInitialGuess(double[] x, double[] y, double[] weights) {
        return initialGuesser.guess(x, y, weights);
    }

    /**
     * Default validator/bounds policy (optional).
     *
     * <p>Return null for "no validation". For simple bound constraints, consider using
     * {@link #clampingValidator(double[], double[])}.
     *
     * @return a parameter validator or null
     */
    protected ParameterValidator defaultValidator() {
        return null;
    }
    /** @return max iterations used for the least-squares problem. */
    protected int getMaxIterations() {
        return maxIterations;
    }

    /** @return max evaluations used for the least-squares problem. */
    protected int getMaxEvaluations() {
        return maxEvaluations;
    }

    /** @return the convergence checker (may be null). */
    protected SimpleVectorValueChecker getChecker() {
        return checker;
    }

    /** @return the covariance extraction threshold. */
    protected double getCovarianceThreshold() {
        return covarianceThreshold;
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
     * Validate weights array.
     *
     * <p>Commons Math accepts any real diagonal matrix as weights, but for typical least-squares usage,
     * weights should be finite and non-negative (often {@code 1/sigma^2}).
     *
     * @param weights optional weights array
     * @param n expected length
     */
    protected static void validateWeights(double[] weights, int n) {
        if (weights == null) {
            return;
        }
        if (weights.length != n) {
            throw new IllegalArgumentException("weights length must match x/y length");
        }
        for (int i = 0; i < n; i++) {
            double w = weights[i];
            if (!Double.isFinite(w) || w < 0.0) {
                throw new IllegalArgumentException("weights must be finite and >= 0; bad value at index " + i);
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
     * Create a {@link ParameterValidator} that clamps parameters to {@code [lower[i], upper[i]]}.
     * Use +/-infinity for "no bound".
     *
     * <p>This is a simple, robust bounds strategy compatible with Commons Math least-squares.
     * It is not a hard-constraint optimizer; it repairs invalid steps by clamping.
     *
     * @param lower lower bounds (length p)
     * @param upper upper bounds (length p)
     * @return clamping validator
     */
    protected static ParameterValidator clampingValidator(final double[] lower, final double[] upper) {
        Objects.requireNonNull(lower, "lower");
        Objects.requireNonNull(upper, "upper");
        if (lower.length != upper.length) {
            throw new IllegalArgumentException("lower/upper must have same length");
        }

        return new ParameterValidator() {
            @Override
            public RealVector validate(RealVector params) {
                final double[] p = params.toArray();
                final int n = Math.min(p.length, lower.length);

                for (int i = 0; i < n; i++) {
                    double lo = lower[i];
                    double hi = upper[i];

                    if (Double.isFinite(lo) && p[i] < lo) {
                        p[i] = lo;
                    }
                    if (Double.isFinite(hi) && p[i] > hi) {
                        p[i] = hi;
                    }
                }
                return new ArrayRealVector(p, false);
            }
        };
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
	 * Create an evaluator for the fit result.
	 *
	 * @param fit the fit result
	 * @return an evaluator
	 */
    
    public abstract Evaluator asEvaluator(final FitResult fit);

    /**
     * Create a {@link FitResult} from an Apache least-squares optimum.
     *
     * @param modelName label for FitResult
     * @param modelFunction model function as a string
     * @param params fitted parameters
     * @param covariance covariance matrix or null
     * @param nPoints number of data points
     * @param nParams number of fitted parameters
     * @param opt optimizer optimum
     * @return FitResult
     */
    protected static FitResult buildFitResult(IFitStringGetter stringGetter,
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

		FitResult fr = new FitResult(stringGetter, params, covariance, cost, chiSq, dof, chiSqReduced, rms,
				opt.getIterations(), opt.getEvaluations());
		return fr;
	}
   
    /**
     * Get the descriptive string getter for this fitter.
     * @return the string getter
     */
    public abstract IFitStringGetter getStringGetter();
    
 
    /**
     * Fit with no weights, no initial guess override, and no parameter validator override.
     *
     * @param x the x data
     * @param y the y data
     * @return the fit result
     */
    @Override
    public final FitResult fit(double[] x, double[] y) {
        return fit(x, y, null, null, null);
    }

    /**
     * Fit with weights but no initial guess override and no parameter validator override.
     *
     * @param x the x data
     * @param y the y data
     * @param weights the weights where w = 1/(sigmaY^2)
     * @return the fit result
     */
    @Override
    public final FitResult fit(double[] x, double[] y, double[] weights) {
        return fit(x, y, weights, null, null);
    }

    /**
     * Full fit entry point. This is the common least-squares pipeline for all derived fitters.
     *
     * @param x x data
     * @param y y data
     * @param weights optional weights (length n), typically {@code 1/sigmaY^2}; may be null
     * @param initialGuessOverride optional explicit initial guess (length p); may be null
     * @param validatorOverride optional explicit parameter validator; may be null
     * @return FitResult
     */
    public final FitResult fit(double[] x, double[] y,
                               double[] weights,
                               double[] initialGuessOverride,
                               ParameterValidator validatorOverride) {

        // Basic validation.
        validateXY(x, y, 2);
        final int n = x.length;

        validateWeights(weights, n);

        final int p = getParameterCount();
        if (p <= 0) {
            throw new IllegalStateException("parameter count must be > 0");
        }

        // Initial guess.
        final double[] start;
        if (initialGuessOverride != null) {
            if (initialGuessOverride.length != p) {
                throw new IllegalArgumentException("initialGuessOverride must have length " + p);
            }
            start = initialGuessOverride.clone();
        } else {
            double[] guess = defaultInitialGuess(x, y, weights);
            if (guess == null) {
                throw new IllegalStateException("defaultInitialGuess returned null");
            }
            if (guess.length != p) {
                throw new IllegalArgumentException("defaultInitialGuess must return length " + p);
            }
            start = guess;
        }

        // Model + validator.
        final MultivariateJacobianFunction m = Objects.requireNonNull(model(x), "model(x) returned null");
        final ParameterValidator v = (validatorOverride != null) ? validatorOverride : defaultValidator();

        // Build the least-squares problem.
        final LeastSquaresBuilder b = new LeastSquaresBuilder()
                .start(start)
                .model(m)
                .target(y)
                .maxIterations(getMaxIterations())
                .maxEvaluations(getMaxEvaluations());

        final SimpleVectorValueChecker c = getChecker();
        if (c != null) {
            b.checkerPair(c);
        }

        if (v != null) {
            b.parameterValidator(v);
        }

        if (weights != null) {
            b.weight(new DiagonalMatrix(weights));
        }

        final LeastSquaresProblem problem = b.build();

        // Optimize.
        final LeastSquaresOptimizer.Optimum opt = optimizer.optimize(problem);

        // Package results.
        final double[] params = opt.getPoint().toArray();
        final RealMatrix cov = safeCovariances(opt, getCovarianceThreshold());

        FitResult fr = buildFitResult(getStringGetter(), params, cov, n, p, opt);
        
        // set the evaluator so can treat the fit as a function useful for plotting
        fr.setEvaluator(asEvaluator(fr));
        return fr;
    }
}
