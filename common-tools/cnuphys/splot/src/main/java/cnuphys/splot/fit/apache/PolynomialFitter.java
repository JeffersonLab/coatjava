package cnuphys.splot.fit.apache;

import java.util.Objects;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.fitting.leastsquares.ParameterValidator;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.optim.SimpleVectorValueChecker;
import org.apache.commons.math3.util.Pair;

import cnuphys.splot.fit.IPlottableFunction;
import cnuphys.splot.fit.IValueGetter;
import cnuphys.splot.fit.PlottableFunction;

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
 * <p>Initial guess:
 * If the caller does not supply an initial guess, the fitter computes a weighted
 * linear least-squares solution (via {@link LinearLeastSquaresGuesser}) which is
 * an excellent starting point (often already the solution) for polynomials.
 */
public final class PolynomialFitter extends AbstractLeastSquaresFitter {

    /** Polynomial degree N (number of parameters is N+1). */
    private final int degree;

    /** Convenience: fit a polynomial of a given degree with unit weights. */
    public static FitResult fit(int degree, double[] x, double[] y) {
        return new PolynomialFitter(degree).fit(x, y);
    }

    /** Convenience: fit a polynomial of a given degree with supplied weights. */
    public static FitResult fit(int degree, double[] x, double[] y, double[] weights) {
        return new PolynomialFitter(degree).fit(x, y, weights, null, null);
    }

    /** Create a polynomial fitter for a given degree using Levenberg-Marquardt and default initial guess. */
    public PolynomialFitter(int degree) {
        this(degree, new LevenbergMarquardtOptimizer(), defaultGuesser(degree));
    }

    /** Create with custom optimizer and default initial guess. */
    public PolynomialFitter(int degree, LeastSquaresOptimizer optimizer) {
        this(degree, optimizer, defaultGuesser(degree));
    }

    /** Create with custom optimizer and custom initial guess strategy. */
    public PolynomialFitter(int degree, LeastSquaresOptimizer optimizer, IInitialGuess guesser) {
        super(optimizer, guesser);
        if (degree < 0) {
            throw new IllegalArgumentException("degree must be >= 0");
        }
        this.degree = degree;
    }

    private static IInitialGuess defaultGuesser(final int degree) {
        return (x, y, weights) -> {
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
        };
    }

    /** @return polynomial degree. */
    public int getDegree() {
        return degree;
    }

    /** Fit with unit weights. */
    public FitResult fit(double[] x, double[] y) {
        return fit(x, y, null, null, null);
    }

    /**
     * Fit with optional weights, bounds, and explicit initial guess.
     *
     * @param x x data
     * @param y y data
     * @param weights optional weights (length n). Typically weights = 1/sigmaY^2.
     * @param bounds optional coefficient bounds; if null, unbounded
     * @param initialGuess optional coefficients length degree+1; if null, uses initialGuesser
     */
    public FitResult fit(double[] x,
                         double[] y,
                         double[] weights,
                         ParameterBounds bounds,
                         double[] initialGuess) {

        validateXY(x, y, 2);
        final int n = x.length;

        if (weights != null && weights.length != n) {
            throw new IllegalArgumentException("weights length must match x/y length");
        }

        final int p = degree + 1;
        if (initialGuess != null && initialGuess.length != p) {
            throw new IllegalArgumentException("initialGuess must have length " + p + " (degree+1)");
        }

        final double[] start = selectInitialGuess(x, y, weights, initialGuess);

        final ParameterValidator validator = new BoundValidator(bounds != null ? bounds : ParameterBounds.unbounded(p));
        final MultivariateJacobianFunction model = new PolynomialModel(x, degree);

        final LeastSquaresBuilder b = new LeastSquaresBuilder()
                .start(start)
                .model(model)
                .target(y)
                .parameterValidator(validator)
                .maxIterations(2000)
                .maxEvaluations(2000)
                .checkerPair(new SimpleVectorValueChecker(1e-12, 1e-12));

        if (weights != null) {
            b.weight(new DiagonalMatrix(weights));
        }

        LeastSquaresProblem problem = b.build();
        LeastSquaresOptimizer.Optimum opt = optimizer.optimize(problem);

        final double[] coeff = opt.getPoint().toArray();
        final RealMatrix cov = safeCovariances(opt, 1e-14);

        return buildFitResult("POLY_" + degree, coeff, cov, n, p, opt);
    }

    /** weights[i] = 1/(sigmaY[i]^2). */
    public static double[] weightsFromSigmaY(double[] sigmaY) {
        return AbstractLeastSquaresFitter.weightsFromSigmaY(sigmaY);
    }

    /**
     * Create an {@link IValueGetter} that evaluates this polynomial using Horner's method.
     */
    public IValueGetter asValueGetter(final FitResult fit) {
        Objects.requireNonNull(fit, "fit");
        final double[] c = fit.params.clone();
        return (double x) -> {
            double y = 0.0;
            for (int k = c.length - 1; k >= 0; k--) {
                y = y * x + c[k];
            }
            return y;
        };
    }

    /** Convenience: wrap the fitted polynomial as an {@link IPlottableFunction}. */
    public IPlottableFunction asPlottable(final FitResult fit, double xmin, double xmax) {
        return new PlottableFunction(asValueGetter(fit), xmin, xmax);
    }

    /** Convenience: plot over the data range. */
    public IPlottableFunction asPlottableOverDataRange(final FitResult fit, double[] x) {
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
        return asPlottable(fit, xmin, xmax);
    }

    /**
     * Least-squares model for polynomial with analytic Jacobian.
     * Value: y_i = sum_{k=0..deg} c_k x_i^k
     * Jacobian row i: [1, x, x^2, ..., x^deg]
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
     */
    public static final class ParameterBounds {
        final double[] lower;
        final double[] upper;

        private ParameterBounds(double[] lower, double[] upper) {
            this.lower = lower;
            this.upper = upper;
        }

        public static ParameterBounds unbounded(int nParams) {
            double[] lo = new double[nParams];
            double[] hi = new double[nParams];
            for (int i = 0; i < nParams; i++) {
                lo[i] = Double.NEGATIVE_INFINITY;
                hi[i] = Double.POSITIVE_INFINITY;
            }
            return new ParameterBounds(lo, hi);
        }

        public static Builder builder(int degree) {
            return new Builder(degree);
        }

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

            /** Set bounds on coefficient c_k. */
            public Builder coeff(int k, double lower, double upper) {
                if (k < 0 || k >= lo.length) {
                    throw new IllegalArgumentException("coefficient index out of range: " + k);
                }
                lo[k] = lower;
                hi[k] = upper;
                return this;
            }

            public ParameterBounds build() {
                return new ParameterBounds(lo, hi);
            }
        }
    }

    /** Enforces coefficient bounds using clamping. */
    private static final class BoundValidator implements ParameterValidator {
        private final ParameterBounds bounds;

        BoundValidator(ParameterBounds bounds) {
            this.bounds = bounds;
        }

        @Override
        public RealVector validate(RealVector params) {
            double[] p = params.toArray();
            int n = Math.min(p.length, Math.min(bounds.lower.length, bounds.upper.length));
            for (int i = 0; i < n; i++) {
                double lo = bounds.lower[i];
                double hi = bounds.upper[i];
                if (Double.isFinite(lo)) p[i] = Math.max(p[i], lo);
                if (Double.isFinite(hi)) p[i] = Math.min(p[i], hi);
            }
            return new ArrayRealVector(p, false);
        }
    }
}
