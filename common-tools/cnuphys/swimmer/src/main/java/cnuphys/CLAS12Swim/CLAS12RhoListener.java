package cnuphys.CLAS12Swim;

import java.io.File;
import java.io.FileNotFoundException;

import cnuphys.magfield.FastMath;
import cnuphys.magfield.MagneticFieldInitializationException;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;

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
	 * @param newS the new path length
	 * @param newU the new state vector
	 * @return the distance to the target (boundary) in cm.
	 */
	public double distanceToTarget(double newS, double[] newU) {
		return Math.abs(rho(newU) - _rhoTarget);
	}


	/**
	 * Interpolate between two points, one on each side of the boundary
	 *
	 * @param s1 the path length of the "left" point (cm)
	 * @param u1 the state vector of the "left" point
	 * @param s2 the path length of the "right" point (cm)
	 * @param u2 the state vector of the "right" point
	 * @param u  will hold the interpolated state vector
	 * @return the interpolated path length
	 */
	@Override
	public double interpolate(double s1, double[] u1, double s2, double[] u2, double u[]) {

		// simple linear interpolation
		double rho1 = rho(u1);
		double rho2 = rho(u2);

		// unlikely, but just in case
		if (Math.abs(rho2 - rho1) < TINY) {
			System.arraycopy(u2, 0, u, 0, 6);
			return s2;
		}

		double t = (_rhoTarget - rho1) / (rho2 - rho1);
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

		int q = 1;
		double p = 2.0;
		double theta = 15;
		double phi = 5;
		double xo = 0.01;
		double yo = 0.02;
		double zo = -0.01;
		double rhotarget = 300;
		double accuracy = 1.0e-5; // cm
		double stepsizeAdaptive = accuracy / 10; // starting stepsize in cm
		double sMax = 800; // cm
		double tolerance = 1.0e-6;

		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); // new

		CLAS12SwimResult c12res = clas12Swimmer.swimRho(q, xo, yo, zo, p, theta, phi, rhotarget, accuracy, sMax,
				stepsizeAdaptive, tolerance);

		System.out.println("DP ACCURATE result:  " + c12res.toString() + "\n");

		// compare to interpolated approx

		c12res = clas12Swimmer.swimRhoInterp(q, xo, yo, zo, p, theta, phi, rhotarget, sMax, stepsizeAdaptive,
				tolerance);
		System.out.println("DP INTERP result:  " + c12res.toString() + "\n");

	}

}
