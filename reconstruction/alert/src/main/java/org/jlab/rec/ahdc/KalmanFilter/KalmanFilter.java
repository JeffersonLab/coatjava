package org.jlab.rec.ahdc.KalmanFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.clas.pdg.PDGDatabase;
import org.jlab.clas.pdg.PDGParticle;
import org.jlab.clas.tracking.kalmanfilter.Material;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.Track.Track;

import org.apache.commons.math3.linear.RealMatrixFormat;

/**
 * This is the main routine of the Kalman Filter. The fit is done by a KFitter
 * 
 * masses/energies should be in MeV; distances should be in mm
 * 
 * @author Mathieu Ouillon
 * @author Éric Fuchey 
 * @author Felix Touchte Codjo
 */
public class KalmanFilter {

	public KalmanFilter(PDGParticle particle, int Niter) {this.particle = particle; this.Niter = Niter;}

    public KalmanFilter(ArrayList<Track> tracks, DataEvent event, final double magfield, boolean IsMC) {propagation(tracks, event, magfield, IsMC);}

	private PDGParticle particle;
	private int Niter = 40; // number of iterations for the Kalman Filter
	private boolean IsVtxDefined = false; // implemented but not used yet
	private double[] vertex_resolutions = {0.09, 1e10}; //  {error in r squared in mm^2, error in z squared in mm^2}
	// mm,  CLAS and AHDC don't necessary have the same alignement (ZERO), this parameter may be subject to calibration
	private double clas_alignement = -54;
	double vz_constraint = 0;
	private int counter = 0; // number of utilisation of the Kalman Filter
	RealMatrixFormat format =
					new RealMatrixFormat(
						"[\n", "\n]",     // matrix start/end
						"[", "]",     // row start/end
						",\n",         // column separator
						" ; ",        // row separator
						new java.text.DecimalFormat(" 0.0000;-0.0000")
					);
	HashMap<Integer, Hit_beam> ATOF_hits = null;

	public void propagation(ArrayList<Track> tracks, DataEvent event, final double magfield, boolean IsMC) {

		try {
			counter++;

			// Initialization ---------------------------------------------------------------------
			final int         numberOfVariables = 6;
			final double      tesla             = 0.001;
			final double[]    B                 = {0.0, 0.0, magfield / 10 * tesla};
			HashMap<String, Material> materialHashMap = MaterialMap.generateMaterials();
			// Recover the vertex of the electron
			if (event.hasBank("REC::Particle") && !IsVtxDefined) { // as we run the KF several times, !IsVtxDefined prevent to look for the vertex again
				DataBank recBank = event.getBank("REC::Particle");
				int row = 0;
				while ((!IsVtxDefined) && row < recBank.rows()) {
					if (recBank.getInt("pid", row) == 11) {
						IsVtxDefined = true;
						vz_constraint = 10*recBank.getFloat("vz",row) - (IsMC ? 0 : clas_alignement); // mm
						////////////////////////////////////////
						/// compute electron resolution here
						/// it depends en p and theta
						/// the fine tuning will be done later
						/// ////////////////////////////////////
						//double px = recBank.getFloat("px",row);
						//double py = recBank.getFloat("py",row);
						//double pz = recBank.getFloat("pz",row);
						//double p = Math.sqrt(px*px+py*py+pz*pz);
						//double theta = Math.acos(pz/p);
						vertex_resolutions[0] = 0.09;
						vertex_resolutions[1] = 64;//4 + 1e10*theta + 1e10*p;
					}
					row++;
				}
			}
					
            // Loop over tracks
			for (Track track : tracks) {
			    // Initialize state vector
			    double x0  = 0.0;
			    double y0  = 0.0;
			    double z0  = (IsVtxDefined && counter < 2) ? vz_constraint : track.get_Z0();
			    double px0 = track.get_px();
			    double py0 = track.get_py();
			    double pz0 = track.get_pz();

			    double[]     y   = new double[]{x0, y0, z0, px0, py0, pz0};
			    // Read list of hits
			    ArrayList<Hit> AHDC_hits = track.getHits();
				Collections.sort(AHDC_hits); // sorted following the compareTo() method in Hit.java
			
			    // Start propagation
			    Stepper     stepper    = new Stepper(y);
			    RungeKutta4 RK4        = new RungeKutta4(particle, numberOfVariables, B);
			    Propagator  propagator = new Propagator(RK4);

			    // Initialization of the Kalman Fitter
				// for the error matrix: first 3 lines in mm^2; last 3 lines in MeV^2
			    RealVector initialStateEstimate   = new ArrayRealVector(stepper.y);
			    //RealMatrix initialErrorCovariance = MatrixUtils.createRealMatrix(new double[][]{{50.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 50.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 900.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 100.00, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 100.00, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 900.0}});
				// System.out.println(">>>>>>>>>>  Error matrix: start (" + counter + ")\n" + format.format(track.getErrorCovarianceMatrix()));
				RealMatrix initialErrorCovariance = track.getErrorCovarianceMatrix();
				KFitter TrackFitter = new KFitter(initialStateEstimate, initialErrorCovariance, stepper, propagator, materialHashMap);
				if (IsVtxDefined) TrackFitter.setVertexResolution(vertex_resolutions);
		 	    
				// Loop over number of iterations
			    for (int k = 0; k < Niter; k++) {
					// Forward propagation
					for (Hit hit : AHDC_hits) {
                        TrackFitter.predict(hit, true);
						TrackFitter.correct(hit);
					
                    }
					// Forward propagation towards the ATOF bar
					{	
						Hit_beam hit = ATOF_hits.get(track.get_trackId());
						if (hit != null) {
							TrackFitter.predict(hit, true);
							TrackFitter.correct(hit);
							// Backward propagation to the last ahdc layer
							Hit hhit = AHDC_hits.get(AHDC_hits.size()-1);
							TrackFitter.predict(hhit, false);
							TrackFitter.correct(hhit);
							//System.out.println("trackid : " + track.get_trackId() + " hit : " + format.format(hit.get_MeasurementNoise()));
						} else {
							//System.out.println("************** No match for this track");
						}
					}
					// Backward propagation (last layer to first layer)
					for (int i = AHDC_hits.size() - 2; i >= 0; i--) {
						Hit hit = AHDC_hits.get(i);
						TrackFitter.predict(hit, false);
						TrackFitter.correct(hit);
					}
					// Backward propagation (first layer to beamline)
					{
						Hit_beam hit = new Hit_beam(0, 0, vz_constraint);
						RealMatrix measurementNoise = new Array2DRowRealMatrix(
                                                        new double[][]{
                                                            {vertex_resolutions[0], 0.0000, 0.0000},
                                                            {0.00, 1e10, 0.0000},
                                                            {0.00, 0.0000, vertex_resolutions[1]}
                                                        });//3x3;
                        hit.set_MeasurementNoise(measurementNoise);
						TrackFitter.predict(hit, false);
						TrackFitter.correct(hit);
					}

			    }

			    RealVector x_out = TrackFitter.getStateEstimationVector();
			    track.setPositionAndMomentumVec(x_out.toArray());
				track.setErrorCovarianceMatrix(TrackFitter.getErrorCovarianceMatrix());
				// System.out.println(">>>>>>>>>>  Error matrix: end (" + counter + ")\n" + format.format(track.getErrorCovarianceMatrix()));
				// System.out.println("> vz : " + track.get_Z0());

			    // Post fit propagation (no correction) to set the residuals
			    KFitter PostFitPropagator = new KFitter(TrackFitter.getStateEstimationVector(), initialErrorCovariance, new Stepper(TrackFitter.getStateEstimationVector().toArray()), new Propagator(RK4), materialHashMap);
			    for (Hit hit : AHDC_hits) {
                    PostFitPropagator.predict(hit, true);
					if( hit.getId()>0){ // for the beamline the hit id is 0, so we only look at AHDC hits
						hit.setResidual(PostFitPropagator.residual(hit));
					}
			    }
			    
                // Fill track and hit bank
				// TO DO : s and p_drift have to be checked to be sure they represent what we want
			    double s = PostFitPropagator.stepper.sTot;
			    double p_drift = PostFitPropagator.stepper.p();
			    int sum_adc = 0;
			    double sum_residuals = 0;
			    double chi2 = 0;
			    for (Hit hit : AHDC_hits) {
                    sum_adc += hit.getADC();
                    sum_residuals += hit.getResidual();
                    chi2 += Math.pow(hit.getResidual(),2)/hit.get_MeasurementNoise().getEntry(0,0);
			    }
			    track.set_sum_adc(sum_adc);
			    track.set_sum_residuals(sum_residuals);
			    track.set_chi2(chi2/(AHDC_hits.size()-3));
			    track.set_p_drift(p_drift);
			    track.set_dEdx(sum_adc/s);
			    track.set_path(s);
			    track.set_n_hits(AHDC_hits.size());
			}//end of loop on track candidates
		} catch (Exception e) {
			//e.printStackTrace();
			//System.out.println("======> Kalman Filter Error");
		}
	}
	public void set_Niter(int Niter) {this.Niter = Niter;}
	public int get_Niter() {return this.Niter;}
	public void set_particle(PDGParticle particle) {this.particle = particle;}
	public PDGParticle get_particle() {return this.particle;}
	public void set_ATOF_hits(HashMap<Integer, Hit_beam> ATOF_hits){ this.ATOF_hits = ATOF_hits;};
	public HashMap<Integer, Hit_beam> get_ATOF_hits() { return this.ATOF_hits;}
}
