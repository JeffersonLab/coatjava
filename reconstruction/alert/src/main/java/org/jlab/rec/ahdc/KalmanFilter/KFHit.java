package org.jlab.rec.ahdc.KalmanFilter;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
/**
 * All we need to use the Kalman filter
 */
public interface KFHit {
	public RealVector get_Vector();
	public RealMatrix get_MeasurementNoise();
	//public void set_MeasurementNoise(RealMatrix measurementNoise);
	public Line3D getLine();
	public double distance(Point3D point3D);
	public double getRadius();
}
