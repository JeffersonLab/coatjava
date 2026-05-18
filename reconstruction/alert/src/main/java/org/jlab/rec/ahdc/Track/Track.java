package org.jlab.rec.ahdc.Track;

import org.jlab.rec.ahdc.AI.InterCluster;
import org.jlab.rec.ahdc.AHDCCluster.AHDCCluster;
import org.jlab.rec.ahdc.HelixFit.HelixFitObject;
import org.jlab.rec.ahdc.Hit.Hit;

import java.util.ArrayList;
import java.util.List;

/** A fitted track: the result of track fitting.
 *
 *  <p>A {@code Track} is produced by fitting a {@link TrackCandidate} (helix
 *  fit, then Kalman filter). It composes the candidate it was fitted from — so
 *  it still exposes the candidate's hits / clusters to the bank writers — and
 *  adds the fitted vertex, momentum and fit-quality quantities.</p>
 */
public class Track {

	private final TrackCandidate candidate;

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

	public Track(TrackCandidate candidate) {
		this.candidate = candidate;
	}

	/** The candidate this track was fitted from. */
	public TrackCandidate getCandidate() {
		return candidate;
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

	@Override
	public String toString() {
		return "Track{" + "candidate=" + candidate + '}';
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

	public void set_chi2(double _chi2) { chi2 = _chi2;}
	public void set_sum_residuals(double _sum_residuals) { sum_residuals = _sum_residuals;}
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
			for (Hit h : candidate.getHits()) {
				sum += (int) Math.round(h.getADC());
			}
			dEdx = sum/path;
		}
		return dEdx;
	}
	public double get_p_drift() {return p_drift;}
	public double get_path() {return path;}

	// --- delegated to the underlying candidate -------------------------------
	// The candidate owns the hits / clusters / specialization; Track exposes the
	// same accessors so callers can treat a fitted Track as a full handle.
	public int               get_trackId()       { return candidate.get_trackId(); }
	public void              set_trackId(int id)  { candidate.set_trackId(id); }
	public int               get_n_hits()        { return candidate.get_n_hits(); }
	public void              set_n_hits(int n)    { candidate.set_n_hits(n); }
	public int               get_sum_adc()       { return candidate.get_sum_adc(); }
	public void              set_sum_adc(int s)   { candidate.set_sum_adc(s); }
	public ArrayList<Hit>    getHits()            { return candidate.getHits(); }
	public List<AHDCCluster> get_Clusters()       { return candidate.get_Clusters(); }
	public List<InterCluster> getInterclusters()  { return candidate.getInterclusters(); }
	public double            get_Distance()       { return candidate.get_Distance(); }
	public boolean           is_Used()            { return candidate.is_Used(); }
	public void              set_Used(boolean u)  { candidate.set_Used(u); }
	public CandidateType     getType()            { return candidate.getType(); }
	public void              setType(CandidateType t)        { candidate.setType(t); }
	public List<AtofHitStub> getAtofHits()        { return candidate.getAtofHits(); }
	public void              addAtofHit(AtofHitStub h)       { candidate.addAtofHit(h); }

	// AHDC::aiprediction — delegated to the candidate
	public void set_predicted_ATOF_sector(int s) { candidate.set_predicted_ATOF_sector(s); }
	public void set_predicted_ATOF_layer(int l)  { candidate.set_predicted_ATOF_layer(l); }
	public void set_predicted_ATOF_wedge(int w)  { candidate.set_predicted_ATOF_wedge(w); }
	public int  get_predicted_ATOF_sector()      { return candidate.get_predicted_ATOF_sector(); }
	public int  get_predicted_ATOF_layer()       { return candidate.get_predicted_ATOF_layer(); }
	public int  get_predicted_ATOF_wedge()       { return candidate.get_predicted_ATOF_wedge(); }
}
