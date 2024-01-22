package cnuphys.adaptiveSwim.test;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Random;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.CLAS12Swim.CLAS12Values;
import cnuphys.CLAS12Swim.EIntegrator;
import cnuphys.adaptiveSwim.AdaptiveSwimException;
import cnuphys.adaptiveSwim.AdaptiveSwimResult;
import cnuphys.adaptiveSwim.AdaptiveSwimmer;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
import cnuphys.swimtest.CSVWriter;
import cnuphys.swimtest.RandomData;

public class RhoTest {



	//swim to a fixed rho
	public static void rhoTest(int n, long seed) {

		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
	    System.err.println("Swim to fixed rho test");

	    //for writing results to CSV
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "testResults/swimRho.csv");

	    CSVWriter writer = new CSVWriter(file);
	    writer.writeRow("Swim to fixed Rho");
	    writer.newLine();

	    //write the header row
	    writer.writeRow("charge", "rhoTarg", "xo (m)", "yo (m)", "zo (m)", "p (GeV/c)", "theta (deg)", "phi (deg)",
	    		"status", "xf_as", "yf_as", "zf_as", "npnt_as", "dRho_as",
	    		"status", "xf_c12", "yf_c12", "zf_c12", "npnt_c12", "dRho_c12");


		AdaptiveSwimmer adaptiveSwimmer = new AdaptiveSwimmer(); //adaptive
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(EIntegrator.CashKarp); //DP

		//results for adaptive
		AdaptiveSwimResult result = new AdaptiveSwimResult(true);

		double stepsizeAdaptive = 1.e-5; // starting

		double sMax = 10; // m
		double accuracy = 1e-5; // m
		double asTolerance = 1.0e-6;
		double c12Tolerance = 1.0e-5;

		//generate some random data
		RandomData data = new RandomData(n, seed);

		//random target rho im meters
	    double rhoTarget[] = new double[n];
	    Random rand = new Random(seed);
	    for (int i= 0; i < n; i++) {
	    	rhoTarget[i] = .5 + 4*rand.nextDouble();
	    }

	    double delAS = 0;
	    double delC12 = 0;

	    int nsAS = 0;
	    int nsC12 = 0;

		for (int i = 0; i < n; i++) {

			int charge = data.charge[i];
			double xo = data.xo[i];
			double yo = data.yo[i];
			double zo = data.zo[i];
			double p = data.p[i];
			double theta = data.theta[i];
			double phi = data.phi[i];
			double rhoTarg = rhoTarget[i];

			result.reset();

			writer.writeStartOfRow(charge, 100*rhoTarg, 100 * xo, 100 * yo, 100 * zo, p, theta, phi);

			// Adaptive
			try {
				adaptiveSwimmer.swimRho(charge, xo, yo, zo, p,
						theta, phi, rhoTarg, accuracy,
						sMax, stepsizeAdaptive, asTolerance, result);

				delAS += swimRhoSwimResult(writer, rhoTarg, result);

				nsAS += result.getNStep();

			} catch (AdaptiveSwimException e) {
				System.err.println("NEW Swimmer Failed." + "  final pathlength = " + result.getS());
				e.printStackTrace();
			}

			result.reset();

			// C12
			CLAS12SwimResult c12res = clas12Swimmer.swimRho(charge, 100*xo, 100*yo, 100*zo, p, theta, phi, 100*rhoTarg,
					100*accuracy, 100*sMax, 100*stepsizeAdaptive, c12Tolerance);
			delC12 += dpSwimResult(writer, 100*rhoTarg, c12res);

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
        bean.setThreadCpuTimeEnabled(true);

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
			double rhoTarg = rhoTarget[i];

			result.reset();

			// NEW
			try {
				adaptiveSwimmer.swimRho(charge, xo, yo, zo, p,
						theta, phi, rhoTarg, accuracy,
						sMax, stepsizeAdaptive, asTolerance, result);

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
			double rhoTarg = rhoTarget[i];

			result.reset();

			// C12
			clas12Swimmer.swimRho(charge, 100*xo, 100*yo, 100*zo, p, theta, phi,
					100*rhoTarg, 100*accuracy, 100*sMax, 100*stepsizeAdaptive, c12Tolerance);

		}

		c12Time = bean.getCurrentThreadCpuTime() - start;


		System.err.println("as time: " + asTime);
		System.err.println("c12 time: " + c12Time);
		System.err.println("ratio as/c12 = " + (double)asTime/(double)c12Time);

		System.err.println("done");


	}


	//swimRho results
	private static double swimRhoSwimResult(CSVWriter writer, double targetRho, AdaptiveSwimResult result) {
		double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString());
		double dist = 0;

		//uf is NOT the intersection
		if (result.getStatus() == AdaptiveSwimmer.SWIM_SUCCESS) {
			double[] uf = result.getU();
			dist = Math.abs(result.getFinalRho() - targetRho);
			writer.writeStartOfRow(100*uf[0], 100*uf[1], 100*uf[2], result.getNStep(), 100*dist);
		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, 0, NaN);
		}

		return 100*dist;
	}

	private static double dpSwimResult(CSVWriter writer, double targetRho, CLAS12SwimResult result) {
        double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString());
		double dist = 0;

		if (result.getStatus() == CLAS12Swimmer.SWIM_SUCCESS) {
			CLAS12Values finalVals = result.getFinalValues();
			double rho = Math.hypot(finalVals.x, finalVals.y);
			dist = Math.abs(rho - targetRho);
			writer.writeStartOfRow(finalVals.x, finalVals.y, finalVals.z, result.getNStep(), dist);
		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, 0, NaN);
		}

		return dist;

	}

}
