package org.jlab.rec.ahdc.KalmanFilter;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;

/**
 * Implement a hit for which the state vector is a 3x1 matrix (r, phi, z)
 * e.g beamline, ATOF wedges/bars
 * 
 * @author Mathieu Ouillon
 * @author Éric Fuchey 
 * @author Felix Touchte Codjo
 */
public class RadialKFHit implements KFHit {

	private double r,phi,z;
    Line3D line;
	RealMatrix measurementNoise = null;

	public RadialKFHit(double x, double y , double z) {
		this.z = z;
		this.r = Math.hypot(x,y);
		this.phi = Math.atan2(y,x);
        line = new Line3D(x,y,0,x,y,1); // a line parallel to the beam axis
	}

	@Override
	public RealVector getMeasurementVector() {
		return new ArrayRealVector(new double[] {this.r, this.phi, this.z});
	}

	@Override
	public RealMatrix getMeasurementNoiseMatrix() {
		return measurementNoise;
	}

	public void setMeasurementNoise(RealMatrix measurementNoise) {
		this.measurementNoise= measurementNoise;
	}

	@Override
	public double distance(Point3D point3D) {
		return this.line.distance(point3D).length();
	}

	@Override
	public double getRadius() {
		return r;
	}
	
	// Projection function
	@Override
	public RealVector getProjectionFunction(RealVector x) {

		double xx = x.getEntry(0);
		double yy = x.getEntry(1);
		double zz = x.getEntry(2);

		double r = Math.hypot(xx, yy);
		double phi = Math.atan2(yy, xx);

		return MatrixUtils.createRealVector(new double[]{r, phi, zz});
	}

	// Jacobian matrix of the measurement for the beamline with respect to (x, y, z, px, py, pz)
	@Override
	public RealMatrix getProjectionMatrix(RealVector x) {

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
	
}
