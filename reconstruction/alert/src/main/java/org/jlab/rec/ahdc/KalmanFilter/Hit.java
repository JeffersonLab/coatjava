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

	private final int    superLayer;
	private final int    layer;
	private final int    wire;
	private final double r;
	private final double phi;
	private final double doca;
	private double adc;
	private final double numWires;
	private final Line3D line3D;
	private final Line3D line3D_plus;
	private final Line3D line3D_minus;
	private int    hitidx;
	private int    hitsign;

	// Comparison with:  common-tools/clas-geometry/src/main/java/org/jlab/geom/detector/alert/AHDC/AlertDCFactory.java
	// here, SuperLayer, Layer, Wire, start from 1
	// in AlertDCFactory, same variables start from 1
	public Hit(int superLayer, int layer, int wire, int numWire, Line3D line, double doca) {
		this.superLayer = superLayer;
		this.layer      = layer;
		this.wire       = wire;
		this.r          = line.end().distance(0, 0, line.end().z());
		this.doca       = doca;
		this.numWires = numWire;
		this.adc = 0;//placeholder
		this.hitidx = -1;
		this.hitsign = 0;
		
		this.phi = line.midpoint().vectorFrom(0,0,0).phi();
		//System.out.println(" superlayer " + this.superLayer + " layer " + this.layer + " wire " + this.wire + " wx " + wx + " wy " + wy + " wx_end " + wx_end + " wy_end " + wy_end + " phi " + this.phi);
		
		this.line3D = line;

		//calculate the "virtual" left and right wires accounting for the DOCA 
		double deltaphi = Math.asin(this.doca/r);
		this.line3D_plus = new Line3D();
                this.line3D_plus.copy(line);
                this.line3D_plus.rotateZ(deltaphi);
                
		this.line3D_minus = new Line3D();
                this.line3D_minus.copy(line);
                this.line3D_minus.rotateZ(deltaphi);
		
	}

        //hit measurement vector in 1 dimension: minimize distance - doca
        public RealVector get_Vector() {
		return new ArrayRealVector(new double[]{this.doca});
	}

        //hit measurement vector in 1 dimension with sign: if sign = 0, return doca, otherwise return 0
        public RealVector get_Vector(int sign, boolean goodsign) {
		if(sign == 0 || goodsign){
			return new ArrayRealVector(new double[]{this.doca});
		}else{
			return new ArrayRealVector(new double[]{0.0});
		}
	}

    	public RealMatrix get_MeasurementNoise() {
		return new Array2DRowRealMatrix(new double[][]{{0.0225}});
	}
    
    	public RealMatrix get_MeasurementNoise(boolean goodsign) {
	    if(goodsign){
		return new Array2DRowRealMatrix(new double[][]{{0.0225}});
	    }else{
		return new Array2DRowRealMatrix(new double[][]{{2*this.doca*this.doca}});
	    }
	}
    
	public double doca() {
		return doca;
	}

	public double r()    {return r;}

        public double phi()    {return phi;}//at z = 0;
    
        public double phi(double z)    {
	    return this.line3D.lerpPoint((z-line3D.origin().z())/line3D.length()).vectorFrom(0,0,0).phi();
	}

	public Line3D line() {return line3D;}

	public double distance(Point3D point3D) {
		return this.line3D.distance(point3D).length();
	}

	public double distance(Point3D point3D, int sign, boolean goodsign) {
		//if(sign!=0)
	    	//System.out.println(" r " + this.r +  " phi " + this.phi + " doca " + this.doca + " sign " + sign + " distance " + this.line3D.distance(point3D).length() + " (sign 0) " + this.line3D_plus.distance(point3D).length() + " (sign+) " + this.line3D_minus.distance(point3D).length() + " (sign-) ");
		if(!goodsign){	    
			if(sign>0)return this.line3D_plus.distance(point3D).length();
			if(sign<0)return this.line3D_minus.distance(point3D).length();
		}
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

	public void setADC(double _adc) {
		this.adc = _adc;
	}

	public Line3D getLine3D() {
		return line3D;
	}

	public double getNumWires() {
		return numWires;
	}

	public int getHitIdx() {
		return hitidx;
	}

	public void setHitIdx(int idx) {
		this.hitidx = idx;
	}

	public int getSign() {
		return hitsign;
	}

	public void setSign(int sign) {
		this.hitsign = sign;
	}
    
}

