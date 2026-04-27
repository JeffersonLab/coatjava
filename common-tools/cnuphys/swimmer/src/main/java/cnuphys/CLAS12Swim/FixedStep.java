package cnuphys.CLAS12Swim;

import cnuphys.CLAS12Swim.geometry.Vector;

public class FixedStep {

	/**
	 * Solves an ordinary differential equation (ODE) using the 4th-order fixed step
	 * size Runge-Kutta method.
	 * 
	 * @param ode      The ODE to solve.
	 * @param y0       The initial state vector.
	 * @param t0       The initial time.
	 * @param t1       The final time.
	 * @param h        The fixed step size.
	 * @param listener An instance of ODEStepListener to monitor the integration
	 *                 process.
	 * @return The state vector at the final time or when the listener terminates
	 *         the integration.
	 */
	public static double[] solve(ODE ode, double[] y0, double t0, double t1, double h, ODEStepListener listener) {
		int n = y0.length;

		double t = t0;

		double[] y = y0.clone();
		
		double k1[] = new double[n];
		double k2[] = new double[n];
		double k3[] = new double[n];
		double k4[] = new double[n];
		double[] ytemp = new double[n];

		while (t < t1) {
		    if (t + h > t1) h = t1 - t;

		    ode.getDerivativesInto(t, y, k1);

		    ODEStepMath.scaleAndAdd(y, k1, 0.5*h, ytemp);
		    ode.getDerivativesInto(t + 0.5*h, ytemp, k2);

		    ODEStepMath.scaleAndAdd(y, k2, 0.5*h, ytemp);
		    ode.getDerivativesInto(t + 0.5*h, ytemp, k3);

		    ODEStepMath.scaleAndAdd(y, k3, h, ytemp);
		    ode.getDerivativesInto(t + h, ytemp, k4);

		    // y += h/6*(k1 + 2k2 + 2k3 + k4)
		    for (int i = 0; i < n; i++) {
		        y[i] += (h/6.0) * (k1[i] + 2.0*k2[i] + 2.0*k3[i] + k4[i]);
		    }

		    t += h;
		    if (listener != null && !listener.newStep(t, y)) break;		}

		return y;
	}

}
