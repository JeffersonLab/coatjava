package cnuphys.CLAS12Swim.test;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.ICLAS12Swimmer;

/**
 * Compares {@link ICLAS12Swimmer#swimZ} between two swimmer implementations.
 *
 * <h2>Metric</h2>
 * Miss is defined as {@code |z_final - zTarget|} in cm.
 */
public final class SwimZTest extends ATest {

    private final double zTargetCm;
    private final double accuracyCm;
    private final double sMaxCm;
    private final double hCm;
    private final double tolerance;

    /**
     * @param zTargetCm  target z in cm
     * @param accuracyCm hit tolerance in cm (used by swimmers)
     * @param sMaxCm     maximum path length in cm
     * @param hCm        initial step size in cm
     * @param tolerance  adaptive control parameter (dimensionless-ish knob used by your swimmers)
     */
    public SwimZTest(double zTargetCm, double accuracyCm, double sMaxCm, double hCm, double tolerance) {
        this.zTargetCm = zTargetCm;
        this.accuracyCm = accuracyCm;
        this.sMaxCm = sMaxCm;
        this.hCm = hCm;
        this.tolerance = tolerance;
    }

    @Override
    public String name() {
        return "SwimZTest (target z=" + zTargetCm + " cm)";
    }

    @Override
    protected CLAS12SwimResult runOne(ICLAS12Swimmer s, RandomTestData d, int i) {
        return s.swimZ(
                d.q[i],
                d.xo[i], d.yo[i], d.zo[i],
                d.p[i], d.theta[i], d.phi[i],
                zTargetCm, accuracyCm,
                sMaxCm, hCm, tolerance
        );
    }

    @Override
    protected double miss(RandomTestData d, int i, CLAS12SwimResult r) {
        return Math.abs(r.getFinalValues().z - zTargetCm);
    }
 
}
