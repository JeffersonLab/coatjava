package org.jlab.rec.ahdc.KalmanFilter;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.geom.prim.Point3D;
import org.jlab.clas.tracking.kalmanfilter.Material;
import java.util.HashMap;

/**
 * Is responsible of the propagation
 * 
 * All distances here should be in mm.
 * 
 * @author Mathieu Ouillon
 * @author Éric Fuchey 
 * @author Felix Touchte Codjo
 */
public class Propagator {

	private final RungeKutta4 RK4;

	public Propagator(RungeKutta4 rungeKutta4) {
		this.RK4 = rungeKutta4;
	}
    
    // Propagate the stepper toward the next hit
	void propagate(Stepper stepper, KFHit hit, HashMap<String, Material> materialHashMap) {
		
		// Do not allow more than 10000 steps (very critical cases)
		final int    maxNbOfStep = 10000;
		
		// Initialise steppers to save the two previous states
		Stepper prev_stepper = new Stepper(stepper);
		Stepper prev_prev_stepper = new Stepper(stepper);

		// Initialize distances, d should always disminish
		double dMin = Double.MAX_VALUE;
		double prev_dMin = Double.MAX_VALUE;
		double prev_prev_dMin = Double.MAX_VALUE;
		double d;

		// Intialize radius
		double R = stepper.r();
		double prev_R = R;

		// Initialize the stepper size
		stepper.h = 0.5;

		// Initialize material
		// R = 3 mm is the location of the target
		if (R < 3) {
			stepper.material = materialHashMap.get("deuteriumGas");
		} else {
			stepper.material = materialHashMap.get("BONuS12Gas");
		}

		// boolean : check if we reach or not the target
		boolean target_reached = false;
		boolean target_crossed = false;
		double thickness = 0;

		// Do the propagation
		int nbStep = 0;
		while (nbStep < maxNbOfStep) {
			nbStep++;
			// Save previous states
			prev_prev_stepper.copyContent(prev_stepper);
			prev_stepper.copyContent(stepper);
			prev_R = stepper.r();
			// Take a step
			RK4.doOneStep(stepper);
			// compute distance with respect to the hit
			// this distance should always disminish
			d = hit.distance(new Point3D(stepper.y[0], stepper.y[1], stepper.y[2]));
			R = stepper.r();
			// check the evolution
			if (d < dMin) {
				prev_prev_dMin = prev_dMin;
				prev_dMin = dMin;
				dMin = d;
			}
			else {
				// check the distance between the steps, they should be very big
				if (stepper.distance(prev_stepper) > 0.05) {
					// we didn't reach the precision
					// so, go back two steps before, reduce the step size
					// and redo the propagation
					stepper.copyContent(prev_prev_stepper);
					dMin = prev_prev_dMin;
					stepper.h /= 2;
					continue;
				}
				else {
					// the distance between the step is not so big but the distance with respect to the hit starts to increase
					// so, go back to the previous step (the best we have) and stop the propagation
					stepper.copyContent(prev_stepper);
					break;
				}
			}
			
			// When the propagation is done between the beamline and the first AHDC hit,
			// we should take care of the target material
			// the target is located at R = 3 mm with a thickness of 0.060 mm (60 µm)
			// if R < 5 mm, be careful!
			if (R < 5) {

				// We try to cross the target
				if (((prev_R < 3 && R > 3) || (prev_R > 3 && R < 3)) && !target_reached) {
					// We want to cross the target with a very small step < 0.060 mm
					if (Math.abs(R - prev_R) > 0.060) {
						// redo the propagation
						// so, go back to the previous step and reduce the step size
						stepper.copyContent(prev_stepper);
						stepper.h /= 5;
						dMin = prev_dMin;
						continue;
					}
					// we can consider that we have reached the target and that we are ready to cross it
					else {
						target_reached = true;
						// redo the propagation
						// so, go back to the previous step
						stepper.copyContent(prev_stepper);
						dMin = prev_dMin;
						// We are now ready to cross the target
						// set the target material
						stepper.material = materialHashMap.get("Kapton");
						// update the step size
						stepper.h = 0.003;
						// initialise the crossed thickness
						thickness = 0;
						continue;
					}
				}
				
				// cross the target for 0.06 mm
				if (target_reached && !target_crossed) {
					thickness += Math.abs(R - prev_R);
					if (thickness > 0.06) {
						target_crossed = true;
						// reset stepper size
						// we go back to a normal propagation
						stepper.h = 0.5;
						// update stepper material
						if (stepper.direction) { // forward propagation
							stepper.material = materialHashMap.get("BONuS12Gas");
						}
						else { // backward propagation
							stepper.material = materialHashMap.get("deuteriumGas");
						}
					}
				}
				
			}

		}

	}

	public RealVector f(Stepper stepper, KFHit hit, HashMap<String, Material> materialHashMap) {
		propagate(stepper, hit, materialHashMap);
		return new ArrayRealVector(stepper.y);
	}

}
