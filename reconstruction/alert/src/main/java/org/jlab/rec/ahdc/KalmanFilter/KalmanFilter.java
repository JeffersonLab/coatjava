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

import java.util.ArrayList;
import java.util.HashMap;

/**
 * TODO : - Fix multi hit on the same layer
 *        - Optimize measurement noise and probably use doca as weight
 *        - Fix the wire number (-1)
 *        - Iterate thought multiple tracks per event
 *        - use a flag for simulation
 *        - add target in the map
 *        - move map to initialization engine
 *        - flag for target material
 *        - error px0 use MC !! Bad !! FIX IT FAST
 */
// masses/energies should be in MeV; distances should be in mm

public class KalmanFilter {

    public KalmanFilter(ArrayList<Track> tracks, DataEvent event, final double magfield, boolean IsMC) {propagation(tracks, event, magfield, IsMC);}

	private final int Niter = 10;
	private final boolean IsVtxDefined = false;

	private void propagation(ArrayList<Track> tracks, DataEvent event, final double magfield, boolean IsMC) {

		try {
			double vz_constraint = 0;
			if(IsMC) {//If simulation read MC::Particle Bank ------------------------------------------------
				DataBank bankParticle = event.getBank("MC::Particle");
				double   vxmc         = bankParticle.getFloat("vx", 0)*10;//mm
				double   vymc         = bankParticle.getFloat("vy", 0)*10;//mm
				double   vzmc         = bankParticle.getFloat("vz", 0)*10;//mm
				double   pxmc         = bankParticle.getFloat("px", 0)*1000;//MeV
				double   pymc         = bankParticle.getFloat("py", 0)*1000;//MeV
				double   pzmc         = bankParticle.getFloat("pz", 0)*1000;//MeV
				double p_mc = java.lang.Math.sqrt(pxmc*pxmc+pymc*pymc+pzmc*pzmc);
				//System.out.println("MC track: vz: " + vzmc*10 + " px: " + pxmc*1000 + " py: " + pymc*1000 + " pz: " + pzmc*1000 + "; p = " + p_mc*1000);//convert p to MeV, v to mm
				
				ArrayList<Point3D> sim_hits = new ArrayList<>();
				sim_hits.add(new Point3D(0, 0, vzmc));
				
				DataBank bankMC = event.getBank("MC::True");
				for (int i = 0; i < bankMC.rows(); i++) {
					if (bankMC.getInt("pid", i) == 2212) {
						float x = bankMC.getFloat("avgX", i);
						float y = bankMC.getFloat("avgY", i);
						float z = bankMC.getFloat("avgZ", i);
						// System.out.println("r_sim = " + Math.hypot(x, y));
						sim_hits.add(new Point3D(x, y, z));
					}
				}
				vz_constraint = vzmc;
			}

			// Initialization ---------------------------------------------------------------------
			final PDGParticle proton            = PDGDatabase.getParticleById(2212);
			final int         numberOfVariables = 6;
			final double      tesla             = 0.001;
			final double[]    B                 = {0.0, 0.0, magfield / 10 * tesla};

			// Initialization material map
			HashMap<String, Material> materialHashMap = materialGeneration();
			int trackId = 0;
			for (Track track : tracks) {
			    trackId++;
			    track.set_trackId(trackId);
			    // Initialization State Vector
			    final double x0  = 0.0;
			    final double y0  = 0.0;
			    final double z0  = track.get_Z0();
			    //final
			    double px0 = track.get_px();
			    //final
			    double py0 = track.get_py();
			    final double pz0 = track.get_pz();
			    //final double p_init = java.lang.Math.sqrt(px0*px0+py0*py0+pz0*pz0);
			    double[]     y   = new double[]{x0, y0, z0, px0, py0, pz0};
			    // Initialization hit
			    ArrayList<org.jlab.rec.ahdc.Hit.Hit> AHDC_hits = track.getHits();
			    ArrayList<Hit>                       KF_hits   = new ArrayList<>();
			    double ADCTot = 0;
			    //System.out.println(" px " +  y[3] + " py " + y[4]  +" pz " +  y[5] +" vz " + y[2] + " number of hits: " + AHDC_hits.size() + " MC hits? " + sim_hits.size());
			    track.set_n_hits(AHDC_hits.size());
			    for (org.jlab.rec.ahdc.Hit.Hit AHDC_hit : AHDC_hits) {
				Hit hit = new Hit(AHDC_hit.getSuperLayerId(), AHDC_hit.getLayerId(), AHDC_hit.getWireId(), AHDC_hit.getNbOfWires(), AHDC_hit.getLine(), AHDC_hit.getDoca());
				hit.setADC(AHDC_hit.getADC());
				hit.setHitIdx(AHDC_hit.getId());
				hit.setSign(0);
				ADCTot+=AHDC_hit.getADC();
				// set track id
				AHDC_hit.setTrackId(trackId);
				
				//System.out.println( " r = " + hit.r() + " hit.phi " + hit.phi() +" hit.doca = " + hit.getDoca()  );
				// Do delete hit with same radius
				boolean phi_rollover = false;
				boolean aleardyHaveR = false;
				for (Hit o: KF_hits){
				    if (o.r() == hit.r()){
					aleardyHaveR = true;
					// //sign+ means (phi track - phi wire) > 0
					//    if(o.phi()>hit.phi()){
					// 	if(Math.abs(o.phi()-hit.phi())< 2*Math.toRadians(360./o.getNumWires()) ){
					// 	    o.setSign(-1);
					// 	    hit.setSign(+1);
					// 	}else{
					// 	    phi_rollover = true;
					// 	    hit.setSign(-1);
					// 	    o.setSign(+1);
					// 	}
					//    }else{
					// 	if(Math.abs(o.phi()-hit.phi())< 2*Math.toRadians(360./o.getNumWires()) ){
					// 	    hit.setSign(-1);
					// 	    o.setSign(+1);
					// 	}else{
					// 	    phi_rollover = true;
					// 	    o.setSign(-1);
					//            hit.setSign(+1);
					// 	}
					//    }
					//    //System.out.println( " r = " + o.r() + " o.phi = " + o.phi() + " o.doca = " + o.getDoca()*o.getSign() + " hit.phi " + hit.phi() +" hit.doca = " + hit.getDoca()*hit.getSign() + " angle between wires: " + Math.toRadians(360./hit.getNumWires()) + " >= ? angle covered by docas: " +  Math.atan( (o.getDoca()+hit.getDoca())/o.r() )  );
				    }
				}
				if(!aleardyHaveR)KF_hits.add(hit);
				// if (phi_rollover){
				//      KF_hits.add(KF_hits.size()-1, hit);
				// }else{
				//     KF_hits.add(hit);
				// }
			    }

			    double zbeam = 0;
			    if(IsVtxDefined)zbeam = vz_constraint;//test
			    final ArrayList<Indicator> forwardIndicators  = forwardIndicators(KF_hits, materialHashMap);
			    final ArrayList<Indicator> backwardIndicators = backwardIndicators(KF_hits, materialHashMap, zbeam);
			
			    // Start propagation
			    Stepper     stepper    = new Stepper(y);
			    RungeKutta4 RK4        = new RungeKutta4(proton, numberOfVariables, B);
			    Propagator  propagator = new Propagator(RK4);

			    // ----------------------------------------------------------------------------------------

			    // Initialization of the Kalman Fitter
			    RealVector initialStateEstimate   = new ArrayRealVector(stepper.y);
			    //first 3 lines in cm^2; last 3 lines in MeV^2
			    RealMatrix initialErrorCovariance = MatrixUtils.createRealMatrix(new double[][]{{1.00, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 1.00, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 25.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 1.00, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 1.00, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 25.0}});
			    KFitter TrackFitter = new KFitter(initialStateEstimate, initialErrorCovariance, stepper, propagator);
			    TrackFitter.setVertexDefined(IsVtxDefined);
		 
			    for (int k = 0; k < Niter; k++) {
				//System.out.println("--------- ForWard propagation !! ---------");
				//Reset error covariance:
				//TrackFitter.ResetErrorCovariance(initialErrorCovariance);
				for (Indicator indicator : forwardIndicators) {
				    TrackFitter.predict(indicator);
				    if (indicator.haveAHit()) {
					if( k==0  && indicator.hit.getHitIdx()>0){
					    for (org.jlab.rec.ahdc.Hit.Hit AHDC_hit : AHDC_hits){
						if(AHDC_hit.getId()==indicator.hit.getHitIdx())AHDC_hit.setResidualPrefit(TrackFitter.residual(indicator));
					    }
					}
					TrackFitter.correct(indicator);
				    }
				}

				//System.out.println("--------- BackWard propagation !! ---------");
				for (Indicator indicator : backwardIndicators) {
				    TrackFitter.predict(indicator);
				    if (indicator.haveAHit()) {
					TrackFitter.correct(indicator);
				    }
				}
			    }

			    
			    RealVector x_out = TrackFitter.getStateEstimationVector();
			    track.setPositionAndMomentumForKF(x_out);
			    
			    //Residual, path and AHDC exit momentum calculation post fit:
			    KFitter PostFitPropagator = new KFitter(TrackFitter.getStateEstimationVector(), initialErrorCovariance, new Stepper(TrackFitter.getStateEstimationVector().toArray()), new Propagator(RK4));
			    for (Indicator indicator : forwardIndicators) {
				PostFitPropagator.predict(indicator);
				if (indicator.haveAHit()) {
				    if( indicator.hit.getHitIdx()>0){
					for (org.jlab.rec.ahdc.Hit.Hit AHDC_hit : AHDC_hits){
					    if(AHDC_hit.getId()==indicator.hit.getHitIdx())AHDC_hit.setResidual(PostFitPropagator.residual(indicator));
					}
				    }
				}
			    }
			    
			    double s = PostFitPropagator.stepper.sTot;
			    double dEdx = ADCTot / s;
			    double p_drift = PostFitPropagator.stepper.p();
			    
			    // At this stage, all relevants AHDC hits are filled
			    // Compute sum_adc, sum_residuals and chi2
			    int sum_adc = 0;
			    double sum_residuals = 0;
			    double chi2 = 0;
			    for (org.jlab.rec.ahdc.Hit.Hit AHDC_hit : AHDC_hits) {
				sum_adc += AHDC_hit.getADC();
				sum_residuals += AHDC_hit.getResidual();
				chi2 += Math.pow(AHDC_hit.getResidual(),2.0);
			    }
			    track.set_sum_adc(sum_adc);
			    track.set_sum_residuals(sum_residuals);
			    track.set_chi2(chi2);
			    track.set_p_drift_kf(p_drift);
			    track.set_dEdx_kf(dEdx);
			    track.set_path_kf(s);
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
			forwardIndicators.add(new Indicator(hit.r(), 0.1, hit, true, materialHashMap.get("BONuS12Gas")));
		}
		return forwardIndicators;
	}

	ArrayList<Indicator> backwardIndicators(ArrayList<Hit> hitArrayList, HashMap<String, org.jlab.clas.tracking.kalmanfilter.Material> materialHashMap) {
		ArrayList<Indicator> backwardIndicators = new ArrayList<>();
		//R, h, defined in mm!
		for (int i = hitArrayList.size() - 2; i >= 0; i--) {
			backwardIndicators.add(new Indicator(hitArrayList.get(i).r(), 0.1, hitArrayList.get(i), false, materialHashMap.get("BONuS12Gas")));
		}
		backwardIndicators.add(new Indicator(3.060, 1, null, false, materialHashMap.get("BONuS12Gas")));
		backwardIndicators.add(new Indicator(3.0, 0.001, null, false, materialHashMap.get("Kapton")));
		Hit hit = new Hit_beam(0, 0, 0, 0, 0, 0, 0, 0);
		backwardIndicators.add(new Indicator(0.0, 0.2, hit, false, materialHashMap.get("deuteriumGas")));
		return backwardIndicators;
	}

	ArrayList<Indicator> backwardIndicators(ArrayList<Hit> hitArrayList, HashMap<String, org.jlab.clas.tracking.kalmanfilter.Material> materialHashMap, double vz) {
		ArrayList<Indicator> backwardIndicators = new ArrayList<>();
		//R, h, defined in mm!
		for (int i = hitArrayList.size() - 2; i >= 0; i--) {
			backwardIndicators.add(new Indicator(hitArrayList.get(i).r(), 0.1, hitArrayList.get(i), false, materialHashMap.get("BONuS12Gas")));
		}
		backwardIndicators.add(new Indicator(3.060, 1, null, false, materialHashMap.get("BONuS12Gas")));
		backwardIndicators.add(new Indicator(3.0, 0.001, null, false, materialHashMap.get("Kapton")));
		Hit hit = new Hit_beam(0, 0, 0, 0, 0, 0, 0, vz);
		backwardIndicators.add(new Indicator(0.0, 0.2, hit, false, materialHashMap.get("deuteriumGas")));
		return backwardIndicators;
	}
}
