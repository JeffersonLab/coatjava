package cnuphys.CLAS12Swim;

import cnuphys.CLAS12Swim.geometry.Cylinder;

/**
 * A listener for swimming to the surface of a fixed infinite cylinder
 */

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
		_canMakeStraightLine = false;
	}

	@Override
	public boolean accuracyReached(double newS, double[] newU) {
		double dist = _targetCylinder.distance(newU[0], newU[1], newU[2]);
		return dist < _accuracy;
	}

	@Override
	public boolean crossedBoundary(double newS, double[] newU) {
		boolean newInside = _targetCylinder.isInside(newU[0], newU[1], newU[2]);
		return newInside != _inside;
	}

	/**
	 * Get the absolute distance to the target (boundary) in cm.
	 * @param newS the new path length
	 * @param newU the new state vector
	 * @return the distance to the target (boundary) in cm.
	 */
	@Override
	public double distanceToTarget(double newS, double[] newU) {
		return _targetCylinder.distance(newU[0], newU[1], newU[2]);
	}

}
