package cnuphys.CLAS12Swim;

import cnuphys.CLAS12Swim.geometry.Vector;

public class FixedStep {

	 /**
     * Solves an ordinary differential equation (ODE) using the 4th-order fixed step size Runge-Kutta method.
     * @param ode The ODE to solve.
     * @param y0 The initial state vector.
     * @param t0 The initial time.
     * @param t1 The final time.
     * @param h The fixed step size.
     * @param listener An instance of ODEStepListener to monitor the integration process.
     * @return The state vector at the final time or when the listener terminates the integration.
     */
    public static double[] solve(ODE ode, double[] y0, double t0, double t1, double h, ODEStepListener listener) {
        int n = y0.length;
        
		double t = t0;

        double[] y = y0.clone();

		while (t < t1) {
        	
			if (t + h > t1) {
				h = t1 - t;
			}

            double[] k1 = ode.getDerivatives(t, y);
            double[] k2 = ode.getDerivatives(t + h / 2.0, Vector.addVectors(y, Vector.scalarMultiply(k1, h / 2.0)));
            double[] k3 = ode.getDerivatives(t + h / 2.0, Vector.addVectors(y, Vector.scalarMultiply(k2, h / 2.0)));
            double[] k4 = ode.getDerivatives(t + h, Vector.addVectors(y, Vector.scalarMultiply(k3, h)));

            double[] yNext = y.clone();
            for (int i = 0; i < n; i++) {
                yNext[i] += h / 6.0 * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]);
            }

            if (!listener.newStep(t + h, yNext)) {
                return yNext; // Terminate integration based on listener's decision
            }

            y = yNext;
            t += h;
        }

        return y;
    }
    

}
