package cnuphys.CLAS12Swim;

import java.util.Arrays;

import cnuphys.magfield.FieldProbe;
import cnuphys.magfield.RotatedCompositeProbe;
import cnuphys.swim.SwimTrajectory;

@SuppressWarnings("serial")
public class CLAS12Trajectory extends SwimTrajectory {

	private double[] _s = new double[200];
	private int _sSize = 0;

	private double _bdlValue = Double.NaN;

	public CLAS12Trajectory(CLAS12Values initialValues) {
		super(initialValues.toGeneratedParticleRecord(), 200);
	}

	public void add(double s, double[] u) {
		addS(s);
		super.add(u);
		_bdlValue = Double.NaN;
	}

	public void replaceLastPoint(double s, double[] u) {
		if (_sSize > 0) {
			int index = _sSize - 1;
			removePoint(index);
			add(s, u);
		}
	}

	public void removeLastPoint() {
		if (_sSize > 0) {
			removePoint(_sSize - 1);
			_bdlValue = Double.NaN;
		}
	}

	public void removePoint(int index) {
		if (index >= 0 && index < _sSize) {
			System.arraycopy(_s, index + 1, _s, index, _sSize - index - 1);
			_sSize--;
			remove(index);
			_bdlValue = Double.NaN;
		}
	}

	public double getS(int index) {
		return _s[index];
	}

	public int getSSize() {
		return _sSize;
	}

	public String sizeReport() {
		return String.format("State vector size: %d   Pathlength size: %d", size(), _sSize);
	}

	@Override
	public void clear() {
		super.clear();
		_sSize = 0;
		_bdlValue = Double.NaN;
	}

	@Override
	public boolean add(double[] u) {
		throw new UnsupportedOperationException("Use add(s, u) instead.");
	}

	@Override
	public boolean add(double[] u, double s) {
		throw new UnsupportedOperationException("Use add(s, u) instead.");
	}

	@Override
	public void add(double xo, double yo, double zo, double p, double theta, double phi) {
		throw new UnsupportedOperationException("Use addPoint instead.");
	}

	public void addPoint(double x, double y, double z, double theta, double phi, double s) {
		double thetaRad = Math.toRadians(theta);
		double phiRad = Math.toRadians(phi);
		double sinTheta = Math.sin(thetaRad);

		double[] u = new double[6];
		u[0] = x;
		u[1] = y;
		u[2] = z;
		u[3] = sinTheta * Math.cos(phiRad);
		u[4] = sinTheta * Math.sin(phiRad);
		u[5] = Math.cos(thetaRad);

		add(s, u);
	}

	@Override
	public double getR(int index) {
		if ((index < 0) || (index >= size())) {
			return Double.NaN;
		}

		double[] v = get(index);
		return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
	}

	@Override
	public double getComputedBDL() {
		if (Double.isNaN(_bdlValue)) {
			computeBDL(FieldProbe.factory());
		}
		return _bdlValue;
	}

	@Override
	public void computeBDL(FieldProbe probe) {
		if (!(probe instanceof RotatedCompositeProbe) && Double.isNaN(_bdlValue) && size() >= 2) {
			_bdlValue = 0.0;
			int n = size();
			double[] dr = new double[3];
			float[] b = new float[3];
			double[] bxdl = new double[3];

			for (int i = 0; i < n - 1; i++) {
				double[] p0 = get(i);
				double[] p1 = get(i + 1);

				dr[0] = p1[0] - p0[0];
				dr[1] = p1[1] - p0[1];
				dr[2] = p1[2] - p0[2];

				float xavg = (float) ((p0[0] + p1[0]) * 0.5);
				float yavg = (float) ((p0[1] + p1[1]) * 0.5);
				float zavg = (float) ((p0[2] + p1[2]) * 0.5);

				probe.field(xavg, yavg, zavg, b);
				cross(b, dr, bxdl);
				_bdlValue += vecmag(bxdl);
			}
		}
	}

	@Override
	public void sectorComputeBDL(int sector, RotatedCompositeProbe probe) {
		if (Double.isNaN(_bdlValue) && size() >= 2) {
			_bdlValue = 0.0;
			int n = size();
			double[] dr = new double[3];
			float[] b = new float[3];
			double[] bxdl = new double[3];

			for (int i = 0; i < n - 1; i++) {
				double[] p0 = get(i);
				double[] p1 = get(i + 1);

				dr[0] = p1[0] - p0[0];
				dr[1] = p1[1] - p0[1];
				dr[2] = p1[2] - p0[2];

				float xavg = (float) ((p0[0] + p1[0]) * 0.5);
				float yavg = (float) ((p0[1] + p1[1]) * 0.5);
				float zavg = (float) ((p0[2] + p1[2]) * 0.5);

				probe.field(sector, xavg, yavg, zavg, b);
				cross(b, dr, bxdl);
				_bdlValue += vecmag(bxdl);
			}
		}
	}

	private void addS(double s) {
		if (_sSize >= _s.length) {
			_s = Arrays.copyOf(_s, _s.length * 2);
		}
		_s[_sSize++] = s;
	}

	private static void cross(float[] a, double[] b, double[] out) {
		out[0] = a[1] * b[2] - a[2] * b[1];
		out[1] = a[2] * b[0] - a[0] * b[2];
		out[2] = a[0] * b[1] - a[1] * b[0];
	}

	private static double vecmag(double[] a) {
		return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
	}
}
