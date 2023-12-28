package cnuphys.CLAS12Swim;

import cnuphys.adaptiveSwim.geometry.Cylinder;

public class CLAS12CylinderListener extends CLAS12BoundaryListener {

	// the target cylinder
	private Cylinder _targetCylinder;

	// starting inside or outside
	private boolean _inside;

	/**
	 * Create a CLAS12 boundary target cylinder listener, for swimming to a fixed
	 * infinite cylinder
	 *
	 * @param ivals          the initial values of the swim
	 * @param targetCylinder the target infinite cylinder
	 * @param accuracy       the desired accuracy (cm)
	 * @param sMax           the final or max path length (cm)
	 */
	public CLAS12CylinderListener(CLAS12Values ivals, Cylinder targetCylinder, double accuracy, double sMax) {
		super(ivals, accuracy, sMax);
		_targetCylinder = targetCylinder;
		_inside = _targetCylinder.isInside(ivals.x, ivals.y, ivals.z);
//
//		if (!_inside) {
//			System.err.println("WARNING swim starting outside of cylinder.\n"
//					+ "There is a risk of the swim stepping over the cylinder and missing the boundary.");
//		}
	}

	@Override
	public boolean accuracyReached(double newS, double[] newU) {
		double dist = _targetCylinder.signedDistance(newU[0], newU[1], newU[2]);
		return Math.abs(dist) < _accuracy;
	}

	@Override
	public boolean crossedBoundary(double newS, double[] newU) {
		boolean newInside = _targetCylinder.isInside(newU[0], newU[1], newU[2]);
		return newInside != _inside;
	}

	@Override
	public double interpolate(double s1, double[] u1, double s2, double[] u2, double[] u) {
		double dist1 = _targetCylinder.signedDistance(u1[0], u1[1], u1[2]);
		double dist2 = _targetCylinder.signedDistance(u2[0], u2[1], u2[2]);

		double t = -dist1 / (dist2 - dist1);

		double s = s1 + t * (s2 - s1);

		for (int i = 0; i < 6; i++) {
			u[i] = u1[i] + t * (u2[i] - u1[i]);
		}

		return s;
	}

}
