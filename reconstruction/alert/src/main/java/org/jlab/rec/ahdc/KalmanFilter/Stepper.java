package org.jlab.rec.ahdc.KalmanFilter;

import org.jlab.clas.tracking.kalmanfilter.Material;

import java.util.Arrays;

/** 
 * It is a screenshot of the system during the Kalman Filter procedure
 * 
 * @author Mathieu Ouillon
 * @author Éric Fuchey
 * @author Felix Touchte Codjo
 */
public class Stepper {
	public double[] y; // position and momentum coordinates
	public double   h; // step size, managed in Propagator
	public Material material; // managed in Propagator
	public double   s; 
	public double   sTot        = 0; 
	public double   s_drift     = 0; 
	public boolean  is_in_drift = false; 
	public double   dEdx; // s, sTot, s_drift, is_in_drift, dEdx are used in RungeKutta.doOneStep()
	public boolean  direction; // true (forward propagation), false (backward propagation)

	public Stepper(double[] y) {
		this.y = Arrays.copyOf(y, y.length);
	}

	// Copy constructor
	public Stepper(Stepper stepper) {
		this.y = Arrays.copyOf(stepper.y, stepper.y.length);
		this.h = stepper.h;
		this.material = stepper.material;
		this.s = stepper.s;
		this.sTot = stepper.sTot;
		this.s_drift = stepper.s_drift;
		this.is_in_drift = stepper.is_in_drift;
		this.dEdx = stepper.dEdx;
		this.direction = stepper.direction;
	}

	// Copy the contents but do not change the reference
	public void copyContent(Stepper stepper) {
		this.y = Arrays.copyOf(stepper.y, stepper.y.length);
		this.h = stepper.h;
		this.material = stepper.material;
		this.s = stepper.s;
		this.sTot = stepper.sTot;
		this.s_drift = stepper.s_drift;
		this.is_in_drift = stepper.is_in_drift;
		this.dEdx = stepper.dEdx;
		this.direction = stepper.direction;
	}
	
    // the initialisation is done in the KFitter.predict()
	public void initialize(boolean direction) {
		this.s           = 0;
		this.dEdx        = 0;
		this.direction   = direction;
		this.is_in_drift = false;
	}
    
    // radius
	public double r() {
		return Math.hypot(y[0], y[1]);
	}
    
    // momentum
	public double p() {
		return Math.sqrt(y[3] * y[3] + y[4] * y[4] + y[5] * y[5]);
	}

	public void print() {
		System.out.println("r = " + this.r() + " p = " + this.p() + " x = " + y[0] + " y = " + y[1] + " z = " + y[2]);
	}

	@Override
	public String toString() {
		return "" + sTot + ' ' + y[0] + ' ' + y[1] + ' ' + y[2] + ' ' + y[3] + ' ' + y[4] + ' ' + y[5];
	}

	// distance between tow steppers
	public double distance(Stepper stepper) {
		double dx = this.y[0] - stepper.y[0];
		double dy = this.y[1] - stepper.y[1];
		double dz = this.y[2] - stepper.y[2];
		return Math.sqrt(dx*dx + dy*dy + dz*dz);
	}

}
