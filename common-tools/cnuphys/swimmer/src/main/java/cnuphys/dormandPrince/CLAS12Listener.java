package cnuphys.dormandPrince;

import cnuphys.adaptiveSwim.AdaptiveSwimmer;
import cnuphys.swim.SwimTrajectory;

/**
 * The most basic CLAS12 listener. It never terminates the integration.
 * The slover will terminate the integration when the pathlength reaches its target value sMax.
 */
public class CLAS12Listener implements ODEStepListener {
	
	private static final double _TINY = 1.0e-10; // meters
		
	//the trajectory if cached
	protected SwimTrajectory _trajectory; //the optional cached trajectory
	
	//the initial values
	protected InitialValues _ivals; //initial values
	
	//the current state vector
	private double[] _u;

	//the number of integration steps
	private int _nStep;

	//the current path length
	private double _s;

	//a status, one of the AdaptiveSwimmer class constants
	private int _status = CLAS12Swimmer.SWIM_SWIMMING;
	
	private double _sFinal;
	

	
	/**
	 * Create a CLAS12 listener
	 * 
	 * @param ivals           the initial values of the swim
	 * @param cacheTrajectory whether or not to cache the trajectory
	 */
	public CLAS12Listener(InitialValues ivals, double sFinal, boolean cacheTrajectory) {
		_ivals = ivals;
		_u = new double[6];
		_sFinal = sFinal;
		
		if (cacheTrajectory) {
			_trajectory = new SwimTrajectory();
		}
		
		reset();
	}
	
	/*
	 * Basic initialization and reset
	 */
	public void reset() {
		_nStep = 0;
		_s = 0;
		_status = CLAS12Swimmer.SWIM_SWIMMING;
		
		for (int i = 0; i < AdaptiveSwimmer.DIM; i++) {
			_u[i] = Double.NaN;
		}

		
		if (_trajectory != null) {
			_trajectory.clear();
			_trajectory.add(_ivals.xo, _ivals.yo, _ivals.zo, _ivals.p, _ivals.theta, _ivals.phi);
		}
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
		
		//TODO: probably unnecessary copy of u fix after testing

		if (accept(newS, newU)) {
			_nStep++;
			_s = newS;
			
			if (Math.abs(_s - _sFinal) < _TINY) {
				_status = CLAS12Swimmer.SWIM_SUCCESS;
			}

			for (int i = 0; i < 6; i++) {
				_u[i] = newU[i];
			}
						
			_u = _trajectory.lastElement();
		}
		return !terminate();
	}

	/**
	 * Override this if there is the possibility of not accepting a step.
	 * 
	 * @return <code>true</code> if the step should be accepted.
	 */
	protected boolean accept(double newS, double[] newU) {
		return true;
	}
	/**
	 * Override this to determine if the integration should stop. For the base
	 * integration always return false. It will stop when max pathlength is reached.
	 * @return <code>true</code> if the integration should stop.
	 */
	protected boolean terminate() {
		return false;
	}

	/**
	 * Get the trajectory
	 * 
	 * @return the trajectory
	 */
	public SwimTrajectory getTrajectory() {
		return _trajectory;
	}

	/**
	 * Get the initial values
	 * 
	 * @return the initial values
	 */
	public InitialValues getIvals() {
		return _ivals;
	}

	/**
	 * Get the current state vector
	 * 
	 * @return the current state vector
	 */
	public double[] getU() {
		return _u;
	}

	/**
	 * Get the number of integration steps
	 * 
	 * @return the number of integration steps
	 */
	public int getNumStep() {
		return _nStep;
	}

	/**
	 * Get the current path length
	 * 
	 * @return the current path length in meters
	 */
	public double getS() {
		return _s;
	}

	/**
	 * Get the status
	 * 
	 * @return the status
	 */
	public int getStatus() {
		return _status;
	}
	
	/**
	 * Set the status
	 * 
	 * @param status the status. One of the constants in CLAS12Swimmer.
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
