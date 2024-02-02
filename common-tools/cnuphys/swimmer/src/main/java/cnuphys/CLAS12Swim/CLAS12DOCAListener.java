package cnuphys.CLAS12Swim;

/**
 * This is an abstract class to be extended by classes that to a distance of
 * closest approach. The assumption is that the first doca is the only one. i.e.
 * we are not dealing with low energy particles looping about.
 */

public abstract class CLAS12DOCAListener extends CLAS12Listener {

	// the requested accuracy in cm
	protected final double _accuracy;

	// current doca
	protected double _currentDOCA = Double.POSITIVE_INFINITY;

	/**
	 * Create a CLAS12 boundary crossing listener
	 *
	 * @param ivals    the initial values of the swim
	 * @param accuracy the accuracy (cm)
	 * @param sMax     the final or max path length (cm)
	 */
	public CLAS12DOCAListener(CLAS12Values ivals, double accuracy, double sMax) {
		super(ivals, sMax);
		_accuracy = accuracy;
	}

	/**
	 * Reset the current DOCA to infinity
	 */
	@Override
	public void reset() {
		_currentDOCA = Double.POSITIVE_INFINITY;
	}

	/**
	 * Get the requested accuracy (on on difference in successive docas)in cm.
	 *
	 * @return the requested accuracy
	 */
	public double getAccuracy() {
		return _accuracy;
	}

	/**
	 * Get the current estimate of the doca
	 *
	 * @return the current doca
	 */
	public double getCurrentDOCA() {
		return _currentDOCA;
	}

	/**
	 * Called when a new step is taken in the ODE solving process.
	 *
	 * @param newS The new path length after the step.
	 * @param newU The new state vector after the step.
	 * @return A boolean indicating whether to continue (true) or stop (false) the
	 *         integration.
	 */
	@Override
	public boolean newStep(double newS, double[] newU) {
		accept(newS, newU);

		double doca = doca(newS, newU);

		if (doca > _currentDOCA) { // getting farther
			_status = CLAS12Swimmer.SWIM_SUCCESS;
			return false;
		}

		// have we reached the max path length?
		if (newS >= _sMax) {
			_status = CLAS12Swimmer.SWIM_TARGET_MISSED;
			return false;
		}

		_currentDOCA = doca;
		return true;
	}

	/**
	 * Get the absolute distance to the target (boundary) in cm.
	 * 
	 * @param newS the new path length
	 * @param newU the new state vector
	 * @return the distance to the target (boundary) in cm.
	 */
	public abstract double doca(double newS, double[] newU);

}
