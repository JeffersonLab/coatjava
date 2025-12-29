package cnuphys.splot.fit.apache;

import java.util.Arrays;
import java.util.Comparator;
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
 * Fit a sum of Gaussians (optionally with a constant baseline).
 *
 * <h3>Model</h3>
 * <pre>
 *   y(x) = sum_{k=0..m-1} A_k * exp(-(x - mu_k)^2 / (2*sigma_k^2))  +  B   (if includeBaseline)
 *   y(x) = sum_{k=0..m-1} A_k * exp(-(x - mu_k)^2 / (2*sigma_k^2))        (otherwise)
 * </pre>
 *
 * <h3>Parameter vector</h3>
 * For each Gaussian component k:
 * <pre>
 *   A_k, mu_k, sigma_k
 * </pre>
 * in that order, followed by baseline B if enabled.
 *
 * <h3>Notes</h3>
 * <ul>
 *   <li>This is a nonconvex problem. Initial guess matters.</li>
 *   <li>Component permutation symmetry exists (swapping components is the same model).
 *       If you care about stable ordering for reporting, use {@link #reorderFitResultByMu(FitResult)}
 *       or {@link #sortComponentsByMu(double[])}.</li>
 *   <li>By default we enforce {@code sigma >= DEFAULT_MIN_SIGMA} via a parameter validator.</li>
 * </ul>
 */
public final class MultiGaussianFitter extends AbstractLeastSquaresFitter {

    /** Number of Gaussian components. */
    private final int m;

    /** Whether to include constant baseline B. */
    private final boolean includeBaseline;

    /** Default minimum allowed sigma to avoid division by zero. */
    public static final double DEFAULT_MIN_SIGMA = 1e-12;

    /** Create an m-Gaussian fitter with baseline using LM optimizer and default guesser. */
    public MultiGaussianFitter(int m) {
        this(m, true);
    }

    /** Create an m-Gaussian fitter with optional baseline using LM optimizer and default guesser. */
    public MultiGaussianFitter(int m, boolean includeBaseline) {
        this(m, includeBaseline, new LevenbergMarquardtOptimizer(), defaultGuesser(m, includeBaseline));
    }

    /** Create with custom optimizer and default guesser. */
    public MultiGaussianFitter(int m, boolean includeBaseline, LeastSquaresOptimizer optimizer) {
        this(m, includeBaseline, optimizer, defaultGuesser(m, includeBaseline));
    }

    /** Create with custom optimizer and custom guesser. */
    public MultiGaussianFitter(int m, boolean includeBaseline, LeastSquaresOptimizer optimizer, IInitialGuess guesser) {
        super(optimizer, guesser);
        if (m <= 0) {
            throw new IllegalArgumentException("number of Gaussians m must be >= 1");
        }
        this.m = m;
        this.includeBaseline = includeBaseline;
    }

    /** @return number of Gaussian components. */
    public int getNumGaussians() {
        return m;
    }

    /** @return true if the model includes a baseline B. */
    public boolean isIncludeBaseline() {
        return includeBaseline;
    }

    /** @return number of fit parameters. */
    public int nParams() {
        return 3 * m + (includeBaseline ? 1 : 0);
    }

    /** Fit with unit weights and default initial guess. */
    public FitResult fit(double[] x, double[] y) {
        return fit(x, y, null, null, null);
    }

    /**
     * Fit with optional weights, bounds, and initial guess.
     *
     * @param x x data
     * @param y y data
     * @param weights optional weights (length n), typically 1/sigmaY^2
     * @param bounds optional bounds; null means unbounded (except sigma clamped)
     * @param initialGuess optional parameter vector of length {@link #nParams()}
     */
    public FitResult fit(double[] x,
                         double[] y,
                         double[] weights,
                         ParameterBounds bounds,
                         double[] initialGuess) {

        final int p = nParams();
        validateXY(x, y, Math.max(4, p));
        final int n = x.length;

        if (weights != null && weights.length != n) {
            throw new IllegalArgumentException("weights length must match x/y length");
        }
        if (initialGuess != null && initialGuess.length != p) {
            throw new IllegalArgumentException("initialGuess must have length " + p);
        }

        final double[] start = selectInitialGuess(x, y, weights, initialGuess);

        final ParameterBounds bds = (bounds != null) ? bounds : ParameterBounds.unbounded(m, includeBaseline);
        final ParameterValidator validator = new BoundValidator(bds, DEFAULT_MIN_SIGMA, m, includeBaseline);

        final MultivariateJacobianFunction model = new MultiGaussianModel(x, m, includeBaseline);

        final LeastSquaresBuilder b = new LeastSquaresBuilder()
                .start(start)
                .model(model)
                .target(y)
                .parameterValidator(validator)
                .maxIterations(5000)
                .maxEvaluations(5000)
                .checkerPair(new SimpleVectorValueChecker(1e-12, 1e-12));

        if (weights != null) {
            b.weight(new DiagonalMatrix(weights));
        }

        LeastSquaresProblem problem = b.build();
        LeastSquaresOptimizer.Optimum opt = optimizer.optimize(problem);

        final double[] params = opt.getPoint().toArray();
        final RealMatrix cov = safeCovariances(opt, 1e-14);

        return buildFitResult(includeBaseline ? "MULTI_GAUSS_" + m : "MULTI_GAUSS_NO_BASE_" + m,
                              params, cov, n, p, opt);
    }

    /** weights[i] = 1/(sigmaY[i]^2). */
    public static double[] weightsFromSigmaY(double[] sigmaY) {
        return AbstractLeastSquaresFitter.weightsFromSigmaY(sigmaY);
    }

    /**
     * Create an {@link IValueGetter} that evaluates the fitted multi-Gaussian curve.
     * Sigma values are clamped to {@link #DEFAULT_MIN_SIGMA} at evaluation time.
     */
    public IValueGetter asValueGetter(final FitResult fit) {
        Objects.requireNonNull(fit, "fit");
        final double[] p = fit.params.clone();
        final int mLocal = this.m;
        final boolean base = this.includeBaseline;

        return (double x) -> {
            double yy = 0.0;
            for (int k = 0; k < mLocal; k++) {
                int off = 3 * k;
                double A = p[off];
                double mu = p[off + 1];
                double s0 = p[off + 2];
                double sigma = Math.max(Math.abs(s0), DEFAULT_MIN_SIGMA);

                double dx = x - mu;
                double e = Math.exp(-(dx * dx) / (2.0 * sigma * sigma));
                yy += A * e;
            }
            if (base) {
                yy += p[3 * mLocal];
            }
            return yy;
        };
    }

    public IPlottableFunction asPlottable(final FitResult fit, double xmin, double xmax) {
        return new PlottableFunction(asValueGetter(fit), xmin, xmax);
    }

    // ------------------------------------------------------------------------
    // Practical suggestion #1: small helper to build an explicit initial guess
    // ------------------------------------------------------------------------

    /**
     * Convenience builder for constructing an explicit initial-guess parameter vector.
     * <p>
     * This avoids callers having to remember the parameter indexing for multi-Gaussian fits.
     * The resulting array can be passed as {@code initialGuess} to {@link #fit(double[], double[], double[], ParameterBounds, double[])}.
     */
    public static final class InitialGuessBuilder {
        private final int m;
        private final boolean includeBaseline;
        private final double[] p;

        /**
         * @param m number of Gaussians (>= 1)
         * @param includeBaseline whether the model includes a baseline B
         */
        public InitialGuessBuilder(int m, boolean includeBaseline) {
            if (m <= 0) throw new IllegalArgumentException("m must be >= 1");
            this.m = m;
            this.includeBaseline = includeBaseline;
            this.p = new double[3 * m + (includeBaseline ? 1 : 0)];

            // defaults
            for (int k = 0; k < m; k++) {
                setSigma(k, 1.0);
            }
            if (includeBaseline) {
                setBaseline(0.0);
            }
        }

        /** Set [A, mu, sigma] for component k. */
        public InitialGuessBuilder component(int k, double A, double mu, double sigma) {
            setAmplitude(k, A);
            setMu(k, mu);
            setSigma(k, sigma);
            return this;
        }

        public InitialGuessBuilder setAmplitude(int k, double A) {
            p[3 * idx(k)] = A;
            return this;
        }

        public InitialGuessBuilder setMu(int k, double mu) {
            p[3 * idx(k) + 1] = mu;
            return this;
        }

        public InitialGuessBuilder setSigma(int k, double sigma) {
            p[3 * idx(k) + 2] = Math.max(sigma, DEFAULT_MIN_SIGMA);
            return this;
        }

        /** Set baseline B (only valid if includeBaseline=true). */
        public InitialGuessBuilder setBaseline(double B) {
            if (!includeBaseline) {
                throw new IllegalStateException("This model has no baseline term");
            }
            p[3 * m] = B;
            return this;
        }

        /**
         * Convenience: evenly-spaced mu values across [xmin, xmax].
         * Leaves A and sigma unchanged.
         */
        public InitialGuessBuilder evenlySpacedMus(double xmin, double xmax) {
            if (!(xmax > xmin)) {
                throw new IllegalArgumentException("xmax must be > xmin");
            }
            for (int k = 0; k < m; k++) {
                double t = (k + 1.0) / (m + 1.0);
                setMu(k, xmin + t * (xmax - xmin));
            }
            return this;
        }

        /** Convenience: set all sigmas to a common value. */
        public InitialGuessBuilder allSigmas(double sigma) {
            for (int k = 0; k < m; k++) {
                setSigma(k, sigma);
            }
            return this;
        }

        /** Convenience: set all amplitudes to a common value. */
        public InitialGuessBuilder allAmplitudes(double A) {
            for (int k = 0; k < m; k++) {
                setAmplitude(k, A);
            }
            return this;
        }

        /** Build the parameter vector. Returns a defensive copy. */
        public double[] build() {
            return p.clone();
        }

        private int idx(int k) {
            if (k < 0 || k >= m) throw new IllegalArgumentException("component index out of range: " + k);
            return k;
        }
    }

    // ------------------------------------------------------------------------
    // Practical suggestion #2: stable ordering helpers (sort by mu)
    // ------------------------------------------------------------------------

    /**
     * Return a copy of the parameter vector sorted by ascending mu.
     * Baseline (if present) is left unchanged.
     */
    public double[] sortComponentsByMu(double[] params) {
        int[] order = sortOrderByMu(params);
        return reorderParams(params, order);
    }

    /**
     * Return the component order (permutation) that sorts by ascending mu.
     * The returned array {@code order[pos] = originalComponentIndex}.
     */
    public int[] sortOrderByMu(double[] params) {
        Objects.requireNonNull(params, "params");
        if (params.length != nParams()) {
            throw new IllegalArgumentException("params length must be " + nParams());
        }

        Integer[] idx = new Integer[m];
        for (int k = 0; k < m; k++) idx[k] = k;
        Arrays.sort(idx, Comparator.comparingDouble(k -> params[3 * k + 1]));

        int[] order = new int[m];
        for (int pos = 0; pos < m; pos++) {
            order[pos] = idx[pos];
        }
        return order;
    }

    /**
     * Reorder a parameter vector using {@code order[pos] = originalComponentIndex}.
     * Baseline (if present) is left unchanged.
     */
    public double[] reorderParams(double[] params, int[] order) {
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(order, "order");
        if (params.length != nParams()) throw new IllegalArgumentException("params length must be " + nParams());
        if (order.length != m) throw new IllegalArgumentException("order length must be " + m);

        double[] out = new double[params.length];
        for (int pos = 0; pos < m; pos++) {
            int k = order[pos];
            if (k < 0 || k >= m) throw new IllegalArgumentException("bad component index in order: " + k);
            System.arraycopy(params, 3 * k, out, 3 * pos, 3);
        }
        if (includeBaseline) {
            out[3 * m] = params[3 * m];
        }
        return out;
    }

    /**
     * Permute a covariance matrix consistent with reordering parameters.
     * Only permutes the component blocks; baseline stays last if present.
     *
     * @param cov original covariance (p x p)
     * @param order order[pos] = originalComponentIndex
     * @return permuted covariance (new matrix)
     */
    public RealMatrix reorderCovariance(RealMatrix cov, int[] order) {
        Objects.requireNonNull(cov, "cov");
        Objects.requireNonNull(order, "order");

        final int p = nParams();
        if (cov.getRowDimension() != p || cov.getColumnDimension() != p) {
            throw new IllegalArgumentException("covariance must be " + p + "x" + p);
        }
        if (order.length != m) {
            throw new IllegalArgumentException("order length must be " + m);
        }

        // Map old parameter index -> new parameter index
        int[] newIndexOfOld = new int[p];
        Arrays.fill(newIndexOfOld, -1);

        for (int pos = 0; pos < m; pos++) {
            int oldComp = order[pos];
            for (int j = 0; j < 3; j++) {
                int oldIdx = 3 * oldComp + j;
                int newIdx = 3 * pos + j;
                newIndexOfOld[oldIdx] = newIdx;
            }
        }
        if (includeBaseline) {
            newIndexOfOld[3 * m] = 3 * m; // baseline fixed
        }

        double[][] out = new double[p][p];
        for (int oldI = 0; oldI < p; oldI++) {
            int newI = newIndexOfOld[oldI];
            for (int oldJ = 0; oldJ < p; oldJ++) {
                int newJ = newIndexOfOld[oldJ];
                out[newI][newJ] = cov.getEntry(oldI, oldJ);
            }
        }
        return new Array2DRowRealMatrix(out, false);
    }

    /**
     * Convenience: return a new {@link FitResult} with components sorted by ascending mu.
     * If covariance is available, it is permuted accordingly.
     */
    public FitResult reorderFitResultByMu(FitResult fit) {
        Objects.requireNonNull(fit, "fit");
        if (fit.params.length != nParams()) {
            throw new IllegalArgumentException("FitResult params length must be " + nParams());
        }

        int[] order = sortOrderByMu(fit.params);
        double[] p2 = reorderParams(fit.params, order);

        RealMatrix cov2 = null;
        if (fit.covariance != null) {
            cov2 = reorderCovariance(fit.covariance, order);
        }

        return new FitResult(
                fit.model,
                p2,
                cov2,
                fit.cost,
                fit.chiSquare,
                fit.dof,
                fit.chiSquareReduced,
                fit.rms,
                fit.iterations,
                fit.evaluations
        );
    }

    // ------------------------------------------------------------------------
    // Internals: model, bounds/validator, and default initial-guess heuristic
    // ------------------------------------------------------------------------

    /** Model + analytic Jacobian. */
    private static final class MultiGaussianModel implements MultivariateJacobianFunction {
        private final double[] x;
        private final int m;
        private final boolean includeBaseline;

        MultiGaussianModel(double[] x, int m, boolean includeBaseline) {
            this.x = x.clone();
            this.m = m;
            this.includeBaseline = includeBaseline;
        }

        @Override
        public Pair<RealVector, RealMatrix> value(final RealVector point) {
            final double[] p = point.toArray();
            final int n = x.length;
            final int nParams = 3 * m + (includeBaseline ? 1 : 0);

            final double[] values = new double[n];
            final double[][] jac = new double[n][nParams];

            for (int i = 0; i < n; i++) {
                final double xi = x[i];
                double yi = 0.0;

                for (int k = 0; k < m; k++) {
                    int off = 3 * k;
                    double A = p[off];
                    double mu = p[off + 1];
                    double sigma = p[off + 2];

                    double s2 = sigma * sigma;
                    double dx = xi - mu;
                    double e = Math.exp(-(dx * dx) / (2.0 * s2));

                    yi += A * e;

                    jac[i][off] = e;                          // dy/dA
                    jac[i][off + 1] = A * e * (dx / s2);      // dy/dmu
                    jac[i][off + 2] = A * e * (dx * dx) / (sigma * s2); // dy/dsigma
                }

                if (includeBaseline) {
                    yi += p[3 * m];
                    jac[i][3 * m] = 1.0; // dy/dB
                }

                values[i] = yi;
            }

            return new Pair<>(new ArrayRealVector(values, false),
                              new Array2DRowRealMatrix(jac, false));
        }
    }

    /** Bounds for all parameters. */
    public static final class ParameterBounds {
        final double[] lower;
        final double[] upper;
        final int m;
        final boolean includeBaseline;

        private ParameterBounds(int m, boolean includeBaseline, double[] lower, double[] upper) {
            this.m = m;
            this.includeBaseline = includeBaseline;
            this.lower = lower;
            this.upper = upper;
        }

        public static ParameterBounds unbounded(int m, boolean includeBaseline) {
            int p = 3 * m + (includeBaseline ? 1 : 0);
            double[] lo = new double[p];
            double[] hi = new double[p];
            for (int i = 0; i < p; i++) {
                lo[i] = Double.NEGATIVE_INFINITY;
                hi[i] = Double.POSITIVE_INFINITY;
            }
            return new ParameterBounds(m, includeBaseline, lo, hi);
        }

        public static Builder builder(int m, boolean includeBaseline) {
            return new Builder(m, includeBaseline);
        }

        public static final class Builder {
            private final int m;
            private final boolean includeBaseline;
            private final double[] lo;
            private final double[] hi;

            Builder(int m, boolean includeBaseline) {
                this.m = m;
                this.includeBaseline = includeBaseline;
                int p = 3 * m + (includeBaseline ? 1 : 0);
                lo = new double[p];
                hi = new double[p];
                for (int i = 0; i < p; i++) {
                    lo[i] = Double.NEGATIVE_INFINITY;
                    hi[i] = Double.POSITIVE_INFINITY;
                }
            }

            private int off(int k) {
                if (k < 0 || k >= m) throw new IllegalArgumentException("component index out of range: " + k);
                return 3 * k;
            }

            public Builder component(int k, double aLo, double aHi, double muLo, double muHi, double sigmaLo, double sigmaHi) {
                int o = off(k);
                lo[o] = aLo; hi[o] = aHi;
                lo[o + 1] = muLo; hi[o + 1] = muHi;
                lo[o + 2] = sigmaLo; hi[o + 2] = sigmaHi;
                return this;
            }

            public Builder baseline(double bLo, double bHi) {
                if (!includeBaseline) throw new IllegalStateException("No baseline term in this model");
                int idx = 3 * m;
                lo[idx] = bLo; hi[idx] = bHi;
                return this;
            }

            public ParameterBounds build() {
                return new ParameterBounds(m, includeBaseline, lo, hi);
            }
        }
    }

    /** Clamp bounds and enforce sigma >= minSigma. */
    private static final class BoundValidator implements ParameterValidator {
        private final ParameterBounds bounds;
        private final double minSigma;
        private final int m;
        private final boolean includeBaseline;

        BoundValidator(ParameterBounds bounds, double minSigma, int m, boolean includeBaseline) {
            this.bounds = bounds;
            this.minSigma = minSigma;
            this.m = m;
            this.includeBaseline = includeBaseline;
        }

        @Override
        public RealVector validate(RealVector params) {
            double[] p = params.toArray();

            // enforce sigma >= minSigma for all components
            for (int k = 0; k < m; k++) {
                int idxSigma = 3 * k + 2;
                p[idxSigma] = Math.max(p[idxSigma], minSigma);
            }

            int n = 3 * m + (includeBaseline ? 1 : 0);
            for (int i = 0; i < n; i++) {
                double lo = bounds.lower[i];
                double hi = bounds.upper[i];
                if (Double.isFinite(lo)) p[i] = Math.max(p[i], lo);
                if (Double.isFinite(hi)) p[i] = Math.min(p[i], hi);
            }

            return new ArrayRealVector(p, false);
        }
    }

    /** Default guesser: simple peak-picking + local width estimate. */
    private static IInitialGuess defaultGuesser(final int m, final boolean includeBaseline) {
        return (x, y, weights) -> InitialGuess.guess(m, includeBaseline, x, y);
    }

    /** Heuristic initial guess utilities. */
    public static final class InitialGuess {
        private InitialGuess() {}

        /**
         * Heuristic initial guess:
         * <ul>
         *   <li>Baseline B: mean of first/last few points (if enabled)</li>
         *   <li>Pick up to m peaks by selecting top y values and enforcing a minimum x-separation</li>
         *   <li>Amplitude A_k: y_peak - B</li>
         *   <li>mu_k: x at selected peak</li>
         *   <li>sigma_k: estimate from local half-height width if possible, else span/(6m)</li>
         * </ul>
         */
        public static double[] guess(int m, boolean includeBaseline, double[] x, double[] y) {
            int n = x.length;

            double xmin = x[0], xmax = x[0];
            double ymin = y[0], ymax = y[0];
            for (int i = 0; i < n; i++) {
                xmin = Math.min(xmin, x[i]);
                xmax = Math.max(xmax, x[i]);
                ymin = Math.min(ymin, y[i]);
                ymax = Math.max(ymax, y[i]);
            }
            double span = xmax - xmin;
            if (!(span > 0.0)) span = 1.0;

            double B = 0.0;
            if (includeBaseline) {
                int k = Math.min(3, n);
                double sum = 0.0;
                for (int i = 0; i < k; i++) sum += y[i];
                for (int i = n - k; i < n; i++) sum += y[i];
                B = sum / (2.0 * k);
            }

            // candidate indices sorted by descending y
            Integer[] idx = new Integer[n];
            for (int i = 0; i < n; i++) idx[i] = i;
            Arrays.sort(idx, (i, j) -> Double.compare(y[j], y[i]));

            // enforce min spacing so we don't pick the same peak multiple times
            double minSep = span / Math.max(6.0, 2.0 * m);

            int[] peaks = new int[m];
            Arrays.fill(peaks, -1);
            int found = 0;

            outer:
            for (int t = 0; t < n && found < m; t++) {
                int ii = idx[t];
                double xi = x[ii];

                for (int j = 0; j < found; j++) {
                    int pj = peaks[j];
                    if (pj >= 0 && Math.abs(xi - x[pj]) < minSep) {
                        continue outer;
                    }
                }
                peaks[found++] = ii;
            }

            // default sigma fallback
            double sigmaFallback = Math.max(span / (6.0 * m), DEFAULT_MIN_SIGMA);

            // sort peaks by x for nicer initial ordering
            Integer[] comp = new Integer[m];
            for (int k = 0; k < m; k++) comp[k] = k;
            Arrays.sort(comp, Comparator.comparingDouble(k -> peaks[k] >= 0 ? x[peaks[k]] : Double.POSITIVE_INFINITY));

            double[] p = new double[3 * m + (includeBaseline ? 1 : 0)];

            for (int pos = 0; pos < m; pos++) {
                int k = comp[pos];
                int pk = peaks[k];

                double Ak, muk, sigk;

                if (pk >= 0) {
                    muk = x[pk];
                    Ak = y[pk] - B;
                    if (!Double.isFinite(Ak) || Math.abs(Ak) < 1e-12) {
                        Ak = (ymax - ymin) / m;
                    }
                    sigk = estimateSigmaFromHalfHeight(x, y, pk, B, Ak);
                    if (!Double.isFinite(sigk) || !(sigk > 0.0)) sigk = sigmaFallback;
                } else {
                    muk = xmin + (pos + 1.0) * span / (m + 1.0);
                    Ak = (ymax - ymin) / Math.max(1.0, m);
                    sigk = sigmaFallback;
                }

                int off = 3 * pos;
                p[off] = Ak;
                p[off + 1] = muk;
                p[off + 2] = Math.max(sigk, DEFAULT_MIN_SIGMA);
            }

            if (includeBaseline) {
                p[3 * m] = B;
            }
            return p;
        }

        private static double estimateSigmaFromHalfHeight(double[] x, double[] y, int peakIndex, double B, double A) {
            // Find approximate FWHM around the peak based on crossing half-height.
            // For a Gaussian: FWHM = 2*sqrt(2*ln2)*sigma => sigma = FWHM / (2*sqrt(2*ln2))
            double half = B + 0.5 * A;

            // search left
            double xL = Double.NaN;
            for (int i = peakIndex; i >= 0; i--) {
                if ((A >= 0 && y[i] <= half) || (A < 0 && y[i] >= half)) {
                    xL = x[i];
                    break;
                }
            }

            // search right
            double xR = Double.NaN;
            for (int i = peakIndex; i < x.length; i++) {
                if ((A >= 0 && y[i] <= half) || (A < 0 && y[i] >= half)) {
                    xR = x[i];
                    break;
                }
            }

            if (Double.isFinite(xL) && Double.isFinite(xR) && xR > xL) {
                double fwhm = xR - xL;
                double denom = 2.0 * Math.sqrt(2.0 * Math.log(2.0));
                double sigma = fwhm / denom;
                return (sigma > 0.0) ? sigma : Double.NaN;
            }
            return Double.NaN;
        }
    }
}
