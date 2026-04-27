package cnuphys.CLAS12Swim.test;

import java.util.Objects;
import java.util.Random;

/**
 * Generates randomized particle start conditions for swimmer accuracy/speed tests.
 *
 * <h2>Units</h2>
 * <ul>
 *   <li>Positions {@code (xo, yo, zo)} are in <b>cm</b>.</li>
 *   <li>Momentum magnitude {@code p} is in <b>GeV/c</b>.</li>
 *   <li>Angles {@code theta, phi} are in <b>degrees</b>.</li>
 * </ul>
 *
 * <h2>Angle conventions</h2>
 * {@code theta} is the polar angle measured from +z; {@code phi} is the azimuthal angle in the x-y plane.
 *
 * <h2>CLAS12 sector convention</h2>
 * For convenience, this generator can constrain {@code phi} to a single CLAS12 sector.
 * The default sector window used here is a 40° wide band centered on the sector mid-plane:
 * {@code phi ∈ [sectorBase + 10°, sectorBase + 50°]}, where {@code sectorBase = -30° + 60°*(sector-1)}.
 *
 * <p>
 * This class is intentionally simple: it stores the generated samples in public arrays for fast access by tests.
 * </p>
 */
public class RandomTestData {

    /** Charge in units of {@code e}; values are {@code +1} or {@code -1}. */
    public int[] q;

    /** Vertex x (cm). */
    public double[] xo;

    /** Vertex y (cm). */
    public double[] yo;

    /** Vertex z (cm). */
    public double[] zo;

    /** Momentum magnitude p (GeV/c). */
    public double[] p;

    /** Polar angle theta (degrees). */
    public double[] theta;

    /** Azimuthal angle phi (degrees). */
    public double[] phi;

    private final Random rand;

    /**
     * Create random test data constrained to a single CLAS12 sector.
     * <p>
     * This constructor uses a practical default phase space intended for swimmer regression tests:
     * modest vertex offsets around the origin, mid-range momenta, and angles that keep tracks inside
     * typical acceptance.
     * </p>
     *
     * @param n      number of samples to generate (must be &gt;= 0)
     * @param seed   RNG seed; if negative, a non-deterministic seed is used
     * @param sector CLAS12 sector in {@code [1..6]}
     * @throws IllegalArgumentException if {@code sector} is not in {@code [1..6]}
     */
    public RandomTestData(int n, long seed, int sector) {
        this(n, seed, defaultSectorConfig(sector));
    }

    /**
     * Create random test data spanning all CLAS12 sectors.
     * <p>
     * Each sample chooses a random sector in {@code [1..6]} and then chooses {@code phi} uniformly within
     * that sector's default 40° window (see class documentation).
     * </p>
     *
     * @param n    number of samples to generate (must be &gt;= 0)
     * @param seed RNG seed; if negative, a non-deterministic seed is used
     */
    public RandomTestData(int n, long seed) {
        this(n, seed, Config.allSectorsDefault());
    }

    /**
     * Create random test data using a fully specified {@link Config}.
     *
     * @param n      number of samples to generate (must be &gt;= 0)
     * @param seed   RNG seed; if negative, a non-deterministic seed is used
     * @param config configuration controlling ranges (must not be {@code null})
     */
    public RandomTestData(int n, long seed, Config config) {
        Objects.requireNonNull(config, "config");
        this.rand = (seed < 0) ? new Random() : new Random(seed);
        allocateArrays(n);

        for (int i = 0; i < n; i++) {
            int sector = config.pickSector(rand);

            q[i] = randomCharge();
            p[i] = uniform(config.pMin, config.pMax);

            theta[i] = uniform(config.thetaMinDeg, config.thetaMaxDeg);
            phi[i] = uniform(config.phiMinDegForSector(sector), config.phiMaxDegForSector(sector));

            xo[i] = uniform(config.xMinCm, config.xMaxCm);
            yo[i] = uniform(config.yMinCm, config.yMaxCm);
            zo[i] = uniform(config.zMinCm, config.zMaxCm);
        }
    }

    /**
     * Backward-compatible constructor matching the original signature.
     * <p>
     * NOTE: the {@code d*} parameters are treated as <b>ranges</b>, not maxima:
     * values are generated as {@code min + d*U} where {@code U ∈ [0,1)}.
     * This is easy to misuse, so prefer {@link #RandomTestData(int, long, Config)} for new code.
     * </p>
     *
     * @param n        number of samples
     * @param seed     RNG seed; if negative, non-deterministic seed is used
     * @param xmin     minimum x (cm)
     * @param dx       x range (cm)
     * @param ymin     minimum y (cm)
     * @param dy       y range (cm)
     * @param zmin     minimum z (cm)
     * @param dz       z range (cm)
     * @param pmin     minimum momentum (GeV/c)
     * @param dp       momentum range (GeV/c)
     * @param thetamin minimum theta (deg)
     * @param dtheta   theta range (deg)
     * @param phimin   minimum phi (deg)
     * @param dphi     phi range (deg)
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
            p[i] = xminPlusRange(pmin, dp);
            theta[i] = xminPlusRange(thetamin, dtheta);
            phi[i] = xminPlusRange(phimin, dphi);

            xo[i] = xminPlusRange(xmin, dx);
            yo[i] = xminPlusRange(ymin, dy);
            zo[i] = xminPlusRange(zmin, dz);
        }
    }

    /**
     * Return a compact multi-line string dump of all samples.
     * Useful for debugging or logging a small test run.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Index  q     xo(cm)   yo(cm)   zo(cm)    p(GeV/c)  theta(deg)   phi(deg)\n");
        for (int i = 0; i < q.length; i++) {
            sb.append(String.format("%3d:  %2d  %8.4f %8.4f %8.4f  %9.4f  %10.3f  %9.3f%n",
                    i, q[i], xo[i], yo[i], zo[i], p[i], theta[i], phi[i]));
        }
        return sb.toString();
    }

    /**
     * Return a one-line, whitespace-friendly representation of the sample at {@code index}.
     *
     * @param index sample index
     * @return formatted string in units documented in this class
     * @throws ArrayIndexOutOfBoundsException if {@code index} is out of range
     */
    public String toStringRaw(int index) {
        return String.format("%2d %10.6f %10.6f %10.6f  %9.6f  %9.6f  %10.6f",
                q[index], xo[index], yo[index], zo[index], p[index], theta[index], phi[index]);
    }

    // ---------------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------------

    /**
     * Configuration describing the phase-space ranges from which samples are drawn.
     * All values use the units stated in {@link RandomTestData}.
     */
    public static final class Config {
        // positions (cm)
        public final double xMinCm, xMaxCm;
        public final double yMinCm, yMaxCm;
        public final double zMinCm, zMaxCm;

        // momentum (GeV/c)
        public final double pMin, pMax;

        // angles (degrees)
        public final double thetaMinDeg, thetaMaxDeg;

        // sector controls
        private final boolean allSectors;
        private final int fixedSector;

        // phi window within a sector (degrees relative to sector base)
        private final double phiOffsetMinDeg;
        private final double phiOffsetMaxDeg;

        private Config(boolean allSectors,
                       int fixedSector,
                       double xMinCm, double xMaxCm,
                       double yMinCm, double yMaxCm,
                       double zMinCm, double zMaxCm,
                       double pMin, double pMax,
                       double thetaMinDeg, double thetaMaxDeg,
                       double phiOffsetMinDeg, double phiOffsetMaxDeg) {

            this.allSectors = allSectors;
            this.fixedSector = fixedSector;

            this.xMinCm = xMinCm;
            this.xMaxCm = xMaxCm;
            this.yMinCm = yMinCm;
            this.yMaxCm = yMaxCm;
            this.zMinCm = zMinCm;
            this.zMaxCm = zMaxCm;

            this.pMin = pMin;
            this.pMax = pMax;

            this.thetaMinDeg = thetaMinDeg;
            this.thetaMaxDeg = thetaMaxDeg;

            this.phiOffsetMinDeg = phiOffsetMinDeg;
            this.phiOffsetMaxDeg = phiOffsetMaxDeg;
        }

        /**
         * Default configuration spanning all sectors with the same phase space as the legacy generator.
         *
         * @return default all-sectors configuration
         */
        public static Config allSectorsDefault() {
            // These defaults mirror the spirit of the prior constructors, but expressed as min/max.
            return new Config(
                    true, 1,
                    -1.0, 1.0,   // x in [-1,1] cm
                    -1.0, 1.0,   // y in [-1,1] cm
                    -1.0, 1.0,   // z in [-1,1] cm
                    0.9, 5.0,    // p in [0.9,5.0] GeV/c
                    25.0, 45.0,  // theta in [25,45] deg
                    10.0, 50.0   // phi offset in [10,50] deg (40° window)
            );
        }

        /**
         * Create a configuration fixed to a single sector.
         *
         * @param sector CLAS12 sector in {@code [1..6]}
         * @return configuration fixed to {@code sector}
         */
        public static Config forSector(int sector) {
            if (sector < 1 || sector > 6) {
                throw new IllegalArgumentException("sector must be in [1..6], got " + sector);
            }
            Config base = allSectorsDefault();
            return new Config(
                    false, sector,
                    base.xMinCm, base.xMaxCm,
                    base.yMinCm, base.yMaxCm,
                    base.zMinCm, base.zMaxCm,
                    base.pMin, base.pMax,
                    base.thetaMinDeg, base.thetaMaxDeg,
                    base.phiOffsetMinDeg, base.phiOffsetMaxDeg
            );
        }

        int pickSector(Random r) {
            return allSectors ? (r.nextInt(6) + 1) : fixedSector;
        }

        double phiMinDegForSector(int sector) {
            return sectorBaseDeg(sector) + phiOffsetMinDeg;
        }

        double phiMaxDegForSector(int sector) {
            return sectorBaseDeg(sector) + phiOffsetMaxDeg;
        }

        private static double sectorBaseDeg(int sector) {
            return -30.0 + 60.0 * (sector - 1);
        }
    }

    private static Config defaultSectorConfig(int sector) {
        // The original sector constructor used very small vertex ranges in cm; preserve that intent.
        Config base = Config.forSector(sector);
        return new Config(
                false, sector,
                -0.01, 0.01,   // x in [-0.01, 0.01] cm
                -0.01, 0.01,   // y in [-0.01, 0.01] cm
                -0.01, 0.01,   // z in [-0.01, 0.01] cm
                base.pMin, base.pMax,
                base.thetaMinDeg, base.thetaMaxDeg,
                base.phiOffsetMinDeg, base.phiOffsetMaxDeg
        );
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

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

    private double uniform(double minInclusive, double maxExclusive) {
        if (maxExclusive <= minInclusive) {
            // Be forgiving: if someone passes swapped bounds, generate a constant.
            return minInclusive;
        }
        return minInclusive + (maxExclusive - minInclusive) * rand.nextDouble();
    }

    private double xminPlusRange(double min, double range) {
        return min + range * rand.nextDouble();
    }
}
