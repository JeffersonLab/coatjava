package cnuphys.swim;

import cnuphys.magfield.FieldProbe;
import cnuphys.rk4.IDerivative;

public class DefaultDerivative implements IDerivative {

        // ddvcs parameters
        //geometry
        private static double[] THETA_SHIELD = {6.64, 41.3}; // min,max polar angle (deg)
        private static double[] THETA_ECAL = {6.64, 30};     // min,max polar angle (deg)
        private static double[] RHO_SHIELD = {60.0, 140};    // min,max distance from (0,0,0) at theta=25 deg
        private static double[] RHO_ECAL = {60.0, 80.0};     // min,max distance from (0,0,0) at theta=25 deg
        private static double TAN25 = Math.tan(Math.toRadians(25));
        private static double COS25 = Math.cos(Math.toRadians(25));
        // eloss
        private static double EMASS = 0.511E-3;
        private static double MUMASS = 105.66E-3;
        private static double K = 0.000307075; //  GeV mol-1 cm2
        // materials
        private static double[] IeV = {823E-9, 600.7E-9}; // GeV
        private static double[] DENSITY = {11.35, 8.3};   // g/cm3
        private static double[] ZOVERA = {0.39573, 0.41315};
        
	private FieldProbe _probe;
        
        private int _charge;

	private double _momentum;  //GeV/c

	// alpha is qe/p where q is the integer charge,
	// e is the electron charge = (10^-9) in GeV/(T*m)
	// p is in GeV/c
	private double _alpha;
        
        private double _totalEnergyLoss = 0;
        private double _energyLoss = 0;
	
	//for mag field result
	float b[] = new float[3];

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
	public DefaultDerivative(int charge, double momentum, FieldProbe field) {
		_probe = field;
                _charge = charge;
		_momentum = momentum;
//units of this  alpha are 1/(T*m)
		_alpha = 1.0e-9 * charge * Swimmer.C / _momentum;
	}
	
	public void set(int charge, double momentum, FieldProbe field) {
		_probe = field;
                _charge = charge;
		_momentum = momentum;
//units of this  alpha are 1/(T*m)
		_alpha = 1.0e-9 * charge * Swimmer.C / _momentum;		
	}

	/**
	 * Compute the derivatives given the value of s (path length) and the values
	 * of the state vector.
	 * 
	 * @param s    the value of the independent variable path length (input).
	 * @param u    the values of the state vector ([x,y,z, tx = px/p, ty = py/p, tz = pz/p]) at s
	 *             (input).
	 * @param du will be filled with the values of the derivatives at s (output).
	 */
	@Override
	public void derivative(double s, double[] u, double[] du) {
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
			// convert to tesla
			Bx = b[0] / 10.0;
			By = b[1] / 10.0;
			Bz = b[2] / 10.0;
		}

		du[3] = _alpha * (u[4] * Bz - u[5] * By); // vyBz-vzBy
		du[4] = _alpha * (u[5] * Bx - u[3] * Bz); // vzBx-vxBz
		du[5] = _alpha * (u[3] * By - u[4] * Bx); // vxBy-vyBx
		du[0] = u[3];
		du[1] = u[4];
		du[2] = u[5];
	}
                
        /**
	 * Calculate energy loss, update momentum and alpha for each step, and accumulate energy loss into totalEnergyLoss
	 * 
	 * @param u    state vector ([x,y,z, tx = px/p, ty = py/p, tz = pz/p]).
	 * @param dx   path length for the step; (= step size if step size is small enough)
	 */
        @Override
        public void energyLossUpdate(double[] u, double dx){
            _energyLoss = getEloss(_momentum, u[0], u[1], u[2], dx); //todo: energy loss function
            double energyOrigin = Math.sqrt(_momentum*_momentum + MUMASS*MUMASS);
            double energyFinal = energyOrigin + _energyLoss;
            _momentum =  Math.sqrt(energyFinal*energyFinal - MUMASS*MUMASS);
            _alpha = 1.0e-9 * _charge * Swimmer.C / _momentum;
            _totalEnergyLoss += _energyLoss;
        }
        
        
        /**
         * Get total energy loss          
         * @return total energy loss
         */
        public double getTotalEnergyLoss(){
            return _totalEnergyLoss;
        }
                
        /**
         * Calculate energy loss
         * @param p momentum in GeV
         * @param x position in meters
         * @param y position in meters
         * @param z position in meters
         * @param dx step pathlength in meters
         * @return energy loss
         */
        public double getEloss(double p, double x, double y, double z, double dx) {
           
            double dz = Math.sqrt(x*x+y*y)*TAN25;
            double r  = (z+dz)*COS25;
            double r_cm = r * 100; // convert to cm
            double theta = Math.toDegrees(Math.acos(z/Math.sqrt(x*x+y*y+z*z))); // degree
            
            if(r_cm<RHO_SHIELD[0] || r_cm>RHO_SHIELD[1] || theta<THETA_SHIELD[0] || theta>THETA_SHIELD[1])
                    return 0;
          
            int imat = 0;
            if(r_cm<RHO_ECAL[1] && theta<THETA_ECAL[1]){
                    imat = 1;
            }
            
            double beta = p / Math.sqrt(p * p + MUMASS * MUMASS);
            double s = EMASS / MUMASS;
            double gamma = 1. / Math.sqrt(1 - beta * beta);
            double Wmax = 2. * EMASS * beta * beta * gamma * gamma
                    / (1. + 2. * s * gamma + s * s);
            double I = this.IeV[imat];
            double logterm = 2. * EMASS * beta * beta * gamma * gamma * Wmax / (I * I);
            double dE = dx * 100 * DENSITY[imat] * K * ZOVERA[imat]
                    * (0.5 * Math.log(logterm) - beta * beta) / beta / beta; //in GeV
            return dE;
        }
}
