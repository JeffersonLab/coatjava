package cnuphys.CLAS12Swim;

public class CashKarp {

	public static void solve(ODE ode, double[] y0, double t0, double t1, double h, double tolerance,
							 double minH, double maxH, ODEStepListener listener) {

		final double[] c = { 0, 1.0 / 5, 3.0 / 10, 3.0 / 5, 1, 7.0 / 8 };
		final double[][] a = {
			{},
			{ 1.0 / 5 },
			{ 3.0 / 40, 9.0 / 40 },
			{ 3.0 / 10, -9.0 / 10, 6.0 / 5 },
			{ -11.0 / 54, 5.0 / 2, -70.0 / 27, 35.0 / 27 },
			{ 1631.0 / 55296, 175.0 / 512, 575.0 / 13824, 44275.0 / 110592, 253.0 / 4096 }
		};

		final double[] b4 = { 2825.0 / 27648, 0, 18575.0 / 48384, 13525.0 / 55296, 277.0 / 14336, 1.0 / 4 };
		final double[] b5 = { 37.0 / 378, 0, 250.0 / 621, 125.0 / 594, 0, 512.0 / 1771 };

		int n = y0.length;
		double t = t0;
		double[] y = y0.clone();

		double[] yTemp = new double[n];
		double[] yTemp4 = new double[n];
		double[] error = new double[n];
		double[] scratch = new double[n];
		
		double k1[] = new double[n];
		double k2[] = new double[n];
		double k3[] = new double[n];
		double k4[] = new double[n];
		double k5[] = new double[n];
		double k6[] = new double[n];

		while (t < t1) {
			if (t + h > t1) h = t1 - t;

			boolean acceptStep = false;
			while (!acceptStep) {
				ode.getDerivativesInto(t, y, k1);
				ODEStepMath.scaleAndAdd(y, k1, a[1][0] * h, scratch);
				ode.getDerivativesInto(t + c[1] * h, scratch, k2);

				ODEStepMath.scaleAndAdd(y, k1, a[2][0] * h, k2, a[2][1] * h, scratch);
				ode.getDerivativesInto(t + c[2] * h, scratch, k3);

				ODEStepMath.scaleAndAdd(y, k1, a[3][0] * h, k2, a[3][1] * h, k3, a[3][2] * h, scratch);
				ode.getDerivativesInto(t + c[3] * h, scratch, k4);

				ODEStepMath.scaleAndAdd(y, k1, a[4][0] * h, k2, a[4][1] * h, k3, a[4][2] * h,
						k4, a[4][3] * h, scratch);
				ode.getDerivativesInto(t + c[4] * h, scratch, k5);

				ODEStepMath.scaleAndAdd(y, k1, a[5][0] * h, k2, a[5][1] * h, k3, a[5][2] * h,
						k4, a[5][3] * h, k5, a[5][4] * h, scratch);
				ode.getDerivativesInto(t + c[5] * h, scratch, k6);

				for (int i = 0; i < n; i++) {
					yTemp[i] = y[i] + h * (b5[0]*k1[i] + b5[1]*k2[i] + b5[2]*k3[i] +
										   b5[3]*k4[i] + b5[4]*k5[i] + b5[5]*k6[i]);
					yTemp4[i] = y[i] + h * (b4[0]*k1[i] + b4[1]*k2[i] + b4[2]*k3[i] +
											b4[3]*k4[i] + b4[4]*k5[i] + b4[5]*k6[i]);
					error[i] = Math.abs(yTemp[i] - yTemp4[i]);
				}

				double maxError = ODEStepMath.getMax(error);
				if (maxError < tolerance) {
					acceptStep = true;
					if (!listener.newStep(t + h, yTemp)) return;
					System.arraycopy(yTemp, 0, y, 0, n);
					t += h;
				}

				h = adjustStepSize(h, maxError, tolerance, minH, maxH);
			}
		}
	}

	private static double adjustStepSize(double h, double error, double tol, double minH, double maxH) {
		double safety = 0.9;
		double ratio = (error == 0) ? 1e-6 : error / tol;
		double scale = safety * Math.pow(ratio, -0.25);
		scale = Math.max(0.2, Math.min(5.0, scale));
		double newH = h * scale;
		return Math.min(maxH, Math.max(minH, newH));
	}
}
