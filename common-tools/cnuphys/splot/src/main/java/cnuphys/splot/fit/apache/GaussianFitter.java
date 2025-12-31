package cnuphys.splot.fit.apache;

import java.util.Arrays;
import java.util.Objects;

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
 * Nonlinear least-squares fitter for a 4-parameter Gaussian with baseline:
 *
 * <pre>
 *   y(x) = A * exp(-(x - mu)^2 / (2*sigma^2)) + B
 * </pre>
 *
 * <h3>Parameter ordering</h3>
 * <ul>
 *   <li>{@code params[0] = A}</li>
 *   <li>{@code params[1] = mu}</li>
 *   <li>{@code params[2] = sigma}</li>
 *   <li>{@code params[3] = B}</li>
 * </ul>
 *
 * <h3>Bounds / validation</h3>
 * This fitter enforces {@code sigma >= DEFAULT_MIN_SIGMA} by default using a parameter
 * clamping validator. Optional bounds can also be supplied via {@link ParameterBounds}.
 */
public final class GaussianFitter extends AbstractLeastSquaresFitter {

    /** Parameter indices. */
    public static final int IDX_A = 0;
    public static final int IDX_MU = 1;
    public static final int IDX_SIGMA = 2;
    public static final int IDX_B = 3;

    /** Default minimum allowed sigma to avoid division by zero and ill-conditioned Jacobians. */
    public static final double DEFAULT_MIN_SIGMA = 1e-12;

    /** Create with a Levenberg-Marquardt optimizer and a robust default guesser. */
    public GaussianFitter() {
        this(new LevenbergMarquardtOptimizer());
    }

    /** Create with a custom optimizer and robust default guesser. */
    public GaussianFitter(LeastSquaresOptimizer optimizer) {
        super(Objects.requireNonNull(optimizer, "optimizer"), defaultGuesser());
    }

    private static IInitialGuess defaultGuesser() {
        return (x, y, w) -> InitialGuess.guess(x, y);
    }

    @Override
    protected int getParameterCount() {
        return 4;
    }

    @Override
    protected String getModelName() {
        return "GAUSSIAN";
    }

    @Override
    protected MultivariateJacobianFunction model(double[] x) {
        return new GaussianModel(x);
    }

    @Override
    protected double[] defaultInitialGuess(double[] x, double[] y, double[] weights) {
        // weights do not materially change a simple gaussian heuristic guess
        return InitialGuess.guess(x, y);
    }

    @Override
    protected ParameterValidator defaultValidator() {
        // Unbounded except sigma >= DEFAULT_MIN_SIGMA
        double[] lo = { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, DEFAULT_MIN_SIGMA, Double.NEGATIVE_INFINITY };
        double[] hi = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
        return clampingValidator(lo, hi);
    }

    /**
     * Expert overload: fit with optional weights, optional bounds, and optional initial guess.
     *
     * <p>If {@code bounds} is null, only {@code sigma >= DEFAULT_MIN_SIGMA} is enforced.</p>
     *
     * @param x x data
     * @param y y data
     * @param weights optional weights (typically 1/sigmaY^2), may be null
     * @param bounds optional bounds, may be null
     * @param initialGuess optional initial guess, may be null
     * @return fit result
     */
    public FitResult fit(double[] x, double[] y,
                         double[] weights,
                         ParameterBounds bounds,
                         double[] initialGuess) {

        final double[] lo;
        final double[] hi;

        if (bounds == null) {
            lo = new double[] { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, DEFAULT_MIN_SIGMA, Double.NEGATIVE_INFINITY };
            hi = new double[] { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
        } else {
            lo = bounds.lower().clone();
            hi = bounds.upper().clone();
            // Always enforce min sigma.
            lo[IDX_SIGMA] = Math.max(lo[IDX_SIGMA], DEFAULT_MIN_SIGMA);
        }

        ParameterValidator v = clampingValidator(lo, hi);
        return super.fit(x, y, weights, initialGuess, v);
    }

    /**
     * Build a value getter using the fitted parameters.
     *
     * @param fit fit result
     * @return an evaluator for the fitted Gaussian
     */
    @Override
    public IValueGetter asValueGetter(final FitResult fit) {
        Objects.requireNonNull(fit, "fit");
        if (fit == null || fit.params == null) {
            throw new IllegalArgumentException("FitResult is null");
        }
        if (fit.params.length != getParameterCount()) {
            throw new IllegalArgumentException(
                "PolynomialFitter: expected " + getParameterCount() +
                " parameters, got " + fit.params.length
            );
        }
        
       final double[] p = fit.params.clone();
        return (double x) -> {
            double A = p[IDX_A];
            double mu = p[IDX_MU];
            double sigma = p[IDX_SIGMA];
            double B = p[IDX_B];

            double z = (x - mu) / sigma;
            return A * Math.exp(-0.5 * z * z) + B;
        };
    }

    /** Analytic model + Jacobian for Gaussian-with-baseline. */
    private static final class GaussianModel implements MultivariateJacobianFunction {
        private final double[] x;

        GaussianModel(double[] x) {
            this.x = x.clone();
        }

        @Override
        public Pair<RealVector, RealMatrix> value(final RealVector point) {
            final double[] p = point.toArray();
            final double A = p[IDX_A];
            final double mu = p[IDX_MU];
            final double sigma = p[IDX_SIGMA];
            final double B = p[IDX_B];

            final int n = x.length;
            final double[] values = new double[n];
            final double[][] jac = new double[n][4];

            final double invSigma = 1.0 / sigma;
            final double invSigma2 = invSigma * invSigma;

            for (int i = 0; i < n; i++) {
                double dx = x[i] - mu;
                double z = dx * invSigma;
                double e = Math.exp(-0.5 * z * z);

                values[i] = A * e + B;

                // dy/dA = e
                jac[i][IDX_A] = e;
                // dy/dmu = A*e*(dx/sigma^2)
                jac[i][IDX_MU] = A * e * (dx * invSigma2);
                // dy/dsigma = A*e*(dx^2/sigma^3)
                jac[i][IDX_SIGMA] = A * e * (dx * dx) * (invSigma2 * invSigma);
                // dy/dB = 1
                jac[i][IDX_B] = 1.0;
            }

            return new Pair<>(
                    new ArrayRealVector(values, false),
                    new Array2DRowRealMatrix(jac, false)
            );
        }
    }

    /**
     * Simple bounds container for the Gaussian parameters. Use +/-infinity for unbounded.
     */
    public static final class ParameterBounds {
        private final double[] lower = new double[4];
        private final double[] upper = new double[4];

        private ParameterBounds(double[] lower, double[] upper) {
            System.arraycopy(lower, 0, this.lower, 0, 4);
            System.arraycopy(upper, 0, this.upper, 0, 4);
        }

        /** @return a defensive copy of lower bounds. */
        public double[] lower() {
            return lower.clone();
        }

        /** @return a defensive copy of upper bounds. */
        public double[] upper() {
            return upper.clone();
        }

        /** @return unbounded bounds (all parameters +/-infinity). */
        public static ParameterBounds unbounded() {
            double[] lo = { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY };
            double[] hi = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
            return new ParameterBounds(lo, hi);
        }

        /** @return builder for bounds. */
        public static Builder builder() {
            return new Builder();
        }

        /** Fluent builder for bounds. */
        public static final class Builder {
            private final double[] lo = { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY };
            private final double[] hi = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };

            public Builder a(double lower, double upper) { lo[IDX_A] = lower; hi[IDX_A] = upper; return this; }
            public Builder mu(double lower, double upper) { lo[IDX_MU] = lower; hi[IDX_MU] = upper; return this; }
            public Builder sigma(double lower, double upper) { lo[IDX_SIGMA] = lower; hi[IDX_SIGMA] = upper; return this; }
            public Builder b(double lower, double upper) { lo[IDX_B] = lower; hi[IDX_B] = upper; return this; }

            public ParameterBounds build() {
                return new ParameterBounds(lo, hi);
            }
        }
    }

    /** Heuristic initial guess for Gaussian parameters. */
    static final class InitialGuess {
        private InitialGuess() {}

        public static double[] guess(double[] x, double[] y) {
            final int n = x.length;
            if (n == 0) {
                return new double[] { 1, 0, 1, 0 };
            }

            // Baseline guess: median-ish from endpoints.
            Integer[] idx = new Integer[n];
            for (int i = 0; i < n; i++) idx[i] = i;
            Arrays.sort(idx, (i, j) -> Double.compare(x[i], x[j]));

            double yLeft = y[idx[0]];
            double yRight = y[idx[n - 1]];
            double B = 0.5 * (yLeft + yRight);

            // Peak amplitude and mu guess from max deviation above baseline.
            int imax = 0;
            double maxVal = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                double v = y[i];
                if (v > maxVal) {
                    maxVal = v;
                    imax = i;
                }
            }
            double mu = x[imax];
            double A = maxVal - B;
            if (!Double.isFinite(A) || A == 0.0) {
                A = 1.0;
            }

            // Sigma guess from half-maximum width (crude fallback).
            double half = B + 0.5 * A;
            double x1 = mu, x2 = mu;
            boolean foundLeft = false, foundRight = false;

            for (int k = imax; k >= 0; k--) {
                if (y[k] <= half) { x1 = x[k]; foundLeft = true; break; }
            }
            for (int k = imax; k < n; k++) {
                if (y[k] <= half) { x2 = x[k]; foundRight = true; break; }
            }

            double sigma;
            if (foundLeft && foundRight) {
                double fwhm = Math.abs(x2 - x1);
                sigma = fwhm / 2.354820045; // FWHM = 2*sqrt(2 ln 2)*sigma
            } else {
                // fallback to a fraction of x-range
                double xmin = x[0], xmax = x[0];
                for (double xv : x) { xmin = Math.min(xmin, xv); xmax = Math.max(xmax, xv); }
                sigma = 0.1 * Math.max(1e-12, (xmax - xmin));
            }

            if (!Double.isFinite(sigma) || sigma < DEFAULT_MIN_SIGMA) {
                sigma = DEFAULT_MIN_SIGMA;
            }

            return new double[] { A, mu, sigma, B };
        }
    }
}
