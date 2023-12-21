package cnuphys.adaptiveSwim.test;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Random;

import cnuphys.adaptiveSwim.AdaptiveSwimException;
import cnuphys.adaptiveSwim.AdaptiveSwimResult;
import cnuphys.adaptiveSwim.AdaptiveSwimmer;
import cnuphys.dormandPrince.CLAS12SwimResult;
import cnuphys.dormandPrince.CLAS12Swimmer;
import cnuphys.dormandPrince.CLAS12Values;
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
	    		"status", "xf_dp", "yf_dp", "zf_dp", "s_dp", "dZ_dp");


		AdaptiveSwimmer adaptiveSwimmer = new AdaptiveSwimmer(); //new

		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); //new

		//results for adaptive and dP
		AdaptiveSwimResult result = new AdaptiveSwimResult(true);


		double stepsizeAdaptive = 0.01; // starting

		double maxPathLength = 10; // m
		double accuracy = 1e-5; // m
		double eps = 1.0e-6;

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
			
			
//		    writer.writeRow("charge", "xo (cm)", "yo (cm)", "zo (cm)", "p (GeV/c)", "theta (deg)", "phi (deg)", "zTarg (cm)",
//		    		"status", "xf_as", "yf_as", "zf_as", "s_as", "dZ_as",
//		    		"status", "xf_dp", "yf_dp", "zf_dp", "s_dp", "dZ_dp");
			

			writer.writeStartOfRow(charge, 100 * xo, 100 * yo, 100 * zo, p, theta, phi, 100*zTarg);

			// Adaptive
			try {
				adaptiveSwimmer.swimZ(charge, xo, yo, zo, p,
						theta, phi, zTarg, accuracy,
						maxPathLength, stepsizeAdaptive, eps, result);

				adaptiveSwimResult(writer, zTarg, result);

			} catch (AdaptiveSwimException e) {
				System.err.println("NEW Swimmer Failed." + "  final pathlength = " + result.getS());
				e.printStackTrace();
			}

			result.reset();

			// DP
			CLAS12SwimResult c12res = clas12Swimmer.swimZ(charge, 100*xo, 100*yo, 100*zo, p, theta, phi, 100*zTarg, 
					100*maxPathLength, 100*accuracy, 100*stepsizeAdaptive, 100*eps);
			dpSwimResult(writer, 100*zTarg, c12res);


			writer.newLine();

			if (i == 0) {
				System.err.println("First DP result:  " + c12res.toString() + "\n");
			}


		}


		writer.close();
		System.err.println("done with main test. Now timing test.");

		ThreadMXBean bean = ManagementFactory.getThreadMXBean();

        // Check if CPU time measurement is supported
        if (!bean.isCurrentThreadCpuTimeSupported()) {
            System.out.println("CPU time measurement is not supported.");
            return;
        }


        // Enable CPU time measurement (if not already enabled)
        if (!bean.isThreadCpuTimeEnabled()) {
            bean.setThreadCpuTimeEnabled(true);
        }


		//timing test
		long asTime;
		long dpTime;

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

			// NEW
			try {
				adaptiveSwimmer.swimZ(charge, xo, yo, zo, p,
						theta, phi, zTarg, accuracy,
						maxPathLength, stepsizeAdaptive, eps, result);

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


			// DP
			CLAS12SwimResult c12res = clas12Swimmer.swimZ(charge, 100*xo, 100*yo, 100*zo, p, theta, phi, 
					100*zTarg, 100*maxPathLength, 100*accuracy, 100*stepsizeAdaptive, 100*eps);
		}

		dpTime = bean.getCurrentThreadCpuTime() - start;


		System.err.println("as time: " + asTime);
		System.err.println("dp time: " + dpTime);
		System.err.println("ratio as/dp = " + (double)asTime/(double)dpTime);

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

	private static void dpSwimResult(CSVWriter writer, double targetZ, CLAS12SwimResult result) {
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
