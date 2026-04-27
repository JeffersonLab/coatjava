package cnuphys.CLAS12Swim;

/**
 * Utility class for efficient vector math in adaptive ODE solvers.
 */
public class ODEStepMath {

	/**
	 * Get the maximum absolute value from a vector.
	 *
	 * @param array input array
	 * @return max absolute value
	 */
	public static double getMax(double[] array) {
		double max = 0;
		for (double v : array) {
			if (v > max) max = v;
		}
		return max;
	}

	// In-place base + w1*a1
	public static void scaleAndAdd(double[] base, double[] a1, double w1, double[] out) {
		for (int i = 0; i < base.length; i++) {
			out[i] = base[i] + w1 * a1[i];
		}
	}

	// base + w1*a1 + w2*a2
	public static void scaleAndAdd(double[] base, double[] a1, double w1,
	                               double[] a2, double w2, double[] out) {
		for (int i = 0; i < base.length; i++) {
			out[i] = base[i] + w1 * a1[i] + w2 * a2[i];
		}
	}

	// base + w1*a1 + w2*a2 + w3*a3
	public static void scaleAndAdd(double[] base, double[] a1, double w1,
	                               double[] a2, double w2, double[] a3, double w3,
	                               double[] out) {
		for (int i = 0; i < base.length; i++) {
			out[i] = base[i] + w1 * a1[i] + w2 * a2[i] + w3 * a3[i];
		}
	}

	// base + w1*a1 + w2*a2 + w3*a3 + w4*a4
	public static void scaleAndAdd(double[] base, double[] a1, double w1,
	                               double[] a2, double w2, double[] a3, double w3,
	                               double[] a4, double w4, double[] out) {
		for (int i = 0; i < base.length; i++) {
			out[i] = base[i] + w1 * a1[i] + w2 * a2[i] + w3 * a3[i] + w4 * a4[i];
		}
	}

	// base + w1*a1 + w2*a2 + w3*a3 + w4*a4 + w5*a5
	public static void scaleAndAdd(double[] base, double[] a1, double w1,
	                               double[] a2, double w2, double[] a3, double w3,
	                               double[] a4, double w4, double[] a5, double w5,
	                               double[] out) {
		for (int i = 0; i < base.length; i++) {
			out[i] = base[i] + w1 * a1[i] + w2 * a2[i] + w3 * a3[i]
					+ w4 * a4[i] + w5 * a5[i];
		}
	}

	// base + w1*a1 + w2*a2 + w3*a3 + w4*a4 + w5*a5 + w6*a6
	public static void scaleAndAdd(double[] base, double[] a1, double w1,
	                               double[] a2, double w2, double[] a3, double w3,
	                               double[] a4, double w4, double[] a5, double w5,
	                               double[] a6, double w6, double[] out) {
		for (int i = 0; i < base.length; i++) {
			out[i] = base[i] + w1 * a1[i] + w2 * a2[i] + w3 * a3[i]
					+ w4 * a4[i] + w5 * a5[i] + w6 * a6[i];
		}
	}
}
