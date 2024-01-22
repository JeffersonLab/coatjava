package cnuphys.CLAS12Swim;

import java.util.ArrayList;

import cnuphys.magfield.FieldProbe;
import cnuphys.magfield.RotatedCompositeProbe;
import cnuphys.swim.SwimTrajectory;

/**
 * This trajectory is in cm and has the path length in cm.
 */
public class CLAS12Trajectory extends SwimTrajectory {

	// cache the path length
	public ArrayList<Double> _s = new ArrayList<>(200);

	/**
	 * Create a trajectory with the given initial values
	 *
	 * @param initialValues the initial values
	 */
	public CLAS12Trajectory(CLAS12Values initialValues) {
		super(initialValues.toGeneratedParticleRecord(), 200);
	}

	/**
	 * add a new point in the trajectory
	 *
	 * @param s the path length in cm
	 * @param u the state vector
	 */
	public void add(double s, double[] u) {
		_s.add(s);

		// this will call the superclass add which adds A COPY of the state vector
		super.add(u);
	}

	/**
	 * Replace the last point in the trajectory with a new point
	 *
	 * @param s the replacement path length in cm
	 * @param u the replacement state vector
	 */
	public void replaceLastPoint(double s, double[] u) {
		if (size() > 0) {
			int index = size() - 1;
			_s.remove(index);
			remove(index);
			add(s, u);
		}
	}

	/**
	 * Remove the last point in the trajectory
	 */
	public void removeLastPoint() {
		removePoint(size() - 1);
	}

	/**
	 * Remove the point at the given index
	 * @param index the index
	 */
	public void removePoint(int index) {
		_s.remove(index);
		remove(index);
	}

	/**
	 * Get the path length at the given index
	 *
	 * @param index the index
	 * @return the path length at the given index
	 */
	public double getS(int index) {
		return _s.get(index);
	}

	/**
	 * Get the size of the s collection, which is the same as the size of the
	 * trajectory. (At least is should be!)
	 *
	 * @return the size of the s collection
	 */
	public int getSSize() {
		return _s.size();
	}

	/**
	 * Report on the sizes. They should be the same.
	 *
	 * @return a report on the sizes
	 */
	public String sizeReport() {
		return String.format("State vector size: %d   Pathlength size: %d", size(), _s.size());
	}

	/**
	 * Clear the trajectory
	 */
	@Override
	public void clear() {
		super.clear();
		_s.clear();
	}

	@Override
	public boolean add(double u[]) {
		System.err.println("BAD add double[] called for CLAS12Trajectory object");
		System.exit(1);
		return false;
	}

	@Override
	public boolean add(double u[], double s) {
		System.err.println("BAD add double[], double s called forCLAS12Trajectory object");
		System.exit(1);
		return false;
	}

	@Override
	public void add(double xo, double yo, double zo, double p, double theta, double phi) {
		System.err.println("BAD add x, y, z, p, theta, phi called for CLAS12Trajectory object");
		System.exit(1);
	}

	/**
	 * Add a point to the trajectory.
	 *
	 * @param x     the x coordinate in cm
	 * @param y     the y coordinate in cm
	 * @param z     the z coordinate in cm
	 * @param theta the theta angle in degrees
	 * @param phi   the phi angle in degrees
	 * @param s     the path length in cm
	 */
	public void addPoint(double x, double y, double z, double theta, double phi, double s) {
		double thetaRad = Math.toRadians(theta);
		double phiRad = Math.toRadians(phi);
		double sinTheta = Math.sin(thetaRad);

		double tx = sinTheta * Math.cos(phiRad); // px/p
		double ty = sinTheta * Math.sin(phiRad); // py/p
		double tz = Math.cos(thetaRad); // pz/p

		double u[] = new double[6];
		// set uf (in the result container) to the starting state vector
		u[0] = x;
		u[1] = y;
		u[2] = z;
		u[3] = tx;
		u[4] = ty;
		u[5] = tz;
		add(s, u);
	}

	/**
	 * Get the r coordinate in cm for the given index
	 *
	 * @param index the index
	 * @return the r coordinate
	 */
	@Override
	public double getR(int index) {
		if ((index < 0) || (index > size())) {
			return Double.NaN;
		}

		double v[] = get(index);
		if (v == null) {
			return Double.NaN;
		}

		double x = v[0];
		double y = v[1];
		double z = v[2];

		// convert to cm
		return Math.sqrt(x * x + y * y + z * z);
	}

	/**
	 * Get the total BDL integral if computed
	 *
	 * @return the total BDL integral in kG-m
	 */
	@Override
	public double getComputedBDL() {
		if (Double.isNaN(_bdlValue)) {
			computeBDL(FieldProbe.factory());
		}

		return _bdlValue;
	}



	private double _bdlValue = Double.NaN;

	/**
	 * Compute the integral B cross dl. This will cause the state vector arrays to
	 * expand by two, becoming [x, y, z, px/p, py/p, pz/p, l, bdl] where the 7th
	 * entry l is cumulative pathlength in cm and the eighth entry bdl is the
	 * cumulative integral bdl in kG-cm.
	 *
	 * @param probe the field getter
	 */
	@Override
	public void computeBDL(FieldProbe probe) {

		if (probe instanceof RotatedCompositeProbe) {
			System.err.println(
					"SHOULD NOT HAPPEN. In rotated composite field probe, should not call Bxdl accumlate without the sector argument.");

			(new Throwable()).printStackTrace();
			System.exit(1);

		}

		if (!Double.isNaN(_bdlValue)) {
			return;
		}

		_bdlValue = 0;
		if (this.size() < 2) {
			return;
		}

		for (int i = 0; i < size() - 2; i++) {
			double[] p0 = get(i);
			double[] p1 = get(i + 1);

			double dr[] = new double[3];
			float b[] = new float[3];

			for (int j = 0; j < 3; j++) {
				dr[j] = p1[j] - p0[j];
			}


			// use the average position (in cm) to compute B for b cross dl
			float xavgcm = (float) ((p0[0] + p1[0]) / 2);
			float yavgcm = (float) ((p0[1] + p1[1]) / 2);
			float zavgcm = (float) ((p0[2] + p1[2]) / 2);

			// get the field at the average position
			probe.field(xavgcm, yavgcm, zavgcm, b);

			for (int j = 0; j < 3; j++) {
				dr[j] = p1[j] - p0[j];
			}

			double[] bxdl = cross(b, dr);
			double magbxdl = vecmag(bxdl);
			_bdlValue += magbxdl;
		}

	}

	/**
	 * Compute the integral B cross dl. This will cause the state vector arrays to
	 * expand by two, becoming [x, y, z, px/p, py/p, pz/p, l, bdl] where the 7th
	 * entry l is cumulative pathlength in cm and the eighth entry bdl is the
	 * cumulative integral bdl in kG-cm.
	 *
	 * @param sector sector 1..6
	 * @param probe  the field getter
	 */
	@Override
	public void sectorComputeBDL(int sector, RotatedCompositeProbe probe) {

		if (!Double.isNaN(_bdlValue)) {
			return;
		}

		_bdlValue = 0;
		if (this.size() < 2) {
			return;
		}

		for (int i = 0; i < size() - 2; i++) {
			double[] p0 = get(i);
			double[] p1 = get(i + 1);

			double dr[] = new double[3];
			float b[] = new float[3];

			for (int j = 0; j < 3; j++) {
				dr[j] = p1[j] - p0[j];
			}


			// use the average position (in cm) to compute B for b cross dl
			float xavgcm = (float) ((p0[0] + p1[0]) / 2);
			float yavgcm = (float) ((p0[1] + p1[1]) / 2);
			float zavgcm = (float) ((p0[2] + p1[2]) / 2);

			// get the field at the average position
			probe.field(sector, xavgcm, yavgcm, zavgcm, b);

			for (int j = 0; j < 3; j++) {
				dr[j] = p1[j] - p0[j];
			}

			double[] bxdl = cross(b, dr);
			double magbxdl = vecmag(bxdl);
			_bdlValue += magbxdl;
		}
	}


	// usual cross product used for BDL
	private static double[] cross(float a[], double b[]) {
		double c[] = new double[3];
		c[0] = a[1] * b[2] - a[2] * b[1];
		c[1] = a[2] * b[0] - a[0] * b[2];
		c[2] = a[0] * b[1] - a[1] * b[0];
		return c;
	}

	// usual vec mag used for BDL
	private static double vecmag(double a[]) {
		double asq = a[0] * a[0] + a[1] * a[1] + a[2] * a[2];
		return Math.sqrt(asq);
	}

}
