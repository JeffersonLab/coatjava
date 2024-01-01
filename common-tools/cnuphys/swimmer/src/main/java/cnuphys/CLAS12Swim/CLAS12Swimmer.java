package cnuphys.CLAS12Swim;

import java.util.Hashtable;

import cnuphys.adaptiveSwim.geometry.Cylinder;
import cnuphys.adaptiveSwim.geometry.Plane;
import cnuphys.adaptiveSwim.geometry.Sphere;
import cnuphys.magfield.FieldProbe;
import cnuphys.magfield.IMagField;
import cnuphys.magfield.RotatedCompositeProbe;

/**
 * The adaptive step size swimmer for CLAS12.
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
	 * Set the integrator (also called the solver) to use
	 *
	 * @param solver the integrator (solver) to use
	 */
	public void setSolver(EIntegrator solver) {
		_solver = solver;
	}

	/**
	 * Get the integrator (also called the solver) being used
	 *
	 * @return the integrator (solver) being used
	 */
	public EIntegrator getSolver() {
		return _solver;
	}

	/**
	 * The listener that can stop the integration and stores results
	 */
	protected CLAS12Listener _listener;

	/**
	 * Get the magnetic field probe being used to swim
	 *
	 * @return the magnetic field probe
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
	protected CLAS12SwimResult baseSwim(CLAS12SwimODE ode, double u[], double sMin, double sMax, double h,
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
	private CLAS12SwimResult swimToAccuracy(CLAS12SwimODE ode, double u[], double sMin, double h, double tolerance,
			CLAS12BoundaryListener listener) {

		double s1 = sMin;
		double s2 = listener.getSMax();

		h = Math.min(h, listener.getAccuracy() / 2.);

		CLAS12SwimResult cres = null;
		while (true) {
			cres = baseSwim(ode, u, s1, s2, h, tolerance, listener);

			if (listener.getStatus() == SWIM_SUCCESS) {
				s2 = _listener.getS();
				_listener.getTrajectory().removeLastPoint();

				if (listener.accuracyReached(cres.getPathLength(), cres.getFinalU())) {
					// do NOT forget to reset the max step size
					resetMaxStepSize();
					return cres;
				}

				u = _listener.getU();
				s1 = _listener.getS();

				double newMaxH = listener.distanceToTarget(s1, u) / 2;
				setMaxStepSize(newMaxH);
				
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
	 * The basic swim method. The swim is terminated when the particle reaches the path
	 * length sMax or if the optional listener terminates the swim. If not terminated by 
	 * the listener, it will swim exactly to sMax.
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
	 * @return the result of the swim
     */
	public CLAS12SwimResult swim(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double sMax, double h, double tolerance) {

		// create the ODE
		CLAS12SwimODE ode = new CLAS12SwimODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12Listener listener = new CLAS12Listener(ivals, sMax);
		return baseSwim(ode, ivals.getU(), 0, sMax, h, tolerance, listener);
	}

	/**
	 * Swim a particle to the surface of a target cylinder. The cylinder is defined by 
	 * two points in {x,y,z} array format and a radius. The two points define the 
	 * centerline of the cylinder. The cylinder is infinite in length. If the
	 * swim starts inside the cylinder, it will terminate when the particle reaches
	 * the surface or if sMax is reached. If the swim starts outside the cylinder,
	 * there is some risk (which we try to mitigate) that a large step size will
	 * cause the swim to jump over the cylinder. 
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
	 * @return the result of the swim
	 */
	public CLAS12SwimResult swimCylinder(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double p1[], double p2[], double r, double accuracy, double sMax, double h, double tolerance) {

		Cylinder targetCylinder = new Cylinder(p1, p2, r);
		return swimCylinder(q, xo, yo, zo, p, theta, phi, targetCylinder, accuracy, sMax, h, tolerance);
	}
	

	/**
	 * Swim a particle to the surface of a target cylinder. The cylinder is defined by 
	 * a Cylinder object. The cylinder is infinite in length. If the
	 * swim starts inside the cylinder, it will terminate when the particle reaches
	 * the surface or if sMax is reached. If the swim starts outside the cylinder
	 * there is some risk (which we try to mitigate) that a large step size will
	 * cause the swim to jump over the cylinder. 
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
	 * @return the result of the swim
	 */
	public CLAS12SwimResult swimCylinder(int q, double xo, double yo, double zo, double p, double theta, double phi,
			Cylinder targetCylinder, double accuracy, double sMax, double h, double tolerance) {

		//if centered on z, same as a rho swim
		if (targetCylinder.centeredOnZ()) {
			return swimRho(q, xo, yo, zo, p, theta, phi, targetCylinder.radius, accuracy, sMax, h, tolerance);
		}
		
		
		// create the ODE
		CLAS12SwimODE ode = new CLAS12SwimODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12CylinderListener listener = new CLAS12CylinderListener(ivals, targetCylinder, accuracy, sMax);

		return swimToAccuracy(ode, ivals.getU(), 0, h, tolerance, listener);
	}
	
	/**
	 * Swim a particle to the surface of a target sphere. The sphere is defined by 
	 * a center point in {x,y,z} array format and a radius. If the
	 * swim starts inside the sphere, it will terminate when the particle reaches
	 * the surface or if sMax is reached. If the swim starts outside the sphere
	 * there is some risk (which we try to mitigate) that a large step size will
	 * cause the swim to jump over the sphere. 
	 *
	 * @param q         in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param p         the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param center    the center of the sphere
	 * @param r         the radius of the sphere (cm)
	 * @param accuracy  the desired accuracy in cm
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 * @return the result of the swim
	 */
	public CLAS12SwimResult swimSphere(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double center[], double r, double accuracy, double sMax, double h, double tolerance) {

		Sphere targetSphere = new Sphere(center, r);
		return swimSphere(q, xo, yo, zo, p, theta, phi, targetSphere, accuracy, sMax, h, tolerance);
	}
	
	/**
	 * Swim a particle to the surface of a target sphere. The sphere is defined by 
	 * a Sphere object. If the swim starts inside the sphere, it will terminate when 
	 * the particle reaches the surface or if sMax is reached. If the swim starts outside
	 * the sphere there is some risk (which we try to mitigate) that a large step size will
	 * cause the swim to jump over the sphere. 
	 *
	 * @param q              charge in integer units of e
	 * @param xo             the x vertex position in cm
	 * @param yo             the y vertex position in cm
	 * @param zo             the z vertex position in cm
	 * @param p              the momentum in GeV/c
	 * @param theta          the initial polar angle in degrees
	 * @param phi            the initial azimuthal angle in degrees
	 * @param targetSphere  the target sphere
	 * @param accuracy       the desired accuracy in cm
	 * @param sMax           the final (max) value of the independent variable
	 *                       (pathlength) unless the integration is terminated by
	 *                       the listener
	 * @param h              the initial stepsize in cm
	 * @param tolerance      The desired tolerance. The solver will automatically
	 *                       adjust the step size to meet this tolerance.
	 * @return the result of the swim
	 */
	public CLAS12SwimResult swimSphere(int q, double xo, double yo, double zo, double p, double theta, double phi,
			Sphere targetSphere, double accuracy, double sMax, double h, double tolerance) {

		
		// create the ODE
		CLAS12SwimODE ode = new CLAS12SwimODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12SphereListener listener = new CLAS12SphereListener(ivals, targetSphere, accuracy, sMax);

		return swimToAccuracy(ode, ivals.getU(), 0, h, tolerance, listener);
	}
	
	/**
	 * Swim a particle until it intersects a target plane or until sMax
	 * is reached. The plane is defined by the components of a normal vector and
	 * the components of a point on the plane.
	 *
	 * @param q           in integer units of e
	 * @param xo          the x vertex position in cm
	 * @param yo          the y vertex position in cm
	 * @param zo          the z vertex position in cm
	 * @param p           the momentum in GeV/c
	 * @param theta       the initial polar angle in degrees
	 * @param phi         the initial azimuthal angle in degrees
	 * @param nx          the x component of the normal vector to the plane
	 * @param ny          the y component of the normal vector to the plane
	 * @param nz          the z component of the normal vector to the plane
	 * @param px          the x component of a point on the plane
	 * @param py          the y component of a point on the plane
	 * @param pz          the z component of a point on the plane
	 * @param accuracy    the desired accuracy in cm
	 * @param sMax        the final (max) value of the independent variable
	 *                    (pathlength) unless the integration is terminated by the
	 *                    listener
	 * @param h           the initial stepsize in cm
	 * @param tolerance   The desired tolerance. The solver will automatically
	 *                    adjust the step size to meet this tolerance.
	 * @return the result of the swim
	 */
	public CLAS12SwimResult swimPlane(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double nx, double ny, double nz, double px, double py, double pz, double accuracy, double sMax, double h, double tolerance) {
		Plane targetPlane = new Plane(nx, ny, nz, px, py, pz);
		return swimPlane(q, xo, yo, zo, p, theta, phi, targetPlane, accuracy, sMax, h, tolerance);
	}

	
	/**
	 * Swim a particle until it intersects a target plane or until sMax
	 * is reached. The plane is defined by a a normal vector and a point on the plane,
	 * both in {x,y,z} array format.
	 *
	 * @param q           in integer units of e
	 * @param xo          the x vertex position in cm
	 * @param yo          the y vertex position in cm
	 * @param zo          the z vertex position in cm
	 * @param p           the momentum in GeV/c
	 * @param theta       the initial polar angle in degrees
	 * @param phi         the initial azimuthal angle in degrees
	 * @param norm        the normal vector to the plane
	 * @param point       a point on the plane
	 * @param accuracy    the desired accuracy in cm
	 * @param sMax        the final (max) value of the independent variable
	 *                    (pathlength) unless the integration is terminated by the
	 *                    listener
	 * @param h           the initial stepsize in cm
	 * @param tolerance   The desired tolerance. The solver will automatically
	 *                    adjust the step size to meet this tolerance.
	 * @return the result of the swim
	 */
	public CLAS12SwimResult swimPlane(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double norm[], double point[], double accuracy, double sMax, double h, double tolerance) {
		Plane targetPlane = new Plane(norm, point);
		return swimPlane(q, xo, yo, zo, p, theta, phi, targetPlane, accuracy, sMax, h, tolerance);
	}

	/**
	 * Swim a particle until it intersects a target plane or until sMax
	 * is reached. The plane is defined by a Plane object.
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
	 * @return the result of the swim
	 */
	public CLAS12SwimResult swimPlane(int q, double xo, double yo, double zo, double p, double theta, double phi,
			Plane targetPlane, double accuracy, double sMax, double h, double tolerance) {

		// create the ODE
		CLAS12SwimODE ode = new CLAS12SwimODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12PlaneListener listener = new CLAS12PlaneListener(ivals, targetPlane, accuracy, sMax);

		return swimToAccuracy(ode, ivals.getU(), 0, h, tolerance, listener);
	}
	

	/**
	 * Swim to a target z in cm in a sector coordinate system.
	 * THIS IS ONLY VALID IF THE FIELD IS A RotatedCompositeField.
	 * The swim is terminated when the particle reaches the
	 * target z or if sMax is reached.
	 *
	 * @param sector    the sector [1..6]
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
	 * @return the result of the swim
	 */
	public CLAS12SwimResult sectorSwimZ(int sector, int q, double xo, double yo, double zo, double p, double theta, double phi,
			double zTarget, double accuracy, double sMax, double h, double tolerance) {

		if (!(_probe instanceof RotatedCompositeProbe)) {
            System.err.println("CLAS12Swimmer: sectorSwimZ only valid for the RotatedComposite field.");
            return null;
        }
		// create the ODE
		CLAS12SectorSwimODE ode = new CLAS12SectorSwimODE(sector, q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12ZListener listener = new CLAS12ZListener(ivals, zTarget, accuracy, sMax);

		return swimToAccuracy(ode, ivals.getU(), 0, h, tolerance, listener);
	}

	/**
	 * Swim to a target z in cm. The swim is terminated when the particle reaches the
	 * target z or if sMax is reached.
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
	 * @return the result of the swim
	 */
	public CLAS12SwimResult swimZ(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double zTarget, double accuracy, double sMax, double h, double tolerance) {

		// create the ODE
		CLAS12SwimODE ode = new CLAS12SwimODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12ZListener listener = new CLAS12ZListener(ivals, zTarget, accuracy, sMax);

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
	 * @param accuracy  the desired accuracy in cm
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 * @return the result of the swim
	 */
	public CLAS12SwimResult swimRho(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double rhoTarget, double accuracy, double sMax, double h, double tolerance) {

		// create the ODE
		CLAS12SwimODE ode = new CLAS12SwimODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12RhoListener listener = new CLAS12RhoListener(ivals, rhoTarget, accuracy, sMax);

		return swimToAccuracy(ode, ivals.getU(), 0, h, tolerance, listener);
	}

	

	/**
	 * Set the maximum integration step size in cm
	 *
	 * @param maxStepSize the maximum integration step size in cm
	 */
	protected void setMaxStepSize(double maxStepSize) {
		MAXSTEPSIZE = maxStepSize;
	}

	/**
	 * Reset the maximum integration step size to infinity
	 */
	protected void resetMaxStepSize() {
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
