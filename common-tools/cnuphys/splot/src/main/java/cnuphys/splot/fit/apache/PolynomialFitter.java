package cnuphys.splot.fit.apache;

import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.fitting.leastsquares.ParameterValidator;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Pair;

import cnuphys.splot.fit.IValueGetter;

/**
 * Polynomial least-squares fitter using Apache Commons Math 3.x least-squares API.
 *
 * <p>Model:
 * <pre>
 *   y(x) = c0 + c1 x + c2 x^2 + ... + cN x^N
 * </pre>
 *
 * <p>Returned parameter ordering:
 * <pre>
 *   params[k] = c_k
 * </pre>
 *
 * <h3>Initial guess</h3>
 * If the caller does not supply an initial guess, the fitter computes a weighted
 * linear least-squares solution (via {@link LinearLeastSquaresGuesser}) which is
 * an excellent starting point (often already the solution) for polynomials.
 */
public final class PolynomialFitter extends AbstractLeastSquaresFitter implements IFitter {

    /** Polynomial degree N (number of parameters is N+1). */
    private final int degree;

    /** Convenience: fit a polynomial of a given degree with unit weights. */
    public static FitResult fit(int degree, double[] x, double[] y) {
        return new PolynomialFitter(degree).fit(x, y);
    }

    /** Convenience: fit a polynomial of a given degree with supplied weights. */
    public static FitResult fit(int degree, double[] x, double[] y, double[] weights) {
        return new PolynomialFitter(degree).fit(x, y, weights);
    }

    /**
     * Create a polynomial fitter for a given degree using Levenberg-Marquardt and a robust default guess.
     *
     * @param degree polynomial degree (>= 0)
     */
    public PolynomialFitter(int degree) {
        this(degree, new LevenbergMarquardtOptimizer());
    }

    /**
     * Create a polynomial fitter for a given degree with a custom optimizer.
     *
     * @param degree polynomial degree (>= 0)
     * @param optimizer least-squares optimizer
     */
    public PolynomialFitter(int degree, LeastSquaresOptimizer optimizer) {
        // For polynomials, we prefer the weighted linear LS guess over generic heuristics.
        // Still pass a non-null initialGuesser to satisfy base class invariants; it is used only
        // if defaultInitialGuess(...) is overridden in the future or returns null (it does not).
        super(optimizer, (x, y, w) -> defaultLinearLsGuess(degree, x, y, w));

        if (degree < 0) {
            throw new IllegalArgumentException("degree must be >= 0");
        }
        this.degree = degree;
    }

    /** @return polynomial degree. */
    public int getDegree() {
        return degree;
    }

    @Override
    protected int getParameterCount() {
        return degree + 1;
    }

    @Override
    protected String getModelName() {
        return "POLY_" + degree;
    }

    @Override
    protected MultivariateJacobianFunction model(double[] x) {
        return new PolynomialModel(x, degree);
    }

    @Override
    protected double[] defaultInitialGuess(double[] x, double[] y, double[] weights) {
        return defaultLinearLsGuess(degree, x, y, weights);
    }

    /**
     * Fit with optional weights, bounds, and explicit initial guess.
     *
     * <p>This is an "expert" overload that adapts polynomial coefficient bounds to the base class
     * {@link ParameterValidator} mechanism.
     *
     * @param x x data
     * @param y y data
     * @param weights optional weights (length n). Typically weights = 1/sigmaY^2.
     * @param bounds optional coefficient bounds; if null, unbounded
     * @param initialGuess optional coefficients length degree+1; if null, uses default guess
     * @return FitResult
     */
    public FitResult fit(double[] x,
                         double[] y,
                         double[] weights,
                         ParameterBounds bounds,
                         double[] initialGuess) {

        final int p = getParameterCount();

        final ParameterValidator v;
        if (bounds == null) {
            v = null; // unbounded
        } else {
            if (bounds.size() != p) {
                throw new IllegalArgumentException("bounds parameter count mismatch: expected " + p);
            }
            v = clampingValidator(bounds.lower(), bounds.upper());
        }

        return super.fit(x, y, weights, initialGuess, v);
    }

    /**
     * Create an {@link IValueGetter} that evaluates this polynomial using Horner's method.
     *
     * @param fit fit result produced by this fitter
     * @return value getter
     */
    @Override
    public IValueGetter asValueGetter(final FitResult fit) {
        if (fit == null || fit.params == null) {
            throw new IllegalArgumentException("FitResult is null");
        }
        if (fit.params.length != getParameterCount()) {
            throw new IllegalArgumentException(
                "PolynomialFitter: expected " + getParameterCount() +
                " parameters, got " + fit.params.length
            );
        }
        final double[] c = fit.params.clone();
        return (double x) -> {
            double y = 0.0;
            for (int k = c.length - 1; k >= 0; k--) {
                y = y * x + c[k];
            }
            return y;
        };
    }

 
    /**
     * Build a weighted (or unweighted) linear least-squares initial guess for polynomial coefficients.
     *
     * @param degree polynomial degree
     * @param x x data
     * @param y y data
     * @param weights optional weights (may be null)
     * @return coefficient vector length degree+1
     */
    private static double[] defaultLinearLsGuess(final int degree, double[] x, double[] y, double[] weights) {
        final int n = x.length;
        final int p = degree + 1;

        final double[][] A = new double[n][p];
        for (int i = 0; i < n; i++) {
            double pow = 1.0;
            for (int k = 0; k < p; k++) {
                A[i][k] = pow;
                pow *= x[i];
            }
        }
        return LinearLeastSquaresGuesser.solve(A, y, weights);
    }

    /**
     * Least-squares model for polynomial with analytic Jacobian.
     *
     * <p>Value:
     * <pre>
     *   y_i = sum_{k=0..deg} c_k x_i^k
     * </pre>
     * Jacobian row i:
     * <pre>
     *   [ 1, x, x^2, ..., x^deg ]
     * </pre>
     */
    private static final class PolynomialModel implements MultivariateJacobianFunction {
        private final double[] x;
        private final int degree;

        PolynomialModel(double[] x, int degree) {
            this.x = x.clone();
            this.degree = degree;
        }

        @Override
        public Pair<RealVector, RealMatrix> value(final RealVector point) {
            final double[] c = point.toArray();
            final int n = x.length;
            final int p = degree + 1;

            final double[] values = new double[n];
            final double[][] jac = new double[n][p];

            for (int i = 0; i < n; i++) {
                final double xi = x[i];

                double pow = 1.0; // xi^0
                double yi = 0.0;

                for (int k = 0; k < p; k++) {
                    jac[i][k] = pow;      // dy/dc_k = x^k
                    yi += c[k] * pow;
                    pow *= xi;
                }

                values[i] = yi;
            }

            return new Pair<>(
                    new ArrayRealVector(values, false),
                    new Array2DRowRealMatrix(jac, false)
            );
        }
    }

    /**
     * Coefficient bounds for c0..cN. Use +/-infinity for "no bound".
     *
     * <p>This is retained as a polynomial-friendly API, but the actual enforcement is handled
     * through the base class {@link ParameterValidator} mechanism (via clamping).
     */
    public static final class ParameterBounds {
        private final double[] lower;
        private final double[] upper;

        private ParameterBounds(double[] lower, double[] upper) {
            this.lower = lower;
            this.upper = upper;
        }

        /** @return number of parameters (degree+1). */
        public int size() {
            return lower.length;
        }

        /** @return defensive copy of lower bounds. */
        public double[] lower() {
            return lower.clone();
        }

        /** @return defensive copy of upper bounds. */
        public double[] upper() {
            return upper.clone();
        }

        /** Create unbounded bounds for nParams parameters. */
        public static ParameterBounds unbounded(int nParams) {
            double[] lo = new double[nParams];
            double[] hi = new double[nParams];
            for (int i = 0; i < nParams; i++) {
                lo[i] = Double.NEGATIVE_INFINITY;
                hi[i] = Double.POSITIVE_INFINITY;
            }
            return new ParameterBounds(lo, hi);
        }

        /** Builder for coefficient bounds for a given degree. */
        public static Builder builder(int degree) {
            return new Builder(degree);
        }

        /** Builder for per-coefficient bounds. */
        public static final class Builder {
            private final double[] lo;
            private final double[] hi;

            Builder(int degree) {
                int p = degree + 1;
                lo = new double[p];
                hi = new double[p];
                for (int i = 0; i < p; i++) {
                    lo[i] = Double.NEGATIVE_INFINITY;
                    hi[i] = Double.POSITIVE_INFINITY;
                }
            }

            /**
             * Set bounds on coefficient c_k.
             *
             * @param k coefficient index
             * @param lower lower bound (use -infinity for none)
             * @param upper upper bound (use +infinity for none)
             * @return this builder
             */
            public Builder coeff(int k, double lower, double upper) {
                if (k < 0 || k >= lo.length) {
                    throw new IllegalArgumentException("coefficient index out of range: " + k);
                }
                lo[k] = lower;
                hi[k] = upper;
                return this;
            }

            /** @return bounds instance. */
            public ParameterBounds build() {
                return new ParameterBounds(lo.clone(), hi.clone());
            }
        }
    }
}
