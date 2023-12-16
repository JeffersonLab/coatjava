package cnuphys.dormondPrince;

public class TestDP {

    public static void main(String[] args) {
        // Parameters for the damped harmonic oscillator
        double omega0 = 1.0; // Natural frequency
        double zeta = 0;   // Damping ratio

        // Initial conditions
        double[] y0 = {1.0, 0.0}; // Initial displacement and velocity
        double t0 = 0.0;          // Initial time
        double t1 = 10.0;         // Final time
        double h = 0.01;          // Initial step size

        // Create an instance of the ODE representing the damped harmonic oscillator
        ODE dampedOscillator = (double t, double[] y) -> new double[] {
            y[1], -2 * zeta * omega0 * y[1] - omega0 * omega0 * y[0]
        };

        // Create a step listener to compare with the exact solution
        ODEStepListener listener = (oldT, oldY, newT, newY) -> {
            // Exact solution of the damped harmonic oscillator (for specific cases)
            double exact = Math.exp(-zeta * omega0 * newT) * Math.cos(omega0 * Math.sqrt(1 - zeta * zeta) * newT);
            System.out.printf("Time: %.2f, Approx: %.5f, Exact: %.5f%n", newT, newY[0], exact);
            return true; // Continue the integration
        };

        // Solve the ODE
        DormandPrince.solve(dampedOscillator, y0, t0, t1, h, listener);
    }
}
