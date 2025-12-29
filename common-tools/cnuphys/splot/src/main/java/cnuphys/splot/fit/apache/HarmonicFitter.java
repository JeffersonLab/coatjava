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
 * Harmonic (sinusoidal) least-squares fitter using a robust "extra nice" initial guess.
 *
 * <h3>Nonlinear model fit</h3>
 * <ul>
 *   <li>With offset: {@code y = A*sin(omega*x + phi) + B}  (4 params)</li>
 *   <li>No offset:   {@code y = A*sin(omega*x + phi)}      (3 params)</li>
 * </ul>
 *
 * <h3>Extra-nice initial guess</h3>
 * The default initial guess scans angular frequency {@code omega} over a grid and for each omega
 * solves a (weighted) linear least squares problem:
 *
 * <pre>
 * y ~= C*sin(omega*x) + D*cos(omega*x) [+ B]
 * </pre>
 *
 * Then it converts {@code (C,D)} into amplitude/phase:
 * <pre>
 * A   = sqrt(C^2 + D^2)
 * phi = atan2(D, C)    // because C = A*cos(phi), D = A*sin(phi)
 * </pre>
 *
 * This is typically far more reliable than zero-crossing heuristics when the data is noisy or sparse.
 */
public final class HarmonicFitter extends AbstractLeastSquaresFitter {

    /** If true, model includes vertical offset B. */
    private final boolean withOffset;

    /** Parameter indices for the A/omega/phi/(B) form. */
    public static final int IDX_A = 0;
    public static final int IDX_OMEGA = 1;
    public static final int IDX_PHI = 2;
    public static final int IDX_B = 3;

    /** Minimum clamp for omega (avoid omega=0 degeneracy). */
    public static final double DEFAULT_MIN_OMEGA = 1e-12;

    /** Default omega scan resolution. */
    public static final int DEFAULT_OMEGA_STEPS = 300;

    /** Default omega scan range in "cycles over x-span". */
    public static final double DEFAULT_MIN_CYCLES_OVER_SPAN = 0.25;
    public static final double DEFAULT_MAX_CYCLES_OVER_SPAN = 8.0;

    /**
     * Create with offset (default) using LM optimizer and the robust omega-scanning initial guess.
     */
    public HarmonicFitter() {
        this(true);
    }

    /**
     * Create with/without offset using LM optimizer and the robust omega-scanning initial guess.
     */
    public HarmonicFitter(boolean withOffset) {
        this(withOffset, new LevenbergMarquardtOptimizer(),
                defaultGuesser(withOffset, DEFAULT_OMEGA_STEPS,
                        DEFAULT_MIN_CYCLES_OVER_SPAN, DEFAULT_MAX_CYCLES_OVER_SPAN));
    }

    /**
     * Create with custom optimizer and robust omega-scanning initial guess.
     */
    public HarmonicFitter(boolean withOffset, LeastSquaresOptimizer optimizer) {
        this(withOffset, optimizer,
                defaultGuesser(withOffset, DEFAULT_OMEGA_STEPS,
                        DEFAULT_MIN_CYCLES_OVER_SPAN, DEFAULT_MAX_CYCLES_OVER_SPAN));
    }

    /**
     * Create with full control.
     */
    public HarmonicFitter(boolean withOffset, LeastSquaresOptimizer optimizer, IInitialGuess guesser) {
        super(optimizer, guesser);
        this.withOffset = withOffset;
    }

    /**
     * Create a default guesser that scans omega and solves linear least squares for each omega.
     */
    public static IInitialGuess defaultGuesser(final boolean withOffset,
                                               final int omegaSteps,
                                               final double minCyclesOverSpan,
                                               final double maxCyclesOverSpan) {
        return (x, y, weights) -> InitialGuess.guessByOmegaScan(withOffset, x, y, weights,
                omegaSteps, minCyclesOverSpan, maxCyclesOverSpan);
    }

    /** Fit with unit weights. */
    public FitResult fit(double[] x, double[] y) {
        return fit(x, y, null, null, null);
    }

    /**
     * Fit harmonic model with optional weights, bounds, and explicit initial guess.
     *
     * @param x x data
     * @param y y data
     * @param weights optional weights (length n), typically 1/sigmaY^2
     * @param bounds optional bounds (null = unbounded, except omega clamped to minOmega)
     * @param initialGuess optional parameters:
     *        <ul>
     *          <li>if withOffset: [A, omega, phi, B]</li>
     *          <li>else:          [A, omega, phi]</li>
     *        </ul>
     */
    public FitResult fit(double[] x,
                         double[] y,
                         double[] weights,
                         ParameterBounds bounds,
                         double[] initialGuess) {

        final int nParams = withOffset ? 4 : 3;
        validateXY(x, y, nParams);
        final int n = x.length;

        if (weights != null && weights.length != n) {
            throw new IllegalArgumentException("weights length must match x/y length");
        }
        if (initialGuess != null && initialGuess.length != nParams) {
            throw new IllegalArgumentException("initialGuess must have length " + nParams);
        }

        final double[] start = selectInitialGuess(x, y, weights, initialGuess);

        final ParameterBounds bds = (bounds != null) ? bounds : ParameterBounds.unbounded(withOffset);
        final ParameterValidator validator = new BoundValidator(bds, DEFAULT_MIN_OMEGA, withOffset);

        final MultivariateJacobianFunction model = new HarmonicModel(x, withOffset);

        final LeastSquaresBuilder b = new LeastSquaresBuilder()
                .start(start)
                .model(model)
                .target(y)
                .parameterValidator(validator)
                .maxIterations(3000)
                .maxEvaluations(3000)
                .checkerPair(new SimpleVectorValueChecker(1e-12, 1e-12));

        if (weights != null) {
            b.weight(new DiagonalMatrix(weights));
        }

        LeastSquaresProblem problem = b.build();
        LeastSquaresOptimizer.Optimum opt = optimizer.optimize(problem);

        final double[] p = opt.getPoint().toArray();
        final RealMatrix cov = safeCovariances(opt, 1e-14);

        return buildFitResult(withOffset ? "HARMONIC" : "HARMONIC_NO_OFFSET", p, cov, n, nParams, opt);
    }

    public static double[] weightsFromSigmaY(double[] sigmaY) {
        return AbstractLeastSquaresFitter.weightsFromSigmaY(sigmaY);
    }

    /** Create an {@link IValueGetter} that evaluates the fitted harmonic curve. */
    public IValueGetter asValueGetter(final FitResult fit) {
        Objects.requireNonNull(fit, "fit");

        final double A = fit.params[IDX_A];
        final double omega = fit.params[IDX_OMEGA];
        final double phi = fit.params[IDX_PHI];
        final double B = withOffset ? fit.params[IDX_B] : 0.0;

        return (double x) -> A * Math.sin(omega * x + phi) + B;
    }

    public IPlottableFunction asPlottable(final FitResult fit, double xmin, double xmax) {
        return new PlottableFunction(asValueGetter(fit), xmin, xmax);
    }

    /**
     * Harmonic model with analytic Jacobian:
     * y = A*sin(omega*x + phi) [+ B]
     */
    private static final class HarmonicModel implements MultivariateJacobianFunction {
        private final double[] x;
        private final boolean withOffset;

        HarmonicModel(double[] x, boolean withOffset) {
            this.x = x.clone();
            this.withOffset = withOffset;
        }

        @Override
        public Pair<RealVector, RealMatrix> value(final RealVector point) {
            final double A = point.getEntry(IDX_A);
            final double omega = point.getEntry(IDX_OMEGA);
            final double phi = point.getEntry(IDX_PHI);
            final double B = withOffset ? point.getEntry(IDX_B) : 0.0;

            final int n = x.length;
            final int p = withOffset ? 4 : 3;

            final double[] values = new double[n];
            final double[][] jac = new double[n][p];

            for (int i = 0; i < n; i++) {
                final double xi = x[i];
                final double t = omega * xi + phi;
                final double s = Math.sin(t);
                final double c = Math.cos(t);

                values[i] = A * s + B;

                jac[i][IDX_A] = s;            // dy/dA
                jac[i][IDX_OMEGA] = A * c * xi; // dy/domega
                jac[i][IDX_PHI] = A * c;      // dy/dphi
                if (withOffset) {
                    jac[i][IDX_B] = 1.0;      // dy/dB
                }
            }

            return new Pair<>(new ArrayRealVector(values, false),
                              new Array2DRowRealMatrix(jac, false));
        }
    }

    /** Parameter bounds for the nonlinear parameters. */
    public static final class ParameterBounds {
        final double[] lower;
        final double[] upper;
        final boolean withOffset;

        private ParameterBounds(boolean withOffset, double[] lower, double[] upper) {
            this.withOffset = withOffset;
            this.lower = lower;
            this.upper = upper;
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

            /** Only valid if withOffset=true. */
            public Builder b(double lower, double upper) {
                if (!withOffset) throw new IllegalStateException("No offset term B in this model.");
                lo[IDX_B] = lower; hi[IDX_B] = upper;
                return this;
            }

            public ParameterBounds build() {
                return new ParameterBounds(withOffset, lo, hi);
            }
        }
    }

    /** Clamp bounds + enforce omega >= minOmega. */
    private static final class BoundValidator implements ParameterValidator {
        private final ParameterBounds bounds;
        private final double minOmega;
        private final boolean withOffset;

        BoundValidator(ParameterBounds bounds, double minOmega, boolean withOffset) {
            this.bounds = bounds;
            this.minOmega = minOmega;
            this.withOffset = withOffset;
        }

        @Override
        public RealVector validate(RealVector params) {
            double[] p = params.toArray();

            // enforce positive omega
            p[IDX_OMEGA] = Math.max(p[IDX_OMEGA], minOmega);

            int n = withOffset ? 4 : 3;
            for (int i = 0; i < n; i++) {
                double lo = bounds.lower[i];
                double hi = bounds.upper[i];
                if (Double.isFinite(lo)) p[i] = Math.max(p[i], lo);
                if (Double.isFinite(hi)) p[i] = Math.min(p[i], hi);
            }
            return new ArrayRealVector(p, false);
        }
    }

    /**
     * Robust initial guess via omega scanning and linear least squares in (sin, cos, [1]).
     */
    public static final class InitialGuess {
        private InitialGuess() {}

        /**
         * Guess parameters [A, omega, phi, (B)] using an omega scan and linear least squares.
         *
         * @param withOffset include constant term B if true
         * @param x x data
         * @param y y data
         * @param weights optional weights (diagonal), may be null
         * @param omegaSteps number of omega grid steps
         * @param minCyclesOverSpan min cycles across x-span for omega search
         * @param maxCyclesOverSpan max cycles across x-span for omega search
         */
        public static double[] guessByOmegaScan(boolean withOffset,
                                               double[] x,
                                               double[] y,
                                               double[] weights,
                                               int omegaSteps,
                                               double minCyclesOverSpan,
                                               double maxCyclesOverSpan) {

            Objects.requireNonNull(x, "x");
            Objects.requireNonNull(y, "y");
            if (x.length != y.length) throw new IllegalArgumentException("x and y must have same length");
            if (x.length < (withOffset ? 4 : 3)) throw new IllegalArgumentException("not enough points");

            double xmin = x[0], xmax = x[0];
            for (double v : x) {
                xmin = Math.min(xmin, v);
                xmax = Math.max(xmax, v);
            }
            double span = xmax - xmin;
            if (!(span > 0.0) || !Double.isFinite(span)) {
                // degenerate x: fall back to something nonzero
                span = 1.0;
            }

            // omega range derived from cycles across span
            double omegaMin = 2.0 * Math.PI * minCyclesOverSpan / span;
            double omegaMax = 2.0 * Math.PI * maxCyclesOverSpan / span;

            omegaMin = Math.max(omegaMin, DEFAULT_MIN_OMEGA);
            omegaMax = Math.max(omegaMax, omegaMin * 1.001);

            int pLin = withOffset ? 3 : 2; // [C, D, B] or [C, D]
            double bestOmega = omegaMin;
            double[] bestLin = null;
            double bestSSE = Double.POSITIVE_INFINITY;

            // scan omega on a linear grid
            for (int k = 0; k < Math.max(2, omegaSteps); k++) {
                double t = (omegaSteps <= 1) ? 0.0 : (double) k / (omegaSteps - 1);
                double omega = omegaMin + t * (omegaMax - omegaMin);

                // Build design matrix A for this omega
                // col0 = sin(omega*x), col1 = cos(omega*x), col2 = 1 (if withOffset)
                final int n = x.length;
                final double[][] A = new double[n][pLin];
                for (int i = 0; i < n; i++) {
                    double s = Math.sin(omega * x[i]);
                    double c = Math.cos(omega * x[i]);
                    A[i][0] = s;
                    A[i][1] = c;
                    if (withOffset) A[i][2] = 1.0;
                }

                double[] lin = LinearLeastSquaresGuesser.solve(A, y, weights);

                // Compute weighted SSE quickly
                double sse = weightedSSE(A, lin, y, weights);
                if (Double.isFinite(sse) && sse < bestSSE) {
                    bestSSE = sse;
                    bestOmega = omega;
                    bestLin = lin;
                }
            }

            if (bestLin == null) {
                // hard fallback: crude guess
                return crudeFallback(withOffset, x, y);
            }

            double C = bestLin[0];
            double D = bestLin[1];
            double B = withOffset ? bestLin[2] : 0.0;

            // Convert C*sin + D*cos to A*sin(omega*x + phi)
            // A = sqrt(C^2 + D^2)
            // phi = atan2(D, C) because:
            // A*sin(ωx+φ) = A*sin(ωx)cosφ + A*cos(ωx)sinφ
            // => C = A*cosφ, D = A*sinφ
            double Aamp = Math.hypot(C, D);
            if (!(Aamp > 0.0) || !Double.isFinite(Aamp)) {
                Aamp = 1.0;
            }
            double phi = Math.atan2(D, C);

            if (withOffset) {
                return new double[] { Aamp, bestOmega, phi, B };
            }
            return new double[] { Aamp, bestOmega, phi };
        }

        private static double weightedSSE(double[][] A, double[] p, double[] y, double[] w) {
            double sse = 0.0;
            for (int i = 0; i < y.length; i++) {
                double yi = 0.0;
                for (int j = 0; j < p.length; j++) {
                    yi += A[i][j] * p[j];
                }
                double r = (y[i] - yi);
                double wi = 1.0;
                if (w != null) {
                    wi = w[i];
                    if (!Double.isFinite(wi) || wi <= 0.0) wi = 1.0;
                }
                sse += wi * r * r;
            }
            return sse;
        }

        private static double[] crudeFallback(boolean withOffset, double[] x, double[] y) {
            double yMin = y[0], yMax = y[0], ySum = 0.0;
            for (double v : y) {
                yMin = Math.min(yMin, v);
                yMax = Math.max(yMax, v);
                ySum += v;
            }
            double B = withOffset ? (ySum / y.length) : 0.0;
            double A = 0.5 * (yMax - yMin);
            if (!(A > 0.0) || !Double.isFinite(A)) A = 1.0;

            double xmin = x[0], xmax = x[0];
            for (double v : x) { xmin = Math.min(xmin, v); xmax = Math.max(xmax, v); }
            double span = xmax - xmin;
            if (!(span > 0.0) || !Double.isFinite(span)) span = 1.0;
            double omega = 2.0 * Math.PI / span;
            double phi = 0.0;

            if (withOffset) return new double[] { A, omega, phi, B };
            return new double[] { A, omega, phi };
        }
    }
}
