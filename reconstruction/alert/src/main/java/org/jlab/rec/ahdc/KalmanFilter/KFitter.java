package org.jlab.rec.ahdc.KalmanFilter;

import java.util.HashMap;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
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
	public final  Stepper    stepper;
	private final Propagator propagator;
	private final HashMap<String, Material> materialHashMap;
	public        double     chi2 = 0;
    // masses/energies in MeV
	private final double     electron_mass_c2 = PhysicsConstants.massElectron() * 1000;
	private final double     proton_mass_c2   = PhysicsConstants.massProton() * 1000;
	private double[] vertex_resolutions = {0.09,1e10}; // default values // dr^2 and dz^2 in mm^2

	public KFitter(final RealVector initialStateEstimate, final RealMatrix initialErrorCovariance, final Stepper stepper, final Propagator propagator, final HashMap<String, Material> materialHashMap) {
		this.stateEstimation = initialStateEstimate;
		this.errorCovariance = initialErrorCovariance;
		this.stepper         = stepper;
		this.propagator      = propagator;
		this.materialHashMap = materialHashMap;
	}

	public void predict(Hit hit, boolean direction) throws Exception {
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

	public void correct(Hit hit) {
        RealVector z;
		RealMatrix measurementNoise;
		RealMatrix measurementMatrix;
		RealVector h;
		// check if the hit is the beamline
		//if (hit.getRadius() < 1) {
		if (hit instanceof Hit_beam) {
			// the diagonal elements are the squared errors in r, phi, z
			measurementNoise =
				new Array2DRowRealMatrix(
					new double[][]{
						{vertex_resolutions[0], 0.0000, 0.0000},
						{0.00, 1e10, 0.0000},
						{0.00, 0.0000, vertex_resolutions[1]}
					});//3x3
			measurementMatrix  = H_beam(stateEstimation);//6x3
			h = h_beam(stateEstimation);//3x1
			z = hit.get_Vector();//0!
		}
		// check if the hit is an ATOF bar
		else if (hit instanceof Hit_atofBar) {
			// the diagonal elements are the squared errors in r, phi, z
			measurementNoise =
				new Array2DRowRealMatrix(
					new double[][]{
						{9, 0.0000, 0.0000},
						{0.00, 9, 0.0000},
						{0.00, 0.0000, 100}
					});//3x3
			measurementMatrix  = H_beam(stateEstimation);//6x3
			h = h_beam(stateEstimation);//3x1
			z = hit.get_Vector();//0!
		}
		// else, it is an AHDC hits
		else {
            measurementNoise = hit.get_MeasurementNoise();//1x1
            measurementMatrix = H(stateEstimation, hit);//6x1
            h = h(stateEstimation, hit);//1x1
			z = hit.get_Vector();//1x1
		}
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

	public double residual(Hit hit) {
		double d = hit.distance( new Point3D( stateEstimation.getEntry(0), stateEstimation.getEntry(1), stateEstimation.getEntry(2) ) );
		return hit.getDoca()-d;
	}

    public void ResetErrorCovariance(final RealMatrix initialErrorCovariance){
        this.errorCovariance = initialErrorCovariance;  
    }
    
	private RealMatrix F(Hit hit, Stepper stepper1) throws Exception {

		double[] dfdx  = subfunctionF(hit, stepper1, 0);
		double[] dfdy  = subfunctionF(hit, stepper1, 1);
		double[] dfdz  = subfunctionF(hit, stepper1, 2);
		double[] dfdpx = subfunctionF(hit, stepper1, 3);
		double[] dfdpy = subfunctionF(hit, stepper1, 4);
		double[] dfdpz = subfunctionF(hit, stepper1, 5);

		return MatrixUtils.createRealMatrix(new double[][]{
				{dfdx[0], dfdy[0], dfdz[0], dfdpx[0], dfdpy[0], dfdpz[0]}, {dfdx[1], dfdy[1], dfdz[1], dfdpx[1], dfdpy[1], dfdpz[1]}, {dfdx[2], dfdy[2], dfdz[2], dfdpx[2], dfdpy[2], dfdpz[2]}, {dfdx[3], dfdy[3], dfdz[3], dfdpx[3], dfdpy[3], dfdpz[3]}, {dfdx[4], dfdy[4], dfdz[4], dfdpx[4], dfdpy[4], dfdpz[4]}, {dfdx[5], dfdy[5], dfdz[5], dfdpx[5], dfdpy[5], dfdpz[5]}});
	}

	double[] subfunctionF(Hit hit, Stepper stepper1, int i) throws Exception {
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

	// Measurement matrix in 1x1 dimension: minimize distance - doca
	private RealVector h(RealVector x, Hit hit) {
		double d = hit.distance(new Point3D(x.getEntry(0), x.getEntry(1), x.getEntry(2)));
		return MatrixUtils.createRealVector(new double[]{d});
	}

	// Jacobian matrix of the measurement with respect to (x, y, z, px, py, pz)
	private RealMatrix H(RealVector x, Hit hit) {

		double ddocadx  = subfunctionH(x, hit, 0);
		double ddocady  = subfunctionH(x, hit, 1);
		double ddocadz  = subfunctionH(x, hit, 2);
		double ddocadpx = subfunctionH(x, hit, 3);
		double ddocadpy = subfunctionH(x, hit, 4);
		double ddocadpz = subfunctionH(x, hit, 5);
		
		return MatrixUtils.createRealMatrix(new double[][]{
			{ddocadx, ddocady, ddocadz, ddocadpx, ddocadpy, ddocadpz}});
	}

	double subfunctionH(RealVector x, Hit hit, int i) {
		double     h       = 1e-8;// in mm
		RealVector x_plus  = x.copy();
		RealVector x_minus = x.copy();

		x_plus.setEntry(i, x_plus.getEntry(i) + h);
		x_minus.setEntry(i, x_minus.getEntry(i) - h);

		double doca_plus  = h(x_plus, hit).getEntry(0);
		double doca_minus = h(x_minus, hit).getEntry(0);

		return (doca_plus - doca_minus) / (2 * h);
	}
	
	// Measurement matrix for the beamline (Hit_beam) in dimeansion 3x1
	private RealVector h_beam(RealVector x) {

		double xx = x.getEntry(0);
		double yy = x.getEntry(1);
		double zz = x.getEntry(2);

		double r = Math.hypot(xx, yy);
		double phi = Math.atan2(yy, xx);

		return MatrixUtils.createRealVector(new double[]{r, phi, zz});
	}
	
	// Jacobian matrix of the measurement for the beamline with respect to (x, y, z, px, py, pz)
	private RealMatrix H_beam(RealVector x) {

		double xx = x.getEntry(0);
		double yy = x.getEntry(1);

		double drdx = (xx) / (Math.hypot(xx, yy));
		double drdy = (yy) / (Math.hypot(xx, yy));
		double drdz = 0.0;
		double drdpx = 0.0;
		double drdpy = 0.0;
		double drdpz = 0.0;

		double dphidx = -(yy) / (xx * xx + yy * yy);
		double dphidy = (xx) / (xx * xx + yy * yy);
		double dphidz = 0.0;
		double dphidpx = 0.0;
		double dphidpy = 0.0;
		double dphidpz = 0.0;

		double dzdx = 0.0;
		double dzdy = 0.0;
		double dzdz = 1.0;
		double dzdpx = 0.0;
		double dzdpy = 0.0;
		double dzdpz = 0.0;

		return MatrixUtils.createRealMatrix(
				new double[][]{
						{drdx, drdy, drdz, drdpx, drdpy, drdpz},
						{dphidx, dphidy, dphidz, dphidpx, dphidpy, dphidpz},
						{dzdx, dzdy, dzdz, dzdpx, dzdpy, dzdpz}
				});
	}

	public RealVector getStateEstimationVector() {
		return stateEstimation.copy();
	}

	public RealMatrix getErrorCovarianceMatrix() {
		return errorCovariance.copy();
	}
	
	public void setVertexResolution(double[] res) {
		vertex_resolutions[0] = res[0];
		vertex_resolutions[1] = res[1];
	}

}
