package cnuphys.CLAS12Swim;

public class TestDP {

	public static void main(String[] args) {
		// Parameters for the damped harmonic oscillator
		double m = 1; // mass
		double b = 1; // damping coefficient
		double k = 1; // spring constant

		double omega0 = Math.sqrt(k / m); // Natural frequency

		double omega = Math.sqrt(omega0 * omega0 - b * b / (4 * m * m));

		// Initial conditions
		double[] y0 = { 1.0, 0.0 }; // Initial displacement and velocity
		double t0 = 0.0; // Initial time
		double t1 = 10.0; // Final time
		double h = 0.001; // Initial step size

		// Create an instance of the ODE representing the damped harmonic oscillator
		ODE dampedOscillator = (double t, double[] y) -> new double[] { y[1], -omega0 * omega0 * y[0] - (b / m) * y[1] // Derivatives
																														// of
																														// the
																														// state
																														// vector
																														// (displacement
																														// and
																														// velocity)
		};

		double B = Math.sqrt(1 / 3.);

		double tolerance = 1e-7; // Desired accuracy default
		double minH = 1e-10; // Minimum step size to prevent infinite loop default

		// Create a step listener to compare with the exact solution
		ODEStepListener listener = (newT, newY) -> {
			// Exact solution of the damped harmonic oscillator (for specific cases)
			double exact = Math.exp(-(b / (2 * m)) * newT) * (B * Math.sin(omega * newT) + Math.cos(omega * newT));
			double diff = Math.abs(exact - newY[0]);
			System.out.printf("Time: %.2f, Approx: %.8f, Exact: %.8f    Diff: %.8f%n", newT, newY[0], exact, diff);
			return true; // Continue the integration
		};

		// Solve the ODE
		System.out.println("\n\nDormand-Prince:");
		DormandPrince.solve(dampedOscillator, y0, t0, t1, h, tolerance, minH, Double.POSITIVE_INFINITY, listener);

		System.out.println("\n\nFehlberg:");
		Fehlberg.solve(dampedOscillator, y0, t0, t1, h, tolerance, minH, Double.POSITIVE_INFINITY, listener);

		System.out.println("\n\nCash-Karp:");
		CashKarp.solve(dampedOscillator, y0, t0, t1, h, tolerance, minH, Double.POSITIVE_INFINITY, listener);
	}
}
