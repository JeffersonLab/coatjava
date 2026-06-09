package org.jlab.rec.ahdc.KalmanFilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.clas.pdg.PDGParticle;
import org.jlab.clas.tracking.kalmanfilter.Material;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.alert.Track.Track;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.base.Component;
import org.jlab.geom.detector.alert.ATOF.AlertTOFDetector;
import org.jlab.geom.detector.alert.ATOF.AlertTOFFactory;
import org.jlab.geom.prim.Point3D;


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

	HashMap<String, Material> materialHashMap = MaterialMap.generateMaterials();

	private PDGParticle particle;
	private int Niter = 40; // number of iterations for the Kalman Filter
	private double vz_constraint = 0;
	private boolean IsVtxDefined = false;

	// mm,  they are the misalignement with respect to the AHDC: the are defined in ALERTEngine
	private double atof_alignement = 0;

	private int counter = 0; // number of utilisation of the Kalman Filter
	
	AlertTOFDetector ATOFdet = null; // reference to the ATOF geometry
	HashMap<Integer, ArrayList<int[]>> ATOF_hits_predicted = new HashMap<>(); // trackid vs (sector, layer, wedge)

	public void propagation(ArrayList<Track> tracks, final double magfield, boolean IsMC) {

		try {
			counter++;

			// Initialization ---------------------------------------------------------------------
			final int         numberOfVariables = 6;
			final double      tesla             = 0.001;
			final double[]    B                 = {0.0, 0.0, magfield / 10 * tesla};
			

            // Loop over tracks
			for (Track track : tracks) {
			    /// Initialize state vector
			    double x0  = 0.0;
			    double y0  = 0.0;
			    double z0  = (IsVtxDefined && counter < 2) ? vz_constraint : track.get_Z0();
			    double px0 = track.get_px();
			    double py0 = track.get_py();
			    double pz0 = track.get_pz();

			    double[]     y   = new double[]{x0, y0, z0, px0, py0, pz0};

			    /// Read list of hits
				RadialKFHit beam_hit = track.getBeamlineHit();
			    ArrayList<Hit> AHDC_hits = track.getHits();
				ArrayList<RadialKFHit> ATOF_hits = track.getATOFHits();
			
			    /// Initialize propagator
			    RungeKutta4 RK4        = new RungeKutta4(particle, numberOfVariables, B);
			    Propagator  propagator = new Propagator(RK4);

			    /// Initialization of the Kalman Fitter
			    RealVector initialStateEstimate   = new ArrayRealVector(y);
				RealMatrix initialErrorCovariance = track.getErrorCovarianceMatrix();
				KFitter TrackFitter = new KFitter(initialStateEstimate, initialErrorCovariance, propagator, materialHashMap);
		 	    
				// Loop over number of iterations
			    for (int k = 0; k < Niter; k++) {
					
					// Forward propagation in AHDC
					for (Hit hit : AHDC_hits) {
                        TrackFitter.predict(hit, true);
						TrackFitter.correct(hit);
					
                    }

					// Take into account ATOF hits
					if (!AHDC_hits.isEmpty() && !ATOF_hits.isEmpty()) {
						// Forward propagation in ATOF
						for (KFHit hit : ATOF_hits) {	
							TrackFitter.predict(hit, true);
							TrackFitter.correct(hit);
						}
						// Backward propagation in ATOF
						for (int i = ATOF_hits.size()-2; i >= 0; i--){
							KFHit hit = ATOF_hits.get(i);
							TrackFitter.predict(hit, false);
							TrackFitter.correct(hit);
						}
						// Backward propagation to the last AHDC layer
						{
							Hit hit = AHDC_hits.getLast();
							TrackFitter.predict(hit, false);
							TrackFitter.correct(hit);
						}
					}
					
					// Backward propagation (from AHDC last layer to first AHDC layer)
					for (int i = AHDC_hits.size()-2; i >= 0; i--) {
						Hit hit = AHDC_hits.get(i);
						TrackFitter.predict(hit, false);
						TrackFitter.correct(hit);
					}

					// Backward propagation (from first AHDC layer to beamline)
					{
						TrackFitter.predict(beam_hit, false);
						TrackFitter.correct(beam_hit);
					}

			    } // end loop over nb. iteration

				/// Output: position and momentum
			    RealVector x_out = TrackFitter.getStateEstimationVector();
			    track.setPositionAndMomentumVec(x_out.toArray());
				track.setErrorCovarianceMatrix(TrackFitter.getErrorCovarianceMatrix());

			    /// Post fit propagation (no correction)
			    KFitter PostFitPropagator = new KFitter(TrackFitter.getStateEstimationVector(), initialErrorCovariance, new Propagator(RK4), materialHashMap);

				// Forward propagation in AHDC
			    for (Hit hit : AHDC_hits) {
                    PostFitPropagator.predict(hit, true);
					hit.setResidual(PostFitPropagator.residual(hit)); // output: residual
			    }

				// Fill values for AHDC::kftrack
				// TO DO : s and p_drift have to be checked to be sure they represent what we want
				Stepper current_stepper = PostFitPropagator.getStepper();
			    double s = current_stepper.sTot;
			    double p_drift = current_stepper.p();
			    double sum_residuals = 0;
			    double chi2 = 0;
			    for (Hit hit : AHDC_hits) {
                    sum_residuals += hit.getResidual();
                    chi2 += Math.pow(hit.getResidual(),2)/hit.MeasurementNoiseMatrix().getEntry(0,0);
			    }
			    track.set_sum_residuals(sum_residuals);
			    track.set_chi2(chi2/(AHDC_hits.size()-3));
			    track.set_p_drift(p_drift);
			    track.set_path(s);

				/// At the end of the PostFit propagation towards the last layer of the AHDC
				/// we project the track on the ATOF surface without any AI ATOF hit indication 
				if (ATOFdet != null) {
					// Projection towards the ATOF surfaces
					// R1 : radius of the lower surface of an ATOF bar
					// R2 : radius of the upper surface of an ATOF bar = lower surface of an ATOF wedge
					// R3 : radius of the upper surface of an ATOF wedge
					// Illustration of an ATOF component : p0 and p3 are the points on the lower surface; p1 and p2 are the points on the upper surface
					//  p2 ---------- p1
					//   \			  /
					//    \          /
					// 	  p3 ------ p0
					Point3D pR1 = ATOFdet.getSector(0).getSuperlayer(0).getLayer(0).getComponent(10).getVolumePoint(0);
					Point3D pR2 = ATOFdet.getSector(0).getSuperlayer(0).getLayer(0).getComponent(10).getVolumePoint(2);
					Point3D pR3 = ATOFdet.getSector(0).getSuperlayer(1).getLayer(0).getComponent(0).getVolumePoint(2);
					double R1 = Math.hypot(pR1.x(), pR1.y());
					double R2 = Math.hypot(pR2.x(), pR2.y());
					double R3 = Math.hypot(pR3.x(), pR3.y());

					/// From last AHDC hit to surface R1
					RadialSurfaceKFHit hitR1 = new RadialSurfaceKFHit(R1);
					PostFitPropagator.predict(hitR1, true);
					Stepper stepperR1 = PostFitPropagator.getStepper();
					track.set_ATOF_S1_stepper(stepperR1); // to store : x,y,z, path, p in AHDC::kftrack
					if (Math.abs(stepperR1.r() - R1) < 1.5*stepperR1.h) {
						track.set_ATOF_region(1); // indicate if we reach this surface or not
					}
					
					/// From surface R1 to surface R2
					RadialSurfaceKFHit hitR2 = new RadialSurfaceKFHit(R2);
					PostFitPropagator.predict(hitR2, true);
					Stepper stepperR2 = PostFitPropagator.getStepper();
					track.set_ATOF_S2_stepper(stepperR2); // to store : x,y,z, path, p in AHDC::kftrack
					if (Math.abs(stepperR2.r() - R2) < 1.5*stepperR2.h) {
						track.set_ATOF_region(2); // indicate if we reach this surface or not
					}
						// predict the wedge
					double[] vecR2 = PostFitPropagator.getStateEstimationVector().toArray();
					Point3D posR2 = new Point3D(vecR2[0], vecR2[1], vecR2[2]);
					posR2.translateXYZ(0, 0, atof_alignement);
					ATOF_hits_predicted.put(track.get_trackId(), predict_adjacent_wedges(ATOFdet, posR2));
					

					/// From surface R2 to surface R3
					RadialSurfaceKFHit hitR3 = new RadialSurfaceKFHit(R3);
					PostFitPropagator.predict(hitR3, true);
					Stepper stepperR3 = PostFitPropagator.getStepper();
					track.set_ATOF_S3_stepper(stepperR3); // to store : x,y,z, path, p in AHDC::kftrack
					if (Math.abs(stepperR3.r() - R3) < 1.5*stepperR3.h) {
						track.set_ATOF_region(3); // indicate if we reach this surface or not
					}

				} // end propagation towards ATOF surface
			    
			}//end of loop on track candidates
		} catch (Exception e) {
			e.printStackTrace();
			//System.out.println("Error in Kalman Filter");
		}
	}
	public void set_Niter(int Niter) {this.Niter = Niter;}
	public int  get_Niter() {return this.Niter;}
	public void        set_particle(PDGParticle particle) {this.particle = particle;}
	public PDGParticle get_particle() {return this.particle;}
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
		if (wedge == -1) return null;
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
		if (sector == -1 || layer == -1) {
			return null;
		} else {
			return new int[] {sector, layer, wedge};
		}
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
		if (sector == -1 || layer == -1) {
			return null;
		} else {
			return new int[] {sector, layer, 10};
		}
	}

	public ArrayList<int[]> predict_adjacent_wedges(AlertTOFDetector atof, Point3D pt) {
		int[] closest_wedge_id = predict_wedge(atof, pt);
		if (closest_wedge_id == null) {
			return new ArrayList<int[]>(); // an empty list
		}

		int sector = closest_wedge_id[0];
		int layer  = closest_wedge_id[1];
		int wedge  = closest_wedge_id[2];
		
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
		// Here are all the adjacents wedges (maximum 9)
		ArrayList<int[]> listOfWedges = new ArrayList<>();
		listOfWedges.add(new int[]{sector, layer, wedge});
		listOfWedges.add(new int[]{sector_plus, layer_plus, wedge});
		listOfWedges.add(new int[]{sector_minus, layer_minus, wedge});
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

		// order the list following the distance to the initial point
		listOfWedges.sort((a,b) -> {
			double da = atof.getSector(a[0]).getSuperlayer(1).getLayer(a[1]).getComponent(a[2]).getMidpoint().distance(pt);
			double db = atof.getSector(b[0]).getSuperlayer(1).getLayer(b[1]).getComponent(b[2]).getMidpoint().distance(pt);
			return Double.compare(da, db);
		});

		return listOfWedges;
	}

	public void set_ATOF_detector(AlertTOFDetector atof) { this.ATOFdet = atof;}
	public void set_atof_alignement(double _shift) {this.atof_alignement = _shift;}
	public void set_vz_constraint(double _vz) {this.vz_constraint = _vz;}
	public void set_vertex_flag(boolean _flag) {this.IsVtxDefined = _flag;}
}
