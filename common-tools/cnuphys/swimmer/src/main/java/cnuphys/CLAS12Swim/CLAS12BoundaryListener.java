package cnuphys.CLAS12Swim;

public abstract class CLAS12BoundaryListener extends CLAS12Listener {

	// the requested accuracy in cm
	protected final double _accuracy;

	/**
	 * Create a CLAS12 boundary crossing listener
	 *
	 * @param ivals    the initial values of the swim
	 * @param accuracy the accuracy (cm)
	 * @param sMax     the final or max path length (cm)
	 */
	public CLAS12BoundaryListener(CLAS12Values ivals, double accuracy, double sMax) {
		super(ivals, sMax);
		_accuracy = accuracy;
	}

	/**
	 * Get the requested accuracy
	 *
	 * @return the requested accuracy
	 */
	public double getAccuracy() {
		return _accuracy;
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

		// have we crossed the boundary? If so the CrossedBoundary method should handle
		// the crossing
		// and return with the "intersection" set as the last point. The status should
		// be set to SWIM_SUCCESS.
		if (crossedBoundary(newS, newU)) {
			_status = CLAS12Swimmer.SWIM_SUCCESS;
			return false;
		}

		// have we reached the max path length?
		if (Math.abs(newS - _sMax) < TINY) {
			_status = CLAS12Swimmer.SWIM_TARGET_MISSED;
			return false;
		}

		return true;
	}

	/**
	 * Called when a new step is taken in the ODE solving process.
	 *
	 * @param newS The new path length after the step.
	 * @param newU The new state vector after the step.
	 * @return A boolean indicating whether the requested accuracy has been reached
	 */
	public abstract boolean accuracyReached(double newS, double[] newU);

	/**
	 * Have we crossed the boundary?
	 *
	 * @param newS The new path length after the step.
	 * @param newU The new state vector after the step.
	 * @return <code>true</code> if we crossed the boundary, in which case we should
	 *         terminate and interpolate to the intersection.
	 */
	public abstract boolean crossedBoundary(double newS, double[] newU);

	/**
	 * Interpolate between two points, one on each side of the boundary
	 *
	 * @param s1 the path length of the "left" point (cm)
	 * @param u1 the state vector of the "left" point
	 * @param s2 the path length of the "right" point (cm)
	 * @param u2 the state vector of the "right" point
	 * @param u  will hold the interpolated state vector
	 *
	 */
	public abstract double interpolate(double s1, double[] u1, double s2, double[] u2, double u[]);
}
