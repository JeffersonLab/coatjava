package cnuphys.CLAS12Swim;

/**
 * This class implements the Fehlberg method for solving ordinary differential equation.
 */
public class Fehlberg {

	/**
	 * Solves an ordinary differential equation (ODE) using the Fehlberg method.
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
	 * @param listener  An instance of ODEStepListener to monitor the integration
	 *                  process.
	 */
	public static void solve(ODE ode, double[] y0, double t0, double t1, double h, double tolerance, double minH,
			double maxH, ODEStepListener listener) {
		// Fehlberg coefficients
		final double[] c = { 0, 1.0 / 4, 3.0 / 8, 12.0 / 13, 1, 1.0 / 2 };
		final double[][] a = { {}, { 1.0 / 4 }, { 3.0 / 32, 9.0 / 32 },
				{ 1932.0 / 2197, -7200.0 / 2197, 7296.0 / 2197 }, { 439.0 / 216, -8, 3680.0 / 513, -845.0 / 4104 },
				{ -8.0 / 27, 2, -3544.0 / 2565, 1859.0 / 4104, -11.0 / 40 } };
		final double[] b4 = { 25.0 / 216, 0, 1408.0 / 2565, 2197.0 / 4104, -1.0 / 5, 0 }; // 4th order coefficients
		final double[] b5 = { 16.0 / 135, 0, 6656.0 / 12825, 28561.0 / 56430, -9.0 / 50, 2.0 / 55 }; // 5th order
																										// coefficients

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

	/**
	 * Adjusts the step size based on the estimated error and the desired tolerance.
	 *
	 * @param h         Current step size.
	 * @param error     Estimated error.
	 * @param tolerance Desired accuracy.
	 * @param minH      Minimum allowed step size.
	 * @param maxH      Maximum allowed step size.
	 * @return Adjusted step size.
	 */
	private static double adjustStepSize(double h, double error, double tolerance, double minH, double maxH) {
		double safetyFactor = 0.9; // Safety factor to reduce the step size slightly
		double errorRatio = error / tolerance;

		// Avoid division by zero in case of very small error
		if (errorRatio == 0) {
			errorRatio = 1e-6;
		}

		// Calculate scaling factor for step size adjustment
		// The exponent -1/5 is typically used for a 4th order method
		double scale = safetyFactor * Math.pow(errorRatio, -0.2);

		// Limit the scaling to prevent excessively large or small step sizes
		scale = Math.max(1.0 / 10.0, Math.min(5.0, scale));

		// Calculate new step size and ensure it's not smaller than the minimum
		double newH = Math.max(minH, h * scale);
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
