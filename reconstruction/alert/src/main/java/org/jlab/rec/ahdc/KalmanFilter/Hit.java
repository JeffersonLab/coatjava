package org.jlab.rec.ahdc.KalmanFilter;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Plane3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;

public class Hit implements Comparable<Hit> {

	private final double thster = Math.toRadians(20.0);
        private final double zl     = 300.0;//OK
	private final int    superLayer;
	private final int    layer;
	private final int    wire;
	private final double r;
	private final double phi;
	private final double doca;
	private final double adc;
	private final double numWires;
	private final Line3D line3D;

        // Comparison with:  common-tools/clas-geometry/src/main/java/org/jlab/geom/detector/alert/AHDC/AlertDCFactory.java
        // here, SuperLayer, Layer, Wire, start from 1
        // in AlertDCFactory, same variables start from 1
	public Hit(int superLayer, int layer, int wire, int numWire, double r, double doca) {
		this.superLayer = superLayer;
		this.layer      = layer;
		this.wire       = wire;
		this.r          = r;
		this.doca       = doca;
		this.numWires = numWire;
		this.adc = 0;//placeholder
		
		final double DR_layer = 4.0;//OK
		final double round    = 360.0;//OK
		final double thster   = Math.toRadians(20.0);//OK

		double numWires = 32.0;
		double R_layer  = 47.0;

		double zoff1 = -zl/2;//OK
		double zoff2 = +zl/2;//OK
		Point3D  p1 = new Point3D(R_layer, 0, zoff1);
		Vector3D n1 = new Vector3D(0, 0, 1);
		//n1.rotateY(-thopen);
		//n1.rotateZ(thtilt);
		Plane3D lPlane = new Plane3D(p1, n1);//OK

		Point3D  p2 = new Point3D(R_layer, 0, zoff2);
		Vector3D n2 = new Vector3D(0, 0, 1);
		//n2.rotateY(thopen);
		//n2.rotateZ(thtilt);
		Plane3D rPlane = new Plane3D(p2, n2);//OK

		switch (this.superLayer) {//OK
			case 1:
				numWires = 47.0;
				R_layer = 32.0;
				break;
			case 2:
				numWires = 56.0;
				R_layer = 38.0;
				break;
			case 3:
				numWires = 72.0;
				R_layer = 48.0;
				break;
			case 4:
				numWires = 87.0;
				R_layer = 58.0;
				break;
			case 5:
				numWires = 99.0;
				R_layer = 68.0;
				break;
		}

		
		R_layer = R_layer + DR_layer * (this.layer-1);//OK
		double alphaW_layer = Math.toRadians(round / (numWires));//OK
		double wx           = -R_layer * Math.sin(alphaW_layer * (this.wire-1));//OK
		double wy           = -R_layer * Math.cos(alphaW_layer * (this.wire-1));//OK

		double wx_end = -R_layer * Math.sin(alphaW_layer * (this.wire-1) + thster * (Math.pow(-1, this.superLayer-1)));//OK
		double wy_end = -R_layer * Math.cos(alphaW_layer * (this.wire-1) + thster * (Math.pow(-1, this.superLayer-1)));//OK

		this.phi = Math.atan2( (wy+wy_end)*0.5, (wx+wx_end)*0.5 );
		//System.out.println(" superlayer " + this.superLayer + " layer " + this.layer + " wire " + this.wire + " wx " + wx + " wy " + wy + " wx_end " + wx_end + " wy_end " + wy_end + " phi " + this.phi);
		
		Line3D line = new Line3D(wx, wy, -zl/2, wx_end, wy_end, zl/2);
		Point3D lPoint = new Point3D();
		Point3D rPoint = new Point3D();
		lPlane.intersection(line, lPoint);
		rPlane.intersection(line, rPoint);
		//lPoint.setZ(-zl/2);
		//rPoint.setZ(zl/2);
		//lPoint.show();
		//rPoint.show();
		// All wire go from left to right
		Line3D wireLine = new Line3D(lPoint, rPoint);
		//wireLine.show();
		this.line3D = wireLine;
	}
    
    //hit measurement vector in cylindrical coordinates: r, phi, z
        public RealVector get_Vector() {
	    // final double costhster = Math.cos(thster);
	    // final double sinthster = Math.cos(thster);
	    RealVector wire_meas = new ArrayRealVector(new double[]{this.r(), this.phi(), 0});
	    // Array2DRowRealMatrix stereo_rotation = new Array2DRowRealMatrix(new double[][]{{1, 0.0, 0.0}, {0, costhster, -sinthster}, {0, sinthster, costhster}});//rotation of wire: needed?
	    return wire_meas;//.multiply(stereo_rotation);
	}

    //hit measurement vector in 1 dimension: minimize distance - doca
        public RealVector get_Vector_simple() {
		return new ArrayRealVector(new double[]{this.doca});
	}

    //hit measurement vector in 1 dimension: minimize distance - doca - adds hit "sign"
        public RealVector get_Vector_sign(int sign) {
 	    // Attempt: multiply doca by sign
		return new ArrayRealVector(new double[]{sign*this.doca});
	}

        public RealMatrix get_MeasurementNoise() {
	    final double costhster = Math.cos(thster);
	    final double sinthster = Math.cos(thster);
	    //dR = 0.1m dphi = pi dz = L/2 
	    Array2DRowRealMatrix wire_noise = new Array2DRowRealMatrix(new double[][]{{0.1, 0.0, 0.0}, {0.0, Math.atan(0.1/this.r), 0.0}, {0.0, 0.0, 150.0/costhster}});//uncertainty matrix in wire coordinates
	    Array2DRowRealMatrix stereo_rotation = new Array2DRowRealMatrix(new double[][]{{1, 0.0, 0.0}, {0, costhster, -sinthster}, {0, sinthster, costhster}});//rotation of wire
	    wire_noise.multiply(stereo_rotation);
	    
	    return wire_noise.multiply(wire_noise);
	    //
	}

    	public RealMatrix get_MeasurementNoise_simple() {
	    return new Array2DRowRealMatrix(new double[][]{{0.01}});
	}
    
	public double doca() {
		return doca;
	}

	public double r()    {return r;}

        public double phi()    {return phi;}//at z = 0;
    
        public double phi(double z)    {
	    // double x_0 = r*sin(phi);
	    // double y_0 = r*cos(phi);
	    double x_z = r*Math.sin( phi + thster * z/(zl*0.5) * (Math.pow(-1, this.superLayer-1)) );
	    double y_z = r*Math.cos( phi + thster * z/(zl*0.5) * (Math.pow(-1, this.superLayer-1)) );
	    return Math.atan2(x_z, y_z);
	}

	public Line3D line() {return line3D;}

	public double distance(Point3D point3D) {
	    //System.out.println("Calculating distance: ");
	    //this.line3D.show();
	    //point3D.show();
	    //System.out.println(" d = " + this.line3D.distance(point3D).length());
		return this.line3D.distance(point3D).length();
	}

	@Override
	public int compareTo(Hit o) {
		System.out.println("r = " + r + " other r = " + o.r);
		return Double.compare(r, o.r);
	}

	@Override
	public String toString() {
		return "Hit{" + "superLayer=" + superLayer + ", layer=" + layer + ", wire=" + wire + ", r=" + r + ", doca=" + doca + '}';
	}

	public RealVector get_Vector_beam() {
		return null;
	}

	public double getThster() {
		return thster;
	}

	public int getSuperLayer() {
		return superLayer;
	}

	public int getLayer() {
		return layer;
	}

	public int getWire() {
		return wire;
	}

	public double getR() {
		return r;
	}

	public double getDoca() {
		return doca;
	}

	public double getADC() {
		return adc;
	}

	public Line3D getLine3D() {
		return line3D;
	}

	public double getNumWires() {
		return numWires;
	}
}

