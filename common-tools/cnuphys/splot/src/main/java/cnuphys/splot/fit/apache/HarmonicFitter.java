package cnuphys.splot.fit.apache;

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
 * Harmonic (sinusoidal) least-squares fitter.
 *
 * <h3>Model</h3>
 * <ul>
 *   <li>With offset: {@code y = A*sin(omega*x + phi) + B}  (4 params)</li>
 *   <li>No offset:   {@code y = A*sin(omega*x + phi)}      (3 params)</li>
 * </ul>
 *
 * <h3>Parameter ordering</h3>
 * <ul>
 *   <li>{@code params[IDX_A]     = A}</li>
 *   <li>{@code params[IDX_OMEGA] = omega}</li>
 *   <li>{@code params[IDX_PHI]   = phi}</li>
 *   <li>{@code params[IDX_B]     = B} (only if withOffset)</li>
 * </ul>
 *
 * <h3>Initial guess</h3>
 * Uses a robust frequency scan that solves a linear LS problem for each omega:
 * {@code y ~= C*sin(omega x) + D*cos(omega x) [+B]} and maps (C,D) -> (A,phi).
 */
public final class HarmonicFitter extends AbstractLeastSquaresFitter {

    public static final int IDX_A = 0;
    public static final int IDX_OMEGA = 1;
    public static final int IDX_PHI = 2;
    public static final int IDX_B = 3;

    private final boolean withOffset;

    /** Create a harmonic fitter (with offset). */
    public HarmonicFitter() {
        this(true);
    }

    /**
     * Create a harmonic fitter.
     *
     * @param withOffset if true includes vertical offset B
     */
    public HarmonicFitter(boolean withOffset) {
        this(withOffset, new LevenbergMarquardtOptimizer());
    }

    public HarmonicFitter(boolean withOffset, LeastSquaresOptimizer optimizer) {
        super(Objects.requireNonNull(optimizer, "optimizer"),
              (x, y, w) -> ExtraNiceInitialGuess.guess(withOffset, x, y, w));
        this.withOffset = withOffset;
    }

    public boolean isWithOffset() {
        return withOffset;
    }

    @Override
    protected int getParameterCount() {
        return withOffset ? 4 : 3;
    }

    @Override
    protected String getModelName() {
        return withOffset ? "HARMONIC_WITH_OFFSET" : "HARMONIC_NO_OFFSET";
    }

    @Override
    protected MultivariateJacobianFunction model(double[] x) {
        return new HarmonicModel(withOffset, x);
    }

    @Override
    protected double[] defaultInitialGuess(double[] x, double[] y, double[] weights) {
        return ExtraNiceInitialGuess.guess(withOffset, x, y, weights);
    }

    /**
     * Expert overload: fit with optional weights, optional bounds, and optional initial guess.
     *
     * @param x x data
     * @param y y data
     * @param weights optional weights, may be null
     * @param bounds optional bounds, may be null
     * @param initialGuess optional initial guess, may be null
     * @return fit result
     */
    public FitResult fit(double[] x, double[] y,
                         double[] weights,
                         ParameterBounds bounds,
                         double[] initialGuess) {

        final int p = getParameterCount();

        final ParameterValidator v;
        if (bounds == null) {
            v = null;
        } else {
            if (bounds.size() != p) {
                throw new IllegalArgumentException("bounds size mismatch: expected " + p);
            }
            v = clampingValidator(bounds.lower(), bounds.upper());
        }

        return super.fit(x, y, weights, initialGuess, v);
    }

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

        final double[] p = fit.params.clone();

        return (double x) -> {
            double A = p[IDX_A];
            double omega = p[IDX_OMEGA];
            double phi = p[IDX_PHI];
            double y = A * Math.sin(omega * x + phi);
            if (withOffset) {
                y += p[IDX_B];
            }
            return y;
        };
    }

     /** Analytic model + Jacobian for harmonic. */
    private static final class HarmonicModel implements MultivariateJacobianFunction {
        private final boolean withOffset;
        private final double[] x;

        HarmonicModel(boolean withOffset, double[] x) {
            this.withOffset = withOffset;
            this.x = x.clone();
        }

        @Override
        public Pair<RealVector, RealMatrix> value(RealVector point) {
            final double[] p = point.toArray();
            final double A = p[IDX_A];
            final double omega = p[IDX_OMEGA];
            final double phi = p[IDX_PHI];
            final double B = withOffset ? p[IDX_B] : 0.0;

            final int n = x.length;
            final int m = withOffset ? 4 : 3;

            final double[] values = new double[n];
            final double[][] jac = new double[n][m];

            for (int i = 0; i < n; i++) {
                double arg = omega * x[i] + phi;
                double s = Math.sin(arg);
                double c = Math.cos(arg);

                values[i] = A * s + B;

                // dy/dA = sin(arg)
                jac[i][IDX_A] = s;
                // dy/domega = A*cos(arg)*x
                jac[i][IDX_OMEGA] = A * c * x[i];
                // dy/dphi = A*cos(arg)
                jac[i][IDX_PHI] = A * c;

                if (withOffset) {
                    jac[i][IDX_B] = 1.0;
                }
            }

            return new Pair<>(
                    new ArrayRealVector(values, false),
                    new Array2DRowRealMatrix(jac, false)
            );
        }
    }

    /**
     * Harmonic parameter bounds. Use +/-infinity for unbounded.
     */
    public static final class ParameterBounds {
        private final boolean withOffset;
        private final double[] lower;
        private final double[] upper;

        private ParameterBounds(boolean withOffset, double[] lower, double[] upper) {
            this.withOffset = withOffset;
            this.lower = lower;
            this.upper = upper;
        }

        public int size() {
            return lower.length;
        }

        public double[] lower() {
            return lower.clone();
        }

        public double[] upper() {
            return upper.clone();
        }

        public static ParameterBounds unbounded(boolean withOffset) {
            int p = withOffset ? 4 : 3;
            double[] lo = new double[p];
            double[] hi = new double[p];
            for (int i = 0; i < p; i++) {
                lo[i] = Double.NEGATIVE_INFINITY;
                hi[i] = Double.POSITIVE_INFINITY;
            }
            return new ParameterBounds(withOffset, lo, hi);
        }

        public static Builder builder(boolean withOffset) {
            return new Builder(withOffset);
        }

        public static final class Builder {
            private final boolean withOffset;
            private final double[] lo;
            private final double[] hi;

            Builder(boolean withOffset) {
                this.withOffset = withOffset;
                int p = withOffset ? 4 : 3;
                lo = new double[p];
                hi = new double[p];
                for (int i = 0; i < p; i++) {
                    lo[i] = Double.NEGATIVE_INFINITY;
                    hi[i] = Double.POSITIVE_INFINITY;
                }
            }

            public Builder a(double lower, double upper) { lo[IDX_A] = lower; hi[IDX_A] = upper; return this; }
            public Builder omega(double lower, double upper) { lo[IDX_OMEGA] = lower; hi[IDX_OMEGA] = upper; return this; }
            public Builder phi(double lower, double upper) { lo[IDX_PHI] = lower; hi[IDX_PHI] = upper; return this; }
            public Builder b(double lower, double upper) {
                if (!withOffset) throw new IllegalStateException("no B parameter when withOffset=false");
                lo[IDX_B] = lower; hi[IDX_B] = upper; return this;
            }

            public ParameterBounds build() {
                return new ParameterBounds(withOffset, lo.clone(), hi.clone());
            }
        }
    }

    /**
     * Robust initial guesser. This is intentionally kept inside the fitter because it is model-specific.
     *
     * <p>If you already have a richer implementation in your current HarmonicFitter, you can
     * paste it here unchanged; the only required contract is returning the parameter vector
     * in the (A,omega,phi[,B]) ordering.</p>
     */
    static final class ExtraNiceInitialGuess {
        private ExtraNiceInitialGuess() {}

        public static double[] guess(boolean withOffset, double[] x, double[] y, double[] weights) {
            // Minimal, safe fallback:
            // - omega from x-range (one cycle over range)
            // - A from half peak-to-peak
            // - phi = 0
            // - B from mean (if offset)

            int n = x.length;
            if (n == 0) {
                return withOffset ? new double[] {1, 1, 0, 0} : new double[] {1, 1, 0};
            }

            double xmin = x[0], xmax = x[0];
            double ymin = y[0], ymax = y[0];
            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                xmin = Math.min(xmin, x[i]);
                xmax = Math.max(xmax, x[i]);
                ymin = Math.min(ymin, y[i]);
                ymax = Math.max(ymax, y[i]);
                sum += y[i];
            }

            double range = Math.max(1e-12, xmax - xmin);
            double omega = 2.0 * Math.PI / range; // ~one cycle across range
            double A = 0.5 * (ymax - ymin);
            if (!Double.isFinite(A) || A == 0.0) A = 1.0;

            double phi = 0.0;
            double B = sum / n;

            return withOffset ? new double[] {A, omega, phi, B} : new double[] {A, omega, phi};
        }
    }
}
