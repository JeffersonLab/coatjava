package cnuphys.dormandPrince;

import java.io.File;
import java.io.FileNotFoundException;

import cnuphys.magfield.FastMath;
import cnuphys.magfield.MagneticFieldInitializationException;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;

public class CLAS12ZListener extends CLAS12BoundaryListener {
	
	//the target z (m)
	private double _zTarget;
	
	//the starting sign. When this changes we have crossed.
	private double _startSign;

	/**
	 * Create a CLAS12 boundary target Z listener, for swimming to a fixed z
	 * 
	 * @param ivals           the initial values of the swim
	 * @param zTarget         the target z (cm)
	 * @param sFinal          the final or max path length (cm)
	 * @param accuracy        the desired accuracy (cm)
	 */
	public CLAS12ZListener(CLAS12Values ivals, double zTarget, double sFinal, double accuracy) {
		super(ivals, sFinal, accuracy);
		_zTarget = zTarget;
		_startSign = sign(ivals.z);
	}

	@Override
	public boolean crossedBoundary(double newS, double[] newU) {
		double newZ = newU[2];
		int sign = sign(newZ);
		
		if (sign != _startSign) {
			//we crossed
			//handleCrossing(newS, newU);
			return true;
		}
		return false;
	}
	
	@Override
	public boolean accuracyReached(double newS, double[] newU) {
		double dZ = Math.abs(newU[2] - _zTarget);
		System.err.println("dZ = " + dZ + " cm    accuracy = " + _accuracy + " cm");

		if (dZ < _accuracy) {
			System.err.println("ACC REACHED");
		}
		return dZ < _accuracy;
	}

	@Override
	public void handleCrossing(double newS, double[] newU) {
		//if we reached here we have crossed the boundary and
		//have not yet reached the desired accuracy. We do not return from this method
		//until we can set the status to success.
		
		//lets try a linear interpolation
		double[] u1 = _trajectory.get(_trajectory.size()-2);
		double[] u2 = _trajectory.get(_trajectory.size()-1);
		double s1 = _trajectory.getS(_trajectory.size()-2);
		double s2 = _trajectory.getS(_trajectory.size()-1);
		double[] uInterp = new double[6];
	    double sTarget = interpolateAtZTarget(u1, u2, s1, s2, _zTarget, uInterp);
	    
	    _trajectory.replaceLastPoint(sTarget, uInterp);
	}
	
	private int sign(double z) {
		return (z < _zTarget) ? -1 : 1;
	}
	
	/**
	 * Linearly interpolate to the target z in cm
	 * @param u1 a point on one side of the target
	 * @param u2 a point on the other side of the target
	 * @param s1 the path length at u1
	 * @param s2 the path length at u2
	 * @param zTarget the target z in cm
	 * @param uInterp to hold the interpolated state vector
	 * @return the interpolated path length
	 */
	private static double interpolateAtZTarget(double[] u1, double[] u2, double s1, double s2, double zTarget,
			double[] uInterp) {

		double z1 = u1[2];
		double z2 = u2[2];

		// s interpolation
		double sTarget = s1 + ((s2 - s1) / (z2 - z1)) * (zTarget - z1);
		for (int i = 0; i < 6; i++) {
			uInterp[i] = u1[i] + ((u2[i] - u1[i]) / (s2 - s1)) * (sTarget - s1);
		}

		return sTarget;
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
		double ztarget = 575.00067;
		double accuracy = 0.0001; //cm
		
		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); //new
		
		double stepsizeAdaptive = 0.0001; // starting stepsize in cm

		double maxS = 800; // cm
		double eps = 1.0e-7;

		CLAS12SwimResult c12res = clas12Swimmer.swimZ(q, xo, yo, zo, p, theta, phi, ztarget, maxS, accuracy, stepsizeAdaptive, eps);
		System.out.println("DP result:  " + c12res.toString() + "\n");


		
	}



}
