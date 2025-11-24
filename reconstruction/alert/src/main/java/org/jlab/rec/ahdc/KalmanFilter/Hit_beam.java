package org.jlab.rec.ahdc.KalmanFilter;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.rec.ahdc.Hit.Hit;

// A weird object that just want to be considered as a Hit
// but it is not!
// It is due to the notion of Indicator that want to unify wire hits, beamline or target surfaces
public class Hit_beam extends Hit {

	private double x,y,z;
	private double r,phi;
    Line3D beamline;

    // A lot of argument are useless
    // FOr clarity, we should remove them
	//public Hit_beam(double x, double y , double z) {}
	public Hit_beam(double x, double y , double z) {
		super(0,0,0,0, 0, 0, -1); // just a line parallel to the beam axis, defined by two end points
		this.x = x;
		this.y = y;
		this.z = z;
		this.r = Math.hypot(x,y);
		this.phi = Math.atan2(y,x);
        beamline = new Line3D(x,y,0,x,y,1);
	}

	public RealVector get_Vector_beam() {
		return new ArrayRealVector(new double[] {this.r, this.phi, this.z});
	}

	public Line3D getLine() {
		return beamline;
    }

	public double distance(Point3D point3D) {
		return this.beamline.distance(point3D).length();
	}
}
