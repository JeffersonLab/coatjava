package org.jlab.rec.ahdc.KalmanFilter;

import org.apache.commons.math3.linear.ArrayRealVector;
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
    Line3D beamline;
	RealMatrix measurementNoise = null;

	public RadialKFHit(double x, double y , double z) {
		this.z = z;
		this.r = Math.hypot(x,y);
		this.phi = Math.atan2(y,x);
        beamline = new Line3D(x,y,0,x,y,1); // a line parallel to the beam axis
	}

	@Override
	public RealVector get_Vector() {
		return new ArrayRealVector(new double[] {this.r, this.phi, this.z});
	}

	@Override
	public RealMatrix get_MeasurementNoise() {
		return measurementNoise;
	}

	public void set_MeasurementNoise(RealMatrix measurementNoise) {
		this.measurementNoise= measurementNoise;
	}
	
	@Override
	public Line3D getLine() {
		return beamline;
    }

	@Override
	public double distance(Point3D point3D) {
		return this.beamline.distance(point3D).length();
	}

	@Override
	public double getRadius() {
		return r;
	}
}
