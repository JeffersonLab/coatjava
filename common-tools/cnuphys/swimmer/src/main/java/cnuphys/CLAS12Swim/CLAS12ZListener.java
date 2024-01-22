package cnuphys.CLAS12Swim;

/**
 * A listener for swimming to a fixed value of z
 */
public class CLAS12ZListener extends CLAS12BoundaryListener {

	// the target z (cm)
	private double _zTarget;

	// the starting sign. When this changes we have crossed.
	private double _startSign;

	/**
	 * Create a CLAS12 boundary target Z listener, for swimming to a fixed z
	 *
	 * @param ivals    the initial values of the swim
	 * @param zTarget  the target z (cm)
	 * @param accuracy the desired accuracy (cm)
	 * @param sMax     the final or max path length (cm)
	 */
	public CLAS12ZListener(CLAS12Values ivals, double zTarget, double accuracy, double sMax) {
		super(ivals, accuracy, sMax);
		_zTarget = zTarget;
		_startSign = sign(ivals.z);
	}

	@Override
	public boolean crossedBoundary(double newS, double[] newU) {
		double newZ = newU[2];
		int sign = sign(newZ);

		if (sign != _startSign) {
			return true;
		}
		return false;
	}

	@Override
	public boolean accuracyReached(double newS, double[] newU) {
		double dZ = Math.abs(newU[2] - _zTarget);
		return dZ < _accuracy;
	}

	// left or right of the target Z?
	private int sign(double z) {
		return (z < _zTarget) ? -1 : 1;
	}

	/**
	 * Get the absolute distance to the target (boundary) in cm.
	 * @param newS the new path length
	 * @param newU the new state vector
	 * @return the distance to the target (boundary) in cm.
	 */
	@Override
	public double distanceToTarget(double newS, double[] newU) {
		return Math.abs(newU[2] - _zTarget);
	}

	/**
	 * Add a second point creating a straight line to the target z
	 */
	@Override
	public void straightLine() {

		double u[] = _trajectory.get(_trajectory.size() - 1);
		double s = _trajectory.getS(_trajectory.size() - 1);

		double u2[] = findPoint(u[0], u[1], u[2], u[3], u[4], u[5], _zTarget);

		double dx = u2[0] - u[0];
		double dy = u2[1] - u[1];
		double dz = u2[2] - u[2];
		double ds = Math.sqrt(dx * dx + dy * dy + dz * dz);

		_trajectory.add(s + ds, u2);
		_status = CLAS12Swimmer.SWIM_SUCCESS;

	}


	 /**
     * Finds the point along the line of velocity where the z coordinate reaches zTarget.
     *
     * @param x0 Starting x coordinate
     * @param y0 Starting y coordinate
     * @param z0 Starting z coordinate
     * @param tx x component of the unit direction vector
     * @param ty y component of the unit direction vector
     * @param tz z component of the unit direction vector
     * @param zTarget The target z coordinate to reach
     * @return The point [x, y, z] where the z coordinate reaches zTarget, or null if it never reaches.
     */
    private double[] findPoint(double x0, double y0, double z0, double tx, double ty, double tz, double zTarget) {
        // Check if the line is parallel to the z-plane (tz = 0)
        if (tz == 0) {
            if (z0 == zTarget) {
                // The entire line is on the plane where z = zTarget
                return new double[]{x0, y0, zTarget};
            } else {
                // The line will never reach zTarget
                return null;
            }
        }

        // Calculate the parameter (s) at which z coordinate reaches zTarget
        double t = (zTarget - z0) / tz;

        // Calculate the x and y coordinates at this point
        double x = x0 + tx * t;
        double y = y0 + ty * t;

        return new double[]{x, y, zTarget, tx, ty, tz};
    }
}
