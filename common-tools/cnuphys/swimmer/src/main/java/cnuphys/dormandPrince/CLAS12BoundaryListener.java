package cnuphys.dormandPrince;

public abstract class CLAS12BoundaryListener extends CLAS12Listener {

	/**
	 * Create a CLAS12 boundary crossing listener
	 * 
	 * @param ivals           the initial values of the swim
	 * @param sFinal          the final or max path length (m)
	 * @param cacheTrajectory whether or not to cache the trajectory
	 */
	public CLAS12BoundaryListener(InitialValues ivals, double sFinal, boolean cacheTrajectory) {
		super(ivals, sFinal, cacheTrajectory);
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
		//have we crossed the boundary?
		if (crossedBoundary(newS, newU)) {
			_status = CLAS12Swimmer.SWIM_SUCCESS;

			return false;
		}
		
		accept(newS, newU);

		//have we reached the max path length?
		if (newS > _sFinal) {
			_status = CLAS12Swimmer.SWIM_TARGET_MISSED;
			return false;
		}
		
		return true;
	}
	
	
	/**
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
