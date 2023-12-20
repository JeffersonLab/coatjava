package cnuphys.dormandPrince;

/**
 * This holds the results of a swim. It gets them from the listener, so in effect this is
 * a wrapper for convenience and clarity.
 */
public class CLAS12SwimResult {
	
	private CLAS12Listener _listener;
	
	public CLAS12SwimResult(CLAS12Listener listener) {
		_listener = listener;
	}
	
	/**
	 * Get the trajectory
	 * 
	 * @return the trajectory
	 */
	public CLAS12Trajectory getTrajectory() {
		return _listener.getTrajectory();
	}
	
	/**
	 * Get the initial values of the swim
	 * 
	 * @return the initial values
	 */
	public CLAS12Values getInitialValues() {
		return _listener.getIvals();
	}
	
	/**
	 * Get the final values of the swim
	 * 
	 * @return the final values
	 */
	public CLAS12Values getFinalValues() {
		double u[] = _listener.getU();
		int q = _listener.getIvals().charge;
		double p = _listener.getIvals().p;
		return new CLAS12Values(q, p, u);
	}
	
	/**
	 * Get the path length in cm
	 * 
	 * @return the path length in cm
	 */
	public double getPathLength() {
		return _listener.getS();
	}
	
	/**
	 * Get the status of the swim. The values are the CLAS12Swimmer constants: SWIM_SUCCESS or SWIM_TARGET_MISSED.
	 * 
	 * @return the status of the swim
	 */
	public int getStatus() {
		return _listener.getStatus();
	}
	
	/**
	 * Get the final state vector
	 * 
	 * @return the final state vector
	 */
	public double[] getFinalU() {
		return _listener.getU();
	}
	
	/**
	 * Get the status of the swim as a string
	 * @return the status of the swim as a string
	 */
	public String statusString() {
		int status = getStatus();
		String s = CLAS12Swimmer.resultNames.get(status);
		if (s == null) {
			s = "Unknown (" + status + ")";
		}
		return s;
	}
	
	/**
	 * Get the number of integration steps
	 * 
	 * @return the number of integration steps
	 */
	public int getNumStep() {
		return _listener.getNumStep();
	}

	
	/**
	 *Get a summary of the results of the swim
	 */
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer(2000);
		CLAS12Values ivalues = getInitialValues();
		CLAS12Values fvalues = getFinalValues();
		
		double norm = ivalues.p / fvalues.p; //should be 1.0
		
		sb.append("Swim results:\n");
		sb.append("Status: " + statusString() + "\n");
		sb.append("Initial values:\n");
		sb.append("charge = " + ivalues.charge + "\n");
		sb.append(String.format("vertex = (%.5f, %.5f, %.5f) cm\n", ivalues.x, ivalues.y, ivalues.z));
		sb.append(String.format("momentum = %.5f GeV/c\n", ivalues.p));
		sb.append(String.format("theta = %.5f deg\n", ivalues.theta));
		sb.append(String.format("phi = %.5f deg\n", ivalues.phi));
		sb.append("--------\nFinal values:\n");
		sb.append(String.format("location = (%.5f, %.5f, %.5f) cm\n", fvalues.x, fvalues.y, fvalues.z));
		sb.append(String.format("momentum = %.5f GeV/c\n", fvalues.p));
		sb.append(String.format("norm = %.5f (should be 1)\n", norm));
		sb.append(String.format("theta = %.5f deg\n", fvalues.theta));
		sb.append(String.format("phi = %.5f deg\n", fvalues.phi));
		sb.append(String.format("path length = %.5f cm\n", getPathLength()));
		sb.append(String.format("number of steps = %d\n", getNumStep()));
		
		return sb.toString();
	}



}
