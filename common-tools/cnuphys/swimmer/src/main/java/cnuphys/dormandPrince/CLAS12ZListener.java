package cnuphys.dormandPrince;

public class CLAS12ZListener extends CLAS12BoundaryListener {
	
	//the target z (m)
	private double _zTarget;
	
	//the starting sign. When this changes we have crossed.
	private double _startSign;

	/**
	 * Create a CLAS12 boundary target Z listener, for swimming to a fixed z
	 * 
	 * @param ivals           the initial values of the swim
	 * @param zTarget         the target z (m)
	 * @param sFinal          the final or max path length (m)
	 */
	public CLAS12ZListener(CLAS12InitialValues ivals, double zTarget, double sFinal) {
		super(ivals, sFinal);
		_zTarget = zTarget;
		_startSign = sign(ivals.zo);
	}

	@Override
	public boolean crossedBoundary(double newS, double[] newU) {
		double newZ = newU[2];
		int sign = sign(newZ);
		
		if (sign != _startSign) {
			//we crossed
			handleCrossing(newS, newU);
			return true;
		}
		return false;
	}

	@Override
	public void handleCrossing(double newS, double[] newU) {
	}
	
	private int sign(double z) {
		return (z < _zTarget) ? -1 : 1;
	}

}
