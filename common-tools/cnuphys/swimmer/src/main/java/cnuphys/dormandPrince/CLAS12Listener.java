package cnuphys.dormandPrince;

import cnuphys.swim.SwimTrajectory;

/**
 * The most basic CLAS12 listener. It never terminates the integration.
 * The sover will terminate the integration when the pathlength reaches its target value sMax.
 */
public class CLAS12Listener implements ODEStepListener {
	
	protected boolean _cacheTrajectory; //whether to cache the trajectory
	
	protected SwimTrajectory _trajectory; //the optional cached trajectory
	
	protected InitialValues _ivals; //initial values
	
	/**
	 * Create a CLAS12 listener
	 * 
	 * @param ivals           the initial values of the swim
	 * @param cacheTrajectory whether or not to cache the trajectory
	 */
	public CLAS12Listener(InitialValues ivals, boolean cacheTrajectory) {
		_ivals = new InitialValues(ivals);
		_cacheTrajectory = cacheTrajectory;
		
		if (_cacheTrajectory) {
			_trajectory = new SwimTrajectory();
			_trajectory.add(ivals.xo, ivals.yo, ivals.zo, ivals.p, ivals.theta, ivals.phi);
		}
	}

    /**
     * Called when a new step is taken in the ODE solving process.
     * 
     * @param oldT The previous independent variable.
     * @param oldY The previous state vector.
     * @param newT The new independent variable after the step.
     * @param newY The new state vector after the step.
     * @return A boolean indicating whether to continue (true) or stop (false) the integration.
     */
	@Override
	public boolean newStep(double oldT, double[] oldY, double newT, double[] newY) {
		// TODO Auto-generated method stub
		return false;
	}

}
