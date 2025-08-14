package cnuphys.CLAS12Swim;

/**
 * Interface representing an ordinary differential equation (ODE).
 */
public interface ODE {
	/**
	 * Computes the derivatives for the ODE system at a given time and state.
	 *
	 * @param t The current time.
	 * @param y The current state vector.
	 * @return The derivatives vector.
	 */
	double[] getDerivatives(double t, double[] y);
	
    // New: allocation-free path (default keeps compatibility)
    default void getDerivativesInto(double t, double[] y, double[] dyOut) {
        double[] tmp = getDerivatives(t, y);
        System.arraycopy(tmp, 0, dyOut, 0, dyOut.length);
    }
}
