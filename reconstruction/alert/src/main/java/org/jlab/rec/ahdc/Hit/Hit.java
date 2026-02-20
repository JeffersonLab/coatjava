package org.jlab.rec.ahdc.Hit;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.detector.alert.AHDC.AlertDCDetector;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.detector.alert.AHDC.AlertDCFactory;
import org.jlab.rec.ahdc.KalmanFilter.KFHit;

public class Hit implements Comparable<Hit>, KFHit {

	private final int    id;
	private final int    superLayerId;
	private final int    layerId;
	private final int    wireId;
	private final double doca;
	private final double raw_adc;
	private final double time;
	private double tot;
	private double adc;

    private Line3D wireLine;
	private double  phi;
	private double  radius;
	private int     nbOfWires;
	private boolean use = false;
	private double  x;
	private double  y;
	private double  residual;
	private int	trackId;

    //updated constructor with ADC
	public Hit(int _Id, int _Super_layer, int _Layer, int _Wire, double _Doca, double _ADC, double _Time) {
		this.id           = _Id;
		this.superLayerId = _Super_layer;
		this.layerId      = _Layer;
		this.wireId       = _Wire;
		this.doca         = _Doca;
		this.raw_adc          = _ADC;
		this.time 	  = _Time;
		this.residual        = 0.0;
		this.trackId	     = -1; // not defined yet
	}

	public void setWirePosition(AlertDCDetector factory) {
	
		//System.out.println(" superlayer " + this.superLayerId + " layer " + this.layerId + " wire " + this.wireId + " R_layer " + R_layer + " wx " + wx + " wy " + wy);
		wireLine = factory.getSector(1).getSuperlayer(superLayerId).getLayer(layerId).getComponent(wireId).getLine();
		Point3D end = wireLine.end();
        this.nbOfWires = factory.getSector(1).getSuperlayer(superLayerId).getLayer(layerId).getNumComponents();
		this.phi       = end.vectorFrom(0, 0, 0).phi();
		this.radius    = end.distance(0, 0, end.z());
		this.x         = end.x();
		this.y         = end.y();
	}

	@Override
	public String toString() {
		return "Hit{" + "_Super_layer=" + superLayerId + ", _Layer=" + layerId + ", _Wire=" + wireId + ", _Doca=" + doca + ", _Phi=" + phi + '}';
	}

	// Should return
	//  0 if equality
	// +1 if this is bigger than arg0
	// -1 if this is lower than arg0 
	@Override
	public int compareTo(Hit arg0) {
		if (this.superLayerId == arg0.superLayerId && this.layerId == arg0.layerId) { // same layer, so they have the same nbOfWires
			if (this.wireId == 1 && arg0.wireId == this.nbOfWires) {
				return 1;
			} 
			else if (this.wireId == this.nbOfWires && arg0.wireId == 1) {
				return -1;
			}
			else {
				return Integer.compare(this.wireId, arg0.wireId);
			}
		}
		else {
			int this_value = 10*this.superLayerId + this.layerId;
			int value = 10*arg0.superLayerId + arg0.layerId;
			return Integer.compare(this_value, value);
		}
	}

	public int getId() {
		return id;
	}
        
	public int getSuperLayerId() {
		return superLayerId;
	}

	public int getLayerId() {
		return layerId;
	}

	public int getWireId() {
		return wireId;
	}

	public double getDoca() {
		return doca;
	}

	@Override
    public Line3D getLine() {
        return wireLine;
    }

	@Override
    public double getRadius() {
        return radius;
    }

	public int getNbOfWires() {
		return nbOfWires;
	}

	public boolean is_NoUsed() {
		return !use;
	}

	public void setUse(boolean use) {
		this.use = use;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getPhi() {return phi;}

	public double getADC() {return adc;}

	public double getResidual() {
		return residual;
	}

	public void setResidual(double resid) {
		this.residual = resid;
	}

	public void setToT(double _tot) {
		this.tot = _tot;
	}

	public double getToT() {
		return tot;
	}

	public void setADC(double _adc) {
		this.adc = _adc;
	}

	public double getRawADC() {
		return raw_adc;
	}

	public double getTime() {
		return time;
	}

	public int getTrackId() {
		return trackId;
	}

	public void setTrackId(int _trackId) {
		this.trackId = _trackId;
	}

	@Override
	public RealVector MeasurementVector() {
		return new ArrayRealVector(new double[]{this.doca});
	}

	@Override
    public RealMatrix MeasurementNoiseMatrix() {
		double mean_error = 0.471; // mm (no difference between adc and time)
		double error_on_adc = (1.15146*raw_adc + 437.63)/(3.21187*raw_adc + 878.855); // mm
		double error_on_time = (0.4423*time + 13.7215)/(0.846038*time + 31.9867); // mm
		double error = error_on_adc*error_on_time/mean_error; // mm
		
		return new Array2DRowRealMatrix(new double[][]{{Math.pow(error, 2)}}); // mm^2
		//return new Array2DRowRealMatrix(new double[][]{{0.09}});
	}

	// Projection function
	@Override
	public RealVector ProjectionFunction(RealVector x) {
		double d = this.distance(new Point3D(x.getEntry(0), x.getEntry(1), x.getEntry(2)));
		return MatrixUtils.createRealVector(new double[]{d});
	}

	// Jacobian matrix of the measurement with respect to (x, y, z, px, py, pz)
	@Override
	public RealMatrix ProjectionMatrix(RealVector x) {

		double ddocadx  = partialProjectionMatrix(x, 0);
		double ddocady  = partialProjectionMatrix(x, 1);
		double ddocadz  = partialProjectionMatrix(x, 2);
		double ddocadpx = partialProjectionMatrix(x, 3);
		double ddocadpy = partialProjectionMatrix(x, 4);
		double ddocadpz = partialProjectionMatrix(x, 5);
		
		return MatrixUtils.createRealMatrix(new double[][]{
			{ddocadx, ddocady, ddocadz, ddocadpx, ddocadpy, ddocadpz}});
	}

	private double partialProjectionMatrix(RealVector x, int i) {
		double     h       = 1e-8;// in mm
		RealVector x_plus  = x.copy();
		RealVector x_minus = x.copy();

		x_plus.setEntry(i, x_plus.getEntry(i) + h);
		x_minus.setEntry(i, x_minus.getEntry(i) - h);

		double doca_plus  = this.ProjectionFunction(x_plus).getEntry(0);
		double doca_minus = this.ProjectionFunction(x_minus).getEntry(0);

		return (doca_plus - doca_minus) / (2 * h);
	}

	@Override
	public double distance(Point3D point3D) {
		return this.wireLine.distance(point3D).length();
	}

	public static void main(String[] args) {
		AlertDCDetector factory = (new AlertDCFactory()).createDetectorCLAS(new DatabaseConstantProvider());
		System.out.println("Run test: comparison between two hits.");
		Hit h1 = new Hit(1,1,1,1,0,0,0);
		Hit h2 = new Hit(1,1,1,47,0,0,0);
		Hit h3 = new Hit(1,2,1,47,0,0,0);
		h1.setWirePosition(factory);
		h2.setWirePosition(factory);
		System.out.println("h1 : " + h1);
		System.out.println("h2 : " + h2);
		System.out.println("h3 : " + h3);
		System.out.println("numWires : " + h1.getNbOfWires());
		System.out.println("h1 compare to h2 : " + h1.compareTo(h2));
		System.out.println("h2 compare to h1 : " + h2.compareTo(h1));
		System.out.println("h1 compare to h3 : " + h1.compareTo(h3));

		System.out.println("/////////////////////////"); 
		System.out.println("Test AHDC geometry"); 
		System.out.println(""); 
		System.out.println("s  : sector"); 
		System.out.println("sl : super layer"); 
		System.out.println("l  : layer"); 
		System.out.println("c  : component"); 
		System.out.println("/////////////////////////"); 
		System.out.println("------------------------------------------------------------------------------"); 
		System.out.println("                |            origin            |             end"); 
		System.out.println("------------------------------------------------------------------------------"); 
		System.out.println("s   sl  l   c   |     x         y        z     |     x        y        z"); 
		System.out.println("------------------------------------------------------------------------------");
		for (int s = 1; s <= factory.getNumSectors(); s++) {
			for (int sl = 1; sl <= factory.getSector(s).getNumSuperlayers(); sl++) {
				for (int l = 1; l <= factory.getSector(s).getSuperlayer(sl).getNumLayers(); l++) {
					for (int c = 1; c <= factory.getSector(s).getSuperlayer(sl).getLayer(l).getNumComponents(); c++) {
						Line3D line = factory.getSector(s).getSuperlayer(sl).getLayer(l).getComponent(c).getLine();
						Point3D end = line.end();
						Point3D origin = line.origin();
						System.out.printf("%2d  %2d  %2d  %2d  |  %7.3f  %7.3f  %7.3f  |  %7.3f  %7.3f  %7.3f\n", s, sl, l, c, origin.x(), origin.y(), origin.z(), end.x(), end.y(), end.z());
					}
				}
			}
		}
    }

}
