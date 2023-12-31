package cnuphys.CLAS12Swim;

import java.io.File;
import java.io.FileNotFoundException;

import cnuphys.magfield.MagneticFieldInitializationException;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;

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
	public double distanceToTarget(double newS, double[] newU) {
		return Math.abs(newU[2] - _zTarget);
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
		System.out.println("C12 result:  " + c12res.toString() + "\n");

	}

}
