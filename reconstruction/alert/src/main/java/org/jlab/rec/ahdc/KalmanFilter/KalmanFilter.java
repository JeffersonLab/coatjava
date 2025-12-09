package org.jlab.rec.ahdc.KalmanFilter;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.clas.pdg.PDGDatabase;
import org.jlab.clas.pdg.PDGParticle;
import org.jlab.clas.tracking.kalmanfilter.Material;
import org.jlab.clas.tracking.kalmanfilter.Units;
import org.jlab.geom.prim.Point3D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.ahdc.Track.Track;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.Track.KFMonitor;
import java.util.Collections;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * TODO : - Fix multi hit on the same layer
 *        - Optimize measurement noise and probably use doca as weight
 *        - Fix the wire number (-1)
 *        - use a flag for simulation
 *        - add target in the map
 *        - move map to initialization engine
 *        - flag for target material
 *        - error px0 use MC !! Bad !! FIX IT FAST
 */
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

			// Initialization material map
			HashMap<String, Material> materialHashMap = materialGeneration();
            // Loop over tracks
            // Each track candidate form the HelixFitter is independently processed by the Kalman Filter
			int trackId = 0;
			for (Track track : tracks) {
			    trackId++;
			    track.set_trackId(trackId);
			    // Initialization State Vector
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
			    // Initialization hit
			    ArrayList<Hit> AHDC_hits = track.getHits();
				/*System.out.println("*** Before sorting ***");
				for (Hit hit : AHDC_hits) {
					System.out.println(hit);
				}*/
				/*System.out.println("*** Set Random position ***");
				Collections.shuffle(AHDC_hits);
				for (Hit hit : AHDC_hits) {
					System.out.println(hit);
				}*/
				Collections.sort(AHDC_hits); // sorted following the compareTo() method in Hit.java
				/*System.out.println("*** After sorting ***");
				for (Hit hit : AHDC_hits) {
					System.out.println(hit);
				}*/

			    double zbeam = 0;
			    if(IsVtxDefined)zbeam = vz_constraint;
                // Define forward and backward indicator
                // cf. Indicator.java
			    final ArrayList<Indicator> forwardIndicators  = forwardIndicators(AHDC_hits, materialHashMap);
			    final ArrayList<Indicator> backwardIndicators = backwardIndicators(AHDC_hits, materialHashMap, zbeam);
			
			    // Start propagation
			    Stepper     stepper    = new Stepper(y);
			    RungeKutta4 RK4        = new RungeKutta4(proton, numberOfVariables, B);
			    Propagator  propagator = new Propagator(RK4);

			    // ----------------------------------------------------------------------------------------

			    // Initialization of the Kalman Fitter
			    RealVector initialStateEstimate   = new ArrayRealVector(stepper.y);
			    //first 3 lines in mm^2; last 3 lines in MeV^2
			    RealMatrix initialErrorCovariance = MatrixUtils.createRealMatrix(new double[][]{{1.00, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 1.00, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 25.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 1.00, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 1.00, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 25.0}});
			    KFitter TrackFitter = new KFitter(initialStateEstimate, initialErrorCovariance, stepper, propagator);
			    TrackFitter.setVertexDefined(IsVtxDefined);
		 	    
			    // KFmonitor: save initial state and error covariance matrix
			    track.add_KFMonitor(new KFMonitor(trackId, 0, 0, 0, 0, TrackFitter.getStateEstimationVector(), TrackFitter.getErrorCovarianceMatrix()));
                // Loop over number of iterations
			    for (int k = 0; k < Niter; k++) {
                    //System.out.println("Niter:" + k + "   --------- ForWard propagation !! ---------");
                    //Reset error covariance:
                    //TrackFitter.ResetErrorCovariance(initialErrorCovariance); // that can be very interesting, to be checked later, this has an effect on the convergence speed
                    for (Indicator indicator : forwardIndicators) {
                        // Prediction
                        TrackFitter.predict(indicator);
                        // KFMonitor: save state and error covariance matrix
                        track.add_KFMonitor(new KFMonitor(trackId, k, 0, indicator.getUniqueId(), 0, TrackFitter.getStateEstimationVector(), TrackFitter.getErrorCovarianceMatrix()));
                        if (indicator.haveAHit()) {
                            // I don't see the utility of this
                            if( k==0  && indicator.hit.getId()>0){ // first iteration and indicator != beamline (because its id is -1)
                                indicator.hit.setResidualPrefit(TrackFitter.residual(indicator));
                            }
                            // Correction only if we have a measure (hit)
                            TrackFitter.correct(indicator);
                            // KFMonitor: save state and error covariance matrix
                            track.add_KFMonitor(new KFMonitor(trackId, k, 0, indicator.getUniqueId(), 1, TrackFitter.getStateEstimationVector(), TrackFitter.getErrorCovarianceMatrix()));
                        }
                    }

                    //System.out.println("Niter:" + k + "--------- BackWard propagation !! ---------");
                    for (Indicator indicator : backwardIndicators) {
                        TrackFitter.predict(indicator);
                        // KFMonitor: save state and error covariance matrix
                        track.add_KFMonitor(new KFMonitor(trackId, k, 1, indicator.getUniqueId(), 0, TrackFitter.getStateEstimationVector(), TrackFitter.getErrorCovarianceMatrix()));
                        if (indicator.haveAHit()) {
                            TrackFitter.correct(indicator);
                            // KFMonitor: save state and error covariance matrix
                            track.add_KFMonitor(new KFMonitor(trackId, k, 1, indicator.getUniqueId(), 1, TrackFitter.getStateEstimationVector(), TrackFitter.getErrorCovarianceMatrix()));
                        }
                    }
			    }

			    
			    RealVector x_out = TrackFitter.getStateEstimationVector();
			    track.setPositionAndMomentumForKF(x_out);
			    
			    //Residual, path and AHDC exit momentum calculation post fit:
			    KFitter PostFitPropagator = new KFitter(TrackFitter.getStateEstimationVector(), initialErrorCovariance, new Stepper(TrackFitter.getStateEstimationVector().toArray()), new Propagator(RK4));
			    for (Indicator indicator : forwardIndicators) {
                    PostFitPropagator.predict(indicator);
                    // KFMonitor: save state and error covariance matrix
                    track.add_KFMonitor(new KFMonitor(trackId, Niter, 2, indicator.getUniqueId(), 0, TrackFitter.getStateEstimationVector(), TrackFitter.getErrorCovarianceMatrix()));
                    if (indicator.haveAHit()) {
                        if( indicator.hit.getId()>0){
                            indicator.hit.setResidual(PostFitPropagator.residual(indicator));
                        }
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
		}
	}

	private HashMap<String, Material> materialGeneration() {
		Units units = Units.CM;

		String name_De      = "deuteriumGas";
		double thickness_De = 1;
		double density_De   = 9.37E-4;// 5.5 atm
		double ZoverA_De    = 0.496499;
		double X0_De        = 1.3445E+5; // I guess X0 is not even used???
		double IeV_De       = 19.2;

		org.jlab.clas.tracking.kalmanfilter.Material deuteriumGas = new org.jlab.clas.tracking.kalmanfilter.Material(name_De, thickness_De, density_De, ZoverA_De, X0_De, IeV_De, units);

		String name_Bo      = "BONuS12Gas";//80% He, 20% CO2
		double thickness_Bo = 1;
		double density_Bo   = 1.39735E-3;
		double ZoverA_Bo    = 0.49983;
		double X0_Bo        = 3.69401E+4;
		double IeV_Bo       = 73.5338;

		org.jlab.clas.tracking.kalmanfilter.Material BONuS12 = new org.jlab.clas.tracking.kalmanfilter.Material(name_Bo, thickness_Bo, density_Bo, ZoverA_Bo, X0_Bo, IeV_Bo, units);

		String name_My      = "Mylar";
		double thickness_My = 1;
		double density_My   = 1.4;
		double ZoverA_My    = 0.52037;
		double X0_My        = 28.54;
		double IeV_My       = 78.7;

		org.jlab.clas.tracking.kalmanfilter.Material Mylar = new org.jlab.clas.tracking.kalmanfilter.Material(name_My, thickness_My, density_My, ZoverA_My, X0_My, IeV_My, units);

		String name_Ka      = "Kapton";
		double thickness_Ka = 1;
		double density_Ka   = 1.42;
		double ZoverA_Ka    = 0.51264;
		double X0_Ka        = 28.57;
		double IeV_Ka       = 79.6;

		org.jlab.clas.tracking.kalmanfilter.Material Kapton = new org.jlab.clas.tracking.kalmanfilter.Material(name_Ka, thickness_Ka, density_Ka, ZoverA_Ka, X0_Ka, IeV_Ka, units);

		return new HashMap<String, Material>() {
			{
				put("deuteriumGas", deuteriumGas);
				put("Kapton", Kapton);
				put("Mylar", Mylar);
				put("BONuS12Gas", BONuS12);
			}
		};
	}

	ArrayList<Indicator> forwardIndicators(ArrayList<Hit> hitArrayList, HashMap<String, org.jlab.clas.tracking.kalmanfilter.Material> materialHashMap) {
		ArrayList<Indicator> forwardIndicators = new ArrayList<>();
		//R, h, defined in mm!
		forwardIndicators.add(new Indicator(3.0, 0.2, null, true, materialHashMap.get("deuteriumGas")));
		forwardIndicators.add(new Indicator(3.060, 0.001, null, true, materialHashMap.get("Kapton")));
		for (Hit hit : hitArrayList) {
			forwardIndicators.add(new Indicator(hit.getRadius(), 0.1, hit, true, materialHashMap.get("BONuS12Gas")));
		}
		return forwardIndicators;
	}

	ArrayList<Indicator> backwardIndicators(ArrayList<Hit> hitArrayList, HashMap<String, org.jlab.clas.tracking.kalmanfilter.Material> materialHashMap) {
		ArrayList<Indicator> backwardIndicators = new ArrayList<>();
		//R, h, defined in mm!
		for (int i = hitArrayList.size() - 2; i >= 0; i--) {
			backwardIndicators.add(new Indicator(hitArrayList.get(i).getRadius(), 0.1, hitArrayList.get(i), false, materialHashMap.get("BONuS12Gas")));
		}
		backwardIndicators.add(new Indicator(3.060, 1, null, false, materialHashMap.get("BONuS12Gas")));
		backwardIndicators.add(new Indicator(3.0, 0.001, null, false, materialHashMap.get("Kapton")));
		Hit hit = new Hit_beam(0, 0, 0);
		backwardIndicators.add(new Indicator(0.0, 0.2, hit, false, materialHashMap.get("deuteriumGas")));
		return backwardIndicators;
	}

	ArrayList<Indicator> backwardIndicators(ArrayList<Hit> hitArrayList, HashMap<String, org.jlab.clas.tracking.kalmanfilter.Material> materialHashMap, double vz) {
		ArrayList<Indicator> backwardIndicators = new ArrayList<>();
		//R, h, defined in mm!
		for (int i = hitArrayList.size() - 2; i >= 0; i--) {
			backwardIndicators.add(new Indicator(hitArrayList.get(i).getRadius(), 0.1, hitArrayList.get(i), false, materialHashMap.get("BONuS12Gas")));
		}
		backwardIndicators.add(new Indicator(3.060, 1, null, false, materialHashMap.get("BONuS12Gas")));
		backwardIndicators.add(new Indicator(3.0, 0.001, null, false, materialHashMap.get("Kapton")));
		Hit hit = new Hit_beam(0, 0, vz);
		backwardIndicators.add(new Indicator(0.0, 0.2, hit, false, materialHashMap.get("deuteriumGas")));
		return backwardIndicators;
	}
}
