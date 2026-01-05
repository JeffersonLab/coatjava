package cnuphys.splot.fit;

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

import cnuphys.splot.pdata.FitVectors;

/**
 * Fit a sum of Gaussians (optionally with a constant baseline).
 *
 * <h3>Model</h3>
 * <pre>
 * y(x) = sum_{k=0..m-1} A_k * exp(-(x - mu_k)^2 / (2*sigma_k^2))  +  B   (if includeBaseline)
 * y(x) = sum_{k=0..m-1} A_k * exp(-(x - mu_k)^2 / (2*sigma_k^2))        (otherwise)
 * </pre>
 *
 * <h3>Parameter vector</h3>
 * For each component k, parameters appear in blocks:
 * <pre>
 *   A_k, mu_k, sigma_k
 * </pre>
 * followed by baseline B if enabled.
 *
 * <p>By default we enforce {@code sigma_k >= DEFAULT_MIN_SIGMA} for all components.</p>
 */
public final class MultiGaussianFitter extends ALeastSquaresFitter {

    public static final double DEFAULT_MIN_SIGMA = 1e-12;

    private final int m; // number of Gaussians
    private final boolean includeBaseline;

    /** Base Parameter names. */
    public static final String[] paramNames = { "A", "μ", "σ", "B" };

    /**
     * Create a MultiGaussianFitter.
     * @param m number of Gaussian components (must be >= 1)
     * @param includeBaseline true to include constant baseline term
     */
    public MultiGaussianFitter(int m, boolean includeBaseline) {
        this(m, includeBaseline, new LevenbergMarquardtOptimizer());
    }

    /**
	 * Create a MultiGaussianFitter.
	 *
	 * @param m number of Gaussian components (must be >= 1 and <= 6)
	 * @param includeBaseline true to include constant baseline term
	 * @param optimizer least squares optimizer to use
	 */
    public MultiGaussianFitter(int m, boolean includeBaseline, LeastSquaresOptimizer optimizer) {
        super(Objects.requireNonNull(optimizer, "optimizer"),
              (x, y, w) -> InitialGuess.guess(m, includeBaseline, x, y));
        if (m <= 0) throw new IllegalArgumentException("m must be >= 1");
        if (m > 6) throw new IllegalArgumentException("m must be <= 6 to keep fit manageable");
        this.m = m;
        this.includeBaseline = includeBaseline;
    }

    /** Number of Gaussian components. */
    public int numGaussians() {
        return m;
    }

    public boolean isIncludeBaseline() {
        return includeBaseline;
    }

    @Override
    protected int getParameterCount() {
        return 3 * m + (includeBaseline ? 1 : 0);
    }

    @Override
    protected MultivariateJacobianFunction model(double[] x) {
        return new MultiGaussianModel(m, includeBaseline, x);
    }

    @Override
    protected double[] defaultInitialGuess(double[] x, double[] y, double[] weights) {
        return InitialGuess.guess(m, includeBaseline, x, y);
    }

    @Override
    protected ParameterValidator defaultValidator() {
        // Unbounded except sigma_k >= DEFAULT_MIN_SIGMA
        final int p = getParameterCount();
        double[] lo = new double[p];
        double[] hi = new double[p];
        for (int i = 0; i < p; i++) {
            lo[i] = Double.NEGATIVE_INFINITY;
            hi[i] = Double.POSITIVE_INFINITY;
        }
        for (int k = 0; k < m; k++) {
            lo[idxSigma(k)] = DEFAULT_MIN_SIGMA;
        }
        return clampingValidator(lo, hi);
    }

    /**
     * Expert overload: fit with optional weights, optional bounds, and optional initial guess.
     *
     * @param x x data
     * @param y y data
     * @param weights optional weights, may be null
     * @param bounds optional bounds, may be null
     * @param initialGuess optional guess, may be null
     * @return fit result
     */
    public FitResult fit(double[] x, double[] y,
                         double[] weights,
                         ParameterBounds bounds,
                         double[] initialGuess) {

        final int p = getParameterCount();

        final ParameterValidator v;
        if (bounds == null) {
            v = defaultValidator();
        } else {
            if (bounds.size() != p) {
                throw new IllegalArgumentException("bounds size mismatch: expected " + p);
            }
            double[] lo = bounds.lower().clone();
            double[] hi = bounds.upper().clone();
            // Always enforce minimum sigma
            for (int k = 0; k < m; k++) {
                int is = idxSigma(k);
                lo[is] = Math.max(lo[is], DEFAULT_MIN_SIGMA);
            }
            v = clampingValidator(lo, hi);
        }

        return super.fit(x, y, weights, initialGuess, v);
    }

    @Override
    public Evaluator asEvaluator(final FitResult fit) {
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
        return (double x) -> eval(m, includeBaseline, p, x);
    }


    /** Evaluate model at x for parameter vector p. */
    private static double eval(int m, boolean includeBaseline, double[] p, double x) {
        double sum = 0.0;
        for (int k = 0; k < m; k++) {
            double A = p[3 * k];
            double mu = p[3 * k + 1];
            double sigma = p[3 * k + 2];
            double z = (x - mu) / sigma;
            sum += A * Math.exp(-0.5 * z * z);
        }
        if (includeBaseline) {
            sum += p[3 * m];
        }
        return sum;
    }

    /** Index helpers. */
    private static int idxA(int k) { return 3 * k; }
    private static int idxMu(int k) { return 3 * k + 1; }
    private static int idxSigma(int k) { return 3 * k + 2; }
    private int idxB() { return 3 * m; }

    /** Analytic model + Jacobian for sum of Gaussians (+ optional baseline). */
    private static final class MultiGaussianModel implements MultivariateJacobianFunction {
        private final int m;
        private final boolean includeBaseline;
        private final double[] x;

        MultiGaussianModel(int m, boolean includeBaseline, double[] x) {
            this.m = m;
            this.includeBaseline = includeBaseline;
            this.x = x.clone();
        }

        @Override
        public Pair<RealVector, RealMatrix> value(RealVector point) {
            final double[] p = point.toArray();
            final int n = x.length;
            final int nParams = 3 * m + (includeBaseline ? 1 : 0);

            final double[] values = new double[n];
            final double[][] jac = new double[n][nParams];

            for (int i = 0; i < n; i++) {
                double xi = x[i];
                double yi = 0.0;

                for (int k = 0; k < m; k++) {
                    int ia = idxA(k);
                    int imu = idxMu(k);
                    int is = idxSigma(k);

                    double A = p[ia];
                    double mu = p[imu];
                    double sigma = p[is];

                    double dx = xi - mu;
                    double invS = 1.0 / sigma;
                    double z = dx * invS;
                    double e = Math.exp(-0.5 * z * z);

                    yi += A * e;

                    // dy/dA_k = e
                    jac[i][ia] = e;
                    // dy/dmu_k = A*e*(dx/sigma^2)
                    jac[i][imu] = A * e * (dx * invS * invS);
                    // dy/dsigma_k = A*e*(dx^2/sigma^3)
                    jac[i][is] = A * e * (dx * dx) * (invS * invS * invS);
                }

                if (includeBaseline) {
                    yi += p[3 * m];
                    jac[i][3 * m] = 1.0;
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
     * Bounds for the full parameter vector. Use +/-infinity for unbounded.
     */
    public static final class ParameterBounds {
        private final int m;
        private final boolean includeBaseline;
        private final double[] lower;
        private final double[] upper;

        private ParameterBounds(int m, boolean includeBaseline, double[] lower, double[] upper) {
            this.m = m;
            this.includeBaseline = includeBaseline;
            this.lower = lower;
            this.upper = upper;
        }

        public int size() { return lower.length; }
        public double[] lower() { return lower.clone(); }
        public double[] upper() { return upper.clone(); }

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

            public Builder componentA(int k, double lower, double upper) { lo[idxA(k)] = lower; hi[idxA(k)] = upper; return this; }
            public Builder componentMu(int k, double lower, double upper) { lo[idxMu(k)] = lower; hi[idxMu(k)] = upper; return this; }
            public Builder componentSigma(int k, double lower, double upper) { lo[idxSigma(k)] = lower; hi[idxSigma(k)] = upper; return this; }

            public Builder baseline(double lower, double upper) {
                if (!includeBaseline) throw new IllegalStateException("baseline not enabled");
                lo[3 * m] = lower; hi[3 * m] = upper; return this;
            }

            public ParameterBounds build() {
                return new ParameterBounds(m, includeBaseline, lo.clone(), hi.clone());
            }
        }
    }

    /**
     * Initial guess strategy.
     *
     * <p>If your current MultiGaussianFitter already has a richer guesser (peak finding,
     * spacing, etc.), paste it here unchanged — the only requirement is that it returns
     * a parameter vector in the documented ordering.</p>
     */
    static final class InitialGuess {
        private InitialGuess() {}

        public static double[] guess(int m, boolean includeBaseline, double[] x, double[] y) {
            // Minimal, stable fallback: split x-range into m bins, pick local maxima.
            // This is intentionally conservative. Replace with your richer current implementation if desired.
            int n = x.length;
            int p = 3 * m + (includeBaseline ? 1 : 0);
            double[] out = new double[p];

            if (n == 0) {
                for (int k = 0; k < m; k++) {
                    out[3 * k] = 1.0;
                    out[3 * k + 1] = 0.0;
                    out[3 * k + 2] = Math.max(DEFAULT_MIN_SIGMA, 1.0);
                }
                if (includeBaseline) out[3 * m] = 0.0;
                return out;
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
            double B = sum / n;
            if (includeBaseline) out[3 * m] = B;

            double range = Math.max(1e-12, xmax - xmin);
            double step = range / m;
            double sigma = Math.max(DEFAULT_MIN_SIGMA, 0.15 * step);

            for (int k = 0; k < m; k++) {
                double left = xmin + k * step;
                double right = (k == m - 1) ? xmax : (left + step);

                int best = 0;
                double bestY = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < n; i++) {
                    if (x[i] >= left && x[i] <= right && y[i] > bestY) {
                        bestY = y[i];
                        best = i;
                    }
                }

                out[3 * k] = bestY - B;
                out[3 * k + 1] = x[best];
                out[3 * k + 2] = sigma;
            }

            return out;
        }
    }
    
    //------- descriptive string section -----------------
   	@Override
   	public String modelName() {
   		if (includeBaseline) {
   			return "MultiGaussian with baseline\n Num Gaussians = " + m;
   		}
   		return "MultiGaussian num = " + m;
   	}

   	@Override
   	public String functionForm() {
		if (includeBaseline) {
			return String.format("y(x)=%s%s%s%se^[-(x-%s%s)%s/(2%s%s%s)] + B", CAPSIG, SUBN,
					paramNames[0], SUBN, paramNames[1], SUBN, SUP2,
					paramNames[2], SUBN, SUP2);
		}
		return String.format("y(x)=%s%s%s%se^[-(x-%s%s)%s/(2%s%s%s)]", CAPSIG, SUBN,
				paramNames[0], SUBN, paramNames[1], SUBN, SUP2,
				paramNames[2], SUBN, SUP2);
	}

   	/**
   	 * Get the parameter name for the given index.
   	 * 
   	 * @param index the parameter index
   	 * @return the parameter name
   	 */
   	@Override
   	public String parameterName(int index) {
   		if (index < 0 || index >= getParameterCount()) {
   			throw new IllegalArgumentException("bad parameter index in Gaussian fit: " + index);
   		}
   		
   		if (includeBaseline && (index == getParameterCount() - 1)) {
   			return "B";
   		}
   		int component = index % 3;
   		int sub = index / 3;

   		return paramNames[component] + subArray[sub];
   	}

   	@Override
   	public IFitStringGetter getStringGetter() {
   		return this;
   	}
   	
 	//--------------------- test main -----------------------
 	public static void main(String arg[]) {
		final double[] mu = {1.2, 3.3};
 		final double[] sigma = {0.3, 0.2};
 		final double[] A = {2.0, 1.1};
 		final double B = 0.5;
 		int n = 100;
 		int numGauss = A.length;

 		Evaluator eval = (double x) -> {
 			double sum = 0;
 			for (int k = 0; k < numGauss; k++) {
 				double dx = x - mu[k];
 				double z = dx / sigma[k];
 				sum += A[k] * Math.exp(-0.5 * z * z);
 			}
 			sum += B;
 			return sum;
 		};
 		
 		FitVectors testData = FitVectors.testData(eval, -1.0, 7.0, n, 4.0, 5.0);
 		MultiGaussianFitter fitter = new MultiGaussianFitter(numGauss, true);
 		FitResult result = fitter.fit(testData.x, testData.y, testData.w);
 		System.out.println("True parameters: ");
			System.out.print(" A = " + Arrays.toString(A) + "\n");
			System.out.print(" mu = " + Arrays.toString(mu) + "\n");
			System.out.print(" sigma = " + Arrays.toString(sigma) + "\n");
			System.out.println(" B = " + B);
		System.out.println(result);
 		
 		//print data and fit values
		for (int i = 0; i < (n-1); i+=10) {
			double xv = testData.x[i];
			double yv = result.evaluator.value(xv);
			System.out.printf("x=%.3f fit y=%.3f data y=%.3f%n", xv, yv, testData.y[i]);
		}

 	}
}
