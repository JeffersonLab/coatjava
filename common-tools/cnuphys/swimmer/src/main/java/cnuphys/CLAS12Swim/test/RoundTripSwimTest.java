package cnuphys.CLAS12Swim.test;

import java.util.Locale;
import java.util.Random;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Values;
import cnuphys.CLAS12Swim.ICLAS12Swimmer;

/**
 * Round-trip reversibility test using the <b>basic</b> {@link ICLAS12Swimmer#swim} method only.
 *
 * <h2>Test definition</h2>
 * <ol>
 *   <li>Start at the vertex (0,0,0) cm with random charge q = ±1, random momentum p, and random direction.</li>
 *   <li>Swim forward for a fixed path length sMax.</li>
 *   <li>Flip charge (q → −q) and reverse the <em>final</em> direction (t → −t).</li>
 *   <li>Swim again for the same path length sMax.</li>
 *   <li>Measure how close you return to the vertex.</li>
 * </ol>
 *
 * <h2>Metric</h2>
 * The miss is defined as the final distance from the vertex after the backward swim:
 * <pre>
 *   miss = sqrt(x^2 + y^2 + z^2)  (cm)
 * </pre>
 *
 * <h2>Units</h2>
 * <ul>
 *   <li>Distances (including sMax and h) are in <b>cm</b>.</li>
 *   <li>Momentum p is in <b>GeV/c</b>.</li>
 *   <li>Angles are in <b>degrees</b>.</li>
 * </ul>
 */
public final class RoundTripSwimTest extends ATest {

    private final double sMaxCm;
    private final double hCm;
    private final double tolerance;

    /**
     * @param sMaxCm    forward/backward path length in cm (6 m = 600 cm)
     * @param hCm       initial step size guess (cm)
     * @param tolerance adaptive control parameter (same knob used by your swimmers)
     */
    public RoundTripSwimTest(double sMaxCm, double hCm, double tolerance) {
        this.sMaxCm = sMaxCm;
        this.hCm = hCm;
        this.tolerance = tolerance;
    }

    @Override
    public String name() {
        return String.format(Locale.US, "RoundTripSwimTest (basic swim; sMax=%.1f cm)", sMaxCm);
    }

    /**
     * Create isotropic round-trip input data.
     * <p>
     * The returned {@link RandomTestData} has:
     * <ul>
     *   <li>vertex fixed at (0,0,0) cm</li>
     *   <li>q ∈ {+1, -1}</li>
     *   <li>p uniform in [pMin, pMax] GeV/c</li>
     *   <li>direction isotropic: cos(theta) uniform in [-1,1], phi uniform in [0,360)</li>
     * </ul>
     * </p>
     *
     * @param n     number of trials/samples
     * @param seed  RNG seed
     * @param pMin  minimum momentum (GeV/c)
     * @param pMax  maximum momentum (GeV/c)
     * @return randomized test data (units as documented in {@link RandomTestData})
     */
    public static RandomTestData createData(int n, long seed, double pMin, double pMax) {

        // Use an existing constructor, then overwrite arrays with our distributions.
        RandomTestData d = new RandomTestData(0, seed);

        d.q = new int[n];
        d.xo = new double[n];
        d.yo = new double[n];
        d.zo = new double[n];
        d.p = new double[n];
        d.theta = new double[n];
        d.phi = new double[n];

        Random r = new Random(seed);

        for (int i = 0; i < n; i++) {
            d.q[i] = r.nextBoolean() ? 1 : -1;

            d.xo[i] = 0.0;
            d.yo[i] = 0.0;
            d.zo[i] = 0.0;

            d.p[i] = pMin + (pMax - pMin) * r.nextDouble();

            // isotropic direction
            double u = -1.0 + 2.0 * r.nextDouble(); // cos(theta) uniform in [-1,1]
            d.theta[i] = Math.toDegrees(Math.acos(clamp(u, -1.0, 1.0)));
            d.phi[i] = 360.0 * r.nextDouble();
        }

        return d;
    }

    @Override
    protected CLAS12SwimResult runOne(ICLAS12Swimmer s, RandomTestData d, int i) {

        // Forward swim from origin
        CLAS12SwimResult f = s.swim(
                d.q[i],
                0.0, 0.0, 0.0,
                d.p[i], d.theta[i], d.phi[i],
                sMaxCm, hCm, tolerance
        );

        if (f == null || !f.isSuccess()) {
            // Forward failed: return as-is so ATest counts this as a failure.
            return f;
        }

        CLAS12Values vf = f.getFinalValues();
        // Extract forward final state
        double xf = vf.x;
        double yf = vf.y;
        double zf = vf.z;

        double tx = vf.tx;
        double ty = vf.ty;
        double tz = vf.tz;

        // Defensive normalization (should already be unit)
        double norm = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (norm > 0) {
            tx /= norm;
            ty /= norm;
            tz /= norm;
        }

        // Reverse direction
        double txb = -tx;
        double tyb = -ty;
        double tzb = -tz;

        // Convert direction cosines back to angles for API
        double thetaBackDeg = Math.toDegrees(Math.acos(clamp(tzb, -1.0, 1.0)));
        double phiBackDeg = Math.toDegrees(Math.atan2(tyb, txb));
        if (phiBackDeg < 0) {
            phiBackDeg += 360.0;
        }
        
        
         // Backward swim from the forward endpoint with flipped charge
        CLAS12SwimResult b = s.swim(
                -d.q[i],
                xf, yf, zf,
                d.p[i], thetaBackDeg, phiBackDeg,
                sMaxCm, hCm, tolerance
        );

        return b; // ATest will treat success/failure from this result
    }

    @Override
    protected double miss(RandomTestData d, int i, CLAS12SwimResult r) {
        // Miss is distance from origin after the backward swim
        CLAS12Values vr = r.getFinalValues();
        double x = vr.x;
        double y = vr.y;
        double z = vr.z;
        return Math.sqrt(x * x + y * y + z * z);
    }

    @Override
    protected int stepsProxy(CLAS12SwimResult r) {
        return (r == null) ? 0 : r.getNStep();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
