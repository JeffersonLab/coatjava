package cnuphys.CLAS12Swim.test;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.ICLAS12Swimmer;
import cnuphys.CLAS12Swim.geometry.Cylinder;

/**
 * Compares {@link ICLAS12Swimmer#swimCylinder} between two swimmer implementations.
 *
 * <h2>Metric</h2>
 * Miss is defined as the shortest absolute distance from the final point to the
 * infinite cylinder surface:
 * <pre>
 *   miss = targetCylinder.distance(xFinal, yFinal, zFinal)
 * </pre>
 * in <b>cm</b>.
 *
 * <h2>Units</h2>
 * <ul>
 *   <li>Cylinder axis points are in <b>cm</b>.</li>
 *   <li>Cylinder radius, accuracy, sMax, and h are in <b>cm</b>.</li>
 *   <li>Input momenta are in <b>GeV/c</b> and angles are in <b>degrees</b>.</li>
 * </ul>
 */
public final class SwimCylinderTest extends ATest {

    private final Cylinder targetCylinder;
    private final double accuracyCm;
    private final double sMaxCm;
    private final double hCm;
    private final double tolerance;

    /**
     * Create a test for swimming to an infinite target cylinder.
     *
     * @param targetCylinder target infinite cylinder
     * @param accuracyCm hit tolerance in cm
     * @param sMaxCm maximum path length in cm
     * @param hCm initial step size guess in cm
     * @param tolerance adaptive control parameter used by the swimmers
     */
    public SwimCylinderTest(Cylinder targetCylinder,
                            double accuracyCm,
                            double sMaxCm,
                            double hCm,
                            double tolerance) {
        if (targetCylinder == null) {
            throw new IllegalArgumentException("targetCylinder must not be null");
        }
        this.targetCylinder = targetCylinder;
        this.accuracyCm = accuracyCm;
        this.sMaxCm = sMaxCm;
        this.hCm = hCm;
        this.tolerance = tolerance;
    }

    /**
     * Convenience constructor from two points on the cylinder axis and a radius.
     *
     * @param p1 first point on cylinder axis (cm)
     * @param p2 second point on cylinder axis (cm)
     * @param radiusCm cylinder radius in cm
     * @param accuracyCm hit tolerance in cm
     * @param sMaxCm maximum path length in cm
     * @param hCm initial step size guess in cm
     * @param tolerance adaptive control parameter used by the swimmers
     */
    public SwimCylinderTest(double[] p1, double[] p2, double radiusCm,
                            double accuracyCm, double sMaxCm, double hCm, double tolerance) {
        this(new Cylinder(p1, p2, radiusCm), accuracyCm, sMaxCm, hCm, tolerance);
    }

    @Override
    public String name() {
        return "SwimCylinderTest (infinite cylinder, radius=" + targetCylinder.radius + " cm)";
    }

    @Override
    protected CLAS12SwimResult runOne(ICLAS12Swimmer s, RandomTestData d, int i) {
        return s.swimCylinder(
                d.q[i],
                d.xo[i], d.yo[i], d.zo[i],
                d.p[i], d.theta[i], d.phi[i],
                targetCylinder,
                accuracyCm,
                sMaxCm,
                hCm,
                tolerance
        );
    }

    @Override
    protected double miss(RandomTestData d, int i, CLAS12SwimResult r) {
    	var fv = r.getFinalValues();
        return targetCylinder.distance(fv.x, fv.y, fv.z);
    }

    @Override
    protected int stepsProxy(CLAS12SwimResult r) {
        return (r == null) ? 0 : r.getNStep();
    }
}
