package cnuphys.CLAS12Swim;

import cnuphys.CLAS12Swim.geometry.Sphere;

/**
 * A listener for swimming to the surface of a fixed sphere
 */

public class CLAS12SphereListener extends CLAS12BoundaryListener {


	// the target sphere
	private Sphere _targetSphere;

	// starting inside or outside
	private boolean _inside;
	
	/**
	 * Create a CLAS12 boundary target sphere listener, for swimming to a fixed sphere
	 *
	 * @param ivals          the initial values of the swim
	 * @param targetSphere   the target infinite sphere
	 * @param accuracy       the desired accuracy (cm)
	 * @param sMax           the final or max path length (cm)
	 */
	public CLAS12SphereListener(CLAS12Values ivals, Sphere targetSphere, double accuracy, double sMax) {
		super(ivals, accuracy, sMax);
		_targetSphere = targetSphere;
		_inside = targetSphere.isInside(ivals.x, ivals.y, ivals.z);
		_canMakeStraightLine = false;
	}

	@Override
	public boolean accuracyReached(double newS, double[] newU) {
		double dist = _targetSphere.distance(newU[0], newU[1], newU[2]);
		return dist < _accuracy;
	}

	@Override
	public boolean crossedBoundary(double newS, double[] newU) {
		boolean newInside = _targetSphere.isInside(newU[0], newU[1], newU[2]);
		return newInside != _inside;
	}
	
	/**
	 * Get the absolute distance to the target (boundary) in cm.
	 * @param newS the new path length
	 * @param newU the new state vector
	 * @return the distance to the target (boundary) in cm.
	 */
	public double distanceToTarget(double newS, double[] newU) {
		return _targetSphere.distance(newU[0], newU[1], newU[2]);
	}
	

}
