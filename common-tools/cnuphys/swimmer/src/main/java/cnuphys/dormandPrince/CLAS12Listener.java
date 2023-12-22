package cnuphys.dormandPrince;


/**
 * The most basic CLAS12 listener. It never terminates the integration.
 * The slover will terminate the integration when the pathlength reaches its target value sMax.
 */
public class CLAS12Listener implements ODEStepListener {
	
	protected static final double TINY = 1.0e-8; // cm
		
	//the trajectory if cached
	protected CLAS12Trajectory _trajectory; //the cached trajectory
	
	//the initial values
	protected CLAS12Values _initialVaues; //initial values
	
	//a status, one of the CLAS12Swimmer class constants
	protected int _status = CLAS12Swimmer.SWIM_SWIMMING;
	
	//the final (target) or maximum path length in cm
	protected double _sFinal;
	
	/**
	 * Create a CLAS12 listener
	 * 
	 * @param ivals           the initial values of the swim
	 * @param sFinal          the final or max path length (cm)
	 */
	public CLAS12Listener(CLAS12Values ivals, double sFinal) {
		_initialVaues = ivals;
		_sFinal = sFinal;
		
		_trajectory = new CLAS12Trajectory();
		reset();
	}
	
	/*
	 * Basic initialization and reset
	 */
	public void reset() {
		_status = CLAS12Swimmer.SWIM_SWIMMING;		
		_trajectory.clear();
		_trajectory.add(0., _initialVaues.getU());
	}

    /**
     * Called when a new step is taken in the ODE solving process.
     * 
     * @param newS The new path length after the step.
     * @param newU The new state vector after the step.
     * @return A boolean indicating whether to continue (true) or stop (false) the integration.
     */
	@Override
	public boolean newStep(double newS, double[] newU) {

		accept(newS, newU);
		
		//if we are done, set the status
		if (Math.abs(newS - _sFinal) < TINY) {
			_status = CLAS12Swimmer.SWIM_SUCCESS;
		}

		//base always continues, the solve with integrate to sFinal and stop
		return true;
	}

	/**
	 * Accept the next step.
     * @param newS The new path length after the step.
     * @param newU The new state vector after the step.
	 */
	protected void accept(double newS, double[] newU) {
		_trajectory.add(newS, newU);
	}

	/**
	 * Get the trajectory
	 * 
	 * @return the trajectory
	 */
	public CLAS12Trajectory getTrajectory() {
		return _trajectory;
	}

	/**
	 * Get the initial values
	 * 
	 * @return the initial values
	 */
	public CLAS12Values getIvals() {
		return _initialVaues;
	}

	/**
	 * Get the current state vector
	 * 
	 * @return the current state vector
	 */
	public double[] getU() {
		return _trajectory.get(_trajectory.size() - 1);
	}

	/**
	 * Get the number of integration steps
	 * 
	 * @return the number of integration steps
	 */
	public int getNumStep() {
		return _trajectory.size();
	}

	/**
	 * Get the current path length
	 * 
	 * @return the current path length in meters
	 */
	public double getS() {
		return _trajectory.getS(_trajectory.size() - 1);
	}

	/**
	 * Get the status of the swim. The values are the CLAS12Swimmer constants: SWIM_SUCCESS or SWIM_TARGET_MISSED.
	 * 
	 * @return the status
	 */
	public int getStatus() {
		return _status;
	}
	
	/**
	 * Set the status
	 * 
	 * @param status the status. The values are the CLAS12Swimmer constants: SWIM_SUCCESS or SWIM_TARGET_MISSED.
	 * @see CLAS12Swimmer
	 */
	public void setStatus(int status) {
		_status = status;
	}
	
	/**
	 * Get the status of the swim as a string
	 * @return the status of the swim as a string
	 */
	public String statusString() {
		String s = CLAS12Swimmer.resultNames.get(_status);
		if (s == null) {
			s = "Unknown (" + _status + ")";
		}
		return s;
	}

}
