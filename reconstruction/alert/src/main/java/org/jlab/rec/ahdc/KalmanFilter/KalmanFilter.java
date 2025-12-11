package org.jlab.rec.ahdc.KalmanFilter;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.clas.pdg.PDGDatabase;
import org.jlab.clas.pdg.PDGParticle;
import org.jlab.clas.tracking.kalmanfilter.Material;
import org.jlab.geom.prim.Point3D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.ahdc.Track.Track;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.Track.KFMonitor;
import java.util.Collections;

import java.util.ArrayList;
import java.util.HashMap;

// masses/energies should be in MeV; distances should be in mm

public class KalmanFilter {

    public KalmanFilter(ArrayList<Track> tracks, DataEvent event, final double magfield, boolean IsMC) {propagation(tracks, event, magfield, IsMC);}

	private final int Niter = 1; // number of iterations of the Kalman Filter
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
			
            //DataBank mcBank = event.getBank("MC::Particle");
			// Load electron vertex
			/*if (event.hasBank("REC::Particle")) {
				DataBank recBank = event.getBank("REC::Particle");
				int row = 0;
				while ((!IsVtxDefined) && row < recBank.rows()) {
					if (recBank.getInt("pid", row) == 11) {
						IsVtxDefined = true;
						vz_constraint = recBank.getFloat("vz",row);
					}
					row++;
				}
			}*/
			
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
			    // using or not mc
                // will be deleted in the final code
                /*if (IsMC) {
                    z0  = mcBank.getFloat("vz", trackId-1)*10;
                    px0 = mcBank.getFloat("px", trackId-1)*1000;
                    py0 = mcBank.getFloat("py", trackId-1)*1000;
                    pz0 = mcBank.getFloat("pz", trackId-1)*1000;
			    }*/	
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
			    RealMatrix initialErrorCovariance = MatrixUtils.createRealMatrix(new double[][]{{1.00, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 1.00, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 25.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 1.00, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 1.00, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 25.0}});
			    KFitter TrackFitter = new KFitter(initialStateEstimate, initialErrorCovariance, stepper, propagator, materialHashMap);
			    TrackFitter.setVertexDefined(IsVtxDefined);
		 	    
			    // KFmonitor: save state and error covariance matrix
			    track.add_KFMonitor(new KFMonitor(trackId, 0, 0, 0, 0, TrackFitter.getStateEstimationVector(), TrackFitter.getErrorCovarianceMatrix()));
                
				// Loop over number of iterations
			    for (int k = 0; k < Niter; k++) {
                    //Reset error covariance:
                    //TrackFitter.ResetErrorCovariance(initialErrorCovariance); // that can be very interesting, to be checked later, this has an effect on the convergence speed
                    
					// Forward propagation
					//System.out.println("==================================> Forward Progation");
					for (Hit hit : AHDC_hits) {
                        TrackFitter.predict(hit, true);
                        track.add_KFMonitor(new KFMonitor(trackId, k, 0, (hit.getSuperLayerId()*10 + hit.getLayerId())*100 + hit.getWireId(), 0, TrackFitter.getStateEstimationVector(), TrackFitter.getErrorCovarianceMatrix()));
						// I don't see the utility of this
						// so I comment it, we will also need to delete the residual_prefit attribut in the AHDC::hits bank
						/*if( k==0  && indicator.hit.getId()>0){ // first iteration and indicator != beamline (because its id is -1)
							indicator.hit.setResidualPrefit(TrackFitter.residual(indicator));
						}*/
						TrackFitter.correct(hit);
						track.add_KFMonitor(new KFMonitor(trackId, k, 0, (hit.getSuperLayerId()*10 + hit.getLayerId())*100 + hit.getWireId(), 1, TrackFitter.getStateEstimationVector(), TrackFitter.getErrorCovarianceMatrix()));
					
                    }
					//System.out.println("==================================> Backward Progation");
					// Backward propagation (last layer to first layer)
					for (int i = AHDC_hits.size() - 2; i >= 0; i--) {
						Hit hit = AHDC_hits.get(i);
						TrackFitter.predict(hit, false);
                        track.add_KFMonitor(new KFMonitor(trackId, k, 1, (hit.getSuperLayerId()*10 + hit.getLayerId())*100 + hit.getWireId(), 0, TrackFitter.getStateEstimationVector(), TrackFitter.getErrorCovarianceMatrix()));
						TrackFitter.correct(hit);
						track.add_KFMonitor(new KFMonitor(trackId, k, 1, (hit.getSuperLayerId()*10 + hit.getLayerId())*100 + hit.getWireId(), 1, TrackFitter.getStateEstimationVector(), TrackFitter.getErrorCovarianceMatrix()));
					}
					// Backward propagation (first layer to beamline)
					{
						Hit hit = new Hit_beam(0, 0, zbeam);
						TrackFitter.predict(hit, false);
                        track.add_KFMonitor(new KFMonitor(trackId, k, 1, (hit.getSuperLayerId()*10 + hit.getLayerId())*100 + hit.getWireId(), 0, TrackFitter.getStateEstimationVector(), TrackFitter.getErrorCovarianceMatrix()));
						TrackFitter.correct(hit);
						track.add_KFMonitor(new KFMonitor(trackId, k, 1, (hit.getSuperLayerId()*10 + hit.getLayerId())*100 + hit.getWireId(), 1, TrackFitter.getStateEstimationVector(), TrackFitter.getErrorCovarianceMatrix()));
					}

                    
			    }

			    
			    RealVector x_out = TrackFitter.getStateEstimationVector();
			    track.setPositionAndMomentumForKF(x_out);
			    //System.out.println("==================================> PostFit Progation");
			    //Residual, path and AHDC exit momentum calculation post fit:
			    KFitter PostFitPropagator = new KFitter(TrackFitter.getStateEstimationVector(), initialErrorCovariance, new Stepper(TrackFitter.getStateEstimationVector().toArray()), new Propagator(RK4), materialHashMap);
			    for (Hit hit : AHDC_hits) {
                    PostFitPropagator.predict(hit, true);
                    track.add_KFMonitor(new KFMonitor(trackId, Niter, 2, (hit.getSuperLayerId()*10 + hit.getLayerId())*100 + hit.getWireId(), 0, TrackFitter.getStateEstimationVector(), TrackFitter.getErrorCovarianceMatrix()));
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
			// e.printStackTrace();
			System.out.println("===> Error");
		}
	}

}
