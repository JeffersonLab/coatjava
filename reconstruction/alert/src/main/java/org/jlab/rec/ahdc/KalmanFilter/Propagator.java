package org.jlab.rec.ahdc.KalmanFilter;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.jlab.geom.prim.Point3D;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

// All distances here should be in mm.
// Do all those hardcoded values even make sense???
public class Propagator {

	private final RungeKutta4 RK4;

	public Propagator(RungeKutta4 rungeKutta4) {
		this.RK4 = rungeKutta4;
	}
    
    // Propagate the stepper toward the next indicator
	void propagate(Stepper stepper, Indicator indicator) {
		// ------------------------------------------------------------
		final int    maxNbOfStep = 10000; // do not allow more than 10000 steps (very critical cases)
		final double R           = indicator.R;

		double dMin = Double.MAX_VALUE;
		double d    = 0;

		// System.out.println("R = " + R);
		// stepper.print();
        
        // We should use a "while" instead of "for"
		for (int nbStep = 0; nbStep < maxNbOfStep; nbStep++) {
			double previous_r = stepper.r();
			RK4.doOneStep(stepper);
			double r = stepper.r();
			// stepper.print();


			if (stepper.direction) { // forward propagation
				if (r >= R - 2 - 1.5 * stepper.h) stepper.h = 1e-2; // ?
				if (indicator.hit != null) { // the indicator is a hit/wire
					if (r >= R - 2) { // only when the stepper is 2 mm closer to the layer of the hit
						d = indicator.hit.distance(new Point3D(stepper.y[0], stepper.y[1], stepper.y[2])); // distance of the stepper with respect to the wire
						if (d < dMin) dMin = d; // the distance d should continue to diminish
					}
					if (r >= R + 2 || d > dMin) { // r should not be bigger than R
                                                  // if the distance d starts to grow again, stop the propagation!
                                                  // in that case, the good stepper is the previous one!
						// System.out.println("dMin = " + dMin);
						break;
					}
				} else { // the indicator is the target face (inner or outer)
					if (r >= R) {
						break; // break as soon as r >= R, again, may the good stepper is the previous one
					}
				}
			} else { // backward direction
				if (r <= R + 2 + 1.5 * stepper.h) stepper.h = 1e-2; // ?
				if (R < 5) { // we are now close the target (outer face is at 3.060 mm)
					if (stepper.h < 1e-4) stepper.h = 1e-4; // this step is too short // it increases the computing time
					if ((previous_r - r) < 0) { // in the backward propagation, we expect r to decrease; if not stop the propagation!
						break;
					}
				}
				if (indicator.hit != null) { // wire/hit or beamline
					if (r <= R + 2) { // here R < r and r decreases, we enter here when r becomes 2 mm closer to R
                                      // we compute the distance, we expect it to decrease
						d = indicator.hit.distance(new Point3D(stepper.y[0], stepper.y[1], stepper.y[2]));
						if (d < dMin) dMin = d;
					}
					if (r <= R - 2 || d > dMin) { // if the distance grows again, stop the propagation
						// System.out.println("dMin = " + dMin);
						break;
					}
				} else { // the indicator is the target face (inner or outer)
					if (r <= R) { 
						break; // break as soon as r <= R, again, may the good stepper is the previous one
					}
				}


			}



		}
	}

	public RealVector f(Stepper stepper, Indicator indicator) {
		propagate(stepper, indicator);
		return new ArrayRealVector(stepper.y);
	}
    
    // -------------------------------
    // not used, to be deleted
    // -------------------------------
	public void propagateAndWrite(Stepper stepper, Indicator indicator, Writer writer) {
		// ------------------------------------------------------------
		final int    maxNbOfStep = 10000;
		final double R           = indicator.R;

		double dMin = Double.MAX_VALUE;
		double d    = 0;

		System.out.println("R = " + R);
		stepper.print();
		try {writer.write("" + Arrays.toString(stepper.y) + '\n');} catch (Exception e) {e.printStackTrace();}

		for (int nbStep = 0; nbStep < maxNbOfStep; nbStep++) {
			double previous_r = stepper.r();
			RK4.doOneStep(stepper);
			double r = stepper.r();
			stepper.print();
			try {writer.write("" + Arrays.toString(stepper.y) + '\n');} catch (Exception e) {e.printStackTrace();}

			if (stepper.direction) {
				if (r >= R - 2 - 1.5 * stepper.h) stepper.h = 1e-2;
				if (indicator.hit != null) {
					if (r >= R - 2) {
						d = indicator.hit.distance(new Point3D(stepper.y[0], stepper.y[1], stepper.y[2]));
						System.out.println("d = " + d);
						if (d < dMin) dMin = d;
					}
					if (r >= R + 2 || d > dMin) {
						System.out.println("dMin = " + dMin);
						break;
					}
				} else {
					if (r >= R) {
						break;
					}
				}
			} else {
				if (r <= R + 2 + 1.5 * stepper.h) stepper.h = 1e-2;
				if (R < 5) {
					if (stepper.h < 1e-4) stepper.h = 1e-4;
					if ((previous_r - r) < 0) {
						break;
					}
				}
				if (indicator.hit != null) {
					if (r <= R + 2) {
						d = indicator.hit.distance(new Point3D(stepper.y[0], stepper.y[1], stepper.y[2]));
						if (d < dMin) dMin = d;
					}
					if (r <= R - 2 || d > dMin) {
						System.out.println("dMin = " + dMin);
						break;
					}
				} else {
					if (r <= R) {
						break;
					}
				}


			}
		}
	}
}
