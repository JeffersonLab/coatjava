package cnuphys.splot.fit.apache;

import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.optim.SimpleVectorValueChecker;
import org.apache.commons.math3.fitting.leastsquares.ParameterValidator;
import org.apache.commons.math3.special.Erf;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;

import cnuphys.splot.fit.IPlottableFunction;
import cnuphys.splot.fit.IValueGetter;
import cnuphys.splot.fit.PlottableFunction;

/**
 * Nonlinear least-squares fitter for models of the form:
 *
 * <pre>
 *   y(x) = A * erf( (x - x0) / sigma ) + B
 *   y(x) = A * erfc((x - x0) / sigma ) + B
 * </pre>
 *
 * Parameters:
 * <ul>
 * <li>A : amplitude</li>
 * <li>x0 : center (transition location)</li>
 * <li>sigma : width/scale (must be &gt; 0)</li>
 * <li>B : vertical offset</li>
 * </ul>
 *
 * Features:
 * <ul>
 * <li>Analytic Jacobian (fast, robust convergence)</li>
 * <li>Optional weights or y-uncertainties</li>
 * <li>Optional bounds via a parameter validator</li>
 * <li>Automatic initial guess heuristics</li>
 * </ul>
 */
public final class ErfErfcFitter {

	/** Which function to fit: erf or erfc. */
	public enum Kind {
		ERF, ERFC
	}

	/** Parameter indices (for readability). */
	public static final int IDX_A = 0;
	public static final int IDX_X0 = 1;
	public static final int IDX_SIGMA = 2;
	public static final int IDX_B = 3;

	/** Default minimum allowed sigma to avoid division by zero. */
	public static final double DEFAULT_MIN_SIGMA = 1e-12;

	private final Kind kind;
	private final LeastSquaresOptimizer optimizer;
	

	/**
	 * Create a fitter using Levenberg-Marquardt (recommended).
	 *
	 * @param kind ERF or ERFC.
	 */
	public ErfErfcFitter(Kind kind) {
		this(kind, new LevenbergMarquardtOptimizer());
	}

	/**
	 * Create a fitter with a custom optimizer.
	 *
	 * @param kind      ERF or ERFC.
	 * @param optimizer optimizer instance (e.g. LevenbergMarquardtOptimizer).
	 */
	public ErfErfcFitter(Kind kind, LeastSquaresOptimizer optimizer) {
		this.kind = Objects.requireNonNull(kind, "kind");
		this.optimizer = Objects.requireNonNull(optimizer, "optimizer");
	}

	/**
	 * Fit the data using an automatic initial guess and unit weights.
	 *
	 * @param x x data (length n).
	 * @param y y data (length n).
	 * @return fit result.
	 */
	public FitResult fit(double[] x, double[] y) {
		return fit(x, y, null, null, null);
	}

	/**
	 * Fit the data with optional weights, bounds, and initial guess.
	 *
	 * @param x            x data (length n).
	 * @param y            y data (length n).
	 * @param weights      optional weights (length n). If null, unit weights are
	 *                     used. Weights should be proportional to 1/variance. (I.e.
	 *                     larger weight = more trusted.)
	 * @param bounds       optional bounds for parameters (A, x0, sigma, B). If
	 *                     null, only enforces sigma &gt;= minSigma.
	 * @param initialGuess optional initial parameter guess (length 4). If null, a
	 *                     heuristic guess is computed.
	 * @return fit result.
	 */
	public FitResult fit(double[] x, double[] y, double[] weights, ParameterBounds bounds, double[] initialGuess) {

		validateXY(x, y);
		int n = x.length;

		if (weights != null && weights.length != n) {
			throw new IllegalArgumentException("weights length must match x/y length");
		}
		if (initialGuess != null && initialGuess.length != 4) {
			throw new IllegalArgumentException("initialGuess must have length 4: [A, x0, sigma, B]");
		}

		final double[] start = (initialGuess != null) ? initialGuess.clone() : InitialGuess.guess(kind, x, y);

		// Always enforce sigma >= minSigma; optionally enforce additional bounds.
		final ParameterValidator validator = new BoundValidator(bounds != null ? bounds : ParameterBounds.unbounded(),
				DEFAULT_MIN_SIGMA);

		final MultivariateJacobianFunction model = new ErfErfcModel(kind, x);

		final LeastSquaresBuilder b = new LeastSquaresBuilder().start(start).model(model).target(y)
				.parameterValidator(validator).maxIterations(2000).maxEvaluations(2000)
				// Helps avoid "false convergence" issues in practice.
				.checkerPair(new SimpleVectorValueChecker(1e-12, 1e-12));

		if (weights != null) {
			// Diagonal weights matrix
			b.weight(new DiagonalMatrix(weights));
		}

		LeastSquaresProblem problem = b.build();
		LeastSquaresOptimizer.Optimum opt = optimizer.optimize(problem);

		double[] p = opt.getPoint().toArray();
		RealMatrix cov;
		try {
			cov = opt.getCovariances(1e-14);
		} catch (Exception e) {
			// Some fits (esp. ill-conditioned) can fail covariance computation.
			cov = null;
		}

		// Commons Math 3.6.1: Optimum has getCost() and getRMS(), but not
		// getChiSquare().
		// cost = sqrt(sum r_i^2) where r_i are the (possibly weighted) residuals.
		// Therefore chiSquare = sum r_i^2 = cost^2.
		final double cost = opt.getCost();
		final double chiSq = cost * cost;

		// Degrees of freedom (n - number_of_parameters). Clamp to at least 1 to avoid
		// divide-by-zero.
		final int nPts = n;
		final int nPar = 4;
		final int dof = Math.max(1, nPts - nPar);

		// Reduced chi-square is chiSquare / dof.
		// Note: if you supplied weights as 1/sigmaY^2, then reduced chi-square ~ 1
		// indicates a statistically
		// consistent fit under the assumed uncertainties.
		final double chiSqReduced = chiSq / dof;

		final double rms = opt.getRMS();

		return new FitResult(kind.name(), // model
				p, // params
				cov, // covariance
				cost, chiSq, dof, chiSqReduced, rms, opt.getIterations(), opt.getEvaluations());
	}

	/**
	 * Convenience: build weights from y-uncertainties (sigmaY). weights[i] = 1 /
	 * (sigmaY[i]^2).
	 *
	 * @param sigmaY measurement standard deviations (length n).
	 * @return weights array (length n).
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

	private static void validateXY(double[] x, double[] y) {
		Objects.requireNonNull(x, "x");
		Objects.requireNonNull(y, "y");
		if (x.length != y.length) {
			throw new IllegalArgumentException("x and y must have the same length");
		}
		if (x.length < 4) {
			throw new IllegalArgumentException("need at least 4 points to fit [A, x0, sigma, B]");
		}
		for (int i = 0; i < x.length; i++) {
			if (!Double.isFinite(x[i]) || !Double.isFinite(y[i])) {
				throw new IllegalArgumentException("x/y must be finite; bad value at index " + i);
			}
		}
	}

	/**
	 * Least-squares model for erf/erfc with analytic Jacobian. Model: y = A * f(z)
	 * + B, z = (x - x0) / sigma, f = erf or erfc.
	 */
	private static final class ErfErfcModel implements MultivariateJacobianFunction {
		private final Kind kind;
		private final double[] x;

		ErfErfcModel(Kind kind, double[] x) {
			this.kind = kind;
			this.x = x.clone();
		}

		@Override
		public Pair<RealVector, RealMatrix> value(final RealVector point) {
			double A = point.getEntry(IDX_A);
			double x0 = point.getEntry(IDX_X0);
			double sigma = point.getEntry(IDX_SIGMA);
			double B = point.getEntry(IDX_B);

			int n = x.length;
			double[] values = new double[n];
			double[][] jac = new double[n][4];

			final double invS = 1.0 / sigma;

			for (int i = 0; i < n; i++) {
				double z = (x[i] - x0) * invS;

				double f = (kind == Kind.ERF) ? Erf.erf(z) : Erf.erfc(z);

				// d/dz erf(z) = 2/sqrt(pi) * exp(-z^2)
				// d/dz erfc(z) = -2/sqrt(pi) * exp(-z^2)
				double exp = FastMath.exp(-z * z);
				double dfdz = (2.0 / FastMath.sqrt(FastMath.PI)) * exp;
				if (kind == Kind.ERFC) {
					dfdz = -dfdz;
				}

				values[i] = A * f + B;

				// Partial derivatives:
				// ∂y/∂A = f
				// ∂y/∂B = 1
				// z = (x - x0)/sigma
				// ∂z/∂x0 = -1/sigma
				// ∂z/∂sigma = -(x-x0)/sigma^2 = -z/sigma
				// ∂y/∂x0 = A * dfdz * ∂z/∂x0
				// ∂y/∂sigma = A * dfdz * ∂z/∂sigma
				jac[i][IDX_A] = f;
				jac[i][IDX_X0] = A * dfdz * (-invS);
				jac[i][IDX_SIGMA] = A * dfdz * (-z * invS);
				jac[i][IDX_B] = 1.0;
			}

			return new Pair<>(new ArrayRealVector(values, false), new Array2DRowRealMatrix(jac, false));
		}
	}

	/**
	 * Enforces parameter bounds and sigma >= minSigma. Uses simple clamping (a
	 * pragmatic approach that works well for LM).
	 */
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
			// sigma must be positive
			p[IDX_SIGMA] = Math.max(p[IDX_SIGMA], minSigma);

			// Optional bounds
			for (int i = 0; i < 4; i++) {
				double lo = bounds.lower[i];
				double hi = bounds.upper[i];
				if (Double.isFinite(lo))
					p[i] = Math.max(p[i], lo);
				if (Double.isFinite(hi))
					p[i] = Math.min(p[i], hi);
			}
			return new ArrayRealVector(p, false);
		}
	}

	/**
	 * Parameter bounds for [A, x0, sigma, B]. Use +/-infinity for "no bound".
	 */
	public static final class ParameterBounds {
		final double[] lower = new double[4];
		final double[] upper = new double[4];

		private ParameterBounds(double[] lower, double[] upper) {
			System.arraycopy(lower, 0, this.lower, 0, 4);
			System.arraycopy(upper, 0, this.upper, 0, 4);
		}

		public static ParameterBounds unbounded() {
			double[] lo = { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
					Double.NEGATIVE_INFINITY };
			double[] hi = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
					Double.POSITIVE_INFINITY };
			return new ParameterBounds(lo, hi);
		}

		public static Builder builder() {
			return new Builder();
		}

		public static final class Builder {
			private final double[] lo = { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
					Double.NEGATIVE_INFINITY };
			private final double[] hi = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
					Double.POSITIVE_INFINITY };

			public Builder a(double lower, double upper) {
				lo[IDX_A] = lower;
				hi[IDX_A] = upper;
				return this;
			}

			public Builder x0(double lower, double upper) {
				lo[IDX_X0] = lower;
				hi[IDX_X0] = upper;
				return this;
			}

			public Builder sigma(double lower, double upper) {
				lo[IDX_SIGMA] = lower;
				hi[IDX_SIGMA] = upper;
				return this;
			}

			public Builder b(double lower, double upper) {
				lo[IDX_B] = lower;
				hi[IDX_B] = upper;
				return this;
			}

			public ParameterBounds build() {
				return new ParameterBounds(lo, hi);
			}
		}
	}

	/**
	 * Heuristic initial guesses for [A, x0, sigma, B]. Designed to work well on
	 * typical "S-curve" erf and "step-down" erfc use cases.
	 */
	public static final class InitialGuess {

		private static final double ERF_AT_1 = 0.8427007929497149; // erf(1)
		private static final double TINY = 1e-300;

		private InitialGuess() {
		}

		public static double[] guess(Kind kind, double[] x, double[] y) {
			int n = x.length;

			// Sort-by-x view (we don't want to reorder caller arrays, so build index order)
			Integer[] idx = new Integer[n];
			for (int i = 0; i < n; i++)
				idx[i] = i;
			Arrays.sort(idx, (i, j) -> Double.compare(x[i], x[j]));

			double xMin = x[idx[0]];
			double xMax = x[idx[n - 1]];
			double yMin = y[idx[0]];
			double yMax = y[idx[n - 1]];

			// Use robust-ish extremes from endpoints.
			// If the curve is noisy, user can pass an explicit initial guess.
			boolean increasing = yMax >= yMin;

			double A, B;

			if (kind == Kind.ERF) {
				// erf goes (-1..+1). With y = A*erf(z) + B, endpoints approximately:
				// low ~ -A + B, high ~ +A + B (if increasing with x)
				A = 0.5 * (yMax - yMin);
				B = 0.5 * (yMax + yMin);
				// If decreasing, A will be negative already (good).
			} else {
				// erfc goes (2..0) as z increases.
				// With y = A*erfc(z) + B:
				// high-x side tends to B, low-x side tends to 2A + B (if decreasing with x).
				// Use endpoints and decide which is the "B side" by looking at direction.
				if (increasing) {
					// if y increases with x, then the "B side" is at low x (erfc ~ 0) and high x is
					// ~ 2A+B
					B = yMin;
					A = 0.5 * (yMax - yMin);
				} else {
					// decreasing: high x is ~ B, low x is ~ 2A + B
					B = yMax;
					A = 0.5 * (yMin - yMax);
				}
				// A ends up positive in the typical decreasing erfc step; can be negative too.
			}

			// Estimate x0 as x where y is near the "mid" value.
			// For erf: midpoint is B (since erf(0)=0). For erfc: midpoint is A + B (since
			// erfc(0)=1).
			double yMid = (kind == Kind.ERF) ? B : (A + B);

			double x0 = nearestXToY(idx, x, y, yMid);

			// Estimate sigma from where z=±1. For erf:
			// y = B ± A*erf(1) corresponds to x = x0 ± sigma.
			// For erfc, use the same idea with f(±1) = erfc(±1) = 1 ∓ erf(1).
			double sigma = estimateSigma(kind, idx, x, y, A, B, x0, xMin, xMax);

			// Final sanity
			if (!(sigma > 0.0) || !Double.isFinite(sigma)) {
				sigma = Math.max((xMax - xMin) / 10.0, DEFAULT_MIN_SIGMA);
			}

			return new double[] { A, x0, sigma, B };
		}

		private static double nearestXToY(Integer[] idx, double[] x, double[] y, double targetY) {
			double bestDx = Double.POSITIVE_INFINITY;
			double bestX = x[idx[0]];
			for (int k = 0; k < idx.length; k++) {
				int i = idx[k];
				double dy = Math.abs(y[i] - targetY);
				if (dy < bestDx) {
					bestDx = dy;
					bestX = x[i];
				}
			}
			return bestX;
		}

		private static double estimateSigma(Kind kind, Integer[] idx, double[] x, double[] y, double A, double B,
				double x0, double xMin, double xMax) {
			// Build target y-values corresponding to z = +1 and z = -1.
			// For erf: f(±1) = ±erf(1)
			// For erfc: f(z) = erfc(z). erfc(1) = 1 - erf(1), erfc(-1) = 1 + erf(1).
			double fPlus1, fMinus1;
			if (kind == Kind.ERF) {
				fPlus1 = +ERF_AT_1;
				fMinus1 = -ERF_AT_1;
			} else {
				fPlus1 = 1.0 - ERF_AT_1; // erfc(1)
				fMinus1 = 1.0 + ERF_AT_1; // erfc(-1)
			}

			double yAtPlus1 = A * fPlus1 + B;
			double yAtMinus1 = A * fMinus1 + B;

			// Find closest x values to those y targets on each side of x0 (if possible).
			double xLow = Double.NaN;
			double xHigh = Double.NaN;

			double bestLow = Double.POSITIVE_INFINITY;
			double bestHigh = Double.POSITIVE_INFINITY;

			for (int k = 0; k < idx.length; k++) {
				int i = idx[k];
				double xi = x[i];
				double yi = y[i];

				if (xi <= x0) {
					double d = Math.abs(yi - yAtMinus1);
					if (d < bestLow) {
						bestLow = d;
						xLow = xi;
					}
				}
				if (xi >= x0) {
					double d = Math.abs(yi - yAtPlus1);
					if (d < bestHigh) {
						bestHigh = d;
						xHigh = xi;
					}
				}
			}

			if (Double.isFinite(xLow) && Double.isFinite(xHigh) && xHigh > xLow) {
				return Math.max((xHigh - xLow) / 2.0, DEFAULT_MIN_SIGMA);
			}

			// Fallback: use x-span scaled by amplitude; if A is tiny, just use 1/10 range.
			double span = xMax - xMin;
			double aScale = Math.max(Math.abs(A), TINY);
			double fallback = span / (6.0 + Math.log10(1.0 + aScale)); // gentle scaling
			return Math.max(fallback, DEFAULT_MIN_SIGMA);
		}
	}

	/**
	 * Create an {@link IValueGetter} that evaluates the fitted erf/erfc curve.
	 * <p>
	 * The returned function is safe for plotting: it clamps sigma to
	 * {@link #DEFAULT_MIN_SIGMA}.
	 */
	public IValueGetter asValueGetter(final FitResult fit) {
		Objects.requireNonNull(fit, "fit");

		// Copy params once so callers can mutate fit.params elsewhere without affecting
		// the plotted curve.
		final double A = fit.params[IDX_A];
		final double x0 = fit.params[IDX_X0];
		final double s0 = fit.params[IDX_SIGMA];
		final double B = fit.params[IDX_B];

		final double sigma = Math.max(Math.abs(s0), DEFAULT_MIN_SIGMA);
		final Kind k = this.kind; // fitter already knows whether it's ERF or ERFC

		return (double x) -> {
			double z = (x - x0) / sigma;
			double f = (k == Kind.ERF) ? Erf.erf(z) : Erf.erfc(z);
			return A * f + B;
		};
	}

	/**
	 * Create an {@link IPlottableFunction} that evaluates the fitted erf/erfc
	 * curve.
	 * <p>
	 * The returned function is safe for plotting: it clamps sigma to
	 * {@link #DEFAULT_MIN_SIGMA}.
	 */
	public IPlottableFunction asPlottable(FitResult fit, double xmin, double xmax) {
		return new PlottableFunction(asValueGetter(fit), xmin, xmax);
	}

}
