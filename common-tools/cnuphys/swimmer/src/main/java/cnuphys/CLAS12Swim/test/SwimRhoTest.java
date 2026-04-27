package cnuphys.CLAS12Swim.test;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.ICLAS12Swimmer;

/**
 * Compares {@link ICLAS12Swimmer#swimRho} between two swimmer implementations.
 *
 * <h2>Metric</h2>
 * Miss is defined as {@code |rho_final - rhoTarget|} in cm, where
 * {@code rho = sqrt(x^2 + y^2)}.
 *
 * <h2>Units</h2>
 * <ul>
 *   <li>{@code rhoTargetCm, accuracyCm, sMaxCm, hCm} are in <b>cm</b>.</li>
 *   <li>Input momenta are in <b>GeV/c</b> and angles are in <b>degrees</b> (from {@link RandomTestData}).</li>
 * </ul>
 */
public final class SwimRhoTest extends ATest {

    private final double rhoTargetCm;
    private final double accuracyCm;
    private final double sMaxCm;
    private final double hCm;
    private final double tolerance;

    /**
     * @param rhoTargetCm target cylindrical radius ρ in cm
     * @param accuracyCm  hit tolerance in cm (used by swimmers)
     * @param sMaxCm      maximum path length in cm
     * @param hCm         initial step size in cm
     * @param tolerance   adaptive control parameter (dimensionless-ish knob used by your swimmers)
     */
    public SwimRhoTest(double rhoTargetCm, double accuracyCm, double sMaxCm, double hCm, double tolerance) {
        this.rhoTargetCm = rhoTargetCm;
        this.accuracyCm = accuracyCm;
        this.sMaxCm = sMaxCm;
        this.hCm = hCm;
        this.tolerance = tolerance;
    }

    @Override
    public String name() {
        return "SwimRhoTest (target rho=" + rhoTargetCm + " cm)";
    }

    @Override
    protected CLAS12SwimResult runOne(ICLAS12Swimmer s, RandomTestData d, int i) {
        return s.swimRho(
                d.q[i],
                d.xo[i], d.yo[i], d.zo[i],
                d.p[i], d.theta[i], d.phi[i],
                rhoTargetCm, accuracyCm,
                sMaxCm, hCm, tolerance
        );
    }

    @Override
    protected double miss(RandomTestData d, int i, CLAS12SwimResult r) {
        // CLAS12SwimResult already provides final rho in cm.
        return Math.abs(r.getFinalRho() - rhoTargetCm);
    }
}
