package cnuphys.adaptiveSwim.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.adaptiveSwim.AdaptiveSwimResult;
import cnuphys.adaptiveSwim.AdaptiveSwimmer;
import cnuphys.adaptiveSwim.geometry.Cylinder;
import cnuphys.lund.AsciiReader;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
import cnuphys.rk4.RungeKuttaException;
import cnuphys.swim.Swimmer;
import cnuphys.swimtest.CSVWriter;
import cnuphys.swimtest.SwimTest;

public class CylinderTest {
	
	//swim to a fixed cylinder
	//data from csv file
	public static void cylinderTest() {
		
		//get data from csv data file
		CylinderTestData testData[] = readDataFile();
	    
		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
	    System.err.println("Swim to cylinder test");
	    
	    //for writing results to CSV
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "testResults/swimCylinder.csv");

	    CSVWriter writer = new CSVWriter(file);
	    writer.writeRow("Swim to cylinder");
	    writer.newLine();
	    
	    //write the header row
	    writer.writeRow("charge", "xo (cm)", "yo (cm)", "zo (cm)", "p (GeV/c)", "theta (deg)", "phi (deg)", 
	    		"status", "xf_old", "yf_old", "zf_old", "s_old", "d_old/acc", 
	    		"status", "xf_new", "yf_new", "zf_new", "s_new", "d_new/acc");
	    
	    
		Swimmer swimmer = new Swimmer(); //old
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); //new
		
		//results for old 
		AdaptiveSwimResult result = new AdaptiveSwimResult(true);
		

		double eps = 1.0e-6;
		
		double c12p1[] = new double[3];
		double c12p2[] = new double[3];
		
		//create the clas12 cylinders
		Cylinder c12Cylinder[] = new Cylinder[testData.length];
		for (int i = 0; i < testData.length; i++) {
			CylinderTestData data = testData[i];
			for (int j = 0; j < 3; j++) {
				c12p1[j] = 100 * data.CLP1[j];
				c12p2[j] = 100 * data.CLP2[j];
			}
			c12Cylinder[i] = new Cylinder(c12p1, c12p2, 100*data.r);
		}

		int idx = 0;
		for (CylinderTestData data : testData) {
			
			int charge = data.charge;
			double xo = data.xo; //these are meters
			double yo = data.yo;
			double zo = data.zo;
			double p = data.p;
			double theta = data.theta;
			double phi = data.phi;
			
			result.reset();

			writer.writeStartOfRow(charge, 100*xo, 100*yo, 100*zo, p, theta, phi);
			
			// interp OLD
			try {
				
				swimmer.swimCylinder(charge, xo, yo, zo, p, theta, phi,
						data.CLP1, data.CLP2,
						data.r, data.accuracy, data.sMax, data.stepSize, Swimmer.CLAS_Tolerance, result);
				
				swimCylinderSwimResult(writer, data, result);


			} catch (RungeKuttaException e) {
				System.err.println("OLD Swimmer Failed." + "  final pathlength = " + result.getFinalS());
				e.printStackTrace();
			}

			result.reset();
			
			// CLAS12Swimmer
			CLAS12SwimResult c12Result = clas12Swimmer.swimCylinder(charge, 100*xo, 100*yo, 100*zo, 
					p, theta, phi, c12Cylinder[idx], 100*data.accuracy, 100*data.sMax, 100*data.stepSize, 100*eps);
			
			swimC12CylinderSwimResult(writer, data, c12Result, c12Cylinder[idx]);
			
			writer.newLine();

			idx++;
		}
		writer.close();
		System.err.println("done with main test. Now timing test.");

		//timing test
		long threadId = Thread.currentThread().getId();
		long oldTime;
		long newTime;
		
		int numTestRun = 200;

		long start = SwimTest.cpuTime(threadId);
		
		idx = 0;
		for (int i = 0; i < numTestRun; i++) {

			for (CylinderTestData data : testData) {

				int charge = data.charge;
				double xo = data.xo;
				double yo = data.yo;
				double zo = data.zo;
				double p = data.p;
				double theta = data.theta;
				double phi = data.phi;
				
				CLAS12SwimResult c12Result = clas12Swimmer.swimCylinder(charge, 100*xo, 100*yo, 100*zo, 
						p, theta, phi, c12Cylinder[idx], 100*data.accuracy, 100*data.sMax, 100*data.stepSize, 100*eps);
				

			}
			
			idx++;

		}
		newTime = SwimTest.cpuTime(threadId) - start;
		
		start = SwimTest.cpuTime(threadId);
		
	
		for (int i = 0; i < numTestRun; i++) {
			for (CylinderTestData data : testData) {

				int charge = data.charge;
				double xo = data.xo;
				double yo = data.yo;
				double zo = data.zo;
				double p = data.p;
				double theta = data.theta;
				double phi = data.phi;

				result.reset();

				// interp OLD
				try {

					swimmer.swimCylinder(charge, xo, yo, zo, p, theta, phi, data.CLP1, data.CLP2, data.r, data.accuracy,
							data.sMax, data.stepSize, Swimmer.CLAS_Tolerance, result);

				} catch (RungeKuttaException e) {
					System.err.println("OLD Swimmer Failed." + "  final pathlength = " + result.getS());
					e.printStackTrace();
				}

			}
		}

		oldTime = SwimTest.cpuTime(threadId) - start;

		System.err.println("old time: " + oldTime);
		System.err.println("new time: " + newTime);
		System.err.println("ratio old/new = " + (double) oldTime / (double) newTime);

		System.err.println("done");

	}
	
	
	//swimCylinder results
	private static void swimCylinderSwimResult(CSVWriter writer, CylinderTestData data, AdaptiveSwimResult result) {
		double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString()); 

		//uf is NOT the intersection
		if (result.getStatus() == AdaptiveSwimmer.SWIM_SUCCESS) {
			double[] uf = result.getU();
			double dist = data.cylinder.signedDistance(uf[0], uf[1], uf[2]);
			writer.writeStartOfRow(100*uf[0], 100*uf[1], 100*uf[2], 100*result.getS(), Math.abs(dist/data.accuracy));


		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN);
		}
	}
	
	//swimCylinder results
	private static void swimC12CylinderSwimResult(CSVWriter writer, CylinderTestData data, 
			CLAS12SwimResult result, Cylinder cylinder) {
		double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString()); 

		//uf is NOT the intersection
		if (result.getStatus() == CLAS12Swimmer.SWIM_SUCCESS) {
			double[] uf = result.getFinalU();
			double dist = cylinder.signedDistance(uf[0], uf[1], uf[2]);
			writer.writeStartOfRow(uf[0], uf[1], uf[2], result.getPathLength(), Math.abs(dist/(100*data.accuracy)));


		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN);
		}
	}

	
	//read a csv data file
	private static CylinderTestData[] readDataFile() {
		
		ArrayList<CylinderTestData> data = new ArrayList<>();
		
		
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "swimTestInput/cylinderdata.csv");

		if (file.exists()) {
			System.err.println("Found cylinder data file");
			try {
				AsciiReader reader = new AsciiReader(file) {

					@Override
					protected void processLine(String line) {
						data.add(new CylinderTestData(line));
					}

					@Override
					public void done() {
						System.out.println("Done reading data file.");
					}
					
				};
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		else {
			System.out.println("Did not find cylinder data file [" + file.getAbsolutePath() + "]");
		}
		
		CylinderTestData array[] = new CylinderTestData[data.size()];
		return data.toArray(array);
	}


}
