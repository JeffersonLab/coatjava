package org.jlab.rec.ahdc.KalmanFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.clas.pdg.PDGDatabase;
import org.jlab.clas.pdg.PDGParticle;
import org.jlab.clas.tracking.kalmanfilter.Material;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.Track.Track;

//import org.apache.commons.math3.linear.RealMatrixFormat;

// masses/energies should be in MeV; distances should be in mm

public class KalmanFilter {

    public KalmanFilter(ArrayList<Track> tracks, DataEvent event, final double magfield, boolean IsMC) {propagation(tracks, event, magfield, IsMC);}

	private final int Niter = 60; // number of iterations of the Kalman Filter
	private boolean IsVtxDefined = false;

	private void propagation(ArrayList<Track> tracks, DataEvent event, final double magfield, boolean IsMC) {

		try {
			double vz_constraint = 0; // to be linked to the electron vertex

			// Initialization ---------------------------------------------------------------------
			final PDGParticle proton            = PDGDatabase.getParticleById(2212);
			final int         numberOfVariables = 6;
			final double      tesla             = 0.001;
			final double[]    B                 = {0.0, 0.0, magfield / 10 * tesla};
			HashMap<String, Material> materialHashMap = MaterialMap.generateMaterials();
					
            // Loop over tracks
			int trackId = 0;
			for (Track track : tracks) {
			    trackId++;
			    track.set_trackId(trackId);
			    // Initialize state vector
			    double x0  = 0.0;
			    double y0  = 0.0;
			    double z0  = track.get_Z0();
			    double px0 = track.get_px();
			    double py0 = track.get_py();
			    double pz0 = track.get_pz();

			    double[]     y   = new double[]{x0, y0, z0, px0, py0, pz0};
			    // Read list of hits
			    ArrayList<Hit> AHDC_hits = track.getHits();
				Collections.sort(AHDC_hits); // sorted following the compareTo() method in Hit.java

			    double zbeam = 0;
			    if(IsVtxDefined)zbeam = vz_constraint;
			
			    // Start propagation
			    Stepper     stepper    = new Stepper(y);
			    RungeKutta4 RK4        = new RungeKutta4(proton, numberOfVariables, B);
			    Propagator  propagator = new Propagator(RK4);

			    // Initialization of the Kalman Fitter
				// for the error matrix: first 3 lines in mm^2; last 3 lines in MeV^2
			    RealVector initialStateEstimate   = new ArrayRealVector(stepper.y);
			    RealMatrix initialErrorCovariance = MatrixUtils.createRealMatrix(new double[][]{{50.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 50.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 900.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 100.00, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 100.00, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 900.0}});
				KFitter TrackFitter = new KFitter(initialStateEstimate, initialErrorCovariance, stepper, propagator, materialHashMap);
			    TrackFitter.setVertexDefined(IsVtxDefined);
		 	    
				// Loop over number of iterations
			    for (int k = 0; k < Niter; k++) {
					// Forward propagation
					for (Hit hit : AHDC_hits) {
                        TrackFitter.predict(hit, true);
						TrackFitter.correct(hit);
					
                    }
					// Backward propagation (last layer to first layer)
					for (int i = AHDC_hits.size() - 2; i >= 0; i--) {
						Hit hit = AHDC_hits.get(i);
						TrackFitter.predict(hit, false);
						TrackFitter.correct(hit);
					}
					// Backward propagation (first layer to beamline)
					{
						Hit hit = new Hit_beam(0, 0, zbeam);
						TrackFitter.predict(hit, false);
						TrackFitter.correct(hit);
					}

			    }

				/*RealMatrixFormat format =
					new RealMatrixFormat(
						"[\n", "\n]",     // matrix start/end
						"[", "]",     // row start/end
						",\n",         // column separator
						" ; ",        // row separator
						new java.text.DecimalFormat("0.0000")
					);
				System.out.println("=====> Print error matrix");
				System.out.println(format.format(TrackFitter.getErrorCovarianceMatrix()));*/

			    
			    RealVector x_out = TrackFitter.getStateEstimationVector();
			    track.setPositionAndMomentumForKF(x_out);
			    //System.out.println("==================================> PostFit Progation");
			    //Residual, path and AHDC exit momentum calculation post fit:
			    KFitter PostFitPropagator = new KFitter(TrackFitter.getStateEstimationVector(), initialErrorCovariance, new Stepper(TrackFitter.getStateEstimationVector().toArray()), new Propagator(RK4), materialHashMap);
			    for (Hit hit : AHDC_hits) {
                    PostFitPropagator.predict(hit, true);
					if( hit.getId()>0){ // for the beamline the hit id is 0, so we only look at AHDC hits
						hit.setResidual(PostFitPropagator.residual(hit));
					}
			    }
			    
                // Fill track and hit bank
			    double s = PostFitPropagator.stepper.sTot;
			    double p_drift = PostFitPropagator.stepper.p();
			    int sum_adc = 0;
			    double sum_residuals = 0;
			    double chi2 = 0;
			    for (Hit hit : AHDC_hits) {
                    hit.setTrackId(trackId);
                    sum_adc += hit.getADC();
                    sum_residuals += hit.getResidual();
                    chi2 += Math.pow(hit.getResidual(),2.0);
			    }
			    track.set_sum_adc(sum_adc);
			    track.set_sum_residuals(sum_residuals);
			    track.set_chi2(chi2);
			    track.set_p_drift_kf(p_drift);
			    track.set_dEdx_kf(sum_adc/s);
			    track.set_path_kf(s);
			    track.set_n_hits(AHDC_hits.size());
			}//end of loop on track candidates
		} catch (Exception e) {
			//e.printStackTrace();
			//System.out.println("======> Kalman Filter Error");
		}
	}

}
