package cnuphys.CLAS12Swim;

/**
 * Result container returned by all {@code CLAS12Swimmer} swim operations.
 * <p>
 * A {@code CLAS12SwimResult} encapsulates:
 * <ul>
 *   <li>the final particle state at termination,</li>
 *   <li>the reason the swim terminated,</li>
 *   <li>the accumulated path length,</li>
 *   <li>optionally, the full trajectory sampled along the path.</li>
 * </ul>
 *
 * <h2>Termination semantics</h2>
 * A swim may terminate because:
 * <ul>
 *   <li>a target surface was reached within the requested accuracy,</li>
 *   <li>a requested path length {@code sMax} was reached,</li>
 *   <li>the distance of closest approach (DOCA) converged,</li>
 *   <li>or an internal failure or limit condition occurred.</li>
 * </ul>
 *
 * The specific termination condition is encoded in {@link #getStatus()},
 * and convenience predicates such as {@link #isSuccess()} are provided.
 *
 * <h2>State representation</h2>
 * The final state is stored internally as:
 * <ul>
 *   <li>position components (x, y, z) in cm,</li>
 *   <li>direction cosines (tx, ty, tz) = (px/p, py/p, pz/p), dimensionless.</li>
 * </ul>
 *
 * <h2>Trajectory storage</h2>
 * Depending on how the swim was configured, the result may contain a sampled
 * trajectory. Trajectories are typically used for visualization or debugging
 * and may be {@code null} if not requested.
 *
 * <p>
 * This class is a passive data container and is not thread-safe for mutation.
 * </p>
 */

public class CLAS12SwimResult {

	private CLAS12Listener _listener;

	public CLAS12SwimResult(CLAS12Listener listener) {
		_listener = listener;
	}

	/**
	 * Get the trajectory
	 *
	 * @return the trajectory
	 */
	public CLAS12Trajectory getTrajectory() {
		return _listener.getTrajectory();
	}

	/**
	 * Get the initial values of the swim
	 *
	 * @return the initial values
	 */
	public CLAS12Values getInitialValues() {
		return _listener.getIvals();
	}

	/**
	 * Get the final values of the swim
	 *
	 * @return the final values
	 */
	public CLAS12Values getFinalValues() {
		double u[] = _listener.getU();
		int q = _listener.getIvals().q;
		double p = _listener.getIvals().p;
		return new CLAS12Values(q, p, u);
	}

	/**
	 * Get the path length in cm
	 *
	 * @return the path length in cm
	 */
	public double getPathLength() {
		return _listener.getS();
	}

	/**
	 * Get the termination status code for the swim.
	 * <p>
	 * The value is one of the {@link CLAS12Swimmer} status constants, e.g.
	 * {@link CLAS12Swimmer#SWIM_SUCCESS}, {@link CLAS12Swimmer#SWIM_TARGET_MISSED},
	 * or {@link CLAS12Swimmer#BELOW_MIN_MOMENTUM}.
	 * </p>
	 *
	 * @return integer status code indicating how the swim terminated
	 */
	public int getStatus() {
		return _listener.getStatus();
	}
	
	/**
	 * Get a copy of the final state vector.
	 * <p>
	 * The state vector {@code u} has length 6 and is interpreted as:
	 * <ul>
	 *   <li>{@code u[0..2]} = position (x, y, z) in cm</li>
	 *   <li>{@code u[3..5]} = direction cosines (tx, ty, tz), dimensionless</li>
	 * </ul>
	 * </p>
	 * <p>
	 * The returned array is a defensive copy and may be modified by the caller.
	 * </p>
	 *
	 * @return a copy of the final state vector
	 */
	public double[] getFinalU() {
		double[] u = _listener.getU();
		return (u == null) ? null : u.clone();
	}

	/**
	 * Determine whether the swim terminated successfully.
	 * <p>
	 * A successful termination indicates that the swimmer reached the requested target condition
	 * (such as a surface, target {@code z}, target {@code ρ}, distance of closest approach,
	 * or maximum path length) within the specified accuracy, and without encountering an internal
	 * failure condition.
	 * </p>
	 * <p>
	 * If this method returns {@code false}, the final state stored in this result still represents
	 * the particle state at the point where the swim terminated (for example, due to exceeding
	 * {@code sMax} or failing to converge).
	 * </p>
	 *
	 * @return {@code true} if the swim terminated successfully; {@code false} otherwise
	 */
	public boolean isSuccess() {
		return getStatus() == CLAS12Swimmer.SWIM_SUCCESS;
	}

	/**
	 * Get the final rho in cm
	 *
	 * @return the final rho in cm
	 */
	public double getFinalRho() {
		return Math.hypot(_listener.getU()[0], _listener.getU()[1]);
	}

	/**
	 * Get the status of the swim as a string
	 *
	 * @return the status of the swim as a string
	 */
	public String statusString() {
		int status = getStatus();
		String s = CLAS12Swimmer.resultNames.get(status);
		if (s == null) {
			s = "Unknown (" + status + ")";
		}
		return s;
	}

	/**
	 * Get the number of integration steps
	 *
	 * @return the number of integration steps
	 */
	public int getNStep() {
		return _listener.getNumStep();
	}

	/**
	 * Get a summary of the results of the swim
	 */
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer(2000);
		CLAS12Values ivalues = getInitialValues();
		CLAS12Values fvalues = getFinalValues();

		double norm = ivalues.p / fvalues.p; // should be 1.0

		sb.append("Swim results:\n");
		sb.append("Status: " + statusString() + "\n");
		sb.append("Initial values:\n");
		sb.append("charge = " + ivalues.q + "\n");
		sb.append(String.format("vertex = (%.4f, %.4f, %.4f) cm\n", ivalues.x, ivalues.y, ivalues.z));

		sb.append(String.format("momentum = %.4f GeV/c\n", ivalues.p));
		sb.append(String.format("theta = %.4f deg\n", ivalues.theta));
		sb.append(String.format("phi = %.4f deg\n", ivalues.phi));
		sb.append("--------\nFinal values:\n");
		sb.append(String.format("location = (%.4f, %.4f, %.4f) cm\n", fvalues.x, fvalues.y, fvalues.z));
		sb.append(String.format("momentum = %.4f GeV/c\n", fvalues.p));
		sb.append(String.format("norm = %.4f (should be 1)\n", norm));
		sb.append(String.format("theta = %.4f deg\n", fvalues.theta));
		sb.append(String.format("phi = %.4f deg\n", fvalues.phi));
		sb.append(String.format("rho = %.4f cm\n", Math.hypot(fvalues.x, fvalues.y)));
		sb.append(String.format("path length = %.4f cm\n", getPathLength()));
		sb.append(String.format("number of steps = %d\n", getNStep()));

		return sb.toString();
	}

}
