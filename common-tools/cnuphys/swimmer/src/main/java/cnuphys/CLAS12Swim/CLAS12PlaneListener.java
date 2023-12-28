package cnuphys.CLAS12Swim;

import java.io.File;
import java.io.FileNotFoundException;

import cnuphys.adaptiveSwim.geometry.Plane;
import cnuphys.magfield.MagneticFieldInitializationException;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;

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
	 * @param sMax        the final or max path length (cm)
	 * @param accuracy    the desired accuracy (cm)
	 */
	public CLAS12PlaneListener(CLAS12Values ivals, Plane targetPlane, double sMax, double accuracy) {
		super(ivals, sMax, accuracy);
		_targetPlane = targetPlane;
		_startSign = _targetPlane.sign(ivals.x, ivals.y, ivals.z);
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
	 * Interpolate between two points, one on each side of the plane
	 *
	 * @param s1 the path length of the "left" point (cm)
	 * @param u1 the state vector of the "left" point
	 * @param s2 the path length of the "right" point (cm)
	 * @param u2 the state vector of the "right" point
	 * @param u  will hold the interpolated state vector
	 * @return the interpolated path length (cm)
	 */
	@Override
	public double interpolate(double s1, double[] u1, double s2, double[] u2, double u[]) {

		double t = _targetPlane.lineSegmentPlaneIntersection(u1, u2, u);

		if (Double.isNaN(t)) {
			return Double.NaN;
		}

		return s1 + t * (s2 - s1);

	}

	// used for testing
	public static void main(String arg[]) {
		final MagneticFields mf = MagneticFields.getInstance();
		File mfdir = new File(System.getProperty("user.home"), "magfield");
		System.out.println("mfdir exists: " + (mfdir.exists() && mfdir.isDirectory()));
		try {
			mf.initializeMagneticFields(mfdir.getPath(), "Symm_torus_r2501_phi16_z251_24Apr2018.dat",
					"Symm_solenoid_r601_phi1_z1201_13June2018.dat");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (MagneticFieldInitializationException e) {
			e.printStackTrace();
			System.exit(1);
		}

		System.out.println("Active Field Description: " + MagneticFields.getInstance().getActiveFieldDescription());

		Plane targetPlane = new Plane(1, 1, 1, 1, 1, 1);

		int q = -1;
		double p = 2.0;
		double theta = 25;
		double phi = 5;
		double xo = 0.01;
		double yo = 0.02;
		double zo = -0.01;
		double accuracy = 1.0e-5; // cm
		double stepsizeAdaptive = accuracy / 10; // starting stepsize in cm
		double sMax = 800; // cm
		double eps = 1.0e-6;

		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); // new

		CLAS12SwimResult c12res = clas12Swimmer.swimPlane(q, xo, yo, zo, p, theta, phi, targetPlane, accuracy, sMax,
				stepsizeAdaptive, eps);
		System.out.println("DP ACCURATE result:  " + c12res.toString() + "\n");
		double[] u = c12res.getFinalU();
		System.out.println("distance to plane: " + targetPlane.distance(u[0], u[1], u[2]) + " cm");

// compare to interpolated approx

		c12res = clas12Swimmer.swimPlaneInterp(q, xo, yo, zo, p, theta, phi, targetPlane, sMax, stepsizeAdaptive, eps);
		System.out.println("DP INTERP result:  " + c12res.toString() + "\n");
		u = c12res.getFinalU();
		System.out.println("distance to plane: " + targetPlane.distance(u[0], u[1], u[2]) + " cm");

	}

}
