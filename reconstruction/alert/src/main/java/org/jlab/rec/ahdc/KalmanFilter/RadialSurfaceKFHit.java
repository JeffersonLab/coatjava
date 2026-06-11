package org.jlab.rec.ahdc.KalmanFilter;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
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
public class RadialSurfaceKFHit implements KFHit {

	private double r;

	public RadialSurfaceKFHit(double r) {
		this.r = r;
	}

	@Override
	public RealVector getMeasurementVector() {
		return new ArrayRealVector(new double[] {this.r});
	}

	@Override
	public RealMatrix getMeasurementNoiseMatrix() {
		return new Array2DRowRealMatrix(new double[][]{{1e-8}});
	}

	@Override
	public double distance(Point3D point3D) {
		return Math.abs(this.r - Math.hypot(point3D.x(), point3D.y()));
	}

	@Override
	public double getRadius() {
		return r;
	}
	
	// Projection function
	@Override
	public RealVector getProjectionVector(RealVector x) {

		double xx = x.getEntry(0);
		double yy = x.getEntry(1);

		double r = Math.hypot(xx, yy);

		return MatrixUtils.createRealVector(new double[]{r});
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

		return MatrixUtils.createRealMatrix(
				new double[][]{
						{drdx, drdy, drdz, drdpx, drdpy, drdpz}
				});
	}

	@Override
	public RealVector getInnovationVector(RealVector x) {
		RealVector measured = getMeasurementVector();
		RealVector predicted = getProjectionVector(x);
		return measured.subtract(predicted);
	}
	
}
