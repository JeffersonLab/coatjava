package cnuphys.swimtest;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.CLAS12Swim.CLAS12Values;
import cnuphys.CLAS12Swim.EIntegrator;
import cnuphys.adaptiveSwim.AdaptiveSwimException;
import cnuphys.adaptiveSwim.AdaptiveSwimResult;
import cnuphys.adaptiveSwim.AdaptiveSwimmer;
import cnuphys.magfield.FieldProbe;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;


public class BaseTest {
	
	private static FieldProbe _probe;

	//swim to a final path length
	public static void baseSwimTest(int n, long seed) {
		
		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
		
		_probe = FieldProbe.factory(MagneticFields.getInstance().getActiveField());
		
	    System.err.println("Swim to fixed pathlength test");

	    //for writing results to CSV
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "testResults/baseSwim.csv");

	    CSVWriter writer = new CSVWriter(file);
	    writer.writeRow("Swim to final path length");
	    writer.newLine();

	    //write the header row
	    writer.writeRow("charge", "xo (m)", "yo (m)", "zo (m)", "p (GeV/c)", "theta (deg)", "phi (deg)",
	    		"status", "xf_as", "yf_as", "zf_as", "s_as", "npnt_as", "bdl_as",
	    		"status", "xf_c12", "yf_c12", "zf_c12", "s_c12", "npnt_as", "bdl_c12");


		AdaptiveSwimmer adaptiveSwimmer = new AdaptiveSwimmer(); //new
		
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(EIntegrator.DormandPrince); //new


		//results for adaptive swimmer
		AdaptiveSwimResult result = new AdaptiveSwimResult(true);


		double h = 1e-5; // starting stepsize in m

		double sMax = 8; // m
		double asTolerance = 1.0e-6;
		double c12Tolerance = 1.0e-5;
		
		//the final pathlengths from the adaptive swimmer
		//will be used as the target pathlengths for the dp swimmer
		
		double s[] = new double[n];


		//generate some random data
		RandomData data = new RandomData(n, seed);
		
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

			result.reset();

			writer.writeStartOfRow(charge, 100 * xo, 100 * yo, 100 * zo, p, theta, phi);

			// Adaptive
			try {
				adaptiveSwimmer.swim(charge, xo, yo, zo, p,
						theta, phi, sMax, h, asTolerance, result);

				adaptiveSwimResult(writer, result);
				nsAS += result.getNStep();

				s[i] = result.getS();

			} catch (AdaptiveSwimException e) {
				s[i] = Double.NaN;
				System.err.println("Adaptive Swimmer Failed.");
				e.printStackTrace();
			}

			result.reset();

			// c12
			CLAS12SwimResult c12Res = clas12Swimmer.swim(charge, 100*xo, 100*yo, 100*zo, p, theta, phi, 100*s[i], 100*h, c12Tolerance);
			c12SwimResult(writer, c12Res);
			nsC12 += c12Res.getNStep();


			writer.newLine();

		}
		

		writer.close();
		
		nsAS = (int)(((double)nsAS)/n);
		nsC12 = (int)(((double)nsC12)/n);

		System.err.println("done with main test.");
		System.err.println(String.format("avg nsAS = %d **  avg nsC12 = %d  ", nsAS, nsC12));
		System.err.println("Now timing test.");
		
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

			result.reset();

			// Adaptive Swimmer
			try {
				adaptiveSwimmer.swim(charge, xo, yo, zo, p,
						theta, phi, sMax, h, asTolerance, result);

			} catch (AdaptiveSwimException e) {
				System.err.println("Adaptive Swimmer Failed.");
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

			result.reset();

			// C12 Swimmer
			clas12Swimmer.swim(charge, 100*xo, 100*yo, 100*zo, p, theta, phi, 100*s[i], 100*h, c12Tolerance);

		}

		c12Time = bean.getCurrentThreadCpuTime() - start;


		System.err.println("as time: " + asTime);
		System.err.println("c12 time: " + c12Time);
		System.err.println("ratio as/c12 = " + (double)asTime/(double)c12Time);

		System.err.println("done");



	}
		
	//swim results
	private static void adaptiveSwimResult(CSVWriter writer, AdaptiveSwimResult result) {
		double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString());

		if (result.getStatus() == AdaptiveSwimmer.SWIM_SUCCESS) {
			double[] uf = result.getU();
			
			result.getTrajectory().computeBDL(_probe);
			double bdl = result.getTrajectory().getComputedBDL();
			
			writer.writeStartOfRow(100*uf[0], 100*uf[1], 100*uf[2], 100*result.getS(), result.getNStep(), 100*bdl);


		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN, NaN, 0, NaN);
		}
	}
	
	private static void c12SwimResult(CSVWriter writer, CLAS12SwimResult result) {
        double NaN = Double.NaN;
        
		writer.writeStartOfRow(result.statusString());
		
		if (result.getStatus() == CLAS12Swimmer.SWIM_SUCCESS) {
			CLAS12Values finalVals = result.getFinalValues();
			result.getTrajectory().computeBDL(_probe);
			double bdl = result.getTrajectory().getComputedBDL();

			writer.writeStartOfRow(finalVals.x, finalVals.y, finalVals.z, result.getPathLength(), result.getNStep(), bdl);
		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN, 0, NaN);
		}

	}

}
