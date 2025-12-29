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
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.optim.SimpleVectorValueChecker;
import org.apache.commons.math3.util.Pair;

import cnuphys.splot.fit.IPlottableFunction;
import cnuphys.splot.fit.IValueGetter;
import cnuphys.splot.fit.PlottableFunction;

/**
 * Nonlinear least-squares polynomial fitter using the Commons Math least-squares API.
 * <p>
 * Model:
 * <pre>
 *   y(x) = c0 + c1 x + c2 x^2 + ... + cN x^N
 * </pre>
 *
 * Coefficients are returned as:
 * <pre>
 *   params[k] = c_k
 * </pre>
 *
 * Notes:
 * <ul>
 *   <li>This is a linear-in-parameters model, but using the same least-squares framework
 *       keeps the API consistent with other fitters and provides RMS/covariance diagnostics.</li>
 *   <li>Bounds are optional and implemented via clamping in a {@link ParameterValidator}.</li>
 * </ul>
 */
public final class PolynomialFitter {

    /** Polynomial degree N (number of parameters is N+1). */
    private final int degree;

    private final LeastSquaresOptimizer optimizer;

    /**
     * Create a polynomial fitter for a given degree using Levenberg-Marquardt.
     *
     * @param degree polynomial degree (>= 0)
     */
    public PolynomialFitter(int degree) {
        this(degree, new LevenbergMarquardtOptimizer());
    }

    public PolynomialFitter(int degree, LeastSquaresOptimizer optimizer) {
        if (degree < 0) {
            throw new IllegalArgumentException("degree must be >= 0");
        }
        this.degree = degree;
        this.optimizer = Objects.requireNonNull(optimizer, "optimizer");
    }

    /** @return the polynomial degree. */
    public int getDegree() {
        return degree;
    }

    /** Fit with heuristic initial guess (all zeros) and unit weights. */
    public FitResult fit(double[] x, double[] y) {
        return fit(x, y, null, null, null);
    }

    /**
     * Fit with optional weights, bounds, and initial guess.
     *
     * @param x x data (length n)
     * @param y y data (length n)
     * @param weights optional weights (length n). If null, unit weights are used.
     *                Typically weights = 1/sigmaY^2.
     * @param bounds optional coefficient bounds; if null, unbounded.
     * @param initialGuess optional coefficient guess length (degree+1). If null, uses zeros.
     * @return fit result
     */
    public FitResult fit(double[] x,
                         double[] y,
                         double[] weights,
                         ParameterBounds bounds,
                         double[] initialGuess) {

        validateXY(x, y);
        final int n = x.length;

        if (weights != null && weights.length != n) {
            throw new IllegalArgumentException("weights length must match x/y length");
        }

        final int p = degree + 1;
        if (initialGuess != null && initialGuess.length != p) {
            throw new IllegalArgumentException("initialGuess must have length " + p + " (degree+1)");
        }

        final double[] start = (initialGuess != null)
                ? initialGuess.clone()
                : linearLeastSquaresGuess(x, y, weights, degree);

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

        RealMatrix cov;
        try {
            cov = opt.getCovariances(1e-14);
        } catch (Exception e) {
            cov = null;
        }

        // Commons Math 3.6.1: chiSquare = cost^2
        final double cost = opt.getCost();
        final double chiSq = cost * cost;

        final int dof = Math.max(1, n - p);
        final double chiSqReduced = chiSq / dof;

        final double rms = opt.getRMS();

        return new FitResult(
                "POLY_" + degree,
                coeff,
                cov,
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
     * Convenience: fit a polynomial of a given degree with unit weights.
     */
    public static FitResult fit(int degree, double[] x, double[] y) {
        return new PolynomialFitter(degree).fit(x, y);
    }

    /**
     * Convenience: fit a polynomial of a given degree with supplied weights.
     * Weights are typically 1 / (sigmaY^2).
     */
    public static FitResult fit(int degree, double[] x, double[] y, double[] weights) {
        return new PolynomialFitter(degree).fit(x, y, weights, null, null);
    }


    /**
     * Convenience: build weights from y-uncertainties (sigmaY).
     * weights[i] = 1 / (sigmaY[i]^2).
     */
    public static double[] weightsFromSigmaY(double[] sigmaY) {
        Objects.requireNonNull(sigmaY, "sigmaY");
        double[] w = new double[sigmaY.length];
        for (int i = 0; i < sigmaY.length; i++) {
            double s = sigmaY[i];
            if (!(s > 0.0)) {
                throw new IllegalArgumentException("sigmaY must be > 0 at index " + i);
            }
            w[i] = 1.0 / (s * s);
        }
        return w;
    }

    /**
     * Create an {@link IValueGetter} that evaluates this polynomial using the coefficients in {@link FitResult#params}.
     * Uses Horner's method for numerical stability.
     *
     * @param fit fit result from this fitter (params are coefficients c0..cN)
     * @return value getter for plotting/evaluation
     */
    public IValueGetter asValueGetter(final FitResult fit) {
        Objects.requireNonNull(fit, "fit");
        final double[] c = fit.params.clone(); // protect against external mutation
        return (double x) -> {
            double y = 0.0;
            for (int k = c.length - 1; k >= 0; k--) {
                y = y * x + c[k];
            }
            return y;
        };
    }

    /**
     * Convenience: wrap the fitted polynomial as an {@link IPlottableFunction}.
     */
    public IPlottableFunction asPlottable(final FitResult fit, double xmin, double xmax) {
        return new PlottableFunction(asValueGetter(fit), xmin, xmax);
    }
    
    /**
	 * Convenience: wrap the fitted polynomial as an {@link IPlottableFunction}
	 * over the range of supplied x data.
	 */
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
        // If all x are equal, widen a little to avoid xmax==xmin
        if (!(xmax > xmin)) {
            double eps = (xmin == 0.0) ? 1.0 : Math.abs(xmin) * 1e-6;
            xmin -= eps;
            xmax += eps;
        }
        return asPlottable(fit, xmin, xmax);
    }


    private static void validateXY(double[] x, double[] y) {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        if (x.length != y.length) {
            throw new IllegalArgumentException("x and y must have the same length");
        }
        if (x.length < 2) {
            throw new IllegalArgumentException("need at least 2 points to fit a polynomial");
        }
        for (int i = 0; i < x.length; i++) {
            if (!Double.isFinite(x[i]) || !Double.isFinite(y[i])) {
                throw new IllegalArgumentException("x/y must be finite; bad value at index " + i);
            }
        }
    }
    
    
    /**
     * Compute an initial guess using a linear least-squares solve (QR).
     * If weights are provided, performs weighted least squares by premultiplying rows
     * by sqrt(weight_i).
     */
    private static double[] linearLeastSquaresGuess(double[] x, double[] y, double[] weights, int degree) {
        final int n = x.length;
        final int p = degree + 1;

        // Build (possibly weighted) design matrix A and rhs b.
        // A[i,k] = x_i^k (or sqrt(w_i)*x_i^k)
        // b[i]   = y_i   (or sqrt(w_i)*y_i)
        double[][] A = new double[n][p];
        double[] b = new double[n];

        for (int i = 0; i < n; i++) {
            double xi = x[i];
            double scale = 1.0;
            if (weights != null) {
                double w = weights[i];
                // weights should be >= 0; if user passes nonsense, treat as unweighted for that point
                if (Double.isFinite(w) && w > 0.0) {
                    scale = Math.sqrt(w);
                }
            }

            double pow = 1.0;
            for (int k = 0; k < p; k++) {
                A[i][k] = scale * pow;
                pow *= xi;
            }
            b[i] = scale * y[i];
        }

        try {
            DecompositionSolver solver = new QRDecomposition(new Array2DRowRealMatrix(A, false)).getSolver();
            RealVector c = solver.solve(new ArrayRealVector(b, false));
            return c.toArray();
        } catch (Exception e) {
            // Fallback if QR fails (e.g., singular/ill-conditioned): start at zeros.
            return new double[p];
        }
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

                // Build powers iteratively to fill Jacobian and compute value.
                double pow = 1.0; // xi^0
                double yi = 0.0;

                for (int k = 0; k < p; k++) {
                    jac[i][k] = pow;      // dy/dc_k = x^k
                    yi += c[k] * pow;     // contribution to y
                    pow *= xi;            // next power
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
