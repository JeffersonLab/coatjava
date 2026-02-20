package cnuphys.CLAS12Swim.test;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.ICLAS12Swimmer;

/**
 * Compares {@link ICLAS12Swimmer#sectorSwimZ(int, int, double, double, double, double, double, double, double, double, double, double, double)}
 * between two swimmer implementations.
 *
 * <h2>What this test does</h2>
 * Runs {@code sectorSwimZ} for a fixed CLAS12 sector and fixed {@code zTarget}. This variant
 * is only valid when the active magnetic field probe is a sector-aware {@code RotatedCompositeProbe}.
 * If the probe is not sector-aware, both swimmers are expected to return {@code null} (legacy behavior)
 * and the test will be skipped by {@link TestSuite}.
 *
 * <h2>Metric</h2>
 * Miss is defined as {@code |zFinal - zTarget|} in cm.
 *
 * <h2>Units</h2>
 * <ul>
 *   <li>{@code zTargetCm, accuracyCm, sMaxCm, hCm} are in <b>cm</b>.</li>
 *   <li>Input momenta are in <b>GeV/c</b> and angles are in <b>degrees</b> (from {@link RandomTestData}).</li>
 * </ul>
 */
public final class SectorSwimZTest extends ATest {

    private final int sector;
    private final double zTargetCm;
    private final double accuracyCm;
    private final double sMaxCm;
    private final double hCm;
    private final double tolerance;

    /**
     * @param sector     CLAS12 sector in [1..6]
     * @param zTargetCm  target z in cm
     * @param accuracyCm hit tolerance (cm) passed to swimmers
     * @param sMaxCm     maximum path length (cm)
     * @param hCm        initial step size guess (cm)
     * @param tolerance  adaptive control parameter (same knob used by your swimmers)
     */
    public SectorSwimZTest(int sector, double zTargetCm, double accuracyCm, double sMaxCm, double hCm, double tolerance) {
        this.sector = sector;
        this.zTargetCm = zTargetCm;
        this.accuracyCm = accuracyCm;
        this.sMaxCm = sMaxCm;
        this.hCm = hCm;
        this.tolerance = tolerance;
    }

    @Override
    public String name() {
        return "SectorSwimZTest (sector=" + sector + ", target z=" + zTargetCm + " cm)";
    }

    @Override
    protected CLAS12SwimResult runOne(ICLAS12Swimmer s, RandomTestData d, int i) {
        return s.sectorSwimZ(
                sector,
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
