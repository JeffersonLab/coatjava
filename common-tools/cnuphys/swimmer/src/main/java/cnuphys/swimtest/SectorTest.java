package cnuphys.swimtest;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Random;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.magfield.FastMath;
import cnuphys.magfield.FieldProbe;
import cnuphys.magfield.MagneticFieldInitializationException;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
import cnuphys.magfield.RotatedCompositeProbe;
import cnuphys.rk4.RungeKuttaException;
import cnuphys.swim.SwimTrajectory;
import cnuphys.swim.Swimmer;

public class SectorTest {

	// test the sector swimmer for rotated composite
	public static void testSectorSwim(int num) {

		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITEROTATED);

		double hdata[] = new double[3];

		int charge = -1;

		double x0 = -0.8; // m
		double y0 = 0.1;
		double z0 = 0.3; // m

		double pTot = 1.0;
		double theta = 10;
		double phi = 5;
		// double z = 511.0/100.;
		double z = z0 + 0.6;
		double accuracy = 1e-6; //m
		double h = 0.001;

		double rMax = Double.POSITIVE_INFINITY;
		double sMax = 8; // meters

		System.out.println("=======");

		// compare old swimmer and C12
		Swimmer swimmer = new Swimmer(MagneticFields.getInstance().getRotatedCompositeField());

		CLAS12Swimmer c12Swimmer = new CLAS12Swimmer(swimmer.getProbe());
		swimmer.getProbe().getField().printConfiguration(System.out);

		for (int sector = 1; sector <= 6; sector++) {

			SwimTrajectory traj;
			try {
				traj = swimmer.sectorSwim(sector, charge, x0, y0, z0, pTot, theta, phi, z, accuracy, rMax, sMax, h,
						Swimmer.CLAS_Tolerance, hdata);

				if (traj == null) {
					System.err.println("Null trajectory in Sector Test");
					System.exit(1);
				}

				FieldProbe probe = swimmer.getProbe();
//				traj.sectorComputeBDL(sector, (RotatedCompositeProbe) probe);
//				System.out.println("BDL = " + traj.getComputedBDL() + " kG-m");

				double lastY[] = traj.lastElement();
				System.out.println("Sector: " + sector);
				printVect("  OLD ", lastY, 100);

				CLAS12SwimResult c12Result = c12Swimmer.sectorSwimZ(sector, charge, 100*x0, 100*y0, 100*z0, pTot, theta, phi, 100*z,
						100*accuracy, 100*sMax, h, 1e-5);
				printVect("  C12 ", c12Result.getFinalU(), 1);
				
				System.out.println("diff = " + del(lastY, c12Result.getFinalU()) + " cm");

			} catch (RungeKuttaException e) {
				e.printStackTrace();
			}
		}

		System.out.println("\nSwim backwards test");

		try {

			x0 = 0;
			y0 = 0;
			z0 = 0;
			pTot = 2;
			theta = 15;
			phi = 5;
			z = 5.75;

			SwimTrajectory traj = swimmer.sectorSwim(1, charge, x0, y0, z0, pTot, theta, phi, z, accuracy, rMax, sMax, h,
					Swimmer.CLAS_Tolerance, hdata);

			if (traj == null) {
				System.err.println("Null trajectory in Sector Test");
				System.exit(1);
			}

			double lastY[] = traj.lastElement();
			printVect("  (OLD, FORWARD)  ", lastY, 100);

			z = z0;
			x0 = lastY[0];
			y0 = lastY[1];
			z0 = lastY[2];

			double txf = -lastY[3];
			double tyf = -lastY[4];
			double tzf = -lastY[5];

			theta = FastMath.acosDeg(tzf);
			phi = FastMath.atan2Deg(tyf, txf);

			traj = swimmer.sectorSwim(1, -charge, x0, y0, z0, pTot, theta, phi, 0, accuracy, rMax, sMax, h,
					Swimmer.CLAS_Tolerance, hdata);

			lastY = traj.lastElement();
			printVect("  (OLD, BACKWARD) ", lastY, 100);
			
			//now c12 swimmer
			
			x0 = 0;
			y0 = 0;
			z0 = 0;
			pTot = 2;
			theta = 15;
			phi = 5;
			z = 575;

			CLAS12SwimResult c12Result = c12Swimmer.sectorSwimZ(1, charge, x0, y0, z0, pTot, theta, phi, z,
					100*accuracy, 100*sMax, h, 1e-5);
			printVect("  (C12, FORWARD)  ", c12Result.getFinalU(), 1);
			
			lastY = c12Result.getFinalU();
			z = z0;
			x0 = lastY[0];
			y0 = lastY[1];
			z0 = lastY[2];

			txf = -lastY[3];
			tyf = -lastY[4];
			tzf = -lastY[5];

			theta = FastMath.acosDeg(tzf);
			phi = FastMath.atan2Deg(tyf, txf);

			c12Result = c12Swimmer.sectorSwimZ(1, -charge, x0, y0, z0, pTot, theta, phi, z,
					100*accuracy, 100*sMax, h, 1e-5);
			printVect("  (C12, BACKWARD) ", c12Result.getFinalU(), 1);
			

		} catch (RungeKuttaException e) {
			e.printStackTrace();
		}

		System.out.println("\nSwim backwards test (C12)");

		System.out.println("\nSTRESS TEST. Will swim " + num + " random trajectories in rotated system");

		Random rand = new Random(88779911);
		long time = System.currentTimeMillis();

		double aX0[] = new double[num];
		double aY0[] = new double[num];
		double aZ0[] = new double[num];
		double aZ[] = new double[num];
		double aTheta[] = new double[num];
		double aPhi[] = new double[num];
		double aP[] = new double[num];
		int aSect[] = new int[num];

		for (int i = 0; i < num; i++) {
			aSect[i] = rand.nextInt(6) + 1;
			double rho = 0.1 + 2 * rand.nextDouble(); // meters

			aZ0[i] = 0.5 + 4 * rand.nextDouble(); // meters
			aZ[i] = 0.5 + 4 * rand.nextDouble(); // meters
			aP[i] = 1. + 2 * rand.nextDouble();
			aTheta[i] = -10 + 20 * rand.nextDouble();
			aPhi[i] = -10 + 20 * rand.nextDouble();
			double phiLoc = Math.toRadians(30. * rand.nextDouble());
			aX0[i] = rho * Math.cos(phiLoc);
			aY0[i] = rho * Math.sin(phiLoc);
		}

		for (int i = 0; i < num; i++) {

			try {
				SwimTrajectory traj = swimmer.sectorSwim(aSect[i], charge, 
						aX0[i], aY0[i], aZ0[i], aP[i], aTheta[i],
						aPhi[i], aZ[i], accuracy, sMax, h, Swimmer.CLAS_Tolerance, hdata);
			} catch (RungeKuttaException e) {
				e.printStackTrace();
			}

		}

		time = System.currentTimeMillis() - time;
		System.out.println("DONE avg swim time OLD = " + (((double) time) / num) + " ms");

		time = System.currentTimeMillis();
		for (int i = 0; i < num; i++) {
			c12Swimmer.sectorSwimZ(aSect[i], charge, 100*aX0[i], 100*aY0[i], 100*aZ0[i], 
					aP[i], aTheta[i], aPhi[i], 100*aZ[i],
					100*accuracy, 100*sMax, h, 1e-5);
		}

		time = System.currentTimeMillis() - time;
		System.out.println("DONE avg swim time C12 = " + (((double) time) / num) + " ms");

		System.out.println("Max ifferences of endpoints (OLD - C12) in cm");
		double maxDiff = 0;
		
		double lastY[] = null;
		
		for (int i = 0; i < num; i++) {

			try {
				SwimTrajectory traj = swimmer.sectorSwim(aSect[i], charge, 
						aX0[i], aY0[i], aZ0[i], aP[i], aTheta[i],
						aPhi[i], aZ[i], accuracy, 10, h, Swimmer.CLAS_Tolerance, hdata);
				lastY = traj.lastElement();
			} catch (RungeKuttaException e) {
				e.printStackTrace();
			}

			CLAS12SwimResult c12Result = c12Swimmer.sectorSwimZ(aSect[i], charge, 100 * aX0[i], 100 * aY0[i],
					100 * aZ0[i], aP[i], aTheta[i], aPhi[i], 100 * aZ[i], 100 * accuracy, 100 * sMax, h, 1e-5);

			if (c12Result.getStatus() == CLAS12Swimmer.SWIM_SUCCESS) {
				double diff = del(lastY, c12Result.getFinalU());
				if (diff > maxDiff) {
					maxDiff = diff;
				}
			}
		}
		
		System.out.println("Max difference = " + maxDiff + " cm");

	}
	
	private static void printVect(String s, double[] vect, double scale) {
		System.out.println(
				String.format("%6s x = %12.6f y = %12.6f z = %12.6f", s, vect[0] * scale, vect[1] * scale, vect[2] * scale));
	}
	
	private static double del(double u[], double v[]) {
		double dx = 100*u[0] - v[0];
        double dy = 100*u[1] - v[1];
        double dz = 100*u[2] - v[2];
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }



	public static void main(String arg[]) {
		final MagneticFields mf = MagneticFields.getInstance();
		File mfdir = new File(System.getProperty("user.home"), "magfield");
		System.out.println("mfdir exists: " + (mfdir.exists() && mfdir.isDirectory()));
		try {
			mf.initializeMagneticFields(mfdir.getPath(), "Symm_torus_r2501_phi16_z251_24Apr2018.dat",
					"Symm_solenoid_r601_phi1_z1201_13June2018.dat");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (MagneticFieldInitializationException e) {
			e.printStackTrace();
			System.exit(1);
		}

		testSectorSwim(1000);
	}
	

}
