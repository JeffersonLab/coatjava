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
	    		"status", "xf_as", "yf_as", "zf_as", "dRho_as", 
	    		"status", "xf_dp", "yf_dp", "zf_dp", "dRho_dp");
	    
	    
		AdaptiveSwimmer adaptiveSwimmer = new AdaptiveSwimmer(); //adaptive
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); //DP

		//results for adaptive
		AdaptiveSwimResult result = new AdaptiveSwimResult(true);

		double stepsizeAdaptive = 1.e-5; // starting

		double maxPathLength = 10; // m
		double accuracy = 1e-5; // m
		double eps = 1.0e-6;

		//generate some random data
		RandomData data = new RandomData(n, seed);

		//random target rho im meters
	    double rhoTarget[] = new double[n];
	    Random rand = new Random(seed);
	    for (int i= 0; i < n; i++) {
	    	rhoTarget[i] = .5 + 4*rand.nextDouble();
	    }
	    
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
						maxPathLength, stepsizeAdaptive, eps, result);
				
				swimRhoSwimResult(writer, rhoTarg, result);

			} catch (AdaptiveSwimException e) {
				System.err.println("NEW Swimmer Failed." + "  final pathlength = " + result.getS());
				e.printStackTrace();
			}

			result.reset();
			
			// DP
			CLAS12SwimResult c12res = clas12Swimmer.swimRho(charge, 100*xo, 100*yo, 100*zo, p, theta, phi, 100*rhoTarg, 
					100*maxPathLength, 100*accuracy, 100*stepsizeAdaptive, 100*eps);
			dpSwimResult(writer, 100*rhoTarg, c12res);


			writer.newLine();

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
			double rhoTarg = rhoTarget[i];
			
			result.reset();
		
			// NEW
			try {
				adaptiveSwimmer.swimRho(charge, xo, yo, zo, p, 
						theta, phi, rhoTarg, accuracy,
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
			double rhoTarg = rhoTarget[i];
			
			result.reset();
			
			// DP
			CLAS12SwimResult c12res = clas12Swimmer.swimRho(charge, 100*xo, 100*yo, 100*zo, p, theta, phi, 
					100*rhoTarg, 100*maxPathLength, 100*accuracy, 100*stepsizeAdaptive, 100*eps);
			
		}
		
		dpTime = bean.getCurrentThreadCpuTime() - start;


		System.err.println("as time: " + asTime);
		System.err.println("dp time: " + dpTime);
		System.err.println("ratio as/dp = " + (double)asTime/(double)dpTime);

		System.err.println("done");


	}
	
	
	//swimRho results
	private static void swimRhoSwimResult(CSVWriter writer, double targetRho, AdaptiveSwimResult result) {
		double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString()); 

		//uf is NOT the intersection
		if (result.getStatus() == AdaptiveSwimmer.SWIM_SUCCESS) {
			double[] uf = result.getU();
			double dist = Math.abs(result.getFinalRho() - targetRho);
			writer.writeStartOfRow(100*uf[0], 100*uf[1], 100*uf[2], 100*dist);


		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN);
		}
	}
	
	private static void dpSwimResult(CSVWriter writer, double targetRho, CLAS12SwimResult result) {
        double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString());

		if (result.getStatus() == CLAS12Swimmer.SWIM_SUCCESS) {
			CLAS12Values finalVals = result.getFinalValues();
			double rho = Math.hypot(finalVals.x, finalVals.y);
			double dist = Math.abs(rho - targetRho);


			writer.writeStartOfRow(finalVals.x, finalVals.y, finalVals.z, dist);
		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN);
		}

	}

}
