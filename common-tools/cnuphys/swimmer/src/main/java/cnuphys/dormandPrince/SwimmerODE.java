package cnuphys.dormandPrince;

import cnuphys.adaptiveSwim.AdaptiveSwimmer;
import cnuphys.magfield.FieldProbe;

/**
 * Thios is the ODE for swimming particles through a magnetic field
 */
 
public class SwimmerODE implements ODE {
	
	private FieldProbe _probe;

	private double _momentum;  //GeV/c

	// alpha is qe/p where q is the integer charge,
	// e is the electron charge = (10^-9) in GeV/(T*m)
	// p is in GeV/c
	private double _alpha;
	
	//for mag field result
	float b[] = new float[3];

	
	// Speed of light in m/s
	public static final double C = 299792458.0; // m/s
	
	/**
	 * The derivative for swimming through a magnetic field
	 * 
	 * @param charge
	 *            -1 for electron, +1 for proton, etc.
	 * @param momentum
	 *            the magnitude of the momentum.
	 * @param field
	 *            the magnetic field
	 */
	public SwimmerODE(int charge, double momentum, FieldProbe field) {
		_probe = field;
		_momentum = momentum;
//units of this  alpha are 1/(kG*m)
		_alpha = 1.0e-10 * charge * AdaptiveSwimmer.C / _momentum;
	}



	/**
	 * Compute the derivatives given the value of s (path length) and the values
	 * of the state vector.
	 * 
	 * @param s    the value of the independent variable path length (input).
	 * @param u    the values of the state vector ([x,y,z, tx = px/p, ty = py/p, tz = pz/p]) at s
	 *             (input).
	 * @return the values of the derivatives at s (output).
	 */
	@Override
	public double[] getDerivatives(double s, double[] u) {
		
		double Bx = 0.0;
		double By = 0.0;
		double Bz = 0.0;

		if (_probe != null) {

			float b[] = new float[3];

			// convert to cm
			double xx = u[0] * 100;
			double yy = u[1] * 100;
			double zz = u[2] * 100;

			_probe.field((float) xx, (float) yy, (float) zz, b);
			
			Bx = b[0];
			By = b[1];
			Bz = b[2];
		}

		double du[] = new double[6];
		du[0] = u[3];
		du[1] = u[4];
		du[2] = u[5];
        du[3] = _alpha * (u[4] * Bz - u[5] * By); // vyBz-vzBy
        du[4] = _alpha * (u[5] * Bx - u[3] * Bz); // vzBx-vxBz
        du[5] = _alpha * (u[3] * By - u[4] * Bx); // vxBy-vyBx
        return du;
	}

}
