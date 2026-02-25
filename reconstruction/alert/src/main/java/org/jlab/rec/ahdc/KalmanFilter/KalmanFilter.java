package org.jlab.rec.ahdc.KalmanFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.clas.pdg.PDGParticle;
import org.jlab.clas.tracking.kalmanfilter.Material;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.Track.Track;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.base.Component;
import org.jlab.geom.detector.alert.ATOF.AlertTOFDetector;
import org.jlab.geom.detector.alert.ATOF.AlertTOFFactory;
import org.jlab.geom.prim.Point3D;
import java.util.Random;


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

	private PDGParticle particle;
	private int Niter = 40; // number of iterations for the Kalman Filter
	private boolean IsVtxDefined = false; // implemented but not used yet
	private double[] vertex_resolutions = {0.09, 1e10}; //  {error in r squared in mm^2, error in z squared in mm^2}
	// mm,  they the misalignement with respect to the AHDC
	private double clas_alignement = +54;
	private double atof_alignement = -32.7;
	double vz_constraint = 0;
	private int counter = 0; // number of utilisation of the Kalman Filter
	HashMap<Integer, RadialKFHit> ATOF_hits = null;
	HashMap<Integer, ArrayList<int[]>> ATOF_hits_predicted = new HashMap<>();
	//ArrayList<int[]> ATOF_hits_predicted = new ArrayList<>(); // list of sector, layer, component (for now : only wedges)
	AlertTOFDetector ATOFdet = null;

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
						vz_constraint = 10*recBank.getFloat("vz",row) + (IsMC ? 0 : clas_alignement); // mm
						
						// TO DO: compute electron resolution as function of p and theta
						// the fine tuning will be done later
						//double px = recBank.getFloat("px",row);
						//double py = recBank.getFloat("py",row);
						//double pz = recBank.getFloat("pz",row);
						//double p = Math.sqrt(px*px+py*py+pz*pz);
						//double theta = Math.acos(pz/p);

						vertex_resolutions[0] = 0.09;
						vertex_resolutions[1] = 64;
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
						RadialKFHit hit = ATOF_hits.get(track.get_trackId());
						if (hit != null) {
							TrackFitter.predict(hit, true);
							TrackFitter.correct(hit);
							// Backward propagation to the last ahdc layer
							Hit hhit = AHDC_hits.get(AHDC_hits.size()-1);
							TrackFitter.predict(hhit, false);
							TrackFitter.correct(hhit);
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
						RadialKFHit hit = new RadialKFHit(0, 0, vz_constraint);
						RealMatrix measurementNoise = new Array2DRowRealMatrix(
                                                        new double[][]{
                                                            {vertex_resolutions[0], 0.0000, 0.0000},
                                                            {0.00, 1e10, 0.0000},
                                                            {0.00, 0.0000, vertex_resolutions[1]}
                                                        });//3x3;
                        hit.setMeasurementNoise(measurementNoise);
						TrackFitter.predict(hit, false);
						TrackFitter.correct(hit);
					}

			    }

			    RealVector x_out = TrackFitter.getStateEstimationVector();
			    track.setPositionAndMomentumVec(x_out.toArray());
				track.setErrorCovarianceMatrix(TrackFitter.getErrorCovarianceMatrix());

			    // Post fit propagation (no correction) to set the residuals
			    KFitter PostFitPropagator = new KFitter(TrackFitter.getStateEstimationVector(), initialErrorCovariance, new Stepper(TrackFitter.getStateEstimationVector().toArray()), new Propagator(RK4), materialHashMap);
				// Projection towards AHDC hits
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
                    chi2 += Math.pow(hit.getResidual(),2)/hit.MeasurementNoiseMatrix().getEntry(0,0);
			    }
			    track.set_sum_adc(sum_adc);
			    track.set_sum_residuals(sum_residuals);
			    track.set_chi2(chi2/(AHDC_hits.size()-3));
			    track.set_p_drift(p_drift);
			    track.set_dEdx(sum_adc/s);
			    track.set_path(s);
			    track.set_n_hits(AHDC_hits.size());

				// Projection towards the ATOF surfaces
				// R1 : lower surface of an ATOF bar
				// R2 : upper surface of an ATOF bar = lower surface of an ATOF wedge
				// R3 : upper surface of an ATOF wedge
				Point3D pR1 = ATOFdet.getSector(0).getSuperlayer(0).getLayer(0).getComponent(10).getVolumePoint(0);
				Point3D pR2 = ATOFdet.getSector(0).getSuperlayer(0).getLayer(0).getComponent(10).getVolumePoint(2);
				Point3D pR3 = ATOFdet.getSector(0).getSuperlayer(1).getLayer(0).getComponent(0).getVolumePoint(2);
				double R1 = Math.hypot(pR1.x(), pR1.y());
				double R2 = Math.hypot(pR2.x(), pR2.y());
				double R3 = Math.hypot(pR3.x(), pR3.y());
				{	
					// From last AHDC hit to surface R1
					RadialSurfaceKFHit hitR1 = new RadialSurfaceKFHit(R1);
					PostFitPropagator.predict(hitR1, true);
					double[] vecR1 = PostFitPropagator.getStateEstimationVector().toArray();
					Point3D posR1 = new Point3D(vecR1[0], vecR1[1], vecR1[2]);
					posR1.translateXYZ(0, 0, atof_alignement);
					int[] idR1 = predict_bar(ATOFdet, posR1);
					System.out.println("ATOF surface R1 : " + R1);
					System.out.printf ("   final position : x (%.2f) y (%.2f) z (%.2f) --> R (%.2f)\n", posR1.x(), posR1.y(), posR1.z(), Math.hypot(posR1.x(), posR1.y()));
					System.out.printf ("   ---> sector (%2d) layer (%2d) component (%2d)\n", idR1[0], idR1[1], idR1[2]);
					// From surface R1 to surface R2
					RadialSurfaceKFHit hitR2 = new RadialSurfaceKFHit(R2);
					PostFitPropagator.predict(hitR2, true);
					double[] vecR2 = PostFitPropagator.getStateEstimationVector().toArray();
					Point3D posR2 = new Point3D(vecR2[0], vecR2[1], vecR2[2]);
					posR2.translateXYZ(0, 0, atof_alignement);
					int[] idR2 = predict_wedge(ATOFdet, posR2);
					System.out.println("ATOF surface R2 : " + R2);
					System.out.printf ("   final position : x (%.2f) y (%.2f) z (%.2f)\n", posR2.x(), posR2.y(), posR2.z());
					System.out.printf ("   ---> sector (%2d) layer (%2d) component (%2d)\n", idR2[0], idR2[1], idR2[2]);
					// From surface R2 to surface R3
					RadialSurfaceKFHit hitR3 = new RadialSurfaceKFHit(R3);
					PostFitPropagator.predict(hitR3, true);
					double[] vecR3 = PostFitPropagator.getStateEstimationVector().toArray();
					Point3D posR3 = new Point3D(vecR3[0], vecR3[1], vecR3[2]);
					posR3.translateXYZ(0, 0, atof_alignement);
					int[] idR3 = predict_wedge(ATOFdet, posR3);
					System.out.println("ATOF surface R3 : " + R3);
					System.out.printf ("   final position : x (%.2f) y (%.2f) z (%.2f)\n", posR3.x(), posR3.y(), posR3.z());
					System.out.printf ("   ---> sector (%2d) layer (%2d) component (%2d)\n", idR3[0], idR3[1], idR3[2]);
				}
			    
                

				

			}//end of loop on track candidates
		} catch (Exception e) {
			e.printStackTrace();
			//System.out.println("Error in Kalman Filter");
		}
	}
	public void set_Niter(int Niter) {this.Niter = Niter;}
	public int get_Niter() {return this.Niter;}
	public void set_particle(PDGParticle particle) {this.particle = particle;}
	public PDGParticle get_particle() {return this.particle;}
	public void set_ATOF_hits(HashMap<Integer, RadialKFHit> ATOF_hits){ this.ATOF_hits = ATOF_hits;};
	public HashMap<Integer, RadialKFHit> get_ATOF_hits() { return this.ATOF_hits;}
	public HashMap<Integer, ArrayList<int[]>> get_ATOF_hits_predicted() {return this.ATOF_hits_predicted;}

	// Test
	public static void main(String[] args) {
		// ATOF detector
		AlertTOFDetector atof = (new AlertTOFFactory()).createDetectorCLAS(new DatabaseConstantProvider());

		int Npts = 1000;
		int counter = 0;
		int err = 0;
		for (int i = 0; i < Npts; i++) {
			Random rand = new Random();
			int true_sector = rand.nextInt(15);
			int true_layer = rand.nextInt(4);
			int true_wedge = rand.nextInt(10);
			
			Component comp = atof.getSector(true_sector).getSuperlayer(1).getLayer(true_layer).getComponent(true_wedge);
				// top face
			Point3D p0 = comp.getVolumePoint(0);
			Point3D p1 = comp.getVolumePoint(1);
			Point3D p2 = comp.getVolumePoint(2);
			Point3D p3 = comp.getVolumePoint(3);
				// bottom face
			Point3D p4 = comp.getVolumePoint(4);
			Point3D p5 = comp.getVolumePoint(5);
			Point3D p6 = comp.getVolumePoint(6);
			Point3D p7 = comp.getVolumePoint(7);

			// Random point int he current wedge volume
			//Point3D pt = p0.lerp(p7, Math.random());
			//Point3D pt = comp.getMidpoint();
			double t0 = rand.nextDouble(1);
			double t1 = rand.nextDouble(1-t0);
			double t2 = rand.nextDouble(1-t0-t1);
			double t3 = 1-t0-t1-t2;
			//System.out.printf("t0 + t1 + t2 + t3 = %f\n", t0+t1+t2+t3);
			double t4 = rand.nextDouble(1);
			double t5 = rand.nextDouble(1-t4);
			double t6 = rand.nextDouble(1-t4-t5);
			double t7 = 1-t4-t5-t6;
			//System.out.printf("t4 + t5 + t6 + t7 = %f\n", t4+t5+t6+t7);
			double x_top = t0*p0.x() + t1*p1.x() + t2*p2.x() + t3*p3.x();
			double y_top = t0*p0.y() + t1*p1.y() + t2*p2.y() + t3*p3.y();
			double x_bot = t4*p4.x() + t5*p5.x() + t6*p6.x() + t7*p7.x();
			double y_bot = t4*p4.y() + t5*p5.y() + t6*p6.y() + t7*p7.y();
			Point3D pt_top = new Point3D(x_top, y_top, p0.z());
			Point3D pt_bot = new Point3D(x_bot, y_bot, p4.z());
			Point3D pt = pt_top.lerp(pt_bot, Math.random());
			//System.out.printf("distance from midpoint : %f\n", comp.getMidpoint().distance(pt));

			// Test the algoritm
			int[] res = KalmanFilter.predict_wedge(atof, pt);

			if (res[0] == true_sector && res[1] == true_layer && res[2] == true_wedge) {
				counter++;
			} else {
				err++;
				System.out.printf("%d) Initial wedge   : sector (% 2d) layer (% 2d) wedge (% 2d)\n", err, true_sector, true_layer, true_wedge);
				System.out.printf("%d) Predicted wedge : sector (% 2d) layer (% 2d) wedge (% 2d)\n", err, res[0], res[1], res[2]);
			}
		} 
		System.out.printf("Nb of testing : %d\n", Npts);
		System.out.printf("Nb of success : %d   (%.2f %%)\n", counter, 100.0*counter/Npts);
	}
	/** 
	 * @param pt is defined in the center of the ATOF
	 */
	static public int[] predict_wedge(AlertTOFDetector atof, Point3D pt) {
		// find the wedge
		int wedge = -1;
		double dz = 1e10;
		for (int c = 0; c < atof.getSector(0).getSuperlayer(1).getLayer(0).getNumComponents(); c++) {
			Point3D midpoint = atof.getSector(0).getSuperlayer(1).getLayer(0).getComponent(c).getMidpoint();
			if (Math.abs(midpoint.z()-pt.z()) < dz) {
				dz = Math.abs(midpoint.z()-pt.z());
				wedge = c;
			}
		}
		// find sector and layer
		int sector = -1;
		int layer = -1;
		double d = 1e10;
		for (int s = 0; s < atof.getNumSectors(); s++) {
			for (int l = 0; l < atof.getSector(s).getSuperlayer(1).getNumLayers(); l++) {
				Point3D midpoint = atof.getSector(s).getSuperlayer(1).getLayer(l).getComponent(wedge).getMidpoint();
				if (midpoint.distance(pt) < d) {
					d = midpoint.distance(pt);
					sector = s;
					layer = l;
				}
			}
        }
		return new int[] {sector, layer, wedge};
	}

	/** 
	 * @param pt is defined in the center of the ATOF
	 */
	static public int[] predict_bar(AlertTOFDetector atof, Point3D pt) {
		// find sector and layer
		int sector = -1;
		int layer = -1;
		double d = 1e10;
		for (int s = 0; s < atof.getNumSectors(); s++) {
			for (int l = 0; l < atof.getSector(s).getSuperlayer(0).getNumLayers(); l++) {
				Point3D midpoint = atof.getSector(s).getSuperlayer(0).getLayer(l).getComponent(10).getMidpoint();
				double distance = midpoint.vectorTo(pt).rho();
				if (distance < d) {
					d = distance;
					sector = s;
					layer = l;
				}
			}
        }
		return new int[] {sector, layer, 10};
	}

	public ArrayList<int[]> get_adjacent_wedges(int[] identifiers) {
		int sector = identifiers[0];
		int layer  = identifiers[1];
		int wedge  = identifiers[2];
		
		// find adjacent layer and sector
		int sector_plus = sector;
		int sector_minus = sector;
		int layer_plus = layer+1;
		int layer_minus = layer-1;
		if (layer == 0) {
			sector_plus = sector;
			sector_minus = Math.floorMod(sector-1, 15);
			layer_plus = layer+1;
			layer_minus = 3;
		}
		else if (layer == 3) {
			sector_plus = Math.floorMod(sector+1, 15);
			sector_minus = sector;
			layer_plus = 0;
			layer_minus = layer-1;
		}
		// Here are all the adjacents wedges (maximum 8)
		ArrayList<int[]> listOfWedges = new ArrayList<>();
		listOfWedges.add(new int[]{sector_plus, layer_plus, wedge});
		listOfWedges.add(new int[]{sector_plus, layer_plus, wedge});
		if (wedge-1 >= 0) {
			listOfWedges.add(new int[]{sector, layer, wedge-1});
			listOfWedges.add(new int[]{sector_plus, layer_plus, wedge-1});
			listOfWedges.add(new int[]{sector_minus, layer_minus, wedge-1});
		} 
		if (wedge+1 <= 9) {
			listOfWedges.add(new int[]{sector, layer, wedge+1});
			listOfWedges.add(new int[]{sector_plus, layer_plus, wedge+1});
			listOfWedges.add(new int[]{sector_minus, layer_minus, wedge+1});
		}
		return listOfWedges;
	}

	public void set_ATOF_detector(AlertTOFDetector atof) { this.ATOFdet = atof;}
}
