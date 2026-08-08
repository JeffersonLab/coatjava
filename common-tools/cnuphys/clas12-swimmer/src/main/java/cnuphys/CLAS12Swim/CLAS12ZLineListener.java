package cnuphys.CLAS12Swim;

public class CLAS12ZLineListener extends CLAS12DOCAListener {
	
	private double _xb; //x offset in cm
	private double _yb; //y offset in cm
	
	/**
	 * Create a CLAS12 swim to an "offest beamline" listener. The offset
	 * beamline is a line parallel to the z-axis, but offset in the x and y
	 * directions by _xb and _yb.
	 *
	 * @param ivals    the initial values of the swim
	 * @param xb       the x offset (cm)
	 * @param yb       the y offset (cm)
	 * @param accuracy the accuracy (cm) (on on difference in successive docas)
	 * @param sMax     the final or max path length (cm)
	 */
	public CLAS12ZLineListener(CLAS12Values ivals, double xb, double yb, double accuracy, double sMax) {
		super(ivals, accuracy, sMax);
		_xb = xb;
		_yb = yb;
	}


	@Override
	public double doca(double newS, double[] newU) {
		double dx = newU[0] - _xb;
		double dy = newU[1] - _yb;
		return Math.hypot(dx, dy);
	}

}
