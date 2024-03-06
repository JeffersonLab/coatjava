package cnuphys.adaptiveSwim.test;

import java.io.File;
import java.util.Random;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.CLAS12Swim.CLAS12Values;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
import cnuphys.swimtest.CSVWriter;
import cnuphys.swimtest.RandomData;

public class ThreadTest {

	public static void threadTest(int n, long seed) {
		System.out.println("Thread test using fixed z test data");
		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);

		// for writing results to CSV
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "testResults/threadTest.csv");

		CSVWriter writer = new CSVWriter(file);
		writer.writeRow("Swim to fixed Z");
		writer.newLine();

		// write the header row
		writer.writeRow("charge", "xo (cm)", "yo (cm)", "zo (cm)", "p (GeV/c)", "theta (deg)", "phi (deg)",
				"zTarg (cm)", "status", "xf_st", "yf_st", "zf_st", "s_st", "npnt_st", "dZ_st", "status", "xf_mt",
				"yf_mt", "zf_mt", "s_mt", "npnt_mt", "dZ_cmt");

		// create the swimmer
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer();

		double h = 1.e-5; // starting

		double sMax = 1000; // cm
		double accuracy = 1e-3; // cm
		double c12Tolerance = 1.0e-5;

		System.out.println("generate random data");
		double zTarget[] = new double[n];
		RandomData data = generateRandomData(n, seed, zTarget);
		
		CLAS12SwimResult c12ResST[] = new CLAS12SwimResult[n];

		// single threaded
		for (int i = 0; i < n; i++) {

			int charge = data.charge[i];
			double xo = data.xo[i];
			double yo = data.yo[i];
			double zo = data.zo[i];
			double p = data.p[i];
			double theta = data.theta[i];
			double phi = data.phi[i];
			double zTarg = zTarget[i];
			


			c12ResST[i] = clas12Swimmer.swimZ(charge, xo, yo, zo, p, theta, phi, zTarg, accuracy,
					sMax, h, c12Tolerance);
			
		}
		

		SwimThread threads[] = new SwimThread[n];
		// multithreaded
		for (int i = 0; i < n; i++) {
			int charge = data.charge[i];
			double xo = data.xo[i];
			double yo = data.yo[i];
			double zo = data.zo[i];
			double p = data.p[i];
			double theta = data.theta[i];
			double phi = data.phi[i];
			double zTarg = zTarget[i];
			
			threads[i] = new SwimThread(i, clas12Swimmer, charge, xo, yo, zo, p, theta, phi, zTarg, accuracy,
					sMax, h, c12Tolerance);
		}
		
		for (int i = 0; i < n; i++) {
			threads[i].start();
		}
		
		System.out.println("Threads started");
		// Wait for all threads to finish
		for (int i = 0; i < n; i++) {
		    try {
		        threads[i].join(); // Waits for this thread to die
		        System.out.println("Thread " + i + " done");
		    } catch (InterruptedException e) {
		        // Handle interruption (e.g., consider whether to break the loop)
		        Thread.currentThread().interrupt(); // Restore the interrupted status
		        System.out.println("Thread was interrupted, failed to complete execution");
		    }
		}
		
		//output results
		for (int i = 0; i < n; i++) {
			int charge = data.charge[i];
			double xo = data.xo[i];
			double yo = data.yo[i];
			double zo = data.zo[i];
			double p = data.p[i];
			double theta = data.theta[i];
			double phi = data.phi[i];
			double zTarg = zTarget[i];

			writer.writeStartOfRow(charge, xo, yo, zo, p, theta, phi, zTarg);
			
			c12SwimResult(writer, zTarget[i], c12ResST[i]);
	//		c12SwimResult(writer, zTarget[i], c12ResMT[i]);
			writer.newLine();
		}
		System.out.println("Thread test done");
	}

	private static RandomData generateRandomData(int n, long seed, double ztarg[]) {
		RandomData data = new RandomData(n, seed);

		// convert data to cm
		for (int i = 0; i < n; i++) {
			data.xo[i] *= 100;
			data.yo[i] *= 100;
			data.zo[i] *= 100;
		}

		Random rand = data.getRand();

		// generate random target z in cm
		for (int i = 0; i < n; i++) {
			ztarg[i] = 250.0 + 350 * rand.nextDouble();
		}

		return data;
	}
	

	private static void c12SwimResult(CSVWriter writer, double targetZ, CLAS12SwimResult result) {
		double NaN = Double.NaN;

		double dist = 0;

		writer.writeStartOfRow(result.statusString());

		if (result.getStatus() == CLAS12Swimmer.SWIM_SUCCESS) {
			CLAS12Values finalVals = result.getFinalValues();
			dist = Math.abs(finalVals.z - targetZ);

			writer.writeStartOfRow(finalVals.x, finalVals.y, finalVals.z, result.getPathLength(), result.getNStep(),
					dist);
		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN, 0, NaN);
		}

	}
	

}

class SwimThread extends Thread {
	
	int index;
	CLAS12Swimmer swimmer;
	int charge;
	double xo, yo, zo, p, theta, phi, zTarg;
	double accuracy, sMax, h, c12Tolerance;
	public CLAS12SwimResult result;

	public SwimThread(int index, CLAS12Swimmer swimmer, int charge, double xo, double yo, double zo, double p,
			double theta, double phi, double zTarg, double accuracy, double sMax, double h, double c12Tolerance) {
		this.index = index;
		this.swimmer = new CLAS12Swimmer();
		this.charge = charge;
		this.xo = xo;
		this.yo = yo;
		this.zo = zo;
		this.p = p;
		this.theta = theta;
		this.phi = phi;
		this.zTarg = zTarg;
		this.accuracy = accuracy;
		this.sMax = sMax;
		this.h = h;
		this.c12Tolerance = c12Tolerance;
	}
	
	public void run() {
		result = swimmer.swimZ(charge, xo, yo, zo, p, theta, phi, zTarg, accuracy, sMax, h,
				c12Tolerance);
	}
}

