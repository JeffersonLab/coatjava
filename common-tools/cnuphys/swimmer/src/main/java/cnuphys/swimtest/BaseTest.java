package cnuphys.swimtest;

import java.io.File;

import cnuphys.adaptiveSwim.AdaptiveSwimException;
import cnuphys.adaptiveSwim.AdaptiveSwimResult;
import cnuphys.adaptiveSwim.AdaptiveSwimmer;
import cnuphys.dormandPrince.CLAS12Listener;
import cnuphys.dormandPrince.CLAS12Swimmer;
import cnuphys.dormandPrince.SwimException;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;


public class BaseTest {

	//swim to a final path length
	public static void baseSwimTest(int n, long seed) {
		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
	    System.err.println("Swim to fixed pathlength test");

	    //for writing results to CSV
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "testResults/baseSwim.csv");

	    CSVWriter writer = new CSVWriter(file);
	    writer.writeRow("Swim to final path length");
	    writer.newLine();

	    //write the header row
	    writer.writeRow("charge", "xo (m)", "yo (m)", "zo (m)", "p (GeV/c)", "theta (deg)", "phi (deg)",
	    		"status", "xf_as", "yf_as", "zf_as", "s_as",
	    		"status", "xf_dp", "yf_dp", "zf_dp", "s_dp");


		AdaptiveSwimmer adaptiveSwimmer = new AdaptiveSwimmer(); //new
		
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); //new


		//results for old and new
		AdaptiveSwimResult result = new AdaptiveSwimResult(true);


		double stepsizeAdaptive = 0.01; // starting

		double maxPathLength = 8; // m
		double eps = 1.0e-6;
		
		//the final pathlengths from the adaptive swimmer
		//will be used as the target pathlengths for the dp swimmer
		
		double s[] = new double[n];


		//generate some random data
		RandomData data = new RandomData(n, seed);

		for (int i = 0; i < n; i++) {

			int charge = data.charge[i];
			double xo = data.xo[i];
			double yo = data.yo[i];
			double zo = data.zo[i];
			double p = data.p[i];
			double theta = data.theta[i];
			double phi = data.phi[i];

			result.reset();

			writer.writeStartOfRow(charge, 100 * xo, 100 * yo, 100 * zo, p, theta, phi);

			// Adaptive
			try {
				adaptiveSwimmer.swim(charge, xo, yo, zo, p,
						theta, phi, maxPathLength, stepsizeAdaptive, eps, result);

				adaptiveSwimResult(writer, result);
				s[i] = result.getS();

			} catch (AdaptiveSwimException e) {
				s[i] = Double.NaN;
				System.err.println("Adaptive Swimmer Failed.");
				e.printStackTrace();
			}

			result.reset();

			// DP
			try {
				clas12Swimmer.swim(charge, xo, yo, zo, p, theta, phi, s[i], stepsizeAdaptive, eps, true);
				dpSwimResult(writer, clas12Swimmer.getListener());
			}  catch (SwimException e) {
				System.err.println("DP Swimmer Failed.");
				e.printStackTrace();
			}



			writer.newLine();

		}

		writer.close();
		System.err.println("done with main test. Now timing test.");

		//timing test
		long threadId = Thread.currentThread().getId();
		long asTime;
		long dpTime;

		long start = SwimTest.cpuTime(threadId);

		for (int i = 0; i < n; i++) {

			int charge = data.charge[i];
			double xo = data.xo[i];
			double yo = data.yo[i];
			double zo = data.zo[i];
			double p = data.p[i];
			double theta = data.theta[i];
			double phi = data.phi[i];

			result.reset();

			// Adaptive Swimmer
			try {
				adaptiveSwimmer.swim(charge, xo, yo, zo, p,
						theta, phi, maxPathLength, stepsizeAdaptive, eps, result);

			} catch (AdaptiveSwimException e) {
				System.err.println("Adaptive Swimmer Failed.");
				e.printStackTrace();
			}
		}
		asTime = SwimTest.cpuTime(threadId) - start;

		start = SwimTest.cpuTime(threadId);

		for (int i = 0; i < n; i++) {

			int charge = data.charge[i];
			double xo = data.xo[i];
			double yo = data.yo[i];
			double zo = data.zo[i];
			double p = data.p[i];
			double theta = data.theta[i];
			double phi = data.phi[i];

			result.reset();

			// DP Swimmer
			try {
				clas12Swimmer.swim(charge, xo, yo, zo, p, theta, phi, s[i], stepsizeAdaptive, eps, true);
			} catch (SwimException e) {
				System.err.println("DP Swimmer Failed.");
				e.printStackTrace();
			}

		}

		dpTime = SwimTest.cpuTime(threadId) - start;


		System.err.println("as time: " + asTime);
		System.err.println("dp time: " + dpTime);
		System.err.println("ratio as/dp = " + (double)asTime/(double)dpTime);

		System.err.println("done");



	}
		
	//swim results
	private static void adaptiveSwimResult(CSVWriter writer, AdaptiveSwimResult result) {
		double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString());

		if (result.getStatus() == AdaptiveSwimmer.SWIM_SUCCESS) {
			double[] uf = result.getU();
			writer.writeStartOfRow(100*uf[0], 100*uf[1], 100*uf[2], 100*result.getS());


		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN, NaN);
		}
	}
	
	private static void dpSwimResult(CSVWriter writer, CLAS12Listener listener) {
        double NaN = Double.NaN;
        
		writer.writeStartOfRow(listener.statusString());
		
		if (listener.getStatus() == CLAS12Swimmer.SWIM_SUCCESS) {
			double[] uf = listener.getU();
			writer.writeStartOfRow(100*uf[0], 100*uf[1], 100*uf[2], 100*listener.getS());


		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN, NaN);
		}

	}

}
