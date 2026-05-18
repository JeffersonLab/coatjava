package org.jlab.rec.ahdc.Track;

import org.jlab.rec.ahdc.AI.InterCluster;
import org.jlab.rec.ahdc.Cluster.Cluster;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.PreCluster.PreCluster;
import org.jlab.rec.ahdc.PreCluster.PreClusterFinder;
import org.jlab.rec.ahdc.AI.PreClustering;

import java.util.ArrayList;
import java.util.List;

/** A track candidate: the output of a track finder.
 *
 *  <p>A candidate is a set of hits grouped into {@link Cluster}s (and the
 *  derived {@link InterCluster}s). It is <em>not</em> a fitted track — fitting
 *  consumes a {@code TrackCandidate} and produces a {@link Track}.</p>
 *
 *  <p>A candidate carries a {@link CandidateType} describing its specialization
 *  (AHDC-only, AHDC+ATOF, ...). The type is what dictates how the candidate is
 *  fitted downstream.</p>
 */
public class TrackCandidate {

	private       double         _Distance;
	private       List<Cluster>  _Clusters = new ArrayList<>();
	private       List<InterCluster>  _InterClusters = new ArrayList<>();
	private       boolean        _Used     = false;
	private final ArrayList<Hit> hits      = new ArrayList<>();

	private int    trackId = -1; ///< id of the track
	private int    n_hits  = 0;  ///< number of hits
	private int    sum_adc = 0;  ///< sum of adc (adc)

	/** Candidate specialization — defaults to AHDC-only; finders that build
	 *  AHDC+ATOF candidates (GNN) override it via {@link #setType}. */
	private CandidateType type = CandidateType.AHDC_ONLY;
	/** ATOF hits attached to this candidate (non-empty only for AHDC_ATOF). */
	private final List<AtofHitStub> atofHits = new ArrayList<>();

    // AHDC::aiprediction
    private int predicted_ATOF_sector = -1;
    private int predicted_ATOF_layer = -1;
    private int predicted_ATOF_wedge = -1;

	public TrackCandidate(List<Cluster> clusters) {
		this._Clusters = clusters;
		this._Distance = 0;
		for (int i = 0; i < clusters.size() - 1; i++) {
			this._Distance += Math.sqrt((clusters.get(i).get_X() - clusters.get(i + 1).get_X()) * (clusters.get(i).get_X() - clusters.get(i + 1).get_X()) + (clusters.get(i).get_Y() - clusters.get(i + 1).get_Y()) * (clusters.get(i).get_Y() - clusters.get(i + 1).get_Y()));
		}
		generateHitList();
		generateInterClusterList();
    }

    public TrackCandidate(ArrayList<Hit> hitslist) {
		hits.addAll(hitslist);
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
		return "TrackCandidate{" + "_Clusters=" + _Clusters + '}';
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

	public CandidateType getType() {
		return type;
	}

	public void setType(CandidateType type) {
		this.type = type;
	}

	public List<AtofHitStub> getAtofHits() {
		return atofHits;
	}

	public void addAtofHit(AtofHitStub hit) {
		atofHits.add(hit);
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

    // AHDC::aiprediction
    public void set_predicted_ATOF_sector(int s) {predicted_ATOF_sector = s;}
    public void set_predicted_ATOF_layer(int l) {predicted_ATOF_layer = l;}
    public void set_predicted_ATOF_wedge(int w) {predicted_ATOF_wedge = w;}
    public int get_predicted_ATOF_sector() {return predicted_ATOF_sector;}
    public int get_predicted_ATOF_layer() {return predicted_ATOF_layer;}
    public int get_predicted_ATOF_wedge() {return predicted_ATOF_wedge;}

}
