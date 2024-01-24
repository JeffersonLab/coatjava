package cnuphys.CLAS12Swim;

import java.util.Hashtable;

import cnuphys.CLAS12Swim.geometry.Cylinder;
import cnuphys.CLAS12Swim.geometry.Plane;
import cnuphys.CLAS12Swim.geometry.Sphere;
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

	public static final Hashtable<Integer, String> resultNames = new Hashtable<>();
	static {
		resultNames.put(SWIM_SWIMMING, "SWIMMING");
		resultNames.put(SWIM_SUCCESS, "SWIM_SUCCESS");
		resultNames.put(SWIM_TARGET_MISSED, "SWIM_TARGET_MISSED");
		resultNames.put(BELOW_MIN_MOMENTUM, "BELOW_MIN_MOMENTUM");
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
	 * Get the magnetic field probe being used to swim
	 *
	 * @return the magnetic field probe
	 */
	public FieldProbe getProbe() {
		return _probe;
	}

	/**
	 * The basic swim method. The swim is terminated when the particle reaches the
	 * pathlength sMax or if the listener terminates the swim.
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
	 * @param listener  a listener that can terminate the integration
	 * @return the result of the swim
	 */
	protected CLAS12SwimResult baseSwim(CLAS12SwimODE ode, double u[], double sMin, double sMax, double h,
			double tolerance, CLAS12Listener listener) {

		if (listener.getIvals().p < MINMOMENTUM) {
			listener.setStatus(BELOW_MIN_MOMENTUM);
			return new CLAS12SwimResult(listener);
		}

		// neutral? Just return a line if we know how
		if (listener.canMakeStraightLine() && listener.getIvals().q == 0) {
			listener.straightLine();
			return new CLAS12SwimResult(listener);
		}
		return atomicSwim(ode, u, sMin, sMax, h, tolerance, listener);
	}

	/**
	 * The basic swim method for fixed step size. The swim is terminated when the
	 * particle reaches the pathlength sMax or if the listener terminates the swim.
	 *
	 * @param ode      the ODE to solve
	 * @param u        the initial state vector (x, y, z, tx, ty, tz) x, y, z in cm
	 * @param sMin     the initial value of the independent variable (pathlength)
	 *                 (usually 0)
	 * @param sMax     the final (max) value of the independent variable
	 *                 (pathlength) unless the integration is terminated by the
	 *                 listener
	 * @param h        the fixed stepsize in cm
	 * @param listener an listener that can terminate the integration
	 * @return the result of the swim
	 */
	protected CLAS12SwimResult baseSwimFixed(CLAS12SwimODE ode, double u[], double sMin, double sMax, double h,
			CLAS12Listener listener) {

		if (listener.getIvals().p < MINMOMENTUM) {
			listener.setStatus(BELOW_MIN_MOMENTUM);
			return new CLAS12SwimResult(listener);
		}

		// neutral? Just return a line if we know how
		if (listener.canMakeStraightLine() && listener.getIvals().q == 0) {
			listener.straightLine();
			return new CLAS12SwimResult(listener);
		}
		return atomicSwimFixed(ode, u, sMin, sMax, h, listener);
	}

	/**
	 * The atomic swim method. The swim is terminated when the particle reaches the
	 * pathlength sMax or if the listener terminates the swim.
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
	 * @param listener  a listener that can terminate the integration
	 * @return the result of the swim
	 */
	private CLAS12SwimResult atomicSwim(CLAS12SwimODE ode, double u[], double sMin, double sMax, double h,
			double tolerance, CLAS12Listener listener) {
		switch (_solver) {
		case Fehlberg:
			Fehlberg.solve(ode, u, sMin, sMax, h, tolerance, MINSTEPSIZE, MAXSTEPSIZE, listener);
			break;
		case DormandPrince:
			DormandPrince.solve(ode, u, sMin, sMax, h, tolerance, MINSTEPSIZE, MAXSTEPSIZE, listener);
			break;
		case CashKarp:
			CashKarp.solve(ode, u, sMin, sMax, h, tolerance, MINSTEPSIZE, MAXSTEPSIZE, listener);
			break;
		default:
			DormandPrince.solve(ode, u, sMin, sMax, h, tolerance, MINSTEPSIZE, MAXSTEPSIZE, listener);
		}

		return new CLAS12SwimResult(listener);
	}

	/**
	 * The atomic swim method for the fixed step size integrator. The swim is
	 * terminated when the particle reaches the pathlength sMax or if the listener
	 * terminates the swim.
	 *
	 * @param ode      the ODE to solve
	 * @param u        the initial state vector (x, y, z, tx, ty, tz) x, y, z in cm
	 * @param sMin     the initial value of the independent variable (pathlength)
	 *                 (usually 0)
	 * @param sMax     the final (max) value of the independent variable
	 *                 (pathlength) unless the integration is terminated by the
	 *                 listener
	 * @param h        the fixed stepsize in cm
	 * @param listener a listener that can terminate the integration
	 * @return the result of the swim
	 */
	private CLAS12SwimResult atomicSwimFixed(CLAS12SwimODE ode, double u[], double sMin, double sMax, double h,
			CLAS12Listener listener) {
		FixedStep.solve(ode, u, sMin, sMax, h, listener);
		return new CLAS12SwimResult(listener);
	}

	/**
	 * An endgame swimmer, for zeroing in on a target when close. It assumes that
	 * the last two points in the trajectory are close to and bracket the target. It
	 * uses a fixed step size integrator to home in.
	 *
	 * @param ode      the ODE to solve
	 * @param h        the step size in cm
	 * @param listener a listener that can terminate the integration
	 * @return the result of the swim
	 */
	private CLAS12SwimResult endGameSwim(CLAS12SwimODE ode, double h, CLAS12Listener listener) {

		CLAS12Trajectory traj = listener.getTrajectory();
		int np = traj.size();

		// these should bracket the
		double s1 = traj.getS(np - 2);
		double s2 = traj.getS(np - 1);
		double u[] = traj.get(np - 2);
		traj.removeLastPoint();

		return atomicSwimFixed(ode, u, s1, s2, h, listener);
	}

	/**
	 * Swim to a target accuracy. The details of the target and the accuracy are in
	 * the CLAS12DOCAListener
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
	 * @see CLAS12DOCAListener
	 */
	private CLAS12SwimResult swimToDOCA(CLAS12SwimODE ode, double u[], double sMin, double h, double tolerance,
			CLAS12DOCAListener listener) {

		// low momentum?
		if (listener.getIvals().p < MINMOMENTUM) {
			listener.setStatus(BELOW_MIN_MOMENTUM);
			return new CLAS12SwimResult(listener);
		}

		double s1 = sMin;
		double s2 = listener.getSMax();

		// set initial step size
		h = Math.min(h, listener.getAccuracy() / 2.);

		CLAS12SwimResult cres = null;
		listener.reset();

		// first swim to bracket is adaptive
		cres = atomicSwim(ode, u, s1, s2, h, tolerance, listener);

		if (listener.getStatus() == SWIM_SUCCESS) {
			// we are bracketing the DOCA
			// do a fixed step size "endgame" swim to zero in

			h = Double.POSITIVE_INFINITY;

			double hMin = listener.getAccuracy() / 2.;
			while (h > hMin) {
				int ns = listener.getNumStep();

				// the next-to-next to last and last point bracket the doca
				if (ns > 2) {
					listener.getTrajectory().removePoint(ns - 2);
					ns--;
				}

				s1 = listener.getS(ns - 2);
				s2 = listener.getS();

				h = (s2 - s1) / 10.;

				listener.reset();
				cres = endGameSwim(ode, h, listener);
			}

			return cres;

		} else { // failed, reached max path length
			return cres;
		}
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

		// low momentum?
		if (listener.getIvals().p < MINMOMENTUM) {
			listener.setStatus(BELOW_MIN_MOMENTUM);
			return new CLAS12SwimResult(listener);
		}

		// neutral? Just return a line if we know how
		if (listener.canMakeStraightLine() && listener.getIvals().q == 0) {
			listener.straightLine();
			return new CLAS12SwimResult(listener);
		}

		double s1 = sMin;
		double s2 = listener.getSMax();

		h = Math.min(h, listener.getAccuracy() / 2.);

		// first swim to bracket is adaptive
		CLAS12SwimResult cres = atomicSwim(ode, u, s1, s2, h, tolerance, listener);

		if (listener.getStatus() == SWIM_SUCCESS) {

			// we are bracketing the DOCA
			// do a fixed step size "endgame" swim to zero in

			h = Double.POSITIVE_INFINITY;

			double hMin = listener.getAccuracy() / 2.;

			while (h > hMin) {
				int ns = listener.getNumStep();

				s1 = listener.getS(ns - 2);
				s2 = listener.getS();

				h = (s2 - s1) / 10.;

				cres = endGameSwim(ode, h, listener);
				if (listener.accuracyReached(cres.getPathLength(), cres.getFinalU())) {
					return cres;
				}
			}

			return cres;
		} else { // failed, reached max path length
			return cres;
		}

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
	private CLAS12SwimResult swimToAccuracyDEP(CLAS12SwimODE ode, double u[], double sMin, double h, double tolerance,
			CLAS12BoundaryListener listener) {

		// all swims pass through here

		if (listener.getIvals().p < MINMOMENTUM) {
			listener.setStatus(BELOW_MIN_MOMENTUM);
			return new CLAS12SwimResult(listener);
		}

		// neutral? Just return a line if we know how
		if (listener.canMakeStraightLine() && listener.getIvals().q == 0) {
			listener.straightLine();
			return new CLAS12SwimResult(listener);
		}

		double s1 = sMin;
		double s2 = listener.getSMax();

		h = Math.min(h, listener.getAccuracy() / 2.);

		CLAS12SwimResult cres = null;
		while (true) {
			cres = atomicSwim(ode, u, s1, s2, h, tolerance, listener);

			if (listener.getStatus() == SWIM_SUCCESS) {
				s2 = listener.getS();
				listener.getTrajectory().removeLastPoint();

				if (listener.accuracyReached(cres.getPathLength(), cres.getFinalU())) {
					// do NOT forget to reset the max step size
					resetMaxStepSize();
					return cres;
				}

				u = listener.getU();
				s1 = listener.getS();

				double newMaxH = listener.distanceToTarget(s1, u) / 2;
				setMaxStepSize(newMaxH);

			} else { // failed, reached max path length
				// do NOT forget to reset the max step size
				resetMaxStepSize();
				return cres;
			}
		}

	}

	/**
	 * The basic swim method. The swim is terminated when the particle reaches the
	 * path length sMax. If not terminated by the listener, it will swim exactly to
	 * sMax.
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
	 * The basic fixed stepsize swim method. The swim is terminated when the
	 * particle reaches the path length sMax.
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
	 * @param h         the fixed stepsize in cm
	 * @return the result of the swim
	 */
	public CLAS12SwimResult swimFixed(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double sMax, double h) {

		// create the ODE
		CLAS12SwimODE ode = new CLAS12SwimODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12Listener listener = new CLAS12Listener(ivals, sMax);
		return baseSwimFixed(ode, ivals.getU(), 0, sMax, h, listener);
	}

	/**
	 * Swim a particle to the surface of a target cylinder. The cylinder is defined
	 * by two points in {x,y,z} array format and a radius. The two points define the
	 * centerline of the cylinder. The cylinder is infinite in length. If the swim
	 * starts inside the cylinder, it will terminate when the particle reaches the
	 * surface or if sMax is reached. If the swim starts outside the cylinder, there
	 * is some risk (which we try to mitigate) that a large step size will cause the
	 * swim to jump over the cylinder.
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
	 * Swim a particle to the surface of a target cylinder. The cylinder is defined
	 * by a Cylinder object. The cylinder is infinite in length. If the swim starts
	 * inside the cylinder, it will terminate when the particle reaches the surface
	 * or if sMax is reached. If the swim starts outside the cylinder there is some
	 * risk (which we try to mitigate) that a large step size will cause the swim to
	 * jump over the cylinder.
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

		// if centered on z, same as a rho swim
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
	 * Swim a particle to the surface of a target sphere. The sphere is defined by a
	 * center point in {x,y,z} array format and a radius. If the swim starts inside
	 * the sphere, it will terminate when the particle reaches the surface or if
	 * sMax is reached. If the swim starts outside the sphere there is some risk
	 * (which we try to mitigate) that a large step size will cause the swim to jump
	 * over the sphere.
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
	 * Swim a particle to the surface of a target sphere. The sphere is defined by a
	 * Sphere object. If the swim starts inside the sphere, it will terminate when
	 * the particle reaches the surface or if sMax is reached. If the swim starts
	 * outside the sphere there is some risk (which we try to mitigate) that a large
	 * step size will cause the swim to jump over the sphere.
	 *
	 * @param q            charge in integer units of e
	 * @param xo           the x vertex position in cm
	 * @param yo           the y vertex position in cm
	 * @param zo           the z vertex position in cm
	 * @param p            the momentum in GeV/c
	 * @param theta        the initial polar angle in degrees
	 * @param phi          the initial azimuthal angle in degrees
	 * @param targetSphere the target sphere
	 * @param accuracy     the desired accuracy in cm
	 * @param sMax         the final (max) value of the independent variable
	 *                     (pathlength) unless the integration is terminated by the
	 *                     listener
	 * @param h            the initial stepsize in cm
	 * @param tolerance    The desired tolerance. The solver will automatically
	 *                     adjust the step size to meet this tolerance.
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
	 * Swim a particle until it intersects a target plane or until sMax is reached.
	 * The plane is defined by the components of a normal vector and the components
	 * of a point on the plane.
	 *
	 * @param q         in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param p         the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param nx        the x component of the normal vector to the plane
	 * @param ny        the y component of the normal vector to the plane
	 * @param nz        the z component of the normal vector to the plane
	 * @param px        the x component of a point on the plane
	 * @param py        the y component of a point on the plane
	 * @param pz        the z component of a point on the plane
	 * @param accuracy  the desired accuracy in cm
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 * @return the result of the swim
	 */
	public CLAS12SwimResult swimPlane(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double nx, double ny, double nz, double px, double py, double pz, double accuracy, double sMax, double h,
			double tolerance) {
		Plane targetPlane = new Plane(nx, ny, nz, px, py, pz);
		return swimPlane(q, xo, yo, zo, p, theta, phi, targetPlane, accuracy, sMax, h, tolerance);
	}

	/**
	 * Swim a particle until it intersects a target plane or until sMax is reached.
	 * The plane is defined by a a normal vector and a point on the plane, both in
	 * {x,y,z} array format.
	 *
	 * @param q         in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param p         the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param norm      the normal vector to the plane
	 * @param point     a point on the plane
	 * @param accuracy  the desired accuracy in cm
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 * @return the result of the swim
	 */
	public CLAS12SwimResult swimPlane(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double norm[], double point[], double accuracy, double sMax, double h, double tolerance) {
		Plane targetPlane = new Plane(norm, point);
		return swimPlane(q, xo, yo, zo, p, theta, phi, targetPlane, accuracy, sMax, h, tolerance);
	}

	/**
	 * Swim a particle until it intersects a target plane or until sMax is reached.
	 * The plane is defined by a Plane object.
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
	 * Swim to a target z in cm in a sector coordinate system. THIS IS ONLY VALID IF
	 * THE FIELD IS A RotatedCompositeField. The swim is terminated when the
	 * particle reaches the target z or if sMax is reached.
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
	public CLAS12SwimResult sectorSwimZ(int sector, int q, double xo, double yo, double zo, double p, double theta,
			double phi, double zTarget, double accuracy, double sMax, double h, double tolerance) {

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
	 * Swim to a target z in cm. The swim is terminated when the particle reaches
	 * the target z or if sMax is reached.
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
	 * Swim to the beamline (defined by rho = 0). That is, find the distance of
	 * closest approach to the beamline. Swim terminates when successive doca
	 * estimates differ by less than accuracy.
	 *
	 * @param q         in integer units of e
	 * @param xo        the x vertex position in cm
	 * @param yo        the y vertex position in cm
	 * @param zo        the z vertex position in cm
	 * @param p         the momentum in GeV/c
	 * @param theta     the initial polar angle in degrees
	 * @param phi       the initial azimuthal angle in degrees
	 * @param accuracy  the desired accuracy in cm. This is how close you'd like to
	 *                  get to the true DOCA
	 * @param sMax      the final (max) value of the independent variable
	 *                  (pathlength) unless the integration is terminated by the
	 *                  listener
	 * @param h         the initial stepsize in cm
	 * @param tolerance The desired tolerance. The solver will automatically adjust
	 *                  the step size to meet this tolerance.
	 * @return the result of the swim
	 */
	public CLAS12SwimResult swimBeamline(int q, double xo, double yo, double zo, double p, double theta, double phi,
			double accuracy, double sMax, double h, double tolerance) {

		// create the ODE
		CLAS12SwimODE ode = new CLAS12SwimODE(q, p, _probe);

		// create the initial values
		CLAS12Values ivals = new CLAS12Values(q, xo, yo, zo, p, theta, phi);

		CLAS12BeamlineListener listener = new CLAS12BeamlineListener(ivals, accuracy, sMax);

		return swimToDOCA(ode, ivals.getU(), 0, h, tolerance, listener);
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

}
