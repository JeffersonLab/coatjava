package cnuphys.dormandPrince;

import cnuphys.magfield.FastMath;

public class CLAS12Values {
	
	/** The integer charge */
	public int charge;

	/** The coordinate x in cm */
	public double x;

	/** The y coordinate in cm */
	public double y;

	/** The z coordinate of in cm */
	public double z;

	/** The momentum in GeV/c */
	public double p;

	/** The polar angle in degrees */
	public double theta;

	/** The azimuthal angle in degrees */
	public double phi;

	/**
	 * Store the initial conditions of a swim
	 * 
	 * @param charge The integer charge
	 * @param xo     The x coordinate of the vertex in cm
	 * @param yo     The y coordinate of the vertex in cm
	 * @param zo     The z coordinate of the vertex in cm
	 * @param p      The momentum in GeV/c
	 * @param theta  The polar angle in degrees
	 * @param phi    The azimuthal angle in degrees
	 */
	public CLAS12Values(int charge, double xo, double yo, double zo, double p, double theta, double phi) {
		this.charge = charge;
		this.x = xo;
		this.y = yo;
		this.z = zo;
		this.p = p;
		this.theta = theta;
		this.phi = phi;
	}
	
	/**
	 * Get the values from a state vector. The state vector is the vector of
	 * that is the dependent variable in the integration.
	 * @param charge the integer charge. Must be supplied, not part of the state vector. It shouldn't change, but
	 * we assume this is the original momentum, so we mutliply by the state vector norm of the t components, which
	 * should be 1 since we have magnetic field only.
	 * 
	 * @param q the integer charge
	 * @param momentum the (initial) momentum in GeV/c
	 * @param u the state vector
	 */
	public CLAS12Values(int q, double momentum, double[] u) {
		charge = q;
		x = u[0];
		y = u[1];
		z = u[2];
		
		//norm should be 1
		double norm = Math.sqrt(u[3] * u[3] + u[4] * u[4] + u[5] * u[5]);
		double r = Math.sqrt(u[0]*u[0] + u[1]*u[1] + u[2]*u[2]);
		
		p = norm*momentum;
		theta = FastMath.acos2Deg(u[2]/r);
        phi = FastMath.atan2Deg(u[1], u[0]);
    }
	
	/**
	 * Get the values as a state vector used in integration
	 * 
	 * @return the values as a state vector
	 */
	public double[] getU() {
		double uo[] = new double[6];
		
		double thetaRad = Math.toRadians(theta);
		double phiRad = Math.toRadians(phi);
		double sinTheta = Math.sin(thetaRad);

		double tx = sinTheta * Math.cos(phiRad); // px/p
		double ty = sinTheta * Math.sin(phiRad); // py/p
		double tz = Math.cos(thetaRad); // pz/p

		// set uf to the starting state vector
		uo[0] = x;
		uo[1] = y;
		uo[2] = z;
		uo[3] = tx;
		uo[4] = ty;
		uo[5] = tz;
		return uo;
	}


	/**
	 * Copy constructor
	 * 
	 * @param src the source initial values
	 */
	public CLAS12Values(CLAS12Values src) {
		this(src.charge, src.x, src.y, src.z, src.p, src.theta, src.phi);
	}

	@Override
	public String toString() {
		return String.format("Q: %d\n", charge) + String.format("xo: %10.7e cm\n", x)
				+ String.format("yo: %10.7e cm\n", y) + String.format("zo: %10.7e cm\n", z)
				+ String.format("p: %10.7e GeV/c\n", p) + String.format("theta: %10.7f deg\n", theta)
				+ String.format("phi: %10.7f deg", phi);
	}
	
	/**
	 * A raw string for output, just numbers no units
	 * 
	 * @return a raw string for output
	 */
	public String toStringRaw() {
		return String.format("%-7.4f  %-7.4f  %-7.4f %-6.3f %-6.3f %-6.3f", x, y, z, p, theta, phi);
	}


}
