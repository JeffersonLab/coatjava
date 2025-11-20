package org.jlab.rec.ahdc.KalmanFilter;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.geom.prim.Line3D;

public class Hit_beam extends Hit {

	double x,y,z;
	double r,phi;

    // A lot of argument are useless
    // FOr clarity, we should remove them
	//public Hit_beam(double x, double y , double z) {}
	public Hit_beam(int superLayer, int layer, int wire, int numWire, double doca, double x, double y , double z) {
	    super(0, 0, 0, 0, new Line3D(x,y,0,x,y,1), 0); // just a line parallel to the beam axis, defined by two end points
		this.x = x;
		this.y = y;
		this.z = z;
		this.r = Math.hypot(x,y);
		this.phi = Math.atan2(y,x);
	}

	public RealVector get_Vector_beam() {
		return new ArrayRealVector(new double[] {this.r, this.phi, this.z});
	}
}
