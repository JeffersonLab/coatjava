package cnuphys.CLAS12Swim;

public class CLAS12BeamlineListener extends CLAS12DOCAListener {

	/**
	 * Create a CLAS12 beamline crossing listener
	 *
	 * @param ivals    the initial values of the swim
	 * @param accuracy the accuracy (cm) (on on difference in successive docas)
	 * @param sMax     the final or max path length (cm)
	 */
	public CLAS12BeamlineListener(CLAS12Values ivals, double accuracy, double sMax) {
		super(ivals, accuracy, sMax);
	}

	@Override
	public double doca(double newS, double[] newU) {
		return Math.hypot(newU[0], newU[1]);
	}

}
