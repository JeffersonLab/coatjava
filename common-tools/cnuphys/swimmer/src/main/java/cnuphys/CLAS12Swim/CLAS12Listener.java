package cnuphys.CLAS12Swim;

/**
 * The most basic CLAS12 listener. It never terminates the integration. The
 * slover will terminate the integration when the pathlength reaches its target
 * value sMax.
 */
public class CLAS12Listener implements ODEStepListener {
	
	/**
	 * This parameter controls whether the listener can make a straight line
	 * to the target for a neutral particle. Some can, like the basic, z, and rho listeners.
	 * Some, like the cylinder, cannot-- in which case we let the full swim occur.
     */
	protected boolean _canMakeStraightLine = true;

	protected static final double TINY = 1.0e-8; // cm

	// the trajectory if cached
	protected CLAS12Trajectory _trajectory; // the cached trajectory

	// the initial values
	protected CLAS12Values _initialVaues; // initial values

	// a status, one of the CLAS12Swimmer class constants
	protected int _status = CLAS12Swimmer.SWIM_SWIMMING;

	// the final (target) or maximum path length in cm
	protected final double _sMax;

	/**
	 * Create a CLAS12 listener
	 *
	 * @param ivals the initial values of the swim
	 * @param sMax  the final or max path length (cm)
	 */
	public CLAS12Listener(CLAS12Values ivals, double sMax) {
		_initialVaues = ivals;
		_sMax = sMax;

		_trajectory = new CLAS12Trajectory();
		reset();
	}
	
	/**
	 * This parameter controls whether the listener can make a straight line to the
	 * target for a neutral particle. Some can, like the basic, z, and rho
	 * listeners. Some, like the cylinder, cannot-- in which case we let the full
	 * swim occur.
	 * @return <code>true</code> if the listener can make a straight line to the target for a neutral particle.
     */
	public boolean canMakeStraightLine() {
		return _canMakeStraightLine;
	}

	/**
	 * Get the final (target) or maximum path length in cm
	 *
	 * @return the final (target) or maximum path length in cm
	 */
	public double getSMax() {
		return _sMax;
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
	 * @return A boolean indicating whether to continue (true) or stop (false) the
	 *         integration.
	 */
	@Override
	public boolean newStep(double newS, double[] newU) {

		accept(newS, newU);

		// if we are done, set the status
		if (Math.abs(newS - _sMax) < TINY) {
			_status = CLAS12Swimmer.SWIM_SUCCESS;
		}

		// base always continues, the solve with integrate to sMax and stop
		return true;
	}

	/**
	 * Accept the next step.
	 *
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
	 * @return the current path length in cm
	 */
	public double getS() {
		return _trajectory.getS(_trajectory.size() - 1);
	}

	/**
	 * Get the status of the swim. The values are the CLAS12Swimmer constants:
	 * SWIM_SUCCESS or SWIM_TARGET_MISSED.
	 *
	 * @return the status
	 */
	public int getStatus() {
		return _status;
	}

	/**
	 * Set the status
	 *
	 * @param status the status. The values are the CLAS12Swimmer constants:
	 *               SWIM_SUCCESS or SWIM_TARGET_MISSED.
	 * @see CLAS12Swimmer
	 */
	public void setStatus(int status) {
		_status = status;
	}

	/**
	 * Get the status of the swim as a string
	 *
	 * @return the status of the swim as a string
	 */
	public String statusString() {
		String s = CLAS12Swimmer.resultNames.get(_status);
		if (s == null) {
			s = "Unknown (" + _status + ")";
		}
		return s;
	}

	/**
	 * Add a second point creating a straight line. This is only used when
	 * "swimming" neutral particles. This can be overridden to stop the straight line
	 * at a target.
	 */
	public void straightLine() {

		double xo = _initialVaues.x;
		double yo = _initialVaues.y;
		double zo = _initialVaues.z;
		double theta = _initialVaues.theta;
		double phi = _initialVaues.phi;
		double sf = _sMax;

		double sintheta = Math.sin(Math.toRadians(theta));
		double costheta = Math.cos(Math.toRadians(theta));
		double sinphi = Math.sin(Math.toRadians(phi));
		double cosphi = Math.cos(Math.toRadians(phi));

		double xf = xo + sf * sintheta * cosphi;
		double yf = yo + sf * sintheta * sinphi;
		double zf = zo + sf * costheta;

		_trajectory.addPoint(xf, yf, zf, theta, phi, sf);
		_status = CLAS12Swimmer.SWIM_SUCCESS;

	}

}
