package cnuphys.CLAS12Swim;

import java.io.File;
import java.io.FileNotFoundException;

import cnuphys.magfield.MagneticFieldInitializationException;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;

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
	 * Interpolate between two points, one on each side of the boundary
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

		// simple linear interpolation
		double z1 = u1[2];
		double z2 = u2[2];

		// unlikely, but just in case
		if (Math.abs(z2 - z1) < TINY) {
			System.arraycopy(u2, 0, u, 0, 6);
			return s2;
		}

		double t = (_zTarget - z1) / (z2 - z1);
		double s = s1 + t * (s2 - s1);

		for (int i = 0; i < 6; i++) {
			u[i] = u1[i] + t * (u2[i] - u1[i]);
		}

		return s;

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

		int q = -1;
		double p = 2.0;
		double theta = 15;
		double phi = 5;
		double xo = 0.01;
		double yo = 0.02;
		double zo = -0.01;
		double ztarget = 575;
		double accuracy = 1.0e-5; // cm
		double stepsizeAdaptive = accuracy / 10; // starting stepsize in cm
		double sMax = 800; // cm
		double tolerance = 1.0e-6;

		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); // new

		CLAS12SwimResult c12res = clas12Swimmer.swimZ(q, xo, yo, zo, p, theta, phi, ztarget, accuracy, sMax,
				stepsizeAdaptive, tolerance);
		System.out.println("DP ACCURATE result:  " + c12res.toString() + "\n");

// compare to interpolated approx

		c12res = clas12Swimmer.swimZInterp(q, xo, yo, zo, p, theta, phi, ztarget, sMax, stepsizeAdaptive, tolerance);
		System.out.println("DP INTERP result:  " + c12res.toString() + "\n");

	}

}
