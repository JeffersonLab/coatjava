package cnuphys.CLAS12Swim;

import cnuphys.CLAS12Swim.geometry.Cylinder;
import cnuphys.CLAS12Swim.geometry.Plane;
import cnuphys.CLAS12Swim.geometry.Sphere;
import cnuphys.magfield.FieldProbe;

/**
 * Public API for the CLAS12 charged-particle swimmer.
 * <p>
 * A “swim” numerically propagates a charged particle through the magnetic field from an
 * initial vertex and direction/momentum until a termination condition is reached
 * (path length, target surface, target z, target rho/beamline, etc.).
 * </p>
 *
 * <h2>Conventions</h2>
 * <ul>
 *   <li><b>Position units:</b> centimeters (cm)</li>
 *   <li><b>Momentum units:</b> GeV/c</li>
 *   <li><b>Angles:</b> degrees (θ = polar, φ = azimuthal)</li>
 *   <li><b>Path length / step size:</b> centimeters (cm)</li>
 *   <li><b>Field probe convention:</b> positions in cm; field returned by the underlying probe
 *       (commonly kG in CLAS12 field packages)</li>
 * </ul>
 *
 * <p>
 * <h2>Results</h2>
 * All swim methods return a {@link CLAS12SwimResult} describing the final particle
 * state and the reason for termination. Callers should always check
 * {@link CLAS12SwimResult#isSuccess()} or {@link CLAS12SwimResult#getStatus()}
 * before using the final state.
 * </p>
 */
public interface ICLAS12Swimmer {

    /**
     * Get the magnetic field probe being used to swim.
     *
     * @return the magnetic field probe
     */
    FieldProbe getProbe();

    /**
     * The basic adaptive-step swim method.
     * The swim is terminated when the particle reaches path length {@code sMax}.
     * If not terminated by an internal termination condition, it will swim exactly to {@code sMax}.
     *
     * @param q         particle charge in integer units of e
     * @param xo        initial x vertex position in cm
     * @param yo        initial y vertex position in cm
     * @param zo        initial z vertex position in cm
     * @param p         initial momentum magnitude in GeV/c
     * @param theta     initial polar angle in degrees
     * @param phi       initial azimuthal angle in degrees
     * @param sMax      final (max) path length in cm
     * @param h         initial step size in cm
     * @param tolerance desired tolerance; the integrator adapts step size to meet this tolerance
     * @return the result of the swim
     */
    CLAS12SwimResult swim(int q, double xo, double yo, double zo, double p, double theta, double phi,
                          double sMax, double h, double tolerance);

    /**
     * The basic fixed-step swim method.
     * The swim is terminated when the particle reaches path length {@code sMax}.
     *
     * @param q     particle charge in integer units of e
     * @param xo    initial x vertex position in cm
     * @param yo    initial y vertex position in cm
     * @param zo    initial z vertex position in cm
     * @param p     initial momentum magnitude in GeV/c
     * @param theta initial polar angle in degrees
     * @param phi   initial azimuthal angle in degrees
     * @param sMax  final (max) path length in cm
     * @param h     fixed step size in cm
     * @return the result of the swim
     */
    CLAS12SwimResult swimFixed(int q, double xo, double yo, double zo, double p, double theta, double phi,
                               double sMax, double h);

    /**
     * Swim a particle to the surface of a target cylinder.
     * The cylinder is defined by two points (each a {@code double[3]} in {x,y,z} cm)
     * that define the centerline, and by a radius {@code r} in cm.
     * <p>
     * If the swim starts inside the cylinder, it will terminate immediately (subject to the
     * implementation’s handling of that case in {@link CLAS12SwimResult}).
     * </p>
     *
     * @param q        particle charge in integer units of e
     * @param xo       initial x vertex position in cm
     * @param yo       initial y vertex position in cm
     * @param zo       initial z vertex position in cm
     * @param p        initial momentum magnitude in GeV/c
     * @param theta    initial polar angle in degrees
     * @param phi      initial azimuthal angle in degrees
     * @param p1       first point on the cylinder centerline: {x,y,z} in cm
     * @param p2       second point on the cylinder centerline: {x,y,z} in cm
     * @param r        cylinder radius in cm
     * @param accuracy desired accuracy in cm for reaching the surface
     * @param sMax     maximum path length in cm
     * @param h        initial step size in cm
     * @param tolerance desired tolerance; the integrator adapts step size to meet this tolerance
     * @return the result of the swim
     */
    CLAS12SwimResult swimCylinder(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                  double p1[], double p2[], double r, double accuracy, double sMax, double h,
                                  double tolerance);

    /**
     * Swim a particle to the surface of a target cylinder.
     * The cylinder is specified by a {@link Cylinder} object (typically treated as infinite in length).
     *
     * @param q              particle charge in integer units of e
     * @param xo             initial x vertex position in cm
     * @param yo             initial y vertex position in cm
     * @param zo             initial z vertex position in cm
     * @param p              initial momentum magnitude in GeV/c
     * @param theta          initial polar angle in degrees
     * @param phi            initial azimuthal angle in degrees
     * @param targetCylinder target cylinder
     * @param accuracy       desired accuracy in cm for reaching the surface
     * @param sMax           maximum path length in cm
     * @param h              initial step size in cm
     * @param tolerance      desired tolerance; the integrator adapts step size to meet this tolerance
     * @return the result of the swim
     */
    CLAS12SwimResult swimCylinder(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                  Cylinder targetCylinder, double accuracy, double sMax, double h,
                                  double tolerance);

    /**
     * Swim a particle to the surface of a target sphere.
     * The sphere is defined by a center point {@code center} as a {@code double[3]} in {x,y,z} cm,
     * and radius {@code r} in cm.
     * <p>
     * If the swim starts inside the sphere, it will terminate immediately (subject to the
     * implementation’s handling of that case in {@link CLAS12SwimResult}).
     * </p>
     *
     * @param q         particle charge in integer units of e
     * @param xo        initial x vertex position in cm
     * @param yo        initial y vertex position in cm
     * @param zo        initial z vertex position in cm
     * @param p         initial momentum magnitude in GeV/c
     * @param theta     initial polar angle in degrees
     * @param phi       initial azimuthal angle in degrees
     * @param center    sphere center: {x,y,z} in cm
     * @param r         sphere radius in cm
     * @param accuracy  desired accuracy in cm for reaching the surface
     * @param sMax      maximum path length in cm
     * @param h         initial step size in cm
     * @param tolerance desired tolerance; the integrator adapts step size to meet this tolerance
     * @return the result of the swim
     */
    CLAS12SwimResult swimSphere(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                double center[], double r, double accuracy, double sMax, double h,
                                double tolerance);

    /**
     * Swim a particle to the surface of a target sphere.
     * The sphere is specified by a {@link Sphere} object.
     *
     * @param q            particle charge in integer units of e
     * @param xo           initial x vertex position in cm
     * @param yo           initial y vertex position in cm
     * @param zo           initial z vertex position in cm
     * @param p            initial momentum magnitude in GeV/c
     * @param theta        initial polar angle in degrees
     * @param phi          initial azimuthal angle in degrees
     * @param targetSphere target sphere
     * @param accuracy     desired accuracy in cm for reaching the surface
     * @param sMax         maximum path length in cm
     * @param h            initial step size in cm
     * @param tolerance    desired tolerance; the integrator adapts step size to meet this tolerance
     * @return the result of the swim
     */
    CLAS12SwimResult swimSphere(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                Sphere targetSphere, double accuracy, double sMax, double h,
                                double tolerance);

    /**
     * Swim a particle until it intersects a target plane or until {@code sMax} is reached.
     * The plane is defined by the components of a normal vector and the components of a point on the plane.
     *
     * @param q         particle charge in integer units of e
     * @param xo        initial x vertex position in cm
     * @param yo        initial y vertex position in cm
     * @param zo        initial z vertex position in cm
     * @param p         initial momentum magnitude in GeV/c
     * @param theta     initial polar angle in degrees
     * @param phi       initial azimuthal angle in degrees
     * @param nx        plane normal x-component
     * @param ny        plane normal y-component
     * @param nz        plane normal z-component
     * @param px        x-component of a point on the plane (cm)
     * @param py        y-component of a point on the plane (cm)
     * @param pz        z-component of a point on the plane (cm)
     * @param accuracy desired accuracy in cm for reaching the plane
     * @param sMax      maximum path length in cm
     * @param h         initial step size in cm
     * @param tolerance desired tolerance; the integrator adapts step size to meet this tolerance
     * @return the result of the swim
     */
    CLAS12SwimResult swimPlane(int q, double xo, double yo, double zo, double p, double theta, double phi,
                               double nx, double ny, double nz, double px, double py, double pz,
                               double accuracy, double sMax, double h, double tolerance);

    /**
     * Swim a particle until it intersects a target plane or until {@code sMax} is reached.
     * The plane is defined by a normal vector and a point on the plane.
     *
     * @param q         particle charge in integer units of e
     * @param xo        initial x vertex position in cm
     * @param yo        initial y vertex position in cm
     * @param zo        initial z vertex position in cm
     * @param p         initial momentum magnitude in GeV/c
     * @param theta     initial polar angle in degrees
     * @param phi       initial azimuthal angle in degrees
     * @param norm      plane normal vector {nx, ny, nz}
     * @param point     a point on the plane {px, py, pz} in cm
     * @param accuracy  desired accuracy in cm for reaching the plane
     * @param sMax      maximum path length in cm
     * @param h         initial step size in cm
     * @param tolerance desired tolerance; the integrator adapts step size to meet this tolerance
     * @return the result of the swim
     */
    CLAS12SwimResult swimPlane(int q, double xo, double yo, double zo, double p, double theta, double phi,
                               double norm[], double point[], double accuracy, double sMax, double h,
                               double tolerance);

    /**
     * Swim a particle until it intersects a target plane or until {@code sMax} is reached.
     * The plane is specified by a {@link Plane} object.
     *
     * @param q           particle charge in integer units of e
     * @param xo          initial x vertex position in cm
     * @param yo          initial y vertex position in cm
     * @param zo          initial z vertex position in cm
     * @param p           initial momentum magnitude in GeV/c
     * @param theta       initial polar angle in degrees
     * @param phi         initial azimuthal angle in degrees
     * @param targetPlane target plane
     * @param accuracy    desired accuracy in cm for reaching the plane
     * @param sMax        maximum path length in cm
     * @param h           initial step size in cm
     * @param tolerance   desired tolerance; the integrator adapts step size to meet this tolerance
     * @return the result of the swim
     */
    CLAS12SwimResult swimPlane(int q, double xo, double yo, double zo, double p, double theta, double phi,
                               Plane targetPlane, double accuracy, double sMax, double h,
                               double tolerance);

    /**
     * Swim to a target {@code z} (cm) in a sector coordinate system.
     * <p>
     * <b>Important:</b> this is only valid if the underlying field/probe is a rotated composite
     * field implementation (your {@code CLAS12Swimmer} uses {@link cnuphys.magfield.RotatedCompositeProbe}
     * internally for sector coordinate transforms).
     * </p>
     * The swim is terminated when the particle reaches {@code zTarget} or if {@code sMax} is reached.
     *
     * @param sector    sector number in [1..6]
     * @param q         particle charge in integer units of e
     * @param xo        initial x vertex position in cm
     * @param yo        initial y vertex position in cm
     * @param zo        initial z vertex position in cm
     * @param p         initial momentum magnitude in GeV/c
     * @param theta     initial polar angle in degrees
     * @param phi       initial azimuthal angle in degrees
     * @param zTarget   target z position in cm
     * @param accuracy  desired accuracy in cm
     * @param sMax      maximum path length in cm
     * @param h         initial step size in cm
     * @param tolerance desired tolerance; the integrator adapts step size to meet this tolerance
     * @return the result of the swim
     */
    CLAS12SwimResult sectorSwimZ(int sector, int q, double xo, double yo, double zo, double p, double theta,
                                 double phi, double zTarget, double accuracy, double sMax, double h,
                                 double tolerance);

    /**
     * Swim to a target {@code z} (cm).
     * The swim is terminated when the particle reaches {@code zTarget} or if {@code sMax} is reached.
     *
     * @param q         particle charge in integer units of e
     * @param xo        initial x vertex position in cm
     * @param yo        initial y vertex position in cm
     * @param zo        initial z vertex position in cm
     * @param p         initial momentum magnitude in GeV/c
     * @param theta     initial polar angle in degrees
     * @param phi       initial azimuthal angle in degrees
     * @param zTarget   target z position in cm
     * @param accuracy  desired accuracy in cm
     * @param sMax      maximum path length in cm
     * @param h         initial step size in cm
     * @param tolerance desired tolerance; the integrator adapts step size to meet this tolerance
     * @return the result of the swim
     */
    CLAS12SwimResult swimZ(int q, double xo, double yo, double zo, double p, double theta, double phi,
                           double zTarget, double accuracy, double sMax, double h, double tolerance);

    /**
     * Swim to a target cylindrical radius {@code rho} (cm), i.e. to the surface of an infinite cylinder
     * about the z-axis.
     * The swim is terminated when the particle reaches {@code rhoTarget} or if {@code sMax} is reached.
     *
     * @param q         particle charge in integer units of e
     * @param xo        initial x vertex position in cm
     * @param yo        initial y vertex position in cm
     * @param zo        initial z vertex position in cm
     * @param p         initial momentum magnitude in GeV/c
     * @param theta     initial polar angle in degrees
     * @param phi       initial azimuthal angle in degrees
     * @param rhoTarget target rho (radius) in cm
     * @param accuracy  desired accuracy in cm
     * @param sMax      maximum path length in cm
     * @param h         initial step size in cm
     * @param tolerance desired tolerance; the integrator adapts step size to meet this tolerance
     * @return the result of the swim
     */
    CLAS12SwimResult swimRho(int q, double xo, double yo, double zo, double p, double theta, double phi,
                             double rhoTarget, double accuracy, double sMax, double h, double tolerance);

    /**
     * Swim to an "offset beamline" listener.
     * The offset beamline is a line parallel to the z-axis, offset in x and y by {@code xb} and {@code yb}.
     * The goal is to swim to the distance of closest approach (DOCA) to this offset line.
     * Swim terminates when successive DOCA estimates differ by less than {@code accuracy}.
     *
     * @param q         particle charge in integer units of e
     * @param xo        initial x vertex position in cm
     * @param yo        initial y vertex position in cm
     * @param zo        initial z vertex position in cm
     * @param p         initial momentum magnitude in GeV/c
     * @param theta     initial polar angle in degrees
     * @param phi       initial azimuthal angle in degrees
     * @param xb        beamline x offset in cm
     * @param yb        beamline y offset in cm
     * @param accuracy  desired DOCA convergence accuracy in cm
     * @param sMax      maximum path length in cm
     * @param h         initial step size in cm
     * @param tolerance desired tolerance; the integrator adapts step size to meet this tolerance
     * @return the result of the swim
     */
    CLAS12SwimResult swimZLine(int q, double xo, double yo, double zo, double p, double theta, double phi,
                               double xb, double yb, double accuracy, double sMax, double h, double tolerance);

    /**
     * Swim to the beamline (defined by {@code rho = 0}), i.e. find the distance of closest approach (DOCA)
     * to the z-axis.
     * Swim terminates when successive DOCA estimates differ by less than {@code accuracy}.
     *
     * @param q         particle charge in integer units of e
     * @param xo        initial x vertex position in cm
     * @param yo        initial y vertex position in cm
     * @param zo        initial z vertex position in cm
     * @param p         initial momentum magnitude in GeV/c
     * @param theta     initial polar angle in degrees
     * @param phi       initial azimuthal angle in degrees
     * @param accuracy  desired DOCA convergence accuracy in cm
     * @param sMax      maximum path length in cm
     * @param h         initial step size in cm
     * @param tolerance desired tolerance; the integrator adapts step size to meet this tolerance
     * @return the result of the swim
     */
    CLAS12SwimResult swimBeamline(int q, double xo, double yo, double zo, double p, double theta, double phi,
                                  double accuracy, double sMax, double h, double tolerance);
}
