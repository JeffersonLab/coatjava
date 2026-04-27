package cnuphys.CLAS12Swim;

public class DormandPrince {

	public static void solve(ODE ode, double[] y0, double t0, double t1, double h, double tolerance,
							 double minH, double maxH, ODEStepListener listener) {

		final double[] c = { 0, 1.0 / 5, 3.0 / 10, 4.0 / 5, 8.0 / 9, 1, 1 };
		final double[][] a = {
			{},
			{ 1.0 / 5 },
			{ 3.0 / 40, 9.0 / 40 },
			{ 44.0 / 45, -56.0 / 15, 32.0 / 9 },
			{ 19372.0 / 6561, -25360.0 / 2187, 64448.0 / 6561, -212.0 / 729 },
			{ 9017.0 / 3168, -355.0 / 33, 46732.0 / 5247, 49.0 / 176, -5103.0 / 18656 },
			{ 35.0 / 384, 0, 500.0 / 1113, 125.0 / 192, -2187.0 / 6784, 11.0 / 84 }
		};

		final double[] b     = { 35.0 / 384, 0, 500.0 / 1113, 125.0 / 192, -2187.0 / 6784, 11.0 / 84, 0 };
		final double[] bStar = { 5179.0 / 57600, 0, 7571.0 / 16695, 393.0 / 640,
								  -92097.0 / 339200, 187.0 / 2100, 1.0 / 40 };

		int n = y0.length;
		double t = t0;
		double[] y = y0.clone();

		double[] yTemp = new double[n];
		double[] yTempStar = new double[n];
		double[] error = new double[n];
		double[] scratch = new double[n];

		double k1[] = new double[n];
		double k2[] = new double[n];
		double k3[] = new double[n];
		double k4[] = new double[n];
		double k5[] = new double[n];
		double k6[] = new double[n];
        double k7[] = new double[n];
        
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

				ODEStepMath.scaleAndAdd(y, k1, a[6][0] * h, k2, a[6][1] * h, k3, a[6][2] * h,
						k4, a[6][3] * h, k5, a[6][4] * h, k6, a[6][5] * h, scratch);
				ode.getDerivativesInto(t + c[6] * h, scratch, k7);

				for (int i = 0; i < n; i++) {
					yTemp[i] = y[i] + h * (b[0]*k1[i] + b[1]*k2[i] + b[2]*k3[i] +
										   b[3]*k4[i] + b[4]*k5[i] + b[5]*k6[i] + b[6]*k7[i]);

					yTempStar[i] = y[i] + h * (bStar[0]*k1[i] + bStar[1]*k2[i] + bStar[2]*k3[i] +
											   bStar[3]*k4[i] + bStar[4]*k5[i] + bStar[5]*k6[i] + bStar[6]*k7[i]);

					error[i] = Math.abs(yTemp[i] - yTempStar[i]);
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
