package cnuphys.splot.fit;

import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.fitting.leastsquares.ParameterValidator;
import org.apache.commons.math3.special.Erf;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Pair;

import cnuphys.splot.pdata.FitVectors;

/**
 * Nonlinear least-squares fitter for scaled/shifted {@code erf} or {@code erfc}:
 *
 * <pre>
 *   y(x) = A * erf((x - x0)/sigma)  + B
 *   y(x) = A * erfc((x - x0)/sigma) + B
 * </pre>
 *
 * <h3>Parameter ordering</h3>
 * <ul>
 *   <li>{@code params[0] = A}</li>
 *   <li>{@code params[1] = x0}</li>
 *   <li>{@code params[2] = sigma}</li>
 *   <li>{@code params[3] = B}</li>
 * </ul>
 *
 * <p>Enforces {@code sigma >= DEFAULT_MIN_SIGMA} by default.</p>
 */
public final class ErfErfcFitter extends ALeastSquaresFitter {

    /** Which function to fit. */
    public enum Kind { ERF, ERFC }

    /** Parameter indices. */
    public static final int IDX_A = 0;
    public static final int IDX_X0 = 1;
    public static final int IDX_SIGMA = 2;
    public static final int IDX_B = 3;
    
    /** Base Parameter names. */
    public static final String[] paramNames = { "A", "x" + SUB0, "σ", "B" };


    /** Default minimum allowed sigma. */
    public static final double DEFAULT_MIN_SIGMA = 1e-12;

    private final Kind kind;

	 /**
	  * Create an Erf/Erfc fitter with default optimizer (Levenberg-Marquardt).
	  *
	  * @param kind which function to fit
	  */

    public ErfErfcFitter(Kind kind) {
        this(kind, new LevenbergMarquardtOptimizer());
    }

    public ErfErfcFitter(Kind kind, LeastSquaresOptimizer optimizer) {
        super(Objects.requireNonNull(optimizer, "optimizer"), (x, y, w) -> InitialGuess.guess(kind, x, y));
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    protected int getParameterCount() {
        return 4;
    }

    @Override
    protected MultivariateJacobianFunction model(double[] x) {
        return new ErfErfcModel(kind, x);
    }

    @Override
    protected double[] defaultInitialGuess(double[] x, double[] y, double[] weights) {
        return InitialGuess.guess(kind, x, y);
    }

    @Override
    protected ParameterValidator defaultValidator() {
        double[] lo = { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, DEFAULT_MIN_SIGMA, Double.NEGATIVE_INFINITY };
        double[] hi = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
        return clampingValidator(lo, hi);
    }

    /**
     * Expert overload: fit with optional weights, optional bounds, and optional initial guess.
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
            lo[IDX_SIGMA] = Math.max(lo[IDX_SIGMA], DEFAULT_MIN_SIGMA);
        }

        return super.fit(x, y, weights, initialGuess, clampingValidator(lo, hi));
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
        return (double x) -> {
            double A = p[IDX_A];
            double x0 = p[IDX_X0];
            double sigma = p[IDX_SIGMA];
            double B = p[IDX_B];
            double z = (x - x0) / sigma;

            double f = (kind == Kind.ERF) ? Erf.erf(z) : Erf.erfc(z);
            return A * f + B;
        };
    }

    /** Analytic model + Jacobian for erf/erfc. */
    private static final class ErfErfcModel implements MultivariateJacobianFunction {
        private final Kind kind;
        private final double[] x;

        ErfErfcModel(Kind kind, double[] x) {
            this.kind = kind;
            this.x = x.clone();
        }

        @Override
        public Pair<RealVector, RealMatrix> value(final RealVector point) {
            final double[] p = point.toArray();
            final double A = p[IDX_A];
            final double x0 = p[IDX_X0];
            final double sigma = p[IDX_SIGMA];
            final double B = p[IDX_B];

            final int n = x.length;
            final double[] values = new double[n];
            final double[][] jac = new double[n][4];

            final double invSigma = 1.0 / sigma;
            final double invSigma2 = invSigma * invSigma;
            final double twoOverSqrtPi = 2.0 / Math.sqrt(Math.PI);

            for (int i = 0; i < n; i++) {
                double z = (x[i] - x0) * invSigma;

                double f = (kind == Kind.ERF) ? Erf.erf(z) : Erf.erfc(z);
                double exp = Math.exp(-(z * z));

                values[i] = A * f + B;

                // df/dz for erf is 2/sqrt(pi) * exp(-z^2)
                // df/dz for erfc is -2/sqrt(pi) * exp(-z^2)
                double dfdz = (kind == Kind.ERF ? +1.0 : -1.0) * twoOverSqrtPi * exp;

                // dy/dA = f
                jac[i][IDX_A] = f;
                // dy/dx0 = A * df/dz * dz/dx0 = A * dfdz * (-1/sigma)
                jac[i][IDX_X0] = A * dfdz * (-invSigma);
                // dy/dsigma = A * df/dz * dz/dsigma where z=(x-x0)/sigma => dz/dsigma = -(x-x0)/sigma^2 = -z/sigma
                jac[i][IDX_SIGMA] = A * dfdz * (-(x[i] - x0) * invSigma2);
                // dy/dB = 1
                jac[i][IDX_B] = 1.0;
            }

            return new Pair<>(
                    new ArrayRealVector(values, false),
                    new Array2DRowRealMatrix(jac, false)
            );
        }
    }

    /** Bounds container for erf/erfc parameters. */
    public static final class ParameterBounds {
        private final double[] lower = new double[4];
        private final double[] upper = new double[4];

        private ParameterBounds(double[] lower, double[] upper) {
            System.arraycopy(lower, 0, this.lower, 0, 4);
            System.arraycopy(upper, 0, this.upper, 0, 4);
        }

        public double[] lower() { return lower.clone(); }
        public double[] upper() { return upper.clone(); }

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

            public ParameterBounds build() { return new ParameterBounds(lo, hi); }
        }
    }

    /** Heuristic initial guess. */
    static final class InitialGuess {
        private InitialGuess() {}

        public static double[] guess(Kind kind, double[] x, double[] y) {
            final int n = x.length;
            if (n == 0) {
                return new double[] { 1, 0, 1, 0 };
            }

            // Sort by x to interpret endpoints robustly.
            Integer[] idx = new Integer[n];
            for (int i = 0; i < n; i++) idx[i] = i;
            Arrays.sort(idx, (i, j) -> Double.compare(x[i], x[j]));

            double yLeft = y[idx[0]];
            double yRight = y[idx[n - 1]];

            // For erf: transitions from low to high (or high to low), similar for erfc but reversed.
            double B = 0.5 * (yLeft + yRight);
            double A = 0.5 * (yRight - yLeft);

            if (kind == Kind.ERFC) {
                // erfc decreases for increasing z; flip sign convention
                A = -A;
            }

            // x0: approximate midpoint of transition from yLeft to yRight
            double target = B; // midpoint
            int best = idx[0];
            double bestErr = Double.POSITIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                double err = Math.abs(y[i] - target);
                if (err < bestErr) {
                    bestErr = err;
                    best = i;
                }
            }
            double x0 = x[best];

            // sigma: rough scale from x-range
            double xmin = x[idx[0]], xmax = x[idx[n - 1]];
            double sigma = 0.1 * Math.max(1e-12, (xmax - xmin));
            sigma = Math.max(DEFAULT_MIN_SIGMA, sigma);

            return new double[] { A, x0, sigma, B };
        }
    }
    
    //------- descriptive string section -----------------
 	@Override
 	public String modelName() {
 		return kind == Kind.ERF ? "erf" 
 				: "erfc";
  	}

 	@Override
 	public String functionForm() {
 		if (kind == Kind.ERF) {
 			return "y(x) = A * erf[(x - x0)/σ] + B";
 		} else {
 			return "y(x) = A * erfc[(x - x0)/σ] + B";
 		}
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
 			throw new IllegalArgumentException("bad parameter index in erf/erfc fit: " + index);
 		}

 		return paramNames[index];
 	}

 	@Override
 	public IFitStringGetter getStringGetter() {
 		return this;
 	}
 	
 	//------------------ test methods -----------------------
 	
 	// Test the Erf fitter
 	private static void testErf() {
		double A = 2.0;
		double x0 = 1.0;
		double sigma = 0.5;
		double B = 0.1;
		int n = 100;

		Evaluator erfEval = (double x) -> {
			double z = (x - x0) / sigma;
			return A * Erf.erf(z) + B;
		};

		FitVectors testData = FitVectors.testData(erfEval, -4.0, 4.0, n, 3.0, 3.0);
		ErfErfcFitter fitter = new ErfErfcFitter(Kind.ERF);
		FitResult result = fitter.fit(testData.x, testData.y, testData.w);
		System.out.println("\n===== Erf Fit Test  =====");
		System.out.println("True parameters: ");
		System.out.print(" A = " + A);
		System.out.print(" x0 = " + x0);
		System.out.print(" sigma = " + sigma);
		System.out.println(" B = " + B);
		System.out.println(result);

		// print data and fit values
		for (int i = 0; i < (n - 1); i += 10) {
			double xv = testData.x[i];
			double yv = result.evaluator.value(xv);
			System.out.printf("x=%.3f fit y=%.3f data y=%.3f%n", xv, yv, testData.y[i]);
		}
	}
 	
 	// Test the Erfc fitter
 	public static void testErfc() {
		double A = 2.0;
 		double x0 = 1.0;
 		double sigma = 0.5;
 		double B = 0.1;
 		int n = 100;
 		
 		Evaluator erfcEval = (double x) -> {
 			double z = (x - x0) / sigma;
 			return A * Erf.erfc(z) + B;
 		};
 		
 		FitVectors testData = FitVectors.testData(erfcEval, -4.0, 4.0, n, 3.0, 3.0);
 		ErfErfcFitter fitter = new ErfErfcFitter(Kind.ERFC);
 		FitResult result = fitter.fit(testData.x, testData.y, testData.w);
		System.out.println("\n===== Erfc Fit Test  =====");
		System.out.println("True parameters: ");
			System.out.print(" A = " + A);
			System.out.print(" x0 = " + x0);
			System.out.print(" sigma = " + sigma);
			System.out.println(" B = " + B);
		System.out.println(result);

		// print data and fit values
		for (int i = 0; i < (n - 1); i += 10) {
			double xv = testData.x[i];
			double yv = result.evaluator.value(xv);
			System.out.printf("x=%.3f fit y=%.3f data y=%.3f%n", xv, yv, testData.y[i]);
		}
 	}
 	//------------------ test main -----------------------
 	public static void main(String[] args) {
 		testErf();
 		testErfc();
 	}
 
}
