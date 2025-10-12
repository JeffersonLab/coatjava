package org.jlab.rec.ahdc.Track;

import org.apache.commons.math3.linear.RealVector;
import org.jlab.rec.ahdc.Cluster.Cluster;
import org.jlab.rec.ahdc.HelixFit.HelixFitObject;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.PreCluster.PreCluster;

import java.util.ArrayList;
import java.util.List;

public class Track {

	private       double         _Distance;
	private       List<Cluster>  _Clusters = new ArrayList<>();
	private       boolean        _Used     = false;
	private final ArrayList<Hit> hits      = new ArrayList<>();
	
	private int    trackId = -1; ///< id of the track
	private int    n_hits  = 0;  ///< number of hits
	private int    sum_adc = 0;  ///< sum of adc (adc)
	private double sum_residuals = 0; ///< sum of residuals (mm)
	private double chi2    = 0;  ///< sum of residuals^2 (mm^2)
	// AHDC::track
	private double x0  = 0;
	private double y0  = 0;
	private double z0  = 0;
	private double px0 = 0;
	private double py0 = 0;
	private double pz0 = 0;
	private double dEdx    = 0;  ///< deposited energy per path length (adc/mm)
	private double p_drift = 0;  ///< momentum in the drift region (MeV)
	private double path    = 0;  ///< length of the track (mm)
	// AHDC::kftrack
	private double x0_kf  = 0;
	private double y0_kf  = 0;
	private double z0_kf  = 0;
	private double px0_kf = 0;
	private double py0_kf = 0;
	private double pz0_kf = 0;
	private double dEdx_kf    = 0;  ///< deposited energy per path length (adc/mm)
	private double p_drift_kf = 0;  ///< momentum in the drift region (MeV)
	private double path_kf    = 0;  ///< length of the track (mm)
    // AHDC::aiprediction
    private int predicted_ATOF_sector = -1;
    private int predicted_ATOF_layer = -1;
    private int predicted_ATOF_wedge = -1;

	public Track(List<Cluster> clusters) {
		this._Clusters = clusters;
		this._Distance = 0;
		for (int i = 0; i < clusters.size() - 1; i++) {
			this._Distance += Math.sqrt((clusters.get(i).get_X() - clusters.get(i + 1).get_X()) * (clusters.get(i).get_X() - clusters.get(i + 1).get_X()) + (clusters.get(i).get_Y() - clusters.get(i + 1).get_Y()) * (clusters.get(i).get_Y() - clusters.get(i + 1).get_Y()));
		}
		generateHitList();
	}

    public Track(ArrayList<Hit> hitslist) {
	hits.addAll(hitslist);
	this.x0  = 0.0;
	this.y0  = 0.0;
	this.z0  = 0.0;
	double p = 150.0;//MeV/c
	//take first hit.
	Hit hit = hitslist.get(0);
	double phi          = Math.atan2(hit.getY(), hit.getX());
	//hitslist.
	this.px0  = p*Math.sin(phi);
	this.py0  = p*Math.cos(phi);
	this.pz0  = 0.0;
    }

	public void setPositionAndMomentum(HelixFitObject helixFitObject) {
		this.x0  = helixFitObject.get_X0();
		this.y0  = helixFitObject.get_Y0();
		this.z0  = helixFitObject.get_Z0();
		this.px0 = helixFitObject.get_px();
		this.py0 = helixFitObject.get_py();
		this.pz0 = helixFitObject.get_pz();
	}

	public void setPositionAndMomentumVec(double[] x) {
		this.x0  = x[0];
		this.y0  = x[1];
		this.z0  = x[2];
		this.px0 = x[3];
		this.py0 = x[4];
		this.pz0 = x[5];
	}

	public void setPositionAndMomentumForKF(RealVector x) {
		this.x0_kf  = x.getEntry(0);
		this.y0_kf  = x.getEntry(1);
		this.z0_kf  = x.getEntry(2);
		this.px0_kf = x.getEntry(3);
		this.py0_kf = x.getEntry(4);
		this.pz0_kf = x.getEntry(5);
	}

	private void generateHitList() {
		for (Cluster cluster : _Clusters) {
			for (PreCluster preCluster : cluster.get_PreClusters_list()) {
				hits.addAll(preCluster.get_hits_list());
			}
		}
	}

	public ArrayList<Hit> getHits() {
		return hits;
	}

	@Override
	public String toString() {
		return "Track{" + "_Clusters=" + _Clusters + '}';
	}

	public double get_Distance() {
		return _Distance;
	}

	public List<Cluster> get_Clusters() {
		return _Clusters;
	}

	public boolean is_Used() {
		return _Used;
	}

	public void set_Used(boolean _Used) {
		this._Used = _Used;
	}

	public double get_X0() {
		return x0;
	}

	public double get_Y0() {
		return y0;
	}

	public double get_Z0() {
		return z0;
	}

	public double get_px() {
		return px0;
	}

	public double get_py() {
		return py0;
	}

	public double get_pz() {
		return pz0;
	}

	public double getX0_kf() {
		return x0_kf;
	}

	public double getY0_kf() {
		return y0_kf;
	}

	public double getZ0_kf() {
		return z0_kf;
	}

	public double getPx0_kf() {
		return px0_kf;
	}

	public double getPy0_kf() {
		return py0_kf;
	}

	public double getPz0_kf() {
		return pz0_kf;
	}

	// Same for Track and KFTrack	
	public void set_trackId(int _trackId) { trackId = _trackId;}
	public void set_n_hits(int _n_hits) { n_hits = _n_hits;}
	public void set_sum_adc(int _sum_adc) { sum_adc = _sum_adc;}
	public void set_chi2(double _chi2) { chi2 = _chi2;}
	public void set_sum_residuals(double _sum_residuals) { sum_residuals = _sum_residuals;}
	public int    get_trackId() {return trackId;}
	public int    get_n_hits() {return n_hits;}
	public int    get_sum_adc() {return sum_adc;}
	public double get_chi2() {return chi2;}
	public double get_sum_residuals() {return sum_residuals;}
	// AHDC::track
	public void set_dEdx(double _dEdx) { dEdx = _dEdx;}
	public void set_p_drift(double _p_drift) { p_drift = _p_drift;}
	public void set_path(double _path) { path = _path;}
	public double get_dEdx() {return dEdx;}
	public double get_p_drift() {return p_drift;}
	public double get_path() {return path;}
	
	// AHDC::kftrack
	public void set_dEdx_kf(double _dEdx_kf) { dEdx_kf = _dEdx_kf;}
	public void set_p_drift_kf(double _p_drift_kf) { p_drift_kf = _p_drift_kf;}
	public void set_path_kf(double _path_kf) { path_kf = _path_kf;}
	public double get_dEdx_kf() {return dEdx_kf;}
	public double get_p_drift_kf() {return p_drift_kf;}
	public double get_path_kf() {return path_kf;}

    // AHDC::aiprediction
    public void set_predicted_ATOF_sector(int s) {predicted_ATOF_sector = s;}
    public void set_predicted_ATOF_layer(int l) {predicted_ATOF_layer = l;}
    public void set_predicted_ATOF_wedge(int w) {predicted_ATOF_wedge = w;}
    public int get_predicted_ATOF_sector() {return predicted_ATOF_sector;}
    public int get_predicted_ATOF_layer() {return predicted_ATOF_layer;}
    public int get_predicted_ATOF_wedge() {return predicted_ATOF_wedge;}

}
