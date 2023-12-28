package cnuphys.CLAS12Swim;

import java.util.Hashtable;

import cnuphys.adaptiveSwim.geometry.Cylinder;
import cnuphys.adaptiveSwim.geometry.Plane;
import cnuphys.magfield.FieldProbe;
import cnuphys.magfield.IMagField;

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

	/** The swim skipped too low momentum */
	public static final int BELOW_MIN_MOMENTUM = -2;

	/** A swim was requested for a neutral particle */
	public static final int SWIM_NEUTRAL_PARTICLE = -3;

	public static final Hashtable<Integer, String> resultNames = new Hashtable<>();
	static {
		resultNames.put(SWIM_SWIMMING, "SWIMMING");
		resultNames.put(SWIM_SUCCESS, "SWIM_SUCCESS");
		resultNames.put(SWIM_TARGET_MISSED, "SWIM_TARGET_MISSED");
		resultNames.put(BELOW_MIN_MOMENTUM, "BELOW_MIN_MOMENTUM");
		resultNames.put(SWIM_NEUTRAL_PARTICLE, "SWIM_NEUTRAL_PARTICLE");

	}

	// min momentum in GeV/c
	private double MINMOMENTUM = 5e-05; // GeV/c

	// Minimum integration step size in meters
	private double MINSTEPSIZE = 1.0e-6; // cm

	// Maximum integration step size in meters
	private double MAXSTEPSIZE = Double.POSITIVE_INFINITY; // cm

	// The magnetic field probe.
	// NOTE: the method of interest in FieldProbe takes a position in cm
	// and returns a field in kG.
	private FieldProbe _probe;

	// which integrator are we using?
	private EIntegrator _solver = EIntegrator.Fehlberg;

	/**
	 * Create a swimmer using the current active field
	 */
	public CLAS12Swimmer() {
		// make a probe using the current active field
		this(FieldProbe.factory());
	}

	/**
	 * Create a swimmer using a specific integrator
	 *
	 * @param solver the integrator to use
	 */
	public CLAS12Swimmer(EIntegrator solver) {
		this();
		_solver = solver;
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
	 * Set the integrator to use
	 *
	 * @param solver the integrator to use
	 */
	public void setSolver(EIntegrator solver) {
		_solver = solver;
	}

	/**
	 * Get the integrator being used
	 *
	 * @return the integrator being used
	 */
	public EIntegrator getSolver() {
		return _solver;
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
	 * @param sMin      the initial value of the independent variable (pathlength)
	 *                  (usually 0)
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
		// all swims pass through here

		if (_listener.getIvals().p < MINMOMENTUM) {
			System.err.println("CLAS12Swimmer: skipped below min momentum swim");
			_listener.setStatus(BELOW_MIN_MOMENTUM);
			return new CLAS12SwimResult(_listener);
		}

		// neutral? Just return a line
		if (listener.getIvals().q == 0) {
			System.err.println("CLAS12Swimmer: neutral particle swimm");
			listener.straightLine();
			listener.setStatus(SWIM_NEUTRAL_PARTICLE);
			return new CLAS12SwimResult(_listener);
		}

		switch (_solver) {
		case Fehlberg:
			Fehlberg.solve(ode, u, sMin, sMax, h, tolerance, MINSTEPSIZE, MAXSTEPSIZE, _listener);
			break;
		case DormandPrince:
			DormandPrince.solve(ode, u, sMin, sMax, h, tolerance, MINSTEPSIZE, MAXSTEPSIZE, _listener);
			break;
		case CashKarp:
			CashKarp.solve(ode, u, sMin, sMax, h, tolerance, MINSTEPSIZE, MAXSTEPSIZE, _listener);
			break;
		default:
			DormandPrince.solve(ode, u, sMin, sMax, h, tolerance, MINSTEPSIZE, MAXSTEPSIZE, _listener);
		}

		return new CLAS12SwimResult(_listener);

	}

	/**
	 * Swim to a target via interpolation. The details of the target are in the
	 * CLAS12BoundaryListener. The "Interp" methods get an approximation to the
	 * intersection by interpolating. They are faster but less accurate than the
	 * corresponding non interp methods.
	 *
	 * @param ode       the ODE to solve
	 * @param u         the initial state vector (x, y, z, tx, ty, tz) x, y, z in cm
	 * @param sMin      the initial value of the independent variable (pathlength)
	 *                  (usually 0)
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 * @param listener  the specific listener that can terminate the integration
	 * @return the result of the swim
	 * @see CLAS12BoundaryListener
	 */
	private CLAS12SwimResult swimToInterp(CLAS12SwimmerODE ode, double u[], double sMin, double h, double tolerance,
			CLAS12BoundaryListener listener) {

		CLAS12SwimResult cres = baseSwim(ode, u, sMin, listener.getSMax(), h, tolerance, listener);

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
	 * Swim to a target accuracy. The details of the target and the accuracy are in
	 * the CLAS12BoundaryListener
	 *
	 * @param ode       the ODE to solve
	 * @param u         the initial state vector (x, y, z, tx, ty, tz) x, y, z in cm
	 * @param sMin      the initial value of the independent variable (pathlength)
	 *                  (usually 0)
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 * @param listener  the specific listener that can terminate the integration
	 * @return the result of the swim
	 * @see CLAS12BoundaryListener
	 */
	private CLAS12SwimResult swimToAccuracy(CLAS12SwimmerODE ode, double u[], double sMin, double h, double tolerance,
			CLAS12BoundaryListener listener) {

		double s1 = sMin;
		double s2 = listener.getSMax();

		h = Math.min(h, listener.getAccuracy() / 2.);

		// for interpolation to help set the step size
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
					// do NOT forget to reset the max step size
					resetMaxStepSize();
					return cres;
				}

				u = _listener.getU();
				s1 = _listener.getS();

				// interpolate to the target
				sInterp = listener.interpolate(s1, u, s2, u2, uInterp);

				// reduce max size so we don't go way past the target
				setMaxStepSize((sInterp - s1) / 5.);

				// remove last point again it will be restored as start of appended swim
				_listener.getTrajectory().removeLastPoint();

			} else { // failed, reached max path length
				// do NOT forget to reset the max step size
				resetMaxStepSize();
				return cres;
			}
		}

	}

	/**
	 * Basic swim method. The swim is terminated when the particle reaches the path
	 * length sMax or if the optional listener terminates the swim.
	 *
	 * @param q         in integer units of e
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
	public CLAS12SwimResult swim(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double sMax, double h, double tolerance) {

		// create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12Listener listener = new CLAS12Listener(ivals, sMax);
		return baseSwim(ode, ivals.getU(), 0, sMax, h, tolerance, listener);
	}

	/**
	 * Swim to a target cylinder
	 *
	 * @param q         in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param p         the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param p1        the first point on the cylinder axis
	 * @param p2        the second point on the cylinder axis
	 * @param r         the radius of the cylinder (cm)
	 * @param accuracy  the desired accuracy in cm
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 */
	public CLAS12SwimResult swimCylinder(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double p1[], double p2[], double r, double accuracy, double sMax, double h, double tolerance) {

		Cylinder targetCylinder = new Cylinder(p1, p2, r);
		return swimCylinder(q, xo, yo, zo, p, theta, phi, targetCylinder, accuracy, sMax, h, tolerance);
	}

	/**
	 * Swim to a target cylinder
	 *
	 * @param q              charge in integer units of e
	 * @param xo             the x vertex position in cm
	 * @param yo             the y vertex position in cm
	 * @param zo             the z vertex position in cm
	 * @param p              the momentum in GeV/c
	 * @param theta          the initial polar angle in degrees
	 * @param phi            the initial azimuthal angle in degrees
	 * @param targetCylinder the target cylinder
	 * @param accuracy       the desired accuracy in cm
	 * @param sMax           the final (max) value of the independent variable
	 *                       (pathlength) unless the integration is terminated by
	 *                       the listener
	 * @param h              the initial stepsize in cm
	 * @param tolerance      The desired tolerance. The solver will automatically
	 *                       adjust the step size to meet this tolerance.
	 */
	public CLAS12SwimResult swimCylinder(int q, double xo, double yo, double zo, double p, double theta, double phi,
			Cylinder targetCylinder, double accuracy, double sMax, double h, double tolerance) {

		// create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12CylinderListener listener = new CLAS12CylinderListener(ivals, targetCylinder, accuracy, sMax);

		return swimToAccuracy(ode, ivals.getU(), 0, h, tolerance, listener);
	}

	/**
	 * Swim to a target plane
	 *
	 * @param q           in integer units of e
	 * @param xo          the x vertex position in cm
	 * @param yo          the y vertex position in cm
	 * @param zo          the z vertex position in cm
	 * @param p           the momentum in GeV/c
	 * @param theta       the initial polar angle in degrees
	 * @param phi         the initial azimuthal angle in degrees
	 * @param targetPlane the target plane
	 * @param accuracy    the desired accuracy in cm
	 * @param sMax        the final (max) value of the independent variable
	 *                    (pathlength) unless the integration is terminated by the
	 *                    listener
	 * @param h           the initial stepsize in cm
	 * @param tolerance   The desired tolerance. The solver will automatically
	 *                    adjust the step size to meet this tolerance.
	 */
	public CLAS12SwimResult swimPlane(int q, double xo, double yo, double zo, double p, double theta, double phi,
			Plane targetPlane, double accuracy, double sMax, double h, double tolerance) {

		// create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12PlaneListener listener = new CLAS12PlaneListener(ivals, targetPlane, accuracy, sMax);

		return swimToAccuracy(ode, ivals.getU(), 0, h, tolerance, listener);
	}

	/**
	 * Swim to a target plane using the faster but less accurate interpolation
	 *
	 * @param q           in integer units of e
	 * @param xo          the x vertex position in cm
	 * @param yo          the y vertex position in cm
	 * @param zo          the z vertex position in cm
	 * @param p           the momentum in GeV/c
	 * @param theta       the initial polar angle in degrees
	 * @param phi         the initial azimuthal angle in degrees
	 * @param targetPlane the target plane
	 * @param sMax        the final (max) value of the independent variable
	 *                    (pathlength) unless the integration is terminated by the
	 *                    listener
	 * @param h           the initial stepsize in cm
	 * @param tolerance   The desired tolerance. The solver will automatically
	 *                    adjust the step size to meet this tolerance.
	 */
	public CLAS12SwimResult swimPlaneInterp(int q, double xo, double yo, double zo, double p, double theta, double phi,
			Plane targetPlane, double sMax, double h, double tolerance) {

		// create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12PlaneListener listener = new CLAS12PlaneListener(ivals, targetPlane, 0, sMax);

		return swimToInterp(ode, ivals.getU(), 0, h, tolerance, listener);

	}

	/**
	 * Swim to a target z in cm
	 *
	 * @param q         in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param p         the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param zTarget   the target z position in cm
	 * @param accuracy  the desired accuracy in cm
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 */
	public CLAS12SwimResult swimZ(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double zTarget, double accuracy, double sMax, double h, double tolerance) {

		// create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12ZListener listener = new CLAS12ZListener(ivals, zTarget, accuracy, sMax);

		return swimToAccuracy(ode, ivals.getU(), 0, h, tolerance, listener);
	}

	/**
	 * Swim to a target z in cm using the faster but less accurate interpolation
	 *
	 * @param q         in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param p         the momentum in GeV/c
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
	public CLAS12SwimResult swimZInterp(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double zTarget, double sMax, double h, double tolerance) {

		// create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12ZListener listener = new CLAS12ZListener(ivals, zTarget, 0, sMax);

		return swimToInterp(ode, ivals.getU(), 0, h, tolerance, listener);
	}

	/**
	 * Swim to a target rho (cylindrical r) in cm
	 *
	 * @param q         in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param p         the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param rhoTarget the target rho position in cm
	 * @param accuracy  the desired accuracy in cm
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 */
	public CLAS12SwimResult swimRho(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double rhoTarget, double accuracy, double sMax, double h, double tolerance) {

		// create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12RhoListener listener = new CLAS12RhoListener(ivals, rhoTarget, accuracy, sMax);

		return swimToAccuracy(ode, ivals.getU(), 0, h, tolerance, listener);
	}

	/**
	 * Swim to a target rho (cylindrical r) in cm
	 *
	 * @param q         in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param p         the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param rhoTarget the target rho position in cm
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 */
	public CLAS12SwimResult swimRhoInterp(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double rhoTarget, double sMax, double h, double tolerance) {

		// create the ODE
		CLAS12SwimmerODE ode = new CLAS12SwimmerODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12RhoListener listener = new CLAS12RhoListener(ivals, rhoTarget, 0, sMax);

		return swimToInterp(ode, ivals.getU(), 0, h, tolerance, listener);
	}

	/**
	 * Set the maximum integration step size in cm
	 *
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
	 *
	 * @return the result of the swim in the listener
	 */
	public CLAS12Listener getListener() {
		return _listener;
	}

}
