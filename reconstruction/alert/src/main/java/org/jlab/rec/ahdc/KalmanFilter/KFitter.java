package org.jlab.rec.ahdc.KalmanFilter;

import java.util.HashMap;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.clas.pdg.PhysicsConstants;
import org.jlab.clas.tracking.kalmanfilter.Material;
import org.jlab.geom.prim.Point3D;
import org.jlab.rec.ahdc.Hit.Hit;

/**
 * Implement the prediction and the correction stages of the Kalman Filter
 * 
 * @author Mathieu Ouillon
 * @author Éric Fuchey 
 * @author Felix Touchte Codjo
 */
public class KFitter {

	private       RealVector stateEstimation;
    private       RealMatrix errorCovariance;
	private       Stepper    stepper;
	private final Propagator propagator;
	private final HashMap<String, Material> materialHashMap;
	public        double     chi2 = 0;
    // masses/energies in MeV
	private final double     electron_mass_c2 = PhysicsConstants.massElectron() * 1000;
	private final double     proton_mass_c2   = PhysicsConstants.massProton() * 1000;

	public KFitter(final RealVector initialStateEstimate, final RealMatrix initialErrorCovariance, final Propagator propagator, final HashMap<String, Material> materialHashMap) {
		this.stateEstimation = initialStateEstimate.copy();
		this.errorCovariance = initialErrorCovariance.copy();
		this.stepper         = new Stepper(initialStateEstimate.toArray());
		this.propagator      = propagator;
		this.materialHashMap = materialHashMap;
	}

	public void predict(KFHit hit, boolean direction) throws Exception {
		// Initialization
		stepper.initialize(direction);
		Stepper stepper1 = new Stepper(stepper.y);
		stepper1.initialize(direction);

		// project the state estimation ahead (a priori state) : xHat(k)- = f(xHat(k-1))
		stateEstimation = propagator.f(stepper, hit, materialHashMap);

		// project the covariance matrix ahead
		RealMatrix transitionMatrix  = F(hit, stepper1);
		RealMatrix transitionMatrixT = transitionMatrix.transpose();

		double px            = Math.abs(stepper.y[3]);
		double py            = Math.abs(stepper.y[4]);
		double pz            = Math.abs(stepper.y[5]);
		double p             = Math.sqrt(px * px + py * py + pz * pz);
		double mass          = proton_mass_c2;
		double kineticEnergy = Math.sqrt(mass * mass + p * p) - mass;

		double ratio = electron_mass_c2 / mass;
		double tau   = kineticEnergy / mass;
		double tmax  = 2.0 * electron_mass_c2 * tau * (tau + 2.) / (1. + 2.0 * (tau + 1.) * ratio + ratio * ratio);

		double gam   = tau + 1.0;
		double bg2   = tau * (tau + 2.0);
		double beta2 = bg2 / (gam * gam);

		double s  = stepper.s;
		double E  = Math.sqrt(p * p + mass * mass);
		double dE = Math.abs(stepper.dEdx);

		double K           = 0.000307075;
		double sigma2_dE   = stepper.material.getDensity() * K * stepper.material.getZoverA() / beta2 * tmax * s / 10 * (1.0 - beta2 / 2) * 1000 * 1000;//in MeV^2
		double dp_prim_ddE = (E + dE) / Math.sqrt((E + dE) * (E + dE) - mass * mass);
		double sigma2_px   = Math.pow(px / p, 2) * Math.pow(dp_prim_ddE, 2) * sigma2_dE;
		double sigma2_py   = Math.pow(py / p, 2) * Math.pow(dp_prim_ddE, 2) * sigma2_dE;
		double sigma2_pz   = Math.pow(pz / p, 2) * Math.pow(dp_prim_ddE, 2) * sigma2_dE;

		double std = 1;
		RealMatrix processNoise = MatrixUtils.createRealMatrix(new double[][]{
				{0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, std * sigma2_px, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, std * sigma2_py, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, std * sigma2_pz}});

		// project the error covariance ahead P(k)- = F * P(k-1) * F' + Q
		errorCovariance = (transitionMatrix.multiply(errorCovariance.multiply(transitionMatrixT))).add(processNoise);
	}

	public void correct(KFHit hit) {
        RealVector z = hit.MeasurementVector();
		RealVector h = hit.ProjectionFunction(stateEstimation);

		RealMatrix measurementNoise = hit.MeasurementNoiseMatrix();
		RealMatrix measurementMatrix = hit.ProjectionMatrix(stateEstimation);
		RealMatrix measurementMatrixT = measurementMatrix.transpose();

		// S = H * P(k) * H' + R
		RealMatrix S = measurementMatrix.multiply(errorCovariance).multiply(measurementMatrixT).add(measurementNoise);

		// Inn = z(k) - h(xHat(k)-)
		RealVector innovation = z.subtract(h);

		double chi2inc = innovation.dotProduct(MatrixUtils.inverse(S).operate(innovation));
		chi2 += chi2inc;

		RealMatrix kalmanGain = errorCovariance.multiply(measurementMatrixT).multiply(MatrixUtils.inverse(S));

		// update estimate with measurement z(k) xHat(k) = xHat(k)- + K * Inn
		stateEstimation = stateEstimation.add(kalmanGain.operate(innovation));
		// update covariance of prediction error P(k) = (I - K * H) * P(k)-
		// The Joseph's form is numerically more stable P(k) = (I - K * H) * P(k)- (I - K * H)' - H*R*K'
		RealMatrix identity = MatrixUtils.createRealIdentityMatrix(kalmanGain.getRowDimension());
		// Numerically more stable !!
		RealMatrix tmpMatrix = identity.subtract(kalmanGain.multiply(measurementMatrix));
		errorCovariance = tmpMatrix.multiply(errorCovariance.multiply(tmpMatrix.transpose())).add(kalmanGain.multiply(measurementNoise.multiply(kalmanGain.transpose())));
		
		// Give back to the stepper the new stateEstimation
		stepper.y = stateEstimation.toArray();
	}

	// specific to AHDC hits
	public double residual(Hit hit) {
		double d = hit.distance( new Point3D( stateEstimation.getEntry(0), stateEstimation.getEntry(1), stateEstimation.getEntry(2) ) );
		return hit.getDoca()-d;
	}

    public void ResetErrorCovariance(final RealMatrix initialErrorCovariance){
        this.errorCovariance = initialErrorCovariance;  
    }
    
	private RealMatrix F(KFHit hit, Stepper stepper1) throws Exception {

		double[] dfdx  = subfunctionF(hit, stepper1, 0);
		double[] dfdy  = subfunctionF(hit, stepper1, 1);
		double[] dfdz  = subfunctionF(hit, stepper1, 2);
		double[] dfdpx = subfunctionF(hit, stepper1, 3);
		double[] dfdpy = subfunctionF(hit, stepper1, 4);
		double[] dfdpz = subfunctionF(hit, stepper1, 5);

		return MatrixUtils.createRealMatrix(new double[][]{
				{dfdx[0], dfdy[0], dfdz[0], dfdpx[0], dfdpy[0], dfdpz[0]}, {dfdx[1], dfdy[1], dfdz[1], dfdpx[1], dfdpy[1], dfdpz[1]}, {dfdx[2], dfdy[2], dfdz[2], dfdpx[2], dfdpy[2], dfdpz[2]}, {dfdx[3], dfdy[3], dfdz[3], dfdpx[3], dfdpy[3], dfdpz[3]}, {dfdx[4], dfdy[4], dfdz[4], dfdpx[4], dfdpy[4], dfdpz[4]}, {dfdx[5], dfdy[5], dfdz[5], dfdpx[5], dfdpy[5], dfdpz[5]}});
	}

	double[] subfunctionF(KFHit hit, Stepper stepper1, int i) throws Exception {
		double  h             = 1e-8;// in mm
		Stepper stepper_plus  = new Stepper(stepper1.y);
		Stepper stepper_minus = new Stepper(stepper1.y);

		stepper_plus.initialize(stepper1.direction);
		stepper_minus.initialize(stepper1.direction);

		stepper_plus.y[i]  = stepper_plus.y[i] + h;
		stepper_minus.y[i] = stepper_minus.y[i] - h;

		propagator.f(stepper_plus, hit, materialHashMap);
		propagator.f(stepper_minus, hit, materialHashMap);

		double dxdi  = (stepper_plus.y[0] - stepper_minus.y[0]) / (2 * h);
		double dydi  = (stepper_plus.y[1] - stepper_minus.y[1]) / (2 * h);
		double dzdi  = (stepper_plus.y[2] - stepper_minus.y[2]) / (2 * h);
		double dpxdi = (stepper_plus.y[3] - stepper_minus.y[3]) / (2 * h);
		double dpydi = (stepper_plus.y[4] - stepper_minus.y[4]) / (2 * h);
		double dpzdi = (stepper_plus.y[5] - stepper_minus.y[5]) / (2 * h);

		return new double[]{dxdi, dydi, dzdi, dpxdi, dpydi, dpzdi};
	}


	public RealVector getStateEstimationVector() {
		return stateEstimation.copy();
	}

	public RealMatrix getErrorCovarianceMatrix() {
		return errorCovariance.copy();
	}

	/** Return a copy of the stepper. It is like a snapshot of the propagation. */
	public Stepper getStepper() {return new Stepper(stepper);}

}
