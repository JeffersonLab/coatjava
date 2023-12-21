package cnuphys.dormandPrince;

public abstract class CLAS12BoundaryListener extends CLAS12Listener {
	
	// the requested accuracy in cm
	protected double _accuracy;

	/**
	 * Create a CLAS12 boundary crossing listener
	 * 
	 * @param ivals           the initial values of the swim
	 * @param sFinal          the final or max path length (cm)
	 * @param accuracy        the accuracy (cm)
	 */
	public CLAS12BoundaryListener(CLAS12Values ivals, double sFinal, double accuracy) {
		super(ivals, sFinal);
		_accuracy = accuracy;
	}
	
	/**
	 * Called when a new step is taken in the ODE solving process.
	 * 
     * @param newS The new path length after the step.
     * @param newU The new state vector after the step.
	 * @return A boolean indicating whether to continue (true) or stop (false) the
	 *         integration.
	 */
	public boolean newStep(double newS, double[] newU) {
		accept(newS, newU);
		
		//have we reached the accuracy?
//		if (accuracyReached(newS, newU)) {
//			_status = CLAS12Swimmer.SWIM_SUCCESS;
//			return false;
//		}

		//have we crossed the boundary? If so this method should handle the crossing
		//and return with the "intersection" set as the last point. The status should
		//be set to SWIM_SUCCESS.
		if (crossedBoundary(newS, newU)) {
			_status = CLAS12Swimmer.SWIM_SUCCESS;
			return false;
		}
		
		//have we reached the max path length?
		if (newS > _sFinal) {
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
     * @param newS The new path length after the step.
     * @param newU The new state vector after the step.
	 * @return <code>true</code> if we crossed the boundary, in which case
	 * we should terminate and interpolate to the intersection.
	 */
	public abstract boolean crossedBoundary(double newS, double[] newU);
	
	/**
	 * Handle the crossing. This is where the interpolation to the intersection is performed
    * @param newS The new path length after the step.
     * @param newU The new state vector after the step.
	 */
	public abstract void handleCrossing(double newS, double[] newU);
	

}
