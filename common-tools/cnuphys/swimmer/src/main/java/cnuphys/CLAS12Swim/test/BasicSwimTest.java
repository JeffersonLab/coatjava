package cnuphys.CLAS12Swim.test;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.ICLAS12Swimmer;

/**
 * Compares {@link ICLAS12Swimmer#swim(int, double, double, double, double, double, double, double, double, double)}
 * between two swimmer implementations.
 *
 * <h2>What this test does</h2>
 * This is the "basic" swim: integrate (swim) a charged particle trajectory for a fixed path length {@code sMax}
 * without a geometric termination surface (no Z/Plane/Cylinder/etc. stop condition).
 *
 * <h2>Metric</h2>
 * Since there is no target surface, the primary correctness check is that the swimmer reaches the requested
 * path length. The miss metric is defined as:
 * <pre>
 *   miss = |sFinal - sMax|
 * </pre>
 * in <b>cm</b>.
 *
 * <p>
 * You can also rely on {@link ATest}'s mismatch reporting to catch cases where one swimmer reports success
 * while the other does not.
 * </p>
 *
 * <h2>Units</h2>
 * <ul>
 *   <li>{@code sMaxCm} and {@code hCm} are in <b>cm</b>.</li>
 *   <li>Input positions are in <b>cm</b>, momenta in <b>GeV/c</b>, angles in <b>degrees</b> (from {@link RandomTestData}).</li>
 * </ul>
 */
public final class BasicSwimTest extends ATest {

    private final double sMaxCm;
    private final double hCm;
    private final double tolerance;

    /**
     * @param sMaxCm    maximum path length (cm)
     * @param hCm       initial step size guess (cm)
     * @param tolerance adaptive control parameter (the same knob used by your swimmers)
     */
    public BasicSwimTest(double sMaxCm, double hCm, double tolerance) {
        this.sMaxCm = sMaxCm;
        this.hCm = hCm;
        this.tolerance = tolerance;
    }

    @Override
    public String name() {
        return "BasicSwimTest (sMax=" + sMaxCm + " cm)";
    }

    @Override
    protected CLAS12SwimResult runOne(ICLAS12Swimmer s, RandomTestData d, int i) {
        return s.swim(
                d.q[i],
                d.xo[i], d.yo[i], d.zo[i],
                d.p[i], d.theta[i], d.phi[i],
                sMaxCm, hCm, tolerance
        );
    }

    @Override
    protected double miss(RandomTestData d, int i, CLAS12SwimResult r) {
        return Math.abs(r.getPathLength() - sMaxCm);
    }

    @Override
    protected int stepsProxy(CLAS12SwimResult r) {
        return (r == null) ? 0 : r.getNStep();
    }
}
