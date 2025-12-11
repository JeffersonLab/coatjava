package org.jlab.rec.ahdc.KalmanFilter;

import java.util.Arrays;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.geom.prim.Point3D;
import org.jlab.clas.tracking.kalmanfilter.Material;
import java.util.HashMap;
import org.jlab.rec.ahdc.Hit.Hit;

// All distances here should be in mm.
public class Propagator {

	private final RungeKutta4 RK4;

	public Propagator(RungeKutta4 rungeKutta4) {
		this.RK4 = rungeKutta4;
	}
    
	// This function needs to know:
	// - the direction
	// These information are already accessible in the stepper, but where shoulb specifiy them
    // Propagate the stepper toward the next hit
	//void propagate(Stepper stepper, Indicator indicator, HashMap<String, Material> materialHashMap) {
	void propagate(Stepper stepper, Hit hit, HashMap<String, Material> materialHashMap) {
		
		// Do not allow more than 10000 steps (very critical cases)
		final int    maxNbOfStep = 10000;
		
		// Initialize a stepper, used to save the previous stepper
		Stepper prevStepper = new Stepper(stepper.y);

		// Initialize distances, d should always disminish
		double dMin = Double.MAX_VALUE;
		double prev_dMin = Double.MAX_VALUE;
		double d;

		// Intialize radius
		double R = stepper.r();
		double prev_R = R;

		// Initialize the stepper size
		stepper.h = 0.1;

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
			// Save previous state
			prevStepper.y = Arrays.copyOf(stepper.y, stepper.y.length);
			prev_R = stepper.r();
			// Take a step
			RK4.doOneStep(stepper);
			// compute distance with respect to the hit
			// this distance should always disminish
			d = hit.distance(new Point3D(stepper.y[0], stepper.y[1], stepper.y[2]));
			R = stepper.r();
			// check the evolution
			if (d < dMin) {
				prev_dMin = dMin;
				dMin = d;
			}
			else {
				// go back to the previous step and stop the propagation
				stepper.y = Arrays.copyOf(prevStepper.y, prevStepper.y.length);
				// ---------------------------------------
				// this part can be optomized
				// we can make the step size dynamical
				// e.g : if d >= dMin, go back to the previous step and reduce the step size by a factor 5
				// to be done later
				// ---------------------------------------
				break;
			}
			
			// When the propagation is done between the beamline and the first AHDC hit,
			// we should take care of the target material
			// target is located at R = 3 mm with a thickness of 0.060 mm (60 µm)
			// if R < 5 mm, be careful!
			if (R < 5) {

				// We try to cross the target
				if (((prev_R < 3 && R > 3) || (prev_R > 3 && R < 3)) && !target_reached) {
					// We want to cross the target with a very small step < 0.060 mm
					if (Math.abs(R - prev_R) > 0.060) {
						stepper.h /= 5;
						// redo the propagation
						// so, go back to the previous step
						stepper.y = Arrays.copyOf(prevStepper.y, prevStepper.y.length);
						dMin = prev_dMin;
						continue;
					}
					// we can consider that we have reached the target and that we are ready to cross it
					else {
						target_reached = true;
						// redo the propagation
						// so, go back to the previous step
						stepper.y = Arrays.copyOf(prevStepper.y, prevStepper.y.length);
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
						// update stepper size
						// we go back to a normal propagation
						stepper.h = 0.1;
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
		//System.out.printf("nbstep : %d (%s)\n", nbStep, stepper.direction ? "forward" : "backward");

	}

	public RealVector f(Stepper stepper, Hit hit, HashMap<String, Material> materialHashMap) {
		propagate(stepper, hit, materialHashMap);
		return new ArrayRealVector(stepper.y);
	}

}

// Draft
// ------------------------------------------------------------
		/*final int    maxNbOfStep = 10000; // do not allow more than 10000 steps (very critical cases)
		final double R           = indicator.R;

		double dMin = Double.MAX_VALUE;
		double d    = 0;
		Stepper prevStepper = new Stepper(stepper.y);
		prevStepper.initialize(indicator);

        int nbStep = 0;
        while (nbStep < maxNbOfStep) {
            nbStep++;
			prevStepper.y = Arrays.copyOf(stepper.y, stepper.y.length);
			RK4.doOneStep(stepper);
            
            // Compute the distance
            if (indicator.hit != null) { // the indicator is a hit/wire
                d = indicator.hit.distance(new Point3D(stepper.y[0], stepper.y[1], stepper.y[2])); // distance of the stepper with respect to the wire
            } else { // the indicator is the target face (inner or outer)
			    double d0 = Math.abs(indicator.R - stepper.r());
                d = Math.max(d0, stepper.h);
				if (flag) {
                	System.out.printf("[%4d] r : %f  --->  R : %2.5f (%s)\n", nbStep, stepper.r(), indicator.R, stepper.direction ? "forward" : "backward");
				}
            }

            // The first time, it will disminish because dMin = "Infinity"
            // Starting nbStep 2, the distance should continue to disminish. If not, stop the propagation
            if (d < dMin) {
                dMin = d; 
            } else {
				stepper.y = Arrays.copyOf(prevStepper.y, prevStepper.y.length);
                break;
            }

		}*/