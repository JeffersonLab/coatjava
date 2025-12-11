package org.jlab.rec.ahdc.Track;

import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.RealMatrix;

public class KFMonitor {
	private int trackid; //< trackid
	private int Niter; //< iteration number of the Kalman Filter algorithm
	private int orientation; //< forward (0), backward (1), postfit (2) propagation
	private int indicator; //< wire ==> layer*100 + component  ; beamline ==> 0 
	private int status; //< state just after: prediction (0) or correction (1)
	private RealVector state; //< vector containing x, y, z, px, py, pz
	private RealMatrix errorCovarianceMatrix; // error covariance matrix, only the diagonal matter (x-x')^2, (y-y')^2, ..., (pz-pz')^2	
	
	// constructor
	public KFMonitor(int _trackid, int _Niter, int _orientation, int _indicator, int _status, RealVector _state, RealMatrix _errorCovarianceMatrix) {
		this.trackid = _trackid;
		this.Niter = _Niter;
		this.orientation = _orientation;
		this.indicator = _indicator;
		this.status = _status;
		this.state = _state.copy();
		this.errorCovarianceMatrix = _errorCovarianceMatrix.copy();
	}

	public int get_trackid() {return trackid;}
	public int get_Niter() {return Niter;}
	public int get_orientation() {return orientation;}
	public int get_indicator() {return indicator;}
	public int get_status() {return status;}
	public RealVector get_state() {return state.copy();}
	public RealMatrix get_errorCovarianceMatrix() {return errorCovarianceMatrix.copy();}
}
