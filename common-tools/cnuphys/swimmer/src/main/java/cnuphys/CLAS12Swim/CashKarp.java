package cnuphys.CLAS12Swim;

/**
 * This class implements the Cash-Karp method for solving ordinary differential
 * equation.
 */
public class CashKarp {

	/**
	 * Solves an ordinary differential equation (ODE) using the Cash-Karp method.
	 *
	 * @param ode       The ODE to solve.
	 * @param y0        The initial state vector.
	 * @param t0        The initial time.
	 * @param t1        The final time.
	 * @param h         The initial step size.
	 * @param tolerance The desired accuracy. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 * @param minH      The minimum step size to prevent an infinite loop.
	 * @param maxH      The maximum step size changed when near boundary, but
	 *                  default is usually infinity.
	 * @param listener  An listener that will be called after each step.
	 */
	public static void solve(ODE ode, double[] y0, double t0, double t1, double h, double tolerance, double minH,
			double maxH, ODEStepListener listener) {
		// Cash-Karp coefficients
		final double[] c = { 0, 1.0 / 5, 3.0 / 10, 3.0 / 5, 1, 7.0 / 8 };
		final double[][] a = { {}, { 1.0 / 5 }, { 3.0 / 40, 9.0 / 40 }, { 3.0 / 10, -9.0 / 10, 6.0 / 5 },
				{ -11.0 / 54, 5.0 / 2, -70.0 / 27, 35.0 / 27 },
				{ 1631.0 / 55296, 175.0 / 512, 575.0 / 13824, 44275.0 / 110592, 253.0 / 4096 } };
		final double[] b4 = { 2825.0 / 27648, 0, 18575.0 / 48384, 13525.0 / 55296, 277.0 / 14336, 1.0 / 4 }; // 4th
																												// order
																												// coefficients
		final double[] b5 = { 37.0 / 378, 0, 250.0 / 621, 125.0 / 594, 0, 512.0 / 1771 }; // 5th order coefficients

		int n = y0.length;
		double[] k1, k2, k3, k4, k5, k6, yTemp, yTemp4, error;

		double t = t0;
		double[] y = y0.clone();

		while (t < t1) {
			if (t + h > t1) {
				h = t1 - t;
			}

			boolean acceptStep = false;
			while (!acceptStep) {
				k1 = ode.getDerivatives(t, y);
				k2 = ode.getDerivatives(t + c[1] * h, addVectors(y, scalarMultiply(k1, a[1][0] * h)));
				k3 = ode.getDerivatives(t + c[2] * h,
						addVectors(y, scalarMultiply(k1, a[2][0] * h), scalarMultiply(k2, a[2][1] * h)));
				k4 = ode.getDerivatives(t + c[3] * h, addVectors(y, scalarMultiply(k1, a[3][0] * h),
						scalarMultiply(k2, a[3][1] * h), scalarMultiply(k3, a[3][2] * h)));
				k5 = ode.getDerivatives(t + c[4] * h,
						addVectors(y, scalarMultiply(k1, a[4][0] * h), scalarMultiply(k2, a[4][1] * h),
								scalarMultiply(k3, a[4][2] * h), scalarMultiply(k4, a[4][3] * h)));
				k6 = ode.getDerivatives(t + c[5] * h,
						addVectors(y, scalarMultiply(k1, a[5][0] * h), scalarMultiply(k2, a[5][1] * h),
								scalarMultiply(k3, a[5][2] * h), scalarMultiply(k4, a[5][3] * h),
								scalarMultiply(k5, a[5][4] * h)));

				yTemp = y.clone();
				yTemp4 = y.clone();
				error = new double[n];

				for (int i = 0; i < n; i++) {
					yTemp[i] += h * (b5[0] * k1[i] + b5[1] * k2[i] + b5[2] * k3[i] + b5[3] * k4[i] + b5[4] * k5[i]
							+ b5[5] * k6[i]);
					yTemp4[i] += h * (b4[0] * k1[i] + b4[1] * k2[i] + b4[2] * k3[i] + b4[3] * k4[i] + b4[4] * k5[i]
							+ b4[5] * k6[i]);
					error[i] = Math.abs(yTemp[i] - yTemp4[i]);
				}

				double maxError = getMax(error);
				if (maxError < tolerance) {
					acceptStep = true;
					if (!listener.newStep(t + h, yTemp)) {
						return; // Stop the integration if listener returns false
					}
					y = yTemp;
					t += h;
				}

				h = adjustStepSize(h, maxError, tolerance, minH, maxH);
			}
		}
	}

	// Helper method to get the maximum value in an array
	private static double getMax(double[] array) {
		double max = array[0];
		for (double v : array) {
			if (v > max) {
				max = v;
			}
		}
		return max;
	}

	// Helper method to adjust the step size based on the error
	private static double adjustStepSize(double h, double error, double tolerance, double minH, double maxH) {
		double safetyFactor = 0.9; // Safety factor to reduce the step size slightly
		double errorRatio = error / tolerance;

		// Avoid division by zero in case of very small error
		if (errorRatio == 0) {
			errorRatio = 1e-6;
		}

		// Calculate scaling factor for step size adjustment
		// The exponent -1/4 is typically used for a 4th order method
		double scale = safetyFactor * Math.pow(errorRatio, -0.25);

		// Limit the scaling to prevent excessively large or small step sizes
		scale = Math.max(1.0 / 10.0, Math.min(5.0, scale));

		// Calculate new step size and ensure it's not smaller than the minimum
		double newH = h * scale;

		return Math.min(maxH, Math.max(newH, minH));
	}

	/**
	 * Multiplies each element of a vector by a scalar.
	 *
	 * @param vector The vector to be multiplied.
	 * @param scalar The scalar value for multiplication.
	 * @return The resulting vector after multiplication.
	 */
	private static double[] scalarMultiply(double[] vector, double scalar) {
		double[] result = new double[vector.length];
		for (int i = 0; i < vector.length; i++) {
			result[i] = vector[i] * scalar;
		}
		return result;
	}

	/**
	 * Adds multiple vectors together element-wise.
	 *
	 * @param vectors An array of vectors to be added.
	 * @return The resulting vector after addition.
	 */
	private static double[] addVectors(double[]... vectors) {
		if (vectors.length == 0) {
			throw new IllegalArgumentException("At least one vector is required for addition.");
		}

		int length = vectors[0].length;
		for (double[] vector : vectors) {
			if (vector.length != length) {
				throw new IllegalArgumentException("All vectors must be of the same length.");
			}
		}

		double[] result = new double[length];
		for (double[] vector : vectors) {
			for (int i = 0; i < length; i++) {
				result[i] += vector[i];
			}
		}
		return result;
	}

}
