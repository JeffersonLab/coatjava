package cnuphys.adaptiveSwim.test;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Random;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.CLAS12Swim.CLAS12Values;
import cnuphys.adaptiveSwim.AdaptiveSwimException;
import cnuphys.adaptiveSwim.AdaptiveSwimResult;
import cnuphys.adaptiveSwim.AdaptiveSwimmer;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
import cnuphys.swimtest.CSVWriter;
import cnuphys.swimtest.RandomData;

public class ZTest {

	//swim to a fixed z
	public static void swimZTest(int n, long seed) {

		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);

	    System.err.println("Swim to fixed z test");

	    //for writing results to CSV
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "testResults/swimZ.csv");

	    CSVWriter writer = new CSVWriter(file);
	    writer.writeRow("Swim to final path length");
	    writer.newLine();

	    //write the header row
	    writer.writeRow("charge", "xo (cm)", "yo (cm)", "zo (cm)", "p (GeV/c)", "theta (deg)", "phi (deg)", "zTarg (cm)",
	    		"status", "xf_as", "yf_as", "zf_as", "s_as", "dZ_as",
	    		"status", "xf_c12", "yf_c12", "zf_c12", "s_c12", "dZ_c12");


		AdaptiveSwimmer adaptiveSwimmer = new AdaptiveSwimmer(); //adaptive
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); //DP

		//results for adaptive
		AdaptiveSwimResult result = new AdaptiveSwimResult(true);

		double stepsizeAdaptive = 1.e-5; // starting

		double sMax = 10; // m
		double accuracy = 1e-5; // m
		double tolerance = 1.0e-6;

		//generate some random data
		RandomData data = new RandomData(n, seed);

		//random target z im meters
	    double zTarget[] = new double[n];
	    Random rand = new Random(seed);
	    for (int i= 0; i < n; i++) {
	    	zTarget[i] = 2.5 + 3.5*rand.nextDouble();
	    }



		for (int i = 0; i < n; i++) {

			int charge = data.charge[i];
			double xo = data.xo[i];
			double yo = data.yo[i];
			double zo = data.zo[i];
			double p = data.p[i];
			double theta = data.theta[i];
			double phi = data.phi[i];
			double zTarg = zTarget[i];

			result.reset();
			
			writer.writeStartOfRow(charge, 100 * xo, 100 * yo, 100 * zo, p, theta, phi, 100*zTarg);

			// Adaptive
			try {
				adaptiveSwimmer.swimZ(charge, xo, yo, zo, p,
						theta, phi, zTarg, accuracy,
						sMax, stepsizeAdaptive, tolerance, result);

				adaptiveSwimResult(writer, zTarg, result);

			} catch (AdaptiveSwimException e) {
				System.err.println("AS Swimmer Failed." + "  final pathlength = " + result.getS());
				e.printStackTrace();
			}

			result.reset();

			// C12
			CLAS12SwimResult c12res = clas12Swimmer.swimZ(charge, 100*xo, 100*yo, 100*zo, p, theta, phi, 100*zTarg, 
					100*accuracy, 100*sMax, 100*stepsizeAdaptive, 100*tolerance);
			c12SwimResult(writer, 100*zTarg, c12res);


			writer.newLine();

			if (i == 0) {
				System.err.println("First C12 result:  " + c12res.toString() + "\n");
			}


		}


		writer.close();
		System.err.println("done with main test. Now timing test.");

		ThreadMXBean bean = ManagementFactory.getThreadMXBean();

		//timing test
		long asTime;
		long c12Time;

        // Measure CPU time before method execution
        long start = bean.getCurrentThreadCpuTime();

		for (int i = 0; i < n; i++) {

			int charge = data.charge[i];
			double xo = data.xo[i];
			double yo = data.yo[i];
			double zo = data.zo[i];
			double p = data.p[i];
			double theta = data.theta[i];
			double phi = data.phi[i];
			double zTarg = zTarget[i];

			result.reset();

			// AS
			try {
				adaptiveSwimmer.swimZ(charge, xo, yo, zo, p,
						theta, phi, zTarg, accuracy,
						sMax, stepsizeAdaptive, tolerance, result);

			} catch (AdaptiveSwimException e) {
				System.err.println("Adaptive Swimmer Failed." + "  final pathlength = " + result.getS());
				e.printStackTrace();
			}
		}

		asTime = bean.getCurrentThreadCpuTime() - start;

		start = bean.getCurrentThreadCpuTime();

		for (int i = 0; i < n; i++) {

			int charge = data.charge[i];
			double xo = data.xo[i];
			double yo = data.yo[i];
			double zo = data.zo[i];
			double p = data.p[i];
			double theta = data.theta[i];
			double phi = data.phi[i];
			double zTarg = zTarget[i];


			// C12
			clas12Swimmer.swimZ(charge, 100*xo, 100*yo, 100*zo, p, theta, phi, 
					100*zTarg, 100*accuracy, 100*sMax, 100*stepsizeAdaptive, 100*tolerance);
		}

		c12Time = bean.getCurrentThreadCpuTime() - start;


		System.err.println("as time: " + asTime);
		System.err.println("c12 time: " + c12Time);
		System.err.println("ratio as/c12 = " + (double)asTime/(double)c12Time);

		System.err.println("done");
	}



	//swimZ results
	private static void adaptiveSwimResult(CSVWriter writer, double targetZ, AdaptiveSwimResult result) {
		double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString());

		//uf is NOT the intersection
		if (result.getStatus() == AdaptiveSwimmer.SWIM_SUCCESS) {
			double[] uf = result.getU();
			double dist = Math.abs(uf[2] - targetZ);
			writer.writeStartOfRow(100*uf[0], 100*uf[1], 100*uf[2], 100*result.getS(), 100*dist);


		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN);
		}
	}

	private static void c12SwimResult(CSVWriter writer, double targetZ, CLAS12SwimResult result) {
        double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString());

		if (result.getStatus() == CLAS12Swimmer.SWIM_SUCCESS) {
			CLAS12Values finalVals = result.getFinalValues();
			double dist = Math.abs(finalVals.z - targetZ);


			writer.writeStartOfRow(finalVals.x, finalVals.y, finalVals.z, result.getPathLength(), dist);
		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN, NaN);
		}

	}


}
