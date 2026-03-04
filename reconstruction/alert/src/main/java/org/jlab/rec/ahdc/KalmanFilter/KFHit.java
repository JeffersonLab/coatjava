package org.jlab.rec.ahdc.KalmanFilter;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
/**
 * An interface to unify the hits used the Kalman Filter (e.g AHDC hits, ATOF hits, beamline)
 * 
 * @author Felix Touchte Codjo
 */
public interface KFHit {
	public double distance(Point3D point3D);
	public double getRadius();
	/** Return the measurement encoded in this KFHit */
	public RealVector MeasurementVector();
	/** Return the measurement noise matrix for this this KFHit */
	public RealMatrix MeasurementNoiseMatrix();
	/** Compute the measure for a given state vector */
	public RealVector ProjectionFunction(RealVector x);
	/** Compute the Jacobian matrix of the {@link #ProjectionFunction(RealVector)} with respect of the components of the state vector */
	public RealMatrix ProjectionMatrix(RealVector x);
}
