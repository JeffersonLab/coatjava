package cnuphys.CLAS12Swim;

import cnuphys.magfield.FastMath;

/**
 * A listener for swimming to a fixed cylindrical radius (rho).
 */
public class CLAS12RhoListener extends CLAS12BoundaryListener {

	// the target rho (cm)
	private double _rhoTarget;

	// the starting sign. When this changes we have crossed.
	private double _startSign;

	/**
	 * Create a CLAS12 boundary target Z listener, for swimming to a fixed z
	 *
	 * @param ivals     the initial values of the swim
	 * @param rhoTarget the target rho (cylindrical r) (cm)
	 * @param accuracy  the desired accuracy (cm)
	 * @param sMax      the final or max path length (cm)
	 */
	public CLAS12RhoListener(CLAS12Values ivals, double rhoTarget, double accuracy, double sMax) {
		super(ivals, accuracy, sMax);
		_rhoTarget = rhoTarget;

		double x = ivals.x;
		double y = ivals.y;
		_startSign = sign(Math.hypot(x, y));
	}

	@Override
	public boolean crossedBoundary(double newS, double[] newU) {
		int sign = sign(rho(newU));

		if (sign != _startSign) {
			return true;
		}
		return false;
	}

	// the rho (cylindrical r) of the state vector in cm
	private double rho(double u[]) {
		double x = u[0];
		double y = u[1];
		return FastMath.hypot(x, y);
	}

	@Override
	public boolean accuracyReached(double newS, double[] newU) {
		double dRho = Math.abs(rho(newU) - _rhoTarget);
		return dRho < _accuracy;
	}

	// left or right of the target rho?
	private int sign(double rho) {
		return (rho < _rhoTarget) ? -1 : 1;
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
		return Math.abs(rho(newU) - _rhoTarget);
	}

	/**
	 * Add a second point creating a straight line to the target rho
	 */
	@Override
	public void straightLine() {

		double u[] = _trajectory.get(_trajectory.size() - 1);
		double s = _trajectory.getS(_trajectory.size() - 1);

		double u2[] = findPoint(u[0], u[1], u[2], u[3], u[4], u[5], _rhoTarget);

		double dx = u2[0] - u[0];
		double dy = u2[1] - u[1];
		double dz = u2[2] - u[2];
		double ds = Math.sqrt(dx * dx + dy * dy + dz * dz);

		_trajectory.add(s + ds, u2);
		_status = CLAS12Swimmer.SWIM_SUCCESS;

	}

	private double[] findPoint(double x0, double y0, double z0, double tx, double ty, double tz, double rTarget) {
		// Calculate the coefficients of the quadratic equation
		double a = tx * tx + ty * ty;
		double b = 2 * (x0 * tx + y0 * ty);
		double c = x0 * x0 + y0 * y0 - rTarget * rTarget;

		// Solve the quadratic equation
		double discriminant = b * b - 4 * a * c;
		if (discriminant < 0) {
			return null; // No real solutions, rTarget cannot be reached
		}

		// Find the two possible values of s
		double t1 = (-b + Math.sqrt(discriminant)) / (2 * a);
		double t2 = (-b - Math.sqrt(discriminant)) / (2 * a);

		// Choose the appropriate t (the one with the smaller positive value)

		if (t1 < 0 && t2 < 0) {
			return null;
		}

		double t;
		if (t1 < 0) {
			t = t2;
		} else if (t2 < 0) {
			t = t1;
		} else {
			t = Math.min(t1, t2);
		}

		// Calculate the resulting point
		double x = x0 + tx * t;
		double y = y0 + ty * t;
		double z = z0 + tz * t;

		return new double[] { x, y, z, tx, ty, tz };
	}

}
