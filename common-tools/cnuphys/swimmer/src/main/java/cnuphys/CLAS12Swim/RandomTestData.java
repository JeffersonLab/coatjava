package cnuphys.CLAS12Swim;

import java.util.Random;

/**
 * A class to generate random test data
 */
public class RandomTestData {
	public int q[];
	public double xo[]; // vertex x in cm
	public double yo[]; // vertex y in cm
	public double zo[]; // vertex z in cm
	public double p[]; // momentum in GeV/c
	public double theta[]; // directional polar angle in degrees
	public double phi[]; // directional azimuthal angle in degrees

	private Random rand;

	/**
	 * Create some random data within a sector for a swim test
	 *
	 * @param n      the number of points
	 * @param seed   random number seed
	 * @param sector the sector [1..6]
	 */
	public RandomTestData(int n, long seed, int sector) {

		this(n, seed, -0.01, 0.02, -0.01, 0.02, -0.01, 0.02, 0.9, 5.0, 25, 20, -30 + (sector - 1) * 60 + 10, 40);
	}

	/**
	 * Create some random data for a swim test
	 *
	 * @param n    the number of points
	 * @param seed random number seed
	 */
	public RandomTestData(int n, long seed) {

		rand = new Random(seed);

		// random sector

		q = new int[n];
		xo = new double[n];
		yo = new double[n];
		zo = new double[n];
		p = new double[n];
		theta = new double[n];
		phi = new double[n];

		for (int i = 0; i < n; i++) {
			int sector = rand.nextInt(5) + 1;
			double phiMin = (sector - 1) * 60;

			q[i] = (rand.nextDouble() < 0.5) ? -1 : 1;
			p[i] = dval(0.9, 5.0); // Gev
			theta[i] = dval(25, 20);
			phi[i] = dval(phiMin, 40);

			xo[i] = dval(-1., 2.); // cm
			yo[i] = dval(-1., 2.); // cm
			zo[i] = dval(-1., 2.); // cm

		}

	}

	/**
	 * Create some random data for a swim test
	 *
	 * @param n        the number to generate
	 * @param seed     the random number seed
	 * @param xmin     the x vertex minimum (cm)
	 * @param dx       the spread in x vertex (cm)
	 * @param ymin     the y vertex minimum (cm)
	 * @param dy       the spread in y vertex (cm)
	 * @param zmin     the z vertex minimum (cm)
	 * @param dz       the spread in z vertex (cm)
	 * @param pmin     the minimum momentum p in Gev/c
	 * @param dp       the spread in momentum p
	 * @param thetamin the minimum theta in degrees
	 * @param dtheta   the spread in theta
	 * @param phimin   the minimum phi in degrees
	 * @param dphi     the spread in phi
	 */
	public RandomTestData(int n, long seed, double xmin, double dx, double ymin, double dy, double zmin, double dz,
			double pmin, double dp, double thetamin, double dtheta, double phimin, double dphi) {

		if (seed < 0) {
			rand = new Random();
		} else {
			rand = new Random(seed);
		}

		q = new int[n];
		xo = new double[n];
		yo = new double[n];
		zo = new double[n];
		p = new double[n];
		theta = new double[n];
		phi = new double[n];

		for (int i = 0; i < n; i++) {
			q[i] = (rand.nextDouble() < 0.5) ? -1 : 1;
			p[i] = dval(pmin, dp); // Gev
			theta[i] = dval(thetamin, dtheta);
			phi[i] = dval(phimin, dphi);

			xo[i] = dval(xmin, dx); // meters
			yo[i] = dval(ymin, dy); // meters
			zo[i] = dval(zmin, dz); // meters

		}

	}

	public String toStringRaw(int index) {
		return String.format("%2d %7.4f  %7.4f  %7.4f   %6.3f   %6.3f  %7.3f", q[index], xo[index], yo[index],
				zo[index], p[index], theta[index], phi[index]);
	}

	// get a random value in a range
	private double dval(double min, double dv) {
		return min + dv * rand.nextDouble();
	}
}