package org.jlab.rec.ahdc.KalmanFilter;

import org.jlab.clas.tracking.kalmanfilter.Material;

public class Indicator {

	public double R;
	public double h;
	public Hit hit;
	public boolean direction;
	public Material material;

	public Indicator(double r, double h, Hit hit, boolean direction, Material material) {
		this.R = r;
		this.h = h;
		this.hit = hit;
		this.direction = direction;
		this.material = material;
	}

	public boolean haveAHit() {
		boolean res = this.hit != null;
		if (this.R == 0 && !direction) res = true;
		return res;
	}


	// This method is not self consistent. We should not have hardcoded values
	// It is just for testing purpose
	public int getUniqueId() {
                if (hit != null) {
                	return (hit.getSuperLayer()*10 + hit.getLayer())*100 + hit.getWire();
                }
                else if (Math.abs(R) < 1e-9) {
                	// beamline
                	return 0;
                }
                else if (Math.abs(R - 3.0) < 1e-9) {
                	// inner face of the target straw
                	return 1;
                }
                else if (Math.abs(R - 3.060) < 1e-9) {
                	// outer face of the target straw
                	return 2;
                }
		else {
			return -1;
		}
	}
}
