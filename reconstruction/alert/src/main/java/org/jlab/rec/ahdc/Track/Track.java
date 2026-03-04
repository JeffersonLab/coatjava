package org.jlab.rec.ahdc.Track;

import org.apache.commons.math3.linear.RealVector;
import org.jlab.rec.ahdc.AI.InterCluster;
import org.jlab.rec.ahdc.Cluster.Cluster;
import org.jlab.rec.ahdc.HelixFit.HelixFitObject;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.PreCluster.PreCluster;
import org.jlab.rec.ahdc.PreCluster.PreClusterFinder;
import org.jlab.rec.ahdc.AI.PreClustering;

import java.util.ArrayList;
import java.util.List;

public class Track {

	private       double         _Distance;
	private       List<Cluster>  _Clusters = new ArrayList<>();
	private       List<InterCluster>  _InterClusters = new ArrayList<>();
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
		generateInterClusterList();
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
		this.chi2 = helixFitObject.get_Chi2();
		this.path = helixFitObject.get_path();
	}

	public void setPositionAndMomentumVec(double[] x) {
		this.x0  = x[0];
		this.y0  = x[1];
		this.z0  = x[2];
		this.px0 = x[3];
		this.py0 = x[4];
		this.pz0 = x[5];
	}

	private void generateHitList() {
		for (Cluster cluster : _Clusters) {
			for (PreCluster preCluster : cluster.get_PreClusters_list()) {
				hits.addAll(preCluster.get_hits_list());
			}
		}
	}

	private void generateInterClusterList() {
		// Use hits to generate preclusters
		PreClusterFinder preclusterfinder = new PreClusterFinder();
		preclusterfinder.findPreclusters(hits);
		ArrayList<PreCluster> AHDC_PreClusters = preclusterfinder.get_AHDCPreClusters();

		// Use preclusters to generate interclusters
		PreClustering preClustering = new PreClustering();
		this._InterClusters = preClustering.mergePreclusters(AHDC_PreClusters);
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

	public List<InterCluster> getInterclusters() {
		return _InterClusters;
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

	public void set_trackId(int _trackId) { 
		trackId = _trackId;
		// set trackId for clusters
		for(Cluster cluster : this._Clusters) {
			cluster.set_trackId(_trackId);
		}
		// set trackId for interclusters
		for(InterCluster interCluster : this._InterClusters) {
			interCluster.setTrackId(_trackId);
		}
		// set trackId for hits
		for (Hit hit : this.hits) {
			hit.setTrackId(_trackId);
		}
	}
	public void set_n_hits(int _n_hits) { n_hits = _n_hits;}
	public void set_sum_adc(int _sum_adc) { sum_adc = _sum_adc;}
	public void set_chi2(double _chi2) { chi2 = _chi2;}
	public void set_sum_residuals(double _sum_residuals) { sum_residuals = _sum_residuals;}
	public int    get_trackId() {return trackId;}
	public int    get_n_hits() {
    	if (hits == null) {
    	    return 0;
    	}
    	return hits.size();
	}
	public int    get_sum_adc() {
		if (hits == null || hits.isEmpty()) {
			return 0;
		}
		int sum = 0;
		for (Hit h : hits) {
			sum += (int) Math.round(h.getADC());
		}
		return sum;
	}
	public double get_chi2() {return chi2;}
	public double get_sum_residuals() {return sum_residuals;}
	// AHDC::track
	public void set_dEdx(double _dEdx) { dEdx = _dEdx;}
	public void set_p_drift(double _p_drift) { p_drift = _p_drift;}
	public void set_path(double _path) { path = _path;}
	public double get_dEdx() {
		if (path <= 0) {
			dEdx = 0;
		}else {
			int sum = 0;
			for (Hit h : hits) {
				sum += (int) Math.round(h.getADC());
			}
			dEdx = sum/path; 
		}
		return dEdx;
	}
	public double get_p_drift() {return p_drift;}
	public double get_path() {return path;}

    // AHDC::aiprediction
    public void set_predicted_ATOF_sector(int s) {predicted_ATOF_sector = s;}
    public void set_predicted_ATOF_layer(int l) {predicted_ATOF_layer = l;}
    public void set_predicted_ATOF_wedge(int w) {predicted_ATOF_wedge = w;}
    public int get_predicted_ATOF_sector() {return predicted_ATOF_sector;}
    public int get_predicted_ATOF_layer() {return predicted_ATOF_layer;}
    public int get_predicted_ATOF_wedge() {return predicted_ATOF_wedge;}

}
