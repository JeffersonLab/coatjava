package org.jlab.rec.ahdc.KalmanFilter;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.rec.ahdc.Hit.Hit;

/**
 * A weird object that just want to be considered as a Hit
 * The methods that matters are : distance(), getLine(), get_Vector_beam(), getRadius()
 * 
 * @author Mathieu Ouillon
 * @author Éric Fuchey 
 * @author Felix Touchte Codjo
 */
public class Hit_beam extends Hit {

	private double x,y,z;
	private double r,phi;
    Line3D beamline;

	public Hit_beam(double x, double y , double z) {
		super(0,0,0,0, 0, 0, -1); 
		this.x = x;
		this.y = y;
		this.z = z;
		this.r = Math.hypot(x,y);
		this.phi = Math.atan2(y,x);
        beamline = new Line3D(x,y,0,x,y,1); // a line parallel to the beam axis
	}

	@Override
	public RealVector get_Vector_beam() {
		return new ArrayRealVector(new double[] {this.r, this.phi, this.z});
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
