package org.jlab.rec.ahdc.Hit;


public class Hit implements Comparable<Hit> {

	private final double thster = Math.toRadians(20.0);
	private final int    id;
	private final int    superLayerId;
	private final int    layerId;
	private final int    wireId;
	private final double doca;

	private double  phi;
	private double  radius;
	private int     nbOfWires;
	private boolean use = false;
	private double  x;
	private double  y;

	public Hit(int _Id, int _Super_layer, int _Layer, int _Wire, double _Doca) {
		this.id           = _Id;
		this.superLayerId = _Super_layer;
		this.layerId      = _Layer;
		this.wireId       = _Wire;
		this.doca         = _Doca;
		wirePosition();
	}

	private void wirePosition() {
		final double DR_layer = 4.0;
		final double round    = 360.0;

		double numWires = 32.0;
		double R_layer  = 47.0;
		
		switch (this.superLayerId) {
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

		R_layer = R_layer + DR_layer * (this.layerId-1);
		double alphaW_layer = Math.toRadians(round / (numWires));
		//should it be at z = 0? in which case, we need to account for the positive or negative stereo angle...
		double wx           = -R_layer * Math.sin(alphaW_layer * (this.wireId-1) + 0.5*thster * (Math.pow(-1, this.superLayerId-1)));
		double wy           = -R_layer * Math.cos(alphaW_layer * (this.wireId-1) + 0.5*thster * (Math.pow(-1, this.superLayerId-1)));
		
		//System.out.println(" superlayer " + this.superLayerId + " layer " + this.layerId + " wire " + this.wireId + " R_layer " + R_layer + " wx " + wx + " wy " + wy);
		
		this.nbOfWires = (int) numWires;
		this.phi       = Math.atan2(wy, wx);
		this.radius    = R_layer;
		this.x         = wx;
		this.y         = wy;
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
}
