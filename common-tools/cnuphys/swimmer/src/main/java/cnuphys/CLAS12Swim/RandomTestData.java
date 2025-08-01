package cnuphys.CLAS12Swim;

import java.util.Random;

/**
 * A class to generate random test data for particle swim simulations.
 * Units:
 * - Position: cm
 * - Momentum: GeV/c
 * - Angles: degrees
 */
public class RandomTestData {

    public int[] q;        // charge
    public double[] xo;    // vertex x in cm
    public double[] yo;    // vertex y in cm
    public double[] zo;    // vertex z in cm
    public double[] p;     // momentum in GeV/c
    public double[] theta; // polar angle in degrees
    public double[] phi;   // azimuthal angle in degrees

    private final Random rand;

    /**
     * Generate random test data within a sector.
     *
     * @param n      number of data points
     * @param seed   random number seed
     * @param sector sector number [1..6]
     */
    public RandomTestData(int n, long seed, int sector) {
        this(n, seed,
             -0.01, 0.02,
             -0.01, 0.02,
             -0.01, 0.02,
             0.9, 5.0,
             25, 20,
             -30 + (sector - 1) * 60 + 10, 40);
    }

    /**
     * Generate random test data across all sectors.
     *
     * @param n    number of data points
     * @param seed random number seed
     */
    public RandomTestData(int n, long seed) {
        this.rand = new Random(seed);
        allocateArrays(n);

        for (int i = 0; i < n; i++) {
            int sector = rand.nextInt(6) + 1;
            double phiMin = (sector - 1) * 60;

            q[i] = randomCharge();
            p[i] = randomInRange(0.9, 5.0);
            theta[i] = randomInRange(25, 20);
            phi[i] = randomInRange(phiMin, 40);

            xo[i] = randomInRange(-1.0, 2.0);
            yo[i] = randomInRange(-1.0, 2.0);
            zo[i] = randomInRange(-1.0, 2.0);
        }
    }

    /**
     * General-purpose constructor with full parameter control.
     */
    public RandomTestData(int n, long seed,
                          double xmin, double dx,
                          double ymin, double dy,
                          double zmin, double dz,
                          double pmin, double dp,
                          double thetamin, double dtheta,
                          double phimin, double dphi) {

        this.rand = (seed < 0) ? new Random() : new Random(seed);
        allocateArrays(n);

        for (int i = 0; i < n; i++) {
            q[i] = randomCharge();
            p[i] = randomInRange(pmin, dp);
            theta[i] = randomInRange(thetamin, dtheta);
            phi[i] = randomInRange(phimin, dphi);

            xo[i] = randomInRange(xmin, dx);
            yo[i] = randomInRange(ymin, dy);
            zo[i] = randomInRange(zmin, dz);
        }
    }

    /**
     * Return a compact string representation of the i-th particle.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Index  q     xo      yo      zo      p      theta     phi\n");
        for (int i = 0; i < q.length; i++) {
            sb.append(String.format("%3d:  %2d  %7.4f %7.4f %7.4f  %6.3f  %6.2f  %7.2f\n",
                    i, q[i], xo[i], yo[i], zo[i], p[i], theta[i], phi[i]));
        }
        return sb.toString();
    }

    public String toStringRaw(int index) {
        return String.format("%2d %7.4f  %7.4f  %7.4f   %6.3f   %6.3f  %7.3f",
                q[index], xo[index], yo[index], zo[index], p[index], theta[index], phi[index]);
    }

    // Helpers

    private void allocateArrays(int n) {
        q = new int[n];
        xo = new double[n];
        yo = new double[n];
        zo = new double[n];
        p = new double[n];
        theta = new double[n];
        phi = new double[n];
    }

    private int randomCharge() {
        return rand.nextBoolean() ? 1 : -1;
    }

    private double randomInRange(double min, double range) {
        return min + range * rand.nextDouble();
    }
}
