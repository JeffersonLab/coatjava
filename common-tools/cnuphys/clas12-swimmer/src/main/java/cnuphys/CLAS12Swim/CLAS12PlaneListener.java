package cnuphys.CLAS12Swim;

import java.io.File;
import java.io.FileNotFoundException;

import cnuphys.CLAS12Swim.geometry.Plane;
import cnuphys.magfield.MagneticFieldInitializationException;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;

/**
 * A listener for swimming to the surface of a fixed infinite plane
 */

public class CLAS12PlaneListener extends CLAS12BoundaryListener {

	// the target plane
	private Plane _targetPlane;

	// the starting sign. When this changes we have crossed.
	private double _startSign;

	/**
	 * Create a CLAS12 boundary target plane listener, for swimming to a fixed
	 * infinite plane
	 *
	 * @param ivals       the initial values of the swim
	 * @param targetPlane the target infinite plane
	 * @param accuracy    the desired accuracy (cm)
	 * @param sMax        the final or max path length (cm)
	 */
	public CLAS12PlaneListener(CLAS12Values ivals, Plane targetPlane, double accuracy, double sMax) {
		super(ivals, accuracy, sMax);
		_targetPlane = targetPlane;
		_startSign = _targetPlane.sign(ivals.x, ivals.y, ivals.z);
		_canMakeStraightLine = false;
	}

	@Override
	public boolean crossedBoundary(double newS, double[] newU) {
		int sign = _targetPlane.sign(newU[0], newU[1], newU[2]);

		if (sign != _startSign) {
			return true;
		}
		return false;
	}

	@Override
	public boolean accuracyReached(double newS, double[] newU) {
		double distance = _targetPlane.distance(newU[0], newU[1], newU[2]);
		return distance < _accuracy;
	}

	/**
	 * Get the absolute distance to the target (boundary) in cm.
	 * 
	 * @param newS the new path length
	 * @param newU the new state vector
	 * @return the distance to the target (boundary) in cm.
	 */
	@Override
	public double distanceToTarget(double newS, double[] newU) {
		return _targetPlane.distance(newU[0], newU[1], newU[2]);
	}

}
