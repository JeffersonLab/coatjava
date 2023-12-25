package cnuphys.CLAS12Swim;

public class DormandPrince {
	
	
	/**
	 * Solves an ordinary differential equation (ODE) using the Dormand-Prince
	 * method.
	 * 
	 * @param ode       The ODE to solve.
	 * @param y0        The initial state vector.
	 * @param t0        The initial time.
	 * @param t1        The final time.
	 * @param h         The initial step size.
	 * @param tolerance The desired accuracy. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 * @param minH      The minimum step size to prevent an infinite loop.
	 * @param maxH      The maximum step size changed when near boundary, but default is usually infinity.
     * @param listener An optional listener that will be called after each step.
     */
	public static void solve(ODE ode, double[] y0, double t0, double t1, double h, double tolerance, double minH, double maxH, ODEStepListener listener) {
		
	      // Dormand-Prince coefficients
        final double[] c = {0, 1.0/5, 3.0/10, 4.0/5, 8.0/9, 1, 1};
        final double[][] a = {
            {},
            {1.0/5},
            {3.0/40, 9.0/40},
            {44.0/45, -56.0/15, 32.0/9},
            {19372.0/6561, -25360.0/2187, 64448.0/6561, -212.0/729},
            {9017.0/3168, -355.0/33, 46732.0/5247, 49.0/176, -5103.0/18656},
            {35.0/384, 0, 500.0/1113, 125.0/192, -2187.0/6784, 11.0/84}
        };
        final double[] b = {35.0/384, 0, 500.0/1113, 125.0/192, -2187.0/6784, 11.0/84, 0};
        final double[] bStar = {5179.0/57600, 0, 7571.0/16695, 393.0/640, -92097.0/339200, 187.0/2100, 1.0/40};

        int n = y0.length;
        double[] k1, k2, k3, k4, k5, k6, k7, yTemp, yTempStar, error;
 
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
                k3 = ode.getDerivatives(t + c[2] * h, addVectors(y, scalarMultiply(k1, a[2][0] * h), scalarMultiply(k2, a[2][1] * h)));
                k4 = ode.getDerivatives(t + c[3] * h, addVectors(y, scalarMultiply(k1, a[3][0] * h), scalarMultiply(k2, a[3][1] * h), scalarMultiply(k3, a[3][2] * h)));
                k5 = ode.getDerivatives(t + c[4] * h, addVectors(y, scalarMultiply(k1, a[4][0] * h), scalarMultiply(k2, a[4][1] * h), scalarMultiply(k3, a[4][2] * h), scalarMultiply(k4, a[4][3] * h)));
                k6 = ode.getDerivatives(t + c[5] * h, addVectors(y, scalarMultiply(k1, a[5][0] * h), scalarMultiply(k2, a[5][1] * h), scalarMultiply(k3, a[5][2] * h), scalarMultiply(k4, a[5][3] * h), scalarMultiply(k5, a[5][4] * h)));
                k7 = ode.getDerivatives(t + c[6] * h, addVectors(y, scalarMultiply(k1, a[6][0] * h), scalarMultiply(k2, a[6][1] * h), scalarMultiply(k3, a[6][2] * h), scalarMultiply(k4, a[6][3] * h), scalarMultiply(k5, a[6][4] * h), scalarMultiply(k6, a[6][5] * h)));

                yTemp = y.clone();
                yTempStar = y.clone();
                error = new double[n];

                for (int i = 0; i < n; i++) {
                    yTemp[i] += h * (b[0] * k1[i] + b[1] * k2[i] + b[2] * k3[i] + b[3] * k4[i] + b[4] * k5[i] + b[5] * k6[i] + b[6] * k7[i]);
                    yTempStar[i] += h * (bStar[0] * k1[i] + bStar[1] * k2[i] + bStar[2] * k3[i] + bStar[3] * k4[i] + bStar[4] * k5[i] + bStar[5] * k6[i] + bStar[6] * k7[i]);
                    error[i] = Math.abs(yTemp[i] - yTempStar[i]);
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

	    // Return or print the final state of y
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

	// Method to adjust the step size based on the error
	private static double adjustStepSize(double h, double error, double tolerance, double minH, double maxH) {
	    double safetyFactor = 0.9;
	    double errorRatio = error / tolerance;
	    double scale = safetyFactor * Math.pow(errorRatio, -0.25);

	    scale = Math.max(1.0/5, Math.min(5.0, scale)); // Limit scaling to prevent drastic changes
	    double newH = h * scale;

	    return Math.min(maxH, Math.max(newH, minH));
	}
	
	
	/**
	 * Multiplies each element of a vector by a scalar.
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
