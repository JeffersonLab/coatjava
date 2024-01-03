package cnuphys.swimtest;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Random;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.CLAS12Swim.CLAS12Values;
import cnuphys.adaptiveSwim.test.SphereTestData;
import cnuphys.magfield.FastMath;
import cnuphys.magfield.FieldProbe;
import cnuphys.magfield.MagneticFieldInitializationException;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
import cnuphys.magfield.RotatedCompositeProbe;
import cnuphys.rk4.RungeKuttaException;
import cnuphys.swim.SwimTrajectory;
import cnuphys.swim.Swimmer;

public class SectorZTest {

	// test the sector swimmer for rotated composite
	public static void testSectorSwim(int n, long seed) {

		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITEROTATED);
		System.err.println("Sector Swim to fixed z test");

		// for writing results to CSV
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "testResults/sectorSwimZ.csv");

		CSVWriter writer = new CSVWriter(file);
		writer.writeRow("Swim to fixed Z");
		writer.newLine();

		// write the header row
		writer.writeRow("charge", "xo (cm)", "yo (cm)", "zo (cm)", "p (GeV/c)", "theta (deg)", "phi (deg)",
				"zTarg (cm)", "xf_as", "yf_as", "zf_as", "npnt_as", "dZ_as", "status", "xf_c12",
				"yf_c12", "zf_c12", "npnt_s12", "dZ_c12");

		double hdata[] = new double[3];

		double accuracy = 1e-5; // m
		double h = 1.e-5; // m

		double sMax = 8; // meters

		double c12Tolerance = 1.0e-5;

		// compare old swimmer and C12
		Swimmer swimmer = new Swimmer(MagneticFields.getInstance().getRotatedCompositeField());

		CLAS12Swimmer c12Swimmer = new CLAS12Swimmer(swimmer.getProbe());
	
		Random rand = new Random(seed);
		
		//generate the data
	
		int q[] = new int[n];
		int sector[] = new int[n];
	    double zTarget[] = new double[n];

		//generate some random data
		RandomData data = new RandomData(n, seed, 1);


		for (int i = 0; i < n; i++) {
			sector[i] = rand.nextInt(6) + 1;
			q[i] = (rand.nextBoolean()) ? 1 : -1;
	    	zTarget[i] = 2.5 + 3.5*rand.nextDouble();
		}
		
	    double delAS = 0;
	    double delC12 = 0;

	    int nsAS = 0;
	    int nsC12 = 0;


		for (int i = 0; i < n; i++) {

			int charge = q[i];
			int sect = sector[i];
			double xo = data.xo[i];
			double yo = data.yo[i];
			double zo = data.zo[i];
			double p = data.p[i];
			double theta = data.theta[i];
			double phi = data.phi[i];

			double zTarg = zTarget[i];
			writer.writeStartOfRow(charge, 100 * xo, 100 * yo, 100 * zo, p, theta, phi, 100 * zTarg);

			try {
				SwimTrajectory traj = swimmer.sectorSwim(sect, charge, xo, yo, zo, p, theta, phi, zTarget[i], accuracy,
						sMax, h, Swimmer.CLAS_Tolerance, hdata);

				if (traj != null) {
					nsAS += traj.size();
				} else {
					System.out.println("Bad sectorZ swim");
				}
				delAS += swimResult(writer, zTarget[i], traj);

			} catch (RungeKuttaException e) {
				e.printStackTrace();
			}

			CLAS12SwimResult c12res = c12Swimmer.sectorSwimZ(sect, charge, 100 * xo, 100 * yo, 100 * zo, p, theta,
					phi, 100 * zTarg, 100 * accuracy, 100 * sMax, 100 * h, c12Tolerance);

			delC12 += c12SwimResult(writer, 100 * zTarget[i], c12res);
			nsC12 += c12res.getNStep();

			writer.newLine();
		}

		writer.close();
		delAS /=n;
		delC12 /=n;
		nsAS = (int)(((double)nsAS)/n);
		nsC12 = (int)(((double)nsC12)/n);

		System.err.println("done with main test.");
		System.err.println(String.format("avg delAS = %6.2e cm **  avg delC12 = %6.2e cm  ", delAS, delC12));
		System.err.println(String.format("avg nsAS = %d **  avg nsC12 = %d  ", nsAS, nsC12));
		System.err.println("Now timing test.");

		ThreadMXBean bean = ManagementFactory.getThreadMXBean();

		//timing test
		long asTime;
		long c12Time;
       
        // Measure CPU time before method execution
        long start = bean.getCurrentThreadCpuTime();
		for (int i = 0; i < n; i++) {

			int charge = q[i];
			int sect = sector[i];
			double xo = data.xo[i];
			double yo = data.yo[i];
			double zo = data.zo[i];
			double p = data.p[i];
			double theta = data.theta[i];
			double phi = data.phi[i];

			double zTarg = zTarget[i];

			try {
				SwimTrajectory traj = swimmer.sectorSwim(sect, charge, xo, yo, zo, p, theta, phi, zTarg, accuracy,
						sMax, h, Swimmer.CLAS_Tolerance, hdata);


			} catch (RungeKuttaException e) {
				e.printStackTrace();
			}
		}
		asTime = bean.getCurrentThreadCpuTime() - start;

		start = bean.getCurrentThreadCpuTime();


		for (int i = 0; i < n; i++) {

			int charge = q[i];
			int sect = sector[i];
			double xo = data.xo[i];
			double yo = data.yo[i];
			double zo = data.zo[i];
			double p = data.p[i];
			double theta = data.theta[i];
			double phi = data.phi[i];

			c12Swimmer.sectorSwimZ(sect, charge, 100 * xo, 100 * yo, 100 * zo, p, theta,
					phi, 100 * zTarget[i], 100 * accuracy, 100 * sMax, 100 * h, c12Tolerance);

		}
		c12Time = bean.getCurrentThreadCpuTime() - start;

		System.err.println("as time: " + asTime);
		System.err.println("c12 time: " + c12Time);
		System.err.println("ratio as/c12 = " + (double)asTime/(double)c12Time);

		System.err.println("done");
		
	}
	
	//old swim results
	private static double swimResult(CSVWriter writer, double targetZ, SwimTrajectory traj) {
		double NaN = Double.NaN;

		double dist = 0;

		if (traj != null) {
			double[] uf = traj.lastElement();
			dist = Math.abs(uf[2] - targetZ);
			
			
			if (dist > 0.01) {
				writer.writeStartOfRow(100 * uf[0], 100 * uf[1], 100 * uf[2], traj.size(), NaN);
			} else {
				writer.writeStartOfRow(100 * uf[0], 100 * uf[1], 100 * uf[2], traj.size(), 100 * dist);
			}		

		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN, 0, NaN);
		}
		
		//a big distance indicates a failure for old swimmer
		if (dist > 0.01) {
			dist = 0;
		}
		
		return 100*dist;
	}
	

	private static double c12SwimResult(CSVWriter writer, double targetZ, CLAS12SwimResult result) {
		double NaN = Double.NaN;

		double dist = 0;

		writer.writeStartOfRow(result.statusString());

		if (result.getStatus() == CLAS12Swimmer.SWIM_SUCCESS) {
			CLAS12Values finalVals = result.getFinalValues();
			dist = Math.abs(finalVals.z - targetZ);

			writer.writeStartOfRow(finalVals.x, finalVals.y, finalVals.z, result.getNStep(),
					dist);
		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, 0, NaN);
		}

		return dist;

	}
	

}
