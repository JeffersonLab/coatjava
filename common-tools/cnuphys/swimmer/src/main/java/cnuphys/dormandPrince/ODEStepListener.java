package cnuphys.dormandPrince;

/**
 * Interface for listening to steps taken by an ODE solver.
 */
public interface ODEStepListener {
    /**
     * Called when a new step is taken in the ODE solving process.
     * 
     * @param oldT The previous independent variable.
     * @param oldY The previous state vector.
     * @param newT The new independent variable after the step.
     * @param newY The new state vector after the step.
     * @return A boolean indicating whether to continue (true) or stop (false) the integration.
     */
    boolean newStep(double oldT, double[] oldY, double newT, double[] newY);
}
