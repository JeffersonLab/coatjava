package cnuphys.dormandPrince;

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

	/** A swim was requested exceeded the max number of tries */
	public static final int SWIM_EXCEED_MAX_TRIES = -3;

	/** A swim crossed a boundary, need to back up and reduce h */
	public static final int SWIM_CROSSED_BOUNDARY = -4;

	/** A swim was requested for a neutral particle */
	public static final int SWIM_NEUTRAL_PARTICLE = 10;

	// Minimum momentum to swim in GeV/c
	public static final double MINMOMENTUM = 5e-05;

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
	 * @param minH      The minimum step size to prevent an infinite loop.
	 * @param listener  An optional listener that will be called after each step.It can also
	 *                 terminate the integration.
	 * @return the result of the swim in a SwimResult object
	 * @throws SwimException
	 * @see SwimResult
     * @see SwimException
     */
	public SwimResult swim(int charge, double xo, double yo, double zo, double momentum, double theta, double phi,
			double sMax, double h, double tolerance, double minH, ODEStepListener listener) throws SwimException {

		SwimmerODE ode = initODE(charge, momentum);
		return null;

	}
	
	/**
	 * Initialize the ODE for swimming
	 * 
	 * @param charge    in integer units of e
	 * @param momentum  the momentum in GeV/c
	 */
	private SwimmerODE initODE(int charge, double momentum) {
		return  new SwimmerODE(charge, momentum, _probe);
	}

}
