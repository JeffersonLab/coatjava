package cnuphys.CLAS12Swim;

import cnuphys.magfield.FieldProbe;

/**
 * This is the ODE for swimming particles through a magnetic field
 */

public class CLAS12SwimODE implements ODE {

	// the magnetic field probe
	protected FieldProbe _probe;

	protected double _momentum; // GeV/c

	// alpha is qe/p where q is the integer charge,
	// e is the electron charge = (10^-9) in GeV/(T*m)
	// p is in GeV/c
	protected double _alpha;

	/**
	 * The derivative for swimming through a magnetic field
	 *
	 * @param q     -1 for electron, +1 for proton, etc.
	 * @param p     the magnitude of the momentum.
	 * @param field the magnetic field
	 */
	public CLAS12SwimODE(int q, double p, FieldProbe field) {
		_probe = field;
		_momentum = p;
//units of this  alpha are 1/(kG*cm)
		_alpha = 1.0e-14 * q * CLAS12Swimmer.C / _momentum;
	}

	/**
	 * Compute the derivatives given the value of s (path length) and the values of
	 * the state vector.
	 *
	 * @param s the value of the independent variable path length (input).
	 * @param u the values of the state vector ([x,y,z, tx = px/p, ty = py/p, tz =
	 *          pz/p]) at s (input).
	 * @return the values of the derivatives at s (output).
	 */
	@Override
	public double[] getDerivatives(double s, double[] u) {

		double Bx = 0.0;
		double By = 0.0;
		double Bz = 0.0;

		if (_probe != null) {

			float b[] = new float[3];
			_probe.field((float) u[0], (float) u[1], (float) u[2], b);

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