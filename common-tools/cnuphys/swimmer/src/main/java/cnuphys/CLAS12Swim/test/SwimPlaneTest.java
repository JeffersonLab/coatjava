package cnuphys.CLAS12Swim.test;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.ICLAS12Swimmer;
import cnuphys.CLAS12Swim.geometry.Plane;

/**
 * Compares {@link ICLAS12Swimmer#swimPlane} between two swimmer implementations.
 *
 * <h2>What this test does</h2>
 * Swims until the trajectory intersects a fixed target plane (or {@code sMax} is reached).
 *
 * <h2>Metric</h2>
 * Miss is defined as the perpendicular distance from the final point to the plane:
 * <pre>
 *   miss = plane.distance(xFinal, yFinal, zFinal)
 * </pre>
 * in <b>cm</b>.
 *
 * <h2>Units</h2>
 * <ul>
 *   <li>Plane coefficients and points are in <b>cm</b>.</li>
 *   <li>{@code accuracyCm, sMaxCm, hCm} are in <b>cm</b>.</li>
 *   <li>Input momenta are in <b>GeV/c</b> and angles are in <b>degrees</b> (from {@link RandomTestData}).</li>
 * </ul>
 */
public final class SwimPlaneTest extends ATest {

    private final Plane plane;
    private final double accuracyCm;
    private final double sMaxCm;
    private final double hCm;
    private final double tolerance;

    /**
     * Create a plane test using a pre-built {@link Plane}.
     *
     * @param plane      target plane (must not be {@code null})
     * @param accuracyCm hit tolerance (cm) passed to swimmers
     * @param sMaxCm     maximum path length (cm)
     * @param hCm        initial step size guess (cm)
     * @param tolerance  adaptive control parameter (same knob used by your swimmers)
     */
    public SwimPlaneTest(Plane plane, double accuracyCm, double sMaxCm, double hCm, double tolerance) {
        if (plane == null) {
            throw new IllegalArgumentException("plane must not be null");
        }
        this.plane = plane;
        this.accuracyCm = accuracyCm;
        this.sMaxCm = sMaxCm;
        this.hCm = hCm;
        this.tolerance = tolerance;
    }

    /**
     * Convenience constructor from a plane normal and a point on the plane.
     *
     * @param nx         x component of plane normal (dimensionless)
     * @param ny         y component of plane normal (dimensionless)
     * @param nz         z component of plane normal (dimensionless)
     * @param px         x coordinate of a point on the plane (cm)
     * @param py         y coordinate of a point on the plane (cm)
     * @param pz         z coordinate of a point on the plane (cm)
     * @param accuracyCm hit tolerance (cm) passed to swimmers
     * @param sMaxCm     maximum path length (cm)
     * @param hCm        initial step size guess (cm)
     * @param tolerance  adaptive control parameter
     */
    public SwimPlaneTest(double nx, double ny, double nz,
                         double px, double py, double pz,
                         double accuracyCm, double sMaxCm, double hCm, double tolerance) {
        this(new Plane(nx, ny, nz, px, py, pz), accuracyCm, sMaxCm, hCm, tolerance);
    }

    @Override
    public String name() {
        // Plane is ax + by + cz = d
        return String.format("SwimPlaneTest (plane: %.4f x + %.4f y + %.4f z = %.4f)",
                plane.a, plane.b, plane.c, plane.d);
    }

    @Override
    protected CLAS12SwimResult runOne(ICLAS12Swimmer s, RandomTestData d, int i) {
        return s.swimPlane(
                d.q[i],
                d.xo[i], d.yo[i], d.zo[i],
                d.p[i], d.theta[i], d.phi[i],
                plane,
                accuracyCm,
                sMaxCm,
                hCm,
                tolerance
        );
    }

    @Override
    protected double miss(RandomTestData d, int i, CLAS12SwimResult r) {
        var fv = r.getFinalValues();
        return plane.distance(fv.x, fv.y, fv.z);
    }

    @Override
    protected int stepsProxy(CLAS12SwimResult r) {
        return (r == null) ? 0 : r.getNStep();
    }
}
