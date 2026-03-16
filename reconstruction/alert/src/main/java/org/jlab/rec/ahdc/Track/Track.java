package org.jlab.rec.ahdc.Track;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.jlab.rec.ahdc.AI.InterCluster;
import org.jlab.rec.ahdc.Cluster.Cluster;
import org.jlab.rec.ahdc.HelixFit.HelixFitObject;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.KalmanFilter.RadialKFHit;
import org.jlab.rec.ahdc.KalmanFilter.Stepper;
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
	private final ArrayList<Hit> hits      = new ArrayList<>(); // AHDC hits
	private ArrayList<RadialKFHit> ATOF_hits = new ArrayList<>();
	private RadialKFHit beamline_hit = null;
	
	private int    trackId = -1; ///< id of the track
	private double sum_residuals = 0; ///< sum of residuals (mm)
	private double chi2    = 0;  ///< sum of residuals^2 (mm^2)
	// AHDC::track
	private double x0  = 0;
	private double y0  = 0;
	private double z0  = 0;
	private double px0 = 0;
	private double py0 = 0;
	private double pz0 = 0;
	private double p_drift = 0;  ///< momentum in the drift region (MeV)
	private double path    = 0;  ///< length of the track (mm)
	// for the error matrix: first 3 lines in mm^2; last 3 lines in MeV^2 (in the beamline)
	RealMatrix errorCovarianceMatrix = MatrixUtils.createRealMatrix(new double[][]{
		{50  , 0.0 , 0.0 , 0.0 , 0.0 , 0.0}, 
		{0.0 , 50  , 0.0 , 0.0 , 0.0 , 0.0}, 
		{0.0 , 0.0 , 900 , 0.0 , 0.0 , 0.0}, 
		{0.0 , 0.0 , 0.0 , 100 , 0.0 , 0.0}, 
		{0.0 , 0.0 , 0.0 , 0.0 , 100 , 0.0}, 
		{0.0 , 0.0 , 0.0 , 0.0 , 0.0 , 900}});
	// Position and momentum when the track crosses the ATOF surface
	// S1 : lower surface of an ATOF bar
	// S2 : upper surface of an ATOF bar = lower surface of an ATOF wedge
	// S3 : upper surface of an ATOF wedge
	Stepper ATOF_S1_stepper;
	Stepper ATOF_S2_stepper;
	Stepper ATOF_S3_stepper;
	double ATOF_S1_radius;
	double ATOF_S2_radius;
	double ATOF_S3_radius;
	int ATOF_region = 0; // is n if the trach reaches Sn, 0 otherwise (i.e does not reach S1)

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
		if (hitslist.size() > 0) {
			//take first hit.
			Hit hit = hitslist.get(0);
			double phi          = Math.atan2(hit.getY(), hit.getX());
			//hitslist.
			this.px0  = p*Math.sin(phi);
			this.py0  = p*Math.cos(phi);
			this.pz0  = 0.0;
		}
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

	public ArrayList<RadialKFHit> getATOFHits() {
		return this.ATOF_hits;
	}

	public RadialKFHit getBeamlineHit() {
		return this.beamline_hit;
	}

	public void setATOFHits(ArrayList<RadialKFHit> _ATOF_hits) {this.ATOF_hits = _ATOF_hits;}
	public void setBeamlineHit(RadialKFHit _beamline_hit) {this.beamline_hit = _beamline_hit;}

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
	//public void set_n_hits(int _n_hits) { n_hits = _n_hits;}
	//public void set_sum_adc(int _sum_adc) { sum_adc = _sum_adc;}
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
	public void set_p_drift(double _p_drift) { p_drift = _p_drift;}
	public void set_path(double _path) { path = _path;}
	public double get_dEdx() {
		if (path <= 0) {
			return 0;
		}else {
			return get_sum_adc()/path; 
		}
	}
	public double get_p_drift() {return p_drift;}
	public double get_path() {return path;}
	public RealMatrix getErrorCovarianceMatrix() {return errorCovarianceMatrix;}
	public void setErrorCovarianceMatrix(RealMatrix errorCovarianceMatrix) {this.errorCovarianceMatrix = errorCovarianceMatrix;}

    // AHDC::aiprediction
    public void set_predicted_ATOF_sector(int s) {predicted_ATOF_sector = s;}
    public void set_predicted_ATOF_layer(int l) {predicted_ATOF_layer = l;}
    public void set_predicted_ATOF_wedge(int w) {predicted_ATOF_wedge = w;}
    public int get_predicted_ATOF_sector() {return predicted_ATOF_sector;}
    public int get_predicted_ATOF_layer() {return predicted_ATOF_layer;}
    public int get_predicted_ATOF_wedge() {return predicted_ATOF_wedge;}

	// Projection of the Track on the ATOF surfaces
	public void set_ATOF_S1_stepper(Stepper _stepper) {this.ATOF_S1_stepper = _stepper;}
	public void set_ATOF_S2_stepper(Stepper _stepper) {this.ATOF_S2_stepper = _stepper;}
	public void set_ATOF_S3_stepper(Stepper _stepper) {this.ATOF_S3_stepper = _stepper;}
	public void set_ATOF_region(int _n) {this.ATOF_region = _n;}
	public Stepper get_ATOF_S1_stepper() {return this.ATOF_S1_stepper;}
	public Stepper get_ATOF_S2_stepper() {return this.ATOF_S2_stepper;}
	public Stepper get_ATOF_S3_stepper() {return this.ATOF_S3_stepper;}
	public int get_ATOF_region() {return this.ATOF_region;}

}
