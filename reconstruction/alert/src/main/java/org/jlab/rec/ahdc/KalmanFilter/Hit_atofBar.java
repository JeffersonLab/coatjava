package org.jlab.rec.ahdc.KalmanFilter;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.rec.ahdc.Hit.Hit;

/**
 * Same philosophy as Hit_beam
 * A weird object that just wants to be considered as a Hit
 * The methods that matters are : distance(), getLine(), get_Vector_beam(), getRadius()
 * 
 * @author Felix Touchte Codjo
 */
public class Hit_atofBar extends Hit {
    private double x,y,z;
	private double r,phi;
    Line3D barline;

    public Hit_atofBar(double x, double y, double z) {
        super(0,0,0,0, 0, 0, -1); 
        this.x = x;
        this.y = y;
        this.z = z;
        this.r = Math.sqrt(x*x + y*y);
        this.phi = Math.atan2(y, x);
        barline = new Line3D(x,y,0,x,y,1); // a line parallel to the beam axis
    }

	@Override
	public RealVector get_Vector() {
		return new ArrayRealVector(new double[] {this.r, this.phi, this.z});
	}
	
	@Override
	public Line3D getLine() {
		return barline;
    }

	@Override
	public double distance(Point3D point3D) {
		return this.barline.distance(point3D).length();
	}

	@Override
	public double getRadius() {
		return r;
	}
}
