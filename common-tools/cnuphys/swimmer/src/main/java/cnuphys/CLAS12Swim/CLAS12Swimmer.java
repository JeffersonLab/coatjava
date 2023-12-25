package cnuphys.CLAS12Swim;

import java.util.Hashtable;

import cnuphys.adaptiveSwim.geometry.Plane;
import cnuphys.magfield.FieldProbe;
import cnuphys.magfield.IMagField;
import cnuphys.magfield.MagneticField;

/*
 * A swimmer using the Dormand-Prince 8(5,3) adaptive step size integrator.
 */
public class CLAS12Swimmer {
	
	// Speed of light in m/s
	public static final double C = 2.99792458e10; // cm/s
	
	/** currently swimming */
	public static final int SWIM_SWIMMING = 88;

	/** The swim was a success */
	public static final int SWIM_SUCCESS = 0;
	
	/**
	 * A target, such as a target rho or z, was not reached before the swim was
	 * stopped for some other reason
	 */
	public static final int SWIM_TARGET_MISSED = -1;
	
	
	public static final Hashtable<Integer, String> resultNames = new Hashtable<Integer, String>();
	static {
		resultNames.put(SWIM_SWIMMING, "SWIMMING");
		resultNames.put(SWIM_SUCCESS, "SWIM_SUCCESS");
		resultNames.put(SWIM_TARGET_MISSED, "SWIM_TARGET_MISSED");
	}

	
	// Minimum integration step size in meters
	private double MINSTEPSIZE = 1.0e-6; // cm
	
	// Maximum integration step size in meters
	private double MAXSTEPSIZE = Double.POSITIVE_INFINITY; // cm


	// The magnetic field probe.
	// NOTE: the method of interest in FieldProbe takes a position in cm
	// and returns a field in kG.
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
	 * The basic swim method. The swim is terminated when the particle reaches the
	 * pathlength sMax or if the optional listener terminates the swim.
	 * 
	 * @param ode       the ODE to solve
	 * @param u         the initial state vector (x, y, z, tx, ty, tz) x, y, z in cm
	 * @param sMin      the initial value of the independent variable (pathlength) (usually 0)
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 * @param listener  an optional listener that can terminate the integration
	 * @return the result of the swim
	 */
	protected CLAS12SwimResult baseSwim(CLAS12SwimmerODE ode, double u[], double sMin, double sMax, double h, 
			double tolerance, CLAS12Listener listener) {

		_listener = listener;
		//call the basic solver that swims to sMax
		DormandPrince.solve(ode, u, sMin, sMax, h, tolerance, MINSTEPSIZE, MAXSTEPSIZE, _listener);
		
		return new CLAS12SwimResult(_listener);

	}

	/**
	 * Swim to a target accuracy. The details of the target are in the CLAS12BoundaryListener.
	 * The "Interp" methods get an approximation to the intersection by interpolating. They
	 * are faster but less accurate than the corresponding non interp methods.
	 * 
	 * @param ode       the ODE to solve
	 * @param u         the initial state vector (x, y, z, tx, ty, tz) x, y, z in cm
	 * @param sMin      the initial value of the independent variable (pathlength)
	 *                  (usually 0)
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 * @param listener  the specific listener that can terminate the integration
	 * @return the result of the swim
	 * @see CLAS12BoundaryListener
	 */
	public CLAS12SwimResult swimToAccuracyInterp(CLAS12SwimmerODE ode, double u[], double sMin, double sMax, 
			double h, double tolerance, CLAS12BoundaryListener listener) {

	
		CLAS12SwimResult cres = baseSwim(ode, u, sMin, sMax, h, tolerance, listener);

		if (listener.getStatus() == SWIM_SUCCESS) {
			double u2[] = _listener.getU();
			double s2 = _listener.getS();
			_listener.getTrajectory().removeLastPoint();
			double u1[] = _listener.getU();
			double s1 = _listener.getS();
			
			double uInterp[] = new double[6];
			
			double sInterp = listener.interpolate(s1, u1, s2, u2, uInterp);
			
			_listener.getTrajectory().add(sInterp, uInterp);
		}
		
		return cres;
	}
	
	/**
	 * Swim to a target accuracy. The details of the target are in the CLAS12BoundaryListener
	 * 
	 * @param ode       the ODE to solve
	 * @param u         the initial state vector (x, y, z, tx, ty, tz) x, y, z in cm
	 * @param sMin      the initial value of the independent variable (pathlength)
	 *                  (usually 0)
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param accuracy  the target accuracy in cm
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 * @param listener  the specific listener that can terminate the integration
	 * @return the result of the swim
	 * @see CLAS12BoundaryListener
	 */
	public CLAS12SwimResult swimToAccuracy(CLAS12SwimmerODE ode, double u[], double sMin, double sMax, 
			double accuracy, double h, double tolerance, CLAS12BoundaryListener listener) {

		double s1 = sMin;
		double s2 = sMax;
		
		h = Math.min(h, accuracy/2.);
		
		//for interpolation to help set the step size
		double[] uInterp = new double[6];
		double sInterp;
		
		CLAS12SwimResult cres = null;
		while (true) {
			cres = baseSwim(ode, u, s1, s2, h, tolerance, listener);

			if (listener.getStatus() == SWIM_SUCCESS) {
				double u2[] = _listener.getU();
				s2 = _listener.getS();
				_listener.getTrajectory().removeLastPoint();
				
				if (listener.accuracyReached(cres.getPathLength(), cres.getFinalU())) {
					//do NOT forget to reset the max step size
					resetMaxStepSize();
					return cres;
				}

				u = _listener.getU();
				s1 = _listener.getS();
				
				//interpolate to the target
				sInterp = listener.interpolate(s1, u, s2, u2, uInterp);
				
				//reduce max size so we don't go way past the target
				setMaxStepSize((sInterp - s1)/5.);
				
				// remove last point again it will be restored as start of appended swim
				_listener.getTrajectory().removeLastPoint();

			} else { // failed, reached max path length
				//do NOT forget to reset the max step size
				resetMaxStepSize();
				return cres;
			}
		}
       
	}
	

	
	/**
	 * Basic swim method. The swim is terminated when the particle reaches the path length sMax
	 * or if the optional listener terminates the swim.
	 * @param charge    in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param p         the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
     */
	public CLAS12SwimResult  swim(int charge, double xo, double yo, double zo, double p, double theta, double phi,
			double sMax, double h, double tolerance) {

		//create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(charge, p, _probe);
		
		//create the initial values
		CLAS12Values ivals = new CLAS12Values(charge, xo, yo, zo, p, theta, phi);
		
		CLAS12Listener listener = new CLAS12Listener(ivals, sMax);
		return baseSwim(ode, ivals.getU(), 0, sMax, h, tolerance, listener);
	}
	
	/**
	 * Swim to a target plane
	 * @param charge    in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param momentum  the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param targetPlane   the target plane
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param accuracy  the desired accuracy in cm
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
     */
	public CLAS12SwimResult  swimPlane(int charge, double xo, double yo, double zo, double momentum, double theta, double phi,
			Plane targetPlane, double sMax, double accuracy, double h, double tolerance) {
		

		//create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(charge, momentum, _probe);
		
		//create the initial values
		CLAS12Values ivals = new CLAS12Values(charge, xo, yo, zo, momentum, theta, phi);
		
		CLAS12PlaneListener listener = new CLAS12PlaneListener(ivals, targetPlane, sMax, accuracy);
		
		return swimToAccuracy(ode, ivals.getU(), 0, sMax, accuracy, h, tolerance, listener);
	}
	
	
	/**
	 * Swim to a target plane using the faster but less accurate interpolation
	 * @param charge    in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param momentum  the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param targetPlane   the target plane
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param accuracy  the desired accuracy in cm
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
     */
	public CLAS12SwimResult  swimPlaneInterp(int charge, double xo, double yo, double zo, double momentum, double theta, double phi,
			Plane targetPlane, double sMax, double h, double tolerance) {
		

		//create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(charge, momentum, _probe);
		
		//create the initial values
		CLAS12Values ivals = new CLAS12Values(charge, xo, yo, zo, momentum, theta, phi);
		
		CLAS12PlaneListener listener = new CLAS12PlaneListener(ivals, targetPlane, sMax, 0);
		
		return swimToAccuracyInterp(ode, ivals.getU(), 0, sMax, h, tolerance, listener);
		
	}


	
	/**
	 * Swim to a target z in cm
	 * @param charge    in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param momentum  the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param zTarget   the target z position in cm
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param accuracy  the desired accuracy in cm
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
     */
	public CLAS12SwimResult  swimZ(int charge, double xo, double yo, double zo, double momentum, double theta, double phi,
			double zTarget, double sMax, double accuracy, double h, double tolerance) {
		

		//create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(charge, momentum, _probe);
		
		//create the initial values
		CLAS12Values ivals = new CLAS12Values(charge, xo, yo, zo, momentum, theta, phi);
		
		CLAS12ZListener listener = new CLAS12ZListener(ivals, zTarget, sMax, accuracy);
		
		return swimToAccuracy(ode, ivals.getU(), 0, sMax, accuracy, h, tolerance, listener);
	}
	
	/**
	 * Swim to a target z in cm using the faster but less accurate interpolation
	 * @param charge    in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param momentum  the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param zTarget   the target z position in cm
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
     */
	public CLAS12SwimResult  swimZInterp(int charge, double xo, double yo, double zo, double momentum, double theta, double phi,
			double zTarget, double sMax, double h, double tolerance) {
		

		//create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(charge, momentum, _probe);
		
		//create the initial values
		CLAS12Values ivals = new CLAS12Values(charge, xo, yo, zo, momentum, theta, phi);
		
		CLAS12ZListener listener = new CLAS12ZListener(ivals, zTarget, sMax, 0);
		
		return swimToAccuracyInterp(ode, ivals.getU(), 0, sMax, h, tolerance, listener);
	}

	
	/**
	 * Swim to a target rho (cylindrical r) in cm
	 * @param charge    in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param momentum  the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param rhoTarget   the target rho position in cm
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
     * @param accuracy  the desired accuracy in cm
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
     */
	public CLAS12SwimResult  swimRho(int charge, double xo, double yo, double zo, double momentum, double theta, double phi,
			double rhoTarget, double sMax, double accuracy, double h, double tolerance) {
		

		//create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(charge, momentum, _probe);
		
		//create the initial values
		CLAS12Values ivals = new CLAS12Values(charge, xo, yo, zo, momentum, theta, phi);
		
		CLAS12RhoListener listener = new CLAS12RhoListener(ivals, rhoTarget, sMax, accuracy);
		
		return swimToAccuracy(ode, ivals.getU(), 0, sMax, accuracy, h, tolerance, listener);
	}
	
	/**
	 * Swim to a target rho (cylindrical r) in cm
	 * @param charge    in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param momentum  the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param rhoTarget   the target rho position in cm
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
 	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
     */
	public CLAS12SwimResult  swimRhoInterp(int charge, double xo, double yo, double zo, double momentum, double theta, double phi,
			double rhoTarget, double sMax, double h, double tolerance) {
		

		//create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(charge, momentum, _probe);
		
		//create the initial values
		CLAS12Values ivals = new CLAS12Values(charge, xo, yo, zo, momentum, theta, phi);
		
		CLAS12RhoListener listener = new CLAS12RhoListener(ivals, rhoTarget, sMax, 0);
		
		return swimToAccuracyInterp(ode, ivals.getU(), 0, sMax, h, tolerance, listener);
	}


	/**
	 * Set the maximum integration step size in cm
	 * @param maxStepSize the maximum integration step size in cm
	 */
	public void setMaxStepSize(double maxStepSize) {
		MAXSTEPSIZE = maxStepSize;
	}
	
	/**
	 * Reset the maximum integration step size to infinity
     */
	public void resetMaxStepSize() {
		MAXSTEPSIZE = Double.POSITIVE_INFINITY;
	}
		
	/**
     * Get the result of the swim
     * @return the result of the swim in the listener
     */
	public CLAS12Listener getListener() {
		return _listener;
    }

}
