package org.jlab.rec.ahdc.Hit;

import org.jlab.geom.detector.alert.AHDC.AlertDCDetector;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;

public class Hit implements Comparable<Hit> {

	private final double thster = Math.toRadians(20.0);
	private final int    id;
	private final int    superLayerId;
	private final int    layerId;
	private final int    wireId;
	private final double doca;
	private final double adc;
	private final double time;

        private Line3D wireLine;
	private double  phi;
	private double  radius;
	private int     nbOfWires;
	private boolean use = false;
	private double  x;
	private double  y;
	private double  residual_prefit;
	private double  residual;
	private int	trackId;

        //updated constructor with ADC
	public Hit(int _Id, int _Super_layer, int _Layer, int _Wire, double _Doca, double _ADC, double _Time) {
		this.id           = _Id;
		this.superLayerId = _Super_layer;
		this.layerId      = _Layer;
		this.wireId       = _Wire;
		this.doca         = _Doca;
		this.adc          = _ADC;
		this.time 	  = _Time;
		this.residual_prefit = 0.0;
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

	@Override
	public int compareTo(Hit arg0) {
		if (this.superLayerId == arg0.superLayerId && this.layerId == arg0.layerId && this.wireId == arg0.wireId) {
			return 0;
		} else {
			return 1;
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

        public Line3D getLine() {
            return wireLine;
        }
        
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

	public double getResidualPrefit() {
		return residual_prefit;
	}

	public void setResidual(double resid) {
		this.residual = resid;
	}

	public void setResidualPrefit(double resid) {
		this.residual_prefit = resid;
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

}
