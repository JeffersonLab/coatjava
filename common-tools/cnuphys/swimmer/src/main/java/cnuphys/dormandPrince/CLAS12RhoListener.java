package cnuphys.dormandPrince;

import java.io.File;
import java.io.FileNotFoundException;

import cnuphys.magfield.FastMath;
import cnuphys.magfield.MagneticFieldInitializationException;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;

public class CLAS12RhoListener extends CLAS12BoundaryListener {
	
	//the target rho (cm)
	private double _rhoTarget;
	
	//the starting sign. When this changes we have crossed.
	private double _startSign;

	/**
	 * Create a CLAS12 boundary target Z listener, for swimming to a fixed z
	 * 
	 * @param ivals           the initial values of the swim
	 * @param rhoTarget       the target rho (cylindrical r) (cm)
	 * @param sFinal          the final or max path length (cm)
	 * @param accuracy        the desired accuracy (cm)
	 */
	public CLAS12RhoListener(CLAS12Values ivals, double rhoTarget, double sFinal, double accuracy) {
		super(ivals, sFinal, accuracy);
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
	
	//the rho (cylindrical r) of the state vector in cm
	private double rho(double u[]) {
		double x = u[0];
		double y = u[1];
		return FastMath.hypot(x, y);
	}
	
	@Override
	public boolean accuracyReached(double newS, double[] newU) {
		double dRho = Math.abs(rho(newU) - _rhoTarget);
//		System.err.println("dRho: " + dRho);
		return dRho < _accuracy;
	}

	// left or right of the target rho?
	private int sign(double rho) {
		return (rho < _rhoTarget) ? -1 : 1;
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
		double accuracy = 0.0001; //cm
		double stepsizeAdaptive = 0.0001; // starting stepsize in cm
		double maxS = 800; // cm
		double eps = 1.0e-7;

		
		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); //new
		

		CLAS12SwimResult c12res = clas12Swimmer.swimRho(q, xo, yo, zo, p, theta, phi, rhotarget, maxS, accuracy, stepsizeAdaptive, eps);
		System.out.println("DP result:  " + c12res.toString() + "\n");


		
	}



}
