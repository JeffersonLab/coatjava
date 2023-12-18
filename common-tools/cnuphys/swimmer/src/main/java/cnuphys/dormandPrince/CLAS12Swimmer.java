package cnuphys.dormandPrince;

import java.util.Hashtable;

import cnuphys.magfield.FieldProbe;
import cnuphys.magfield.IMagField;
import cnuphys.magfield.MagneticField;

/*
 * A swimmer using the Dormand-Prince 8(5,3) adaptive step size integrator.
 */
public class CLAS12Swimmer {
	
	// Speed of light in m/s
	public static final double C = 299792458.0; // m/s
	
	/** currently swimming */
	public static final int SWIM_SWIMMING = 88;

	/** The swim was a success */
	public static final int SWIM_SUCCESS = 0;
	
	/**
	 * A target, such as a target rho or z, was not reached before the swim was
	 * stopped for some other reason
	 */
	public static final int SWIM_TARGET_MISSED = -1;

	/** A swim was requested for a particle with extremely low momentum */
	public static final int SWIM_BELOW_MIN_P = -2;


	/** A swim was requested for a neutral particle */
	public static final int SWIM_NEUTRAL_PARTICLE = 10;
	
	public static final Hashtable<Integer, String> resultNames = new Hashtable<Integer, String>();
	static {
		resultNames.put(SWIM_SWIMMING, "SWIMMING");
		resultNames.put(SWIM_SUCCESS, "SWIM_SUCCESS");
		resultNames.put(SWIM_TARGET_MISSED, "SWIM_TARGET_MISSED");
		resultNames.put(SWIM_BELOW_MIN_P, "SWIM_BELOW_MIN_P");
		resultNames.put(SWIM_NEUTRAL_PARTICLE, "SWIM_NEUTRAL_PARTICLE");
	}


	// Minimum momentum to swim in GeV/c
	public static final double MINMOMENTUM = 5e-05;
	
	// Minimum integration step size in meters
	public static final double MINSTEPSIZE = 1.0e-9; // meters

	// The magnetic field probe.
	// NOTE: the method of interest in FieldProbe takes a position in cm
	// and returns a field in kG.This swim package works in SI (meters and
	// kG.)
	private FieldProbe _probe;

	
	/**
	 * Create a swimmer using the current active field
	 */
	public CLAS12Swimmer() {
		// make a probe using the current active field
		this(FieldProbe.factory());
	}

	/**
	 * Create a swimmer specific to a magnetic field
	 *
	 * @param magneticField the magnetic field
	 */
	public CLAS12Swimmer(MagneticField magneticField) {
		this(FieldProbe.factory(magneticField));
	}

	/**
	 * Create a swimmer specific to a magnetic field
	 *
	 * @param magneticField the magnetic field
	 */
	public CLAS12Swimmer(IMagField magneticField) {
		this(FieldProbe.factory(magneticField));
	}
	
	/**
	 * Create a swimmer specific to a magnetic field probe
	 *
	 * @param probe the magnetic field probe
	 */
	public CLAS12Swimmer(FieldProbe probe) {
		_probe = probe;
	}
	
	/**
	 * The listener that can stop the integration and stores results
	 */
	protected CLAS12Listener _listener;

	/**
	 * Get the probe being used to swim
	 *
	 * @return the probe
	 */
	public FieldProbe getProbe() {
		return _probe;
	}
	
	
	/**
	 * Basic swim method. The swim is terminated when the particle reaches the pathlength sMax
	 * or if the optional listener terminates the swim.
	 * @param charge    in integer units of e
	 * @param xo        the x vertex position in meters
	 * @param yo        the y vertex position in meters
	 * @param zo        the z vertex position in meters
	 * @param momentum  the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param h         the initial stepsize in meters
	 * @param tolerance The desired accuracy. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 * @param cacheTrajectory whether or not to cache the trajectory
	 * @throws SwimException
	 * @see SwimResult
     * @see SwimException
     */
	public void  swim(int charge, double xo, double yo, double zo, double momentum, double theta, double phi,
			double sMax, double h, double tolerance, boolean cacheTrajectory) throws SwimException {

		//create the ODE
		SwimmerODE ode = new SwimmerODE(charge, momentum, _probe);
		
		//create the initial values
		InitialValues ivals = new InitialValues(charge, xo, yo, zo, momentum, theta, phi);
		
		//use the base listener
		_listener = new CLAS12Listener(ivals, sMax, cacheTrajectory); 
		
		//call the basic solver that swims to sMax
		DormandPrince.solve(ode, ivals.getUo(), 0, sMax, h, tolerance, MINSTEPSIZE, _listener);

	}
	
	/**
     * Get the result of the swim
     * @return the result of the swim in the listener
     */
	public CLAS12Listener getListener() {
		return _listener;
    }


}
