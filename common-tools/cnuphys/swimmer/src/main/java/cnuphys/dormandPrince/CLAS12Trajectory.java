package cnuphys.dormandPrince;

import java.util.ArrayList;

import cnuphys.magfield.FieldProbe;
import cnuphys.magfield.RotatedCompositeProbe;
import cnuphys.swim.SwimTrajectory;

/**
 * This trajectory is in cm and has the path length in cm.
 */
public class CLAS12Trajectory extends SwimTrajectory {
	
	//cache the path length
	ArrayList<Double> _s = new ArrayList<>(200);
	
	/**
	 * add a new point in the trajectory
	 * @param s the path length in cm
	 * @param u the state vector
	 */
	public void add(double s, double[] u) {
		_s.add(s);
		
		//this will call the superclass add which adds A COPY of the state vector
        super.add(u);
	}
	
	/**
	 * Get the path length at the given index
	 * 
	 * @param index the index
	 * @return the path length at the given index
	 */
	public double getS(int index) {
		return _s.get(index);
	}
	
	/**
	 * Get the size of the s collection, which is the same as the size of the trajectory.
	 * (At least is should be!)
	 * 
	 * @return the size of the s collection
	 */
	public int getSSize() {
		return _s.size();
	}
	
	/**
	 * Report on the sizes. They should be the same.
	 * @return a report on the sizes
	 */
	public String sizeReport() {
		return String.format("State vector size: %d   Pathlength size: %d", size(), _s.size());
	}
	
	/**
	 * Clear the trajectory
	 */
	@Override
	public void clear() {
		super.clear();
		_s.clear();
	}
	
	@Override
	public boolean add(double u[]) {
		System.err.println("BAD add double[] called for Full trajectory");
		System.exit(1);
		return false;
	}
	
	@Override
	public boolean add(double u[], double s) {
		System.err.println("BAD add double[], double s called for Full trajectory");
		System.exit(1);
		return false;
	}
	
	
	@Override
	public void add(double xo, double yo, double zo, double momentum, double theta, double phi) {
		System.err.println("BAD add x, y, z, p, theta, phi called for Full trajectory");
		System.exit(1);

	}

	
	/**
	 * Get the r coordinate in cm for the given index
	 * 
	 * @param index the index
	 * @return the r coordinate
	 */
	public double getR(int index) {
		if ((index < 0) || (index > size())) {
			return Double.NaN;
		}

		double v[] = get(index);
		if (v == null) {
			return Double.NaN;
		}

		double x = v[0];
		double y = v[1];
		double z = v[2];

		// convert to cm
		return Math.sqrt(x * x + y * y + z * z);
	}
	
	/**
	 * Compute the integral B cross dl. This will cause the state vector arrays to
	 * expand by two, becoming [x, y, z, px/p, py/p, pz/p, l, bdl] where the 7th
	 * entry l is cumulative pathlength in cm and the eighth entry bdl is the
	 * cumulative integral bdl in kG-cm.
	 * 
	 * @param probe the field getter
	 */
	public void computeBDL(FieldProbe probe) {
		if (_computedBDL) {
			return;
		}

		if (probe instanceof RotatedCompositeProbe) {
			System.err.println(
					"SHOULD NOT HAPPEN. In rotated composite field probe, should not call computeBDL without the sector argument.");

			(new Throwable()).printStackTrace();
			System.exit(1);

		}

		CLAS12Bxdl previous = new CLAS12Bxdl();
		CLAS12Bxdl current = new CLAS12Bxdl();
		double[] p0 = this.get(0);
		augment(p0, 0, 0, 0);

		for (int i = 1; i < size(); i++) {
			double[] p1 = get(i);
			CLAS12Bxdl.accumulate(previous, current, p0, p1, probe);

			augment(p1, current.getPathlength(), current.getIntegralBxdl(), i);
			previous.set(current);
			p0 = p1;
		}

		_computedBDL = true;
	}

	/**
	 * Compute the integral B cross dl. This will cause the state vector arrays to
	 * expand by two, becoming [x, y, z, px/p, py/p, pz/p, l, bdl] where the 7th
	 * entry l is cumulative pathlength in cm and the eighth entry bdl is the
	 * cumulative integral bdl in kG-cm.
	 * 
	 * @param sector sector 1..6
	 * @param probe  the field getter
	 */
	public void sectorComputeBDL(int sector, RotatedCompositeProbe probe) {
		if (_computedBDL) {
			return;
		}

		CLAS12Bxdl previous = new CLAS12Bxdl();
		CLAS12Bxdl current = new CLAS12Bxdl();
		double[] p0 = get(0);
		augment(p0, 0, 0, 0);

		for (int i = 1; i < size(); i++) {
			double[] p1 = get(i);
			CLAS12Bxdl.sectorAccumulate(sector, previous, current, p0, p1, probe);

			augment(p1, current.getPathlength(), current.getIntegralBxdl(), i);

			previous.set(current);
			p0 = p1;
		}
		_computedBDL = true;
	}


	
	
}
