package org.jlab.rec.ahdc.Banks;

import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.ahdc.AI.TrackPrediction;
import org.jlab.rec.ahdc.Cluster.Cluster;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.PreCluster.PreCluster;
import org.jlab.rec.ahdc.Track.Track;

import java.util.ArrayList;

public class RecoBankWriter {

	public DataBank fillAHDCHitsBank(DataEvent event, ArrayList<Hit> hitList) {
		if (hitList == null || hitList.size() == 0) return null;

		DataBank bank = event.createBank("AHDC::hits", hitList.size());

		for (int i = 0; i < hitList.size(); i++) {

			bank.setShort("id", i, (short) hitList.get(i).getId());
			bank.setByte("layer", i, (byte) hitList.get(i).getLayerId());
			bank.setByte("superlayer", i, (byte) hitList.get(i).getSuperLayerId());
			bank.setInt("wire", i, hitList.get(i).getWireId());
			bank.setDouble("doca", i, hitList.get(i).getDoca());
			bank.setDouble("residual", i, hitList.get(i).getResidual());
			bank.setDouble("residual_prefit", i, hitList.get(i).getResidualPrefit());
			bank.setDouble("time", i, hitList.get(i).getTime());
			bank.setInt("trackid", i, hitList.get(i).getTrackId());
		}

		return bank;
	}

	public DataBank fillPreClustersBank(DataEvent event, ArrayList<PreCluster> preClusters) {
		if (preClusters == null || preClusters.size() == 0) return null;

		DataBank bank = event.createBank("AHDC::preclusters", preClusters.size());

		for (int i = 0; i < preClusters.size(); i++) {
			bank.setFloat("x", i, (float) preClusters.get(i).get_X());
			bank.setFloat("y", i, (float) preClusters.get(i).get_Y());
		}

		return bank;
	}

	public DataBank fillClustersBank(DataEvent event, ArrayList<Cluster> clusters) {
		if (clusters == null || clusters.size() == 0) return null;

		DataBank bank = event.createBank("AHDC::clusters", clusters.size());

		for (int i = 0; i < clusters.size(); i++) {
			bank.setFloat("x", i, (float) clusters.get(i).get_X());
			bank.setFloat("y", i, (float) clusters.get(i).get_Y());
			bank.setFloat("z", i, (float) clusters.get(i).get_Z());
		}

		return bank;
	}


	public DataBank fillAHDCMCTrackBank(DataEvent event) {

		DataBank particle = event.getBank("MC::Particle");
		double   x_mc     = particle.getFloat("vx", 0);
		double   y_mc     = particle.getFloat("vy", 0);
		double   z_mc     = particle.getFloat("vz", 0);
		double   px_mc    = particle.getFloat("px", 0) * 1000;
		double   py_mc    = particle.getFloat("py", 0) * 1000;
		double   pz_mc    = particle.getFloat("pz", 0) * 1000;

		int      row  = 0;
		DataBank bank = event.createBank("AHDC::mc", row + 1);
		bank.setFloat("x", row, (float) x_mc);
		bank.setFloat("y", row, (float) y_mc);
		bank.setFloat("z", row, (float) z_mc);
		bank.setFloat("px", row, (float) px_mc);
		bank.setFloat("py", row, (float) py_mc);
		bank.setFloat("pz", row, (float) pz_mc);

		return bank;
	}

	public DataBank fillAHDCTrackBank(DataEvent event, ArrayList<Track> tracks) {

		DataBank bank = event.createBank("AHDC::track", tracks.size());

		int row = 0;

		for (Track track : tracks) {
			if (track == null) continue;
			double x  = track.get_X0();
			double y  = track.get_Y0();
			double z  = track.get_Z0();
			double px = track.get_px();
			double py = track.get_py();
			double pz = track.get_pz();

			bank.setInt("trackid", row, (int) track.get_trackId());
			bank.setFloat("x", row, (float) x);
			bank.setFloat("y", row, (float) y);
			bank.setFloat("z", row, (float) z);
			bank.setFloat("px", row, (float) px);
			bank.setFloat("py", row, (float) py);
			bank.setFloat("pz", row, (float) pz);
			bank.setInt("n_hits", row, (int) track.get_n_hits());
			bank.setInt("sum_adc", row, (int) track.get_sum_adc());
			bank.setFloat("path", row, (float) track.get_path());
			bank.setFloat("dEdx", row, (float) track.get_dEdx());
			bank.setFloat("p_drift", row, (float) track.get_p_drift());
			bank.setFloat("chi2", row, (float) track.get_chi2());
			bank.setFloat("sum_residuals", row, (float) track.get_sum_residuals());

            bank.setInt("ATOF_sector", row, track.get_predicted_ATOF_sector());
            bank.setInt("ATOF_layer", row, track.get_predicted_ATOF_layer());
            bank.setInt("ATOF_wedge", row, track.get_predicted_ATOF_wedge());

			row++;
		}

		return bank;
	}

	public DataBank fillAHDCKFTrackBank(DataEvent event, ArrayList<Track> tracks) {

		DataBank bank = event.createBank("AHDC::kftrack", tracks.size());

		int row = 0;

		for (Track track : tracks) {
			if (track == null) continue;
			double x  = track.getX0_kf();
			double y  = track.getY0_kf();
			double z  = track.getZ0_kf();
			double px = track.getPx0_kf();
			double py = track.getPy0_kf();
			double pz = track.getPz0_kf();

			bank.setInt("trackid", row, (int) track.get_trackId());
			bank.setFloat("x", row, (float) x);
			bank.setFloat("y", row, (float) y);
			bank.setFloat("z", row, (float) z);
			bank.setFloat("px", row, (float) px);
			bank.setFloat("py", row, (float) py);
			bank.setFloat("pz", row, (float) pz);
			bank.setInt("n_hits", row, (int) track.get_n_hits());
			bank.setInt("sum_adc", row, (int) track.get_sum_adc());
			bank.setFloat("path", row, (float) track.get_path_kf());
			bank.setFloat("dEdx", row, (float) track.get_dEdx_kf());
			bank.setFloat("p_drift", row, (float) track.get_p_drift_kf());
			bank.setFloat("chi2", row, (float) track.get_chi2());
			bank.setFloat("sum_residuals", row, (float) track.get_sum_residuals());

			row++;
		}

		return bank;
	}

	public DataBank fillAIPrediction(DataEvent event, ArrayList<TrackPrediction> predictions) {

		DataBank bank = event.createBank("AHDC::ai:prediction", predictions.size());

		int row = 0;

		for (TrackPrediction track : predictions) {
			bank.setFloat("x1", row, (float) track.getSuperpreclusters().get(0).getX());
			bank.setFloat("y1", row, (float) track.getSuperpreclusters().get(0).getY());
			bank.setFloat("x2", row, (float) track.getSuperpreclusters().get(1).getX());
			bank.setFloat("y2", row, (float) track.getSuperpreclusters().get(1).getY());
			bank.setFloat("x3", row, (float) track.getSuperpreclusters().get(2).getX());
			bank.setFloat("y3", row, (float) track.getSuperpreclusters().get(2).getY());
			bank.setFloat("x4", row, (float) track.getSuperpreclusters().get(3).getX());
			bank.setFloat("y4", row, (float) track.getSuperpreclusters().get(3).getY());
			bank.setFloat("x5", row, (float) track.getSuperpreclusters().get(4).getX());
			bank.setFloat("y5", row, (float) track.getSuperpreclusters().get(4).getY());

			bank.setFloat("pred", row, track.getPrediction());
			row++;
		}

		return bank;
	}
}
