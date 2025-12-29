package cnuphys.splot.fit.apache;

import java.util.Arrays;
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
import org.apache.commons.math3.special.Erf;
import org.apache.commons.math3.util.Pair;

import cnuphys.splot.fit.IPlottableFunction;
import cnuphys.splot.fit.IValueGetter;
import cnuphys.splot.fit.PlottableFunction;

/**
 * Nonlinear least-squares fitter for scaled/shifted {@code erf} or {@code erfc}:
 *
 * <pre>
 *   y(x) = A * erf((x - x0)/sigma)  + B
 *   y(x) = A * erfc((x - x0)/sigma) + B
 * </pre>
 */
public final class ErfErfcFitter extends AbstractLeastSquaresFitter {

    /** Which function to fit: erf or erfc. */
    public enum Kind { ERF, ERFC }

    /** Parameter indices (for readability). */
    public static final int IDX_A = 0;
    public static final int IDX_X0 = 1;
    public static final int IDX_SIGMA = 2;
    public static final int IDX_B = 3;

    /** Default minimum allowed sigma to avoid division by zero. */
    public static final double DEFAULT_MIN_SIGMA = 1e-12;

    /** Which curve this fitter fits. */
    private final Kind kind;

    public ErfErfcFitter(Kind kind) {
        this(kind, new LevenbergMarquardtOptimizer(), defaultGuesser(kind));
    }

    public ErfErfcFitter(Kind kind, LeastSquaresOptimizer optimizer) {
        this(kind, optimizer, defaultGuesser(kind));
    }

    public ErfErfcFitter(Kind kind, LeastSquaresOptimizer optimizer, IInitialGuess guesser) {
        super(optimizer, guesser);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    private static IInitialGuess defaultGuesser(final Kind kind) {
        return (x, y, weights) -> InitialGuess.guess(kind, x, y);
    }

    public FitResult fit(double[] x, double[] y) {
        return fit(x, y, null, null, null);
    }

    public FitResult fit(double[] x,
                         double[] y,
                         double[] weights,
                         ParameterBounds bounds,
                         double[] initialGuess) {

        validateXY(x, y, 4);
        final int n = x.length;

        if (weights != null && weights.length != n) {
            throw new IllegalArgumentException("weights length must match x/y length");
        }
        if (initialGuess != null && initialGuess.length != 4) {
            throw new IllegalArgumentException("initialGuess must have length 4: [A, x0, sigma, B]");
        }

        final double[] start = selectInitialGuess(x, y, weights, initialGuess);

        final ParameterValidator validator = new BoundValidator(
                bounds != null ? bounds : ParameterBounds.unbounded(),
                DEFAULT_MIN_SIGMA
        );

        final MultivariateJacobianFunction model = new ErfErfcModel(kind, x);

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

        final double[] p = opt.getPoint().toArray();
        final RealMatrix cov = safeCovariances(opt, 1e-14);

        return buildFitResult(kind.name(), p, cov, n, 4, opt);
    }

    public static double[] weightsFromSigmaY(double[] sigmaY) {
        return AbstractLeastSquaresFitter.weightsFromSigmaY(sigmaY);
    }

    /**
     * Create an {@link IValueGetter} that evaluates the fitted erf/erfc curve.
     * The returned function clamps sigma to {@link #DEFAULT_MIN_SIGMA}.
     */
    public IValueGetter asValueGetter(final FitResult fit) {
        Objects.requireNonNull(fit, "fit");

        final double A  = fit.params[IDX_A];
        final double x0 = fit.params[IDX_X0];
        final double s0 = fit.params[IDX_SIGMA];
        final double B  = fit.params[IDX_B];

        final double sigma = Math.max(Math.abs(s0), DEFAULT_MIN_SIGMA);
        final Kind k = this.kind;

        return (double x) -> {
            double z = (x - x0) / sigma;
            double f = (k == Kind.ERF) ? Erf.erf(z) : Erf.erfc(z);
            return A * f + B;
        };
    }

    /** Convenience: wrap the fitted erf/erfc as an {@link IPlottableFunction}. */
    public IPlottableFunction asPlottable(final FitResult fit, double xmin, double xmax) {
        return new PlottableFunction(asValueGetter(fit), xmin, xmax);
    }

    /** Least-squares model + analytic Jacobian. */
    private static final class ErfErfcModel implements MultivariateJacobianFunction {
        private final Kind kind;
        private final double[] x;

        ErfErfcModel(Kind kind, double[] x) {
            this.kind = kind;
            this.x = x.clone();
        }

        @Override
        public Pair<RealVector, RealMatrix> value(final RealVector point) {
            final double A = point.getEntry(IDX_A);
            final double x0 = point.getEntry(IDX_X0);
            final double sigma = point.getEntry(IDX_SIGMA);
            final double B = point.getEntry(IDX_B);

            final int n = x.length;
            final double[] values = new double[n];
            final double[][] jac = new double[n][4];

            final double s2 = sigma * sigma;
            final double norm = Math.sqrt(Math.PI);

            for (int i = 0; i < n; i++) {
                final double dx = x[i] - x0;
                final double z = dx / sigma;

                final double erf = Erf.erf(z);
                final double erfc = Erf.erfc(z);

                final double f = (kind == Kind.ERF) ? erf : erfc;
                values[i] = A * f + B;

                final double exp = Math.exp(-z * z);
                final double dfdz = (kind == Kind.ERF ? (2.0 / norm) : (-2.0 / norm)) * exp;

                jac[i][IDX_A] = f;
                jac[i][IDX_B] = 1.0;
                jac[i][IDX_X0] = A * dfdz * (-1.0 / sigma);
                jac[i][IDX_SIGMA] = A * dfdz * (-dx / s2);
            }

            return new Pair<>(new ArrayRealVector(values, false),
                              new Array2DRowRealMatrix(jac, false));
        }
    }

    /** Bounds for [A, x0, sigma, B]. */
    public static final class ParameterBounds {
        final double[] lower = new double[4];
        final double[] upper = new double[4];

        private ParameterBounds(double[] lower, double[] upper) {
            System.arraycopy(lower, 0, this.lower, 0, 4);
            System.arraycopy(upper, 0, this.upper, 0, 4);
        }

        public static ParameterBounds unbounded() {
            double[] lo = { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY };
            double[] hi = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
            return new ParameterBounds(lo, hi);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private final double[] lo = { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY };
            private final double[] hi = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };

            public Builder a(double lower, double upper) { lo[IDX_A] = lower; hi[IDX_A] = upper; return this; }
            public Builder x0(double lower, double upper) { lo[IDX_X0] = lower; hi[IDX_X0] = upper; return this; }
            public Builder sigma(double lower, double upper) { lo[IDX_SIGMA] = lower; hi[IDX_SIGMA] = upper; return this; }
            public Builder b(double lower, double upper) { lo[IDX_B] = lower; hi[IDX_B] = upper; return this; }

            public ParameterBounds build() {
                return new ParameterBounds(lo, hi);
            }
        }
    }

    /** Enforces bounds and sigma >= minSigma using clamping. */
    private static final class BoundValidator implements ParameterValidator {
        private final ParameterBounds bounds;
        private final double minSigma;

        BoundValidator(ParameterBounds bounds, double minSigma) {
            this.bounds = bounds;
            this.minSigma = minSigma;
        }

        @Override
        public RealVector validate(RealVector params) {
            double[] p = params.toArray();
            p[IDX_SIGMA] = Math.max(p[IDX_SIGMA], minSigma);

            for (int i = 0; i < 4; i++) {
                double lo = bounds.lower[i];
                double hi = bounds.upper[i];
                if (Double.isFinite(lo)) p[i] = Math.max(p[i], lo);
                if (Double.isFinite(hi)) p[i] = Math.min(p[i], hi);
            }
            return new ArrayRealVector(p, false);
        }
    }

    /** Heuristic initial guess for [A, x0, sigma, B]. */
    public static final class InitialGuess {
        private InitialGuess() {}

        public static double[] guess(Kind kind, double[] x, double[] y) {
            final int n = x.length;
            Integer[] idx = new Integer[n];
            for (int i = 0; i < n; i++) idx[i] = i;
            Arrays.sort(idx, (i, j) -> Double.compare(x[i], x[j]));

            double yLeft = y[idx[0]];
            double yRight = y[idx[n - 1]];

            double B = 0.5 * (yLeft + yRight);
            double A = 0.5 * (yRight - yLeft);
            if (kind == Kind.ERFC) {
                A = -A;
            }

            double x0 = x[idx[0]];
            double best = Double.POSITIVE_INFINITY;
            for (int k = 0; k < n; k++) {
                int i = idx[k];
                double d = Math.abs(y[i] - B);
                if (d < best) {
                    best = d;
                    x0 = x[i];
                }
            }

            double target = (kind == Kind.ERF)
                    ? (A * Erf.erf(1.0) + B)
                    : (A * Erf.erfc(1.0) + B);

            double xAtTarget = Double.NaN;
            best = Double.POSITIVE_INFINITY;
            for (int k = 0; k < n; k++) {
                int i = idx[k];
                double d = Math.abs(y[i] - target);
                if (d < best) {
                    best = d;
                    xAtTarget = x[i];
                }
            }

            double sigma;
            if (Double.isFinite(xAtTarget)) {
                sigma = Math.abs(xAtTarget - x0);
            } else {
                double span = x[idx[n - 1]] - x[idx[0]];
                sigma = Math.max(span / 6.0, DEFAULT_MIN_SIGMA);
            }

            if (!(sigma > 0.0) || !Double.isFinite(sigma)) {
                sigma = DEFAULT_MIN_SIGMA;
            }

            return new double[] { A, x0, sigma, B };
        }
    }
}
