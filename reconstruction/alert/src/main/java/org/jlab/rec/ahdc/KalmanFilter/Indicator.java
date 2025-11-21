package org.jlab.rec.ahdc.KalmanFilter;

import org.jlab.clas.tracking.kalmanfilter.Material;
import org.jlab.rec.ahdc.Hit.Hit;

// Give the relative positions of the main constituents of the detector
//      - beamline
//      - target (inner face)
//      - target (outer face)
//      - ordered list of hits (wires)
//          - hit(s) in layer 1
//          - hit(s) in layer 2
//          -
//          - hit(s) in layer 8
//  It is used to indicate the Stepper(.java)
//      - where to go
//      - the medium/material it will cross
//      - the step length to be used by RungeKutta4 (RK4)
public class Indicator {

	public double R; // radius position of the detector constituents (cylindrical geometry)
	public double h; // step length used by RK4
	public Hit hit; // is null when the stepper moves toward target surfaces
	public boolean direction; // true is the forward propagation (beamline to last layer) and false is the backward propagation (last layer to beamline)
	public Material material; // medium property

	public Indicator(double r, double h, Hit hit, boolean direction, Material material) {
		this.R = r;
		this.h = h;
		this.hit = hit;
		this.direction = direction;
		this.material = material;
	}
    
    // Not well named
    // Tell if we have a measurement or not at this indication (indicator)
    //      - Each AHDC hit (sense wire) provides a measurement, it is the distance (doca, measured distance to the wire)
    //      - When the stepper move toward the beamline (backward propagation), we also have a measurement (the distance to the beamline is 0)
	public boolean haveAHit() {
		boolean res = this.hit != null;
		if (this.R == 0 && !direction) res = true; // beamline during backward propagation
		return res;
	}


	// This method is not self consistent. We should not have hardcoded values
	// It is just for testing
	// Only used by KFMonitor
	public int getUniqueId() {
                if (hit != null) {
                	return (hit.getSuperLayerId()*10 + hit.getLayerId())*100 + hit.getWireId();
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
