package org.jlab.rec.ahdc.KalmanFilter;

import org.jlab.clas.tracking.kalmanfilter.Material;

import java.util.Arrays;

// A trajectory point computed by the Kalman Filter
public class Stepper {
	public double[] y; // position and momentum coordinates
	public double   h; // 
	public Material material; 
	public double   s; // path length during the propagation between the current indicator and the next one given in initialize()
	public double   sTot        = 0; // total lenght with respect to the beamline // increase in forward propagation, reach the maximum in the last layer and decrease in the backward propagation // cf doOneStep() method in RungeKutta4.java 
	public double   s_drift     = 0; // cf doOneStep() method in RungeKutta4.java
	public boolean  is_in_drift = false; // cf doOneStep() method in RungeKutta4.java 
	public double   dEdx; // deposited energy during the propagation between the current indicator and the next one given in initialize()
	public boolean  direction; // true is the forward propagation (beamline to last layer) and false is the backward propagation (last layer to beamline)

	public Stepper(double[] y) {
		this.y = Arrays.copyOf(y, y.length);
	}
	
	// The initialisation tells the stepper where to go (i.e toward the `indicator`)
    // Cf. notes in Indicator.java
    // The initilisation is done before any propagation between indicators, in the predict() method of KFitter.java
	//public void initialize(Indicator indicator) {
	public void initialize(boolean direction) {
		this.s           = 0;
		this.dEdx        = 0;
		this.direction   = direction;
		this.is_in_drift = false;
	}
    
    // radius, distance to the beamline
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
}
