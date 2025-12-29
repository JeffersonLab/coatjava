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
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Pair;

import cnuphys.splot.fit.IPlottableFunction;
import cnuphys.splot.fit.IValueGetter;
import cnuphys.splot.fit.PlottableFunction;

/**
 * Nonlinear least-squares fitter for a 4-parameter Gaussian:
 *
 * <pre>
 * y(x) = A * exp(-(x - mu) ^ 2 / (2 * sigma ^ 2)) + B
 * </pre>
 *
 * Parameters:
 * <ul>
 * <li>A : amplitude</li>
 * <li>mu : mean/center</li>
 * <li>sigma : width (must be &gt; 0)</li>
 * <li>B : vertical offset</li>
 * </ul>
 */
public final class GaussianFitter {

	/** Parameter indices. */
	public static final int IDX_A = 0;
	public static final int IDX_MU = 1;
	public static final int IDX_SIGMA = 2;
	public static final int IDX_B = 3;

	/** Default minimum allowed sigma to avoid division by zero. */
	public static final double DEFAULT_MIN_SIGMA = 1e-12;

	private final LeastSquaresOptimizer optimizer;

	/** Create a fitter using Levenberg-Marquardt (recommended). */
	public GaussianFitter() {
		this(new LevenbergMarquardtOptimizer());
	}

	public GaussianFitter(LeastSquaresOptimizer optimizer) {
		this.optimizer = Objects.requireNonNull(optimizer, "optimizer");
	}

	/** Fit with heuristic initial guess and unit weights. */
	public FitResult fit(double[] x, double[] y) {
		return fit(x, y, null, null, null);
	}

	/**
	 * Fit with optional weights, bounds, and initial guess.
	 *
	 * @param x            x data (length n).
	 * @param y            y data (length n).
	 * @param weights      optional weights (length n). If null, unit weights are
	 *                     used. Typically weights = 1/sigmaY^2.
	 * @param bounds       optional bounds for (A, mu, sigma, B). If null, unbounded
	 *                     except sigma clamp.
	 * @param initialGuess optional initial parameters [A, mu, sigma, B]. If null,
	 *                     uses heuristic guess.
	 * @return fit result.
	 */
	public FitResult fit(double[] x, double[] y, double[] weights, ParameterBounds bounds, double[] initialGuess) {

		validateXY(x, y);
		final int n = x.length;

		if (weights != null && weights.length != n) {
			throw new IllegalArgumentException("weights length must match x/y length");
		}
		if (initialGuess != null && initialGuess.length != 4) {
			throw new IllegalArgumentException("initialGuess must have length 4: [A, mu, sigma, B]");
		}

		final double[] start = (initialGuess != null) ? initialGuess.clone() : InitialGuess.guess(x, y);

		final ParameterValidator validator = new BoundValidator(bounds != null ? bounds : ParameterBounds.unbounded(),
				DEFAULT_MIN_SIGMA);

		final MultivariateJacobianFunction model = new GaussianModel(x);

		final LeastSquaresBuilder b = new LeastSquaresBuilder().start(start).model(model).target(y)
				.parameterValidator(validator).maxIterations(2000).maxEvaluations(2000)
				.checkerPair(new SimpleVectorValueChecker(1e-12, 1e-12));

		if (weights != null) {
			b.weight(new DiagonalMatrix(weights));
		}

		LeastSquaresProblem problem = b.build();
		LeastSquaresOptimizer.Optimum opt = optimizer.optimize(problem);

		final double[] p = opt.getPoint().toArray();

		RealMatrix cov;
		try {
			cov = opt.getCovariances(1e-14);
		} catch (Exception e) {
			cov = null;
		}

		// Commons Math 3.6.1: chiSquare = cost^2
		final double cost = opt.getCost();
		final double chiSq = cost * cost;

		final int nPar = 4;
		final int dof = Math.max(1, n - nPar);
		final double chiSqReduced = chiSq / dof;

		final double rms = opt.getRMS();

		return new FitResult("GAUSSIAN", p, cov, cost, chiSq, dof, chiSqReduced, rms, opt.getIterations(),
				opt.getEvaluations());
	}

	/**
	 * Convenience: build weights from y-uncertainties (sigmaY). weights[i] = 1 /
	 * (sigmaY[i]^2).
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
			throw new IllegalArgumentException("need at least 4 points to fit [A, mu, sigma, B]");
		}
		for (int i = 0; i < x.length; i++) {
			if (!Double.isFinite(x[i]) || !Double.isFinite(y[i])) {
				throw new IllegalArgumentException("x/y must be finite; bad value at index " + i);
			}
		}
	}

	/** Least-squares model for Gaussian with analytic Jacobian. */
	private static final class GaussianModel implements MultivariateJacobianFunction {
		private final double[] x;

		GaussianModel(double[] x) {
			this.x = x.clone();
		}

		@Override
		public Pair<RealVector, RealMatrix> value(final RealVector point) {
			final double A = point.getEntry(IDX_A);
			final double mu = point.getEntry(IDX_MU);
			final double sigma = point.getEntry(IDX_SIGMA);
			final double B = point.getEntry(IDX_B);

			final int n = x.length;
			final double[] values = new double[n];
			final double[][] jac = new double[n][4];

			final double s2 = sigma * sigma;

			for (int i = 0; i < n; i++) {
				final double dx = x[i] - mu;
				final double e = FastMath.exp(-(dx * dx) / (2.0 * s2));

				values[i] = A * e + B;

				// ∂y/∂A = e
				jac[i][IDX_A] = e;

				// ∂y/∂mu = A * e * (dx / sigma^2)
				jac[i][IDX_MU] = A * e * (dx / s2);

				// ∂y/∂sigma = A * e * (dx^2 / sigma^3)
				jac[i][IDX_SIGMA] = A * e * (dx * dx) / (sigma * s2);

				// ∂y/∂B = 1
				jac[i][IDX_B] = 1.0;
			}

			return new Pair<>(new ArrayRealVector(values, false), new Array2DRowRealMatrix(jac, false));
		}
	}

	/**
	 * Parameter bounds for [A, mu, sigma, B]. Use +/- infinity for "no bound".
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

			public Builder mu(double lower, double upper) {
				lo[IDX_MU] = lower;
				hi[IDX_MU] = upper;
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

			// sigma must be positive
			p[IDX_SIGMA] = Math.max(p[IDX_SIGMA], minSigma);

			// clamp other bounds (if finite)
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
     * Create an {@link IValueGetter} that evaluates the fitted erf/erfc curve.
     * <p>
     * The returned function is safe for plotting: it clamps sigma to {@link #DEFAULT_MIN_SIGMA}.
     */
 	public IValueGetter asValueGetter(final FitResult fit) {
		Objects.requireNonNull(fit, "fit");

		final double A = fit.params[IDX_A];
		final double mu = fit.params[IDX_MU];
		final double s0 = fit.params[IDX_SIGMA];
		final double B = fit.params[IDX_B];

		final double sigma = Math.max(Math.abs(s0), DEFAULT_MIN_SIGMA);
		final double twoS2 = 2.0 * sigma * sigma;

		return (double x) -> {
			double dx = x - mu;
			return A * Math.exp(-(dx * dx) / twoS2) + B;
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

	/** Heuristic initial guess for [A, mu, sigma, B]. */
	public static final class InitialGuess {

		private InitialGuess() {
		}

		public static double[] guess(double[] x, double[] y) {
			final int n = x.length;

			// Create index order sorted by x (do not reorder input arrays).
			Integer[] idx = new Integer[n];
			for (int i = 0; i < n; i++)
				idx[i] = i;
			Arrays.sort(idx, (i, j) -> Double.compare(x[i], x[j]));

			// Find global min/max y
			int iMin = idx[0], iMax = idx[0];
			for (int k = 1; k < n; k++) {
				int i = idx[k];
				if (y[i] < y[iMin])
					iMin = i;
				if (y[i] > y[iMax])
					iMax = i;
			}

			double yMin = y[iMin];
			double yMax = y[iMax];

			// Decide if we have a peak (positive A) or a dip (negative A)
			double yLeft = y[idx[0]];
			double yRight = y[idx[n - 1]];

			boolean looksLikeDip = (Math.abs(yLeft - yMax) + Math.abs(yRight - yMax)) < (Math.abs(yLeft - yMin)
					+ Math.abs(yRight - yMin));

			double A, B, mu;
			if (looksLikeDip) {
				// notch: baseline ~ max, amplitude negative
				B = yMax;
				A = yMin - yMax; // negative
				mu = x[iMin];
			} else {
				// peak: baseline ~ min, amplitude positive
				B = yMin;
				A = yMax - yMin; // positive
				mu = x[iMax];
			}

			// Estimate sigma using half-amplitude points (approx FWHM -> sigma)
			double half = B + 0.5 * A;

			double xL = Double.NaN, xR = Double.NaN;
			double bestL = Double.POSITIVE_INFINITY, bestR = Double.POSITIVE_INFINITY;

			for (int k = 0; k < n; k++) {
				int i = idx[k];
				double xi = x[i];
				double di = Math.abs(y[i] - half);
				if (xi <= mu && di < bestL) {
					bestL = di;
					xL = xi;
				}
				if (xi >= mu && di < bestR) {
					bestR = di;
					xR = xi;
				}
			}

			double sigma;
			if (Double.isFinite(xL) && Double.isFinite(xR) && xR > xL) {
				double fwhm = (xR - xL);
				// FWHM = 2*sqrt(2*ln2)*sigma
				sigma = fwhm / (2.0 * Math.sqrt(2.0 * Math.log(2.0)));
			} else {
				double xSpan = x[idx[n - 1]] - x[idx[0]];
				sigma = Math.max(xSpan / 10.0, DEFAULT_MIN_SIGMA);
			}

			if (!(sigma > 0.0) || !Double.isFinite(sigma)) {
				sigma = DEFAULT_MIN_SIGMA;
			}

			return new double[] { A, mu, sigma, B };
		}
	}
}
