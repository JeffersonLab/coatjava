package cnuphys.swim;

import cnuphys.magfield.FieldProbe;
import cnuphys.rk4.IDerivative;
import java.util.Arrays;

public class DefaultDerivative implements IDerivative {

        // ddvcs parameters
        //geometry
        private static final double[] SHIELD_RMAX = {45.7, 61.4, 74.0, 78.4, 79.8, 79.8, 88.25, 62.79, 15.01};   // polycone rmax (cm)
        private static final double[] SHIELD_RMIN = {302., 40.2, 9.88, 10.52, 10.52, 11.33, 13.48, 15.0, 15.0};  // polycone rmin (cm)
        private static final double[] SHIELD_Z    = {52.0, 69.6, 83.7, 88.8, 88.8, 95.1, 113.23, 125.1, 147.38}; // polycone z (cm)
        private static final double[] ECAL_RMAX   = {30.11, 36.06, 40.1, 9.88}; // polycone rmax (cm)
        private static final double[] ECAL_RMIN   = {30.1, 7.28, 8.15, 9.87};   // polycone rmin (cm)
        private static final double[] ECAL_Z      = {52.0, 62.5, 69.6, 83.6};   // polycone z (cm)
        private static final double[] Z_RANGE     = {SHIELD_Z[0], SHIELD_Z[SHIELD_Z.length-1]};
        private static final double[] RHO_RANGE   = {Arrays.stream(ECAL_RMIN).min().getAsDouble(), 
                                                     Arrays.stream(SHIELD_RMAX).max().getAsDouble()}; 
        // eloss
        private static final double EMASS = 0.511E-3;
        private static final double MUMASS = 105.66E-3;
        private static final double K = 0.000307075; //  GeV mol-1 cm2
        // materials
        private static final double[] IeV = {823E-9, 600.7E-9}; // GeV
        private static final double[] DENSITY = {11.35, 8.3*0.95};   // g/cm3 ^0.95 accounts for crystals/mother-volume filling factor
        private static final double[] ZOVERA = {0.39573, 0.41315};
        
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
           
            double r  = Math.sqrt(x*x+y*y);
            double r_cm = r * 100; // convert to cm
            double z_cm = z * 100; // convert to cm

            if(r_cm<RHO_RANGE[0] || r_cm>RHO_RANGE[1] || z_cm<Z_RANGE[0] || z_cm>Z_RANGE[1])
                    return 0;
          
            int imat = -1;
          
            if(this.isInPolycone(SHIELD_Z ,SHIELD_RMIN, SHIELD_RMAX, z_cm, r_cm)) {
                imat = 0;
            }
            else if(this.isInPolycone(ECAL_Z, ECAL_RMIN, ECAL_RMAX, z_cm, r_cm)) {
                imat = 1;
            }
            else {
                return 0;
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
        
        private boolean isInPolycone(double[] Z, double[] RI, double[] RO, double z, double r){
            int iz = Arrays.binarySearch(Z, z);
            if(iz<0) iz = -iz -1;
            if(iz>0 && iz<Z.length) {
                double ri = RI[iz-1] + (RI[iz]-RI[iz-1])*(z-Z[iz-1])/(Z[iz]-Z[iz-1]);
                double ro = RO[iz-1] + (RO[iz]-RO[iz-1])*(z-Z[iz-1])/(Z[iz]-Z[iz-1]);
                return r>ri && r<ro;
            }
            return false;
          
        }
}
