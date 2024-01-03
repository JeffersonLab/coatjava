package cnuphys.adaptiveSwim.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.adaptiveSwim.AdaptiveSwimException;
import cnuphys.adaptiveSwim.AdaptiveSwimResult;
import cnuphys.adaptiveSwim.AdaptiveSwimmer;
import cnuphys.adaptiveSwim.geometry.Cylinder;
import cnuphys.lund.AsciiReader;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
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
	    		"status", "xf_as", "yf_as", "zf_as", "s_as", "npnt_as", "dist_as", 
	    		"status", "xf_c12", "yf_c12", "zf_c12", "s_c12", "npnt_c12", "dist_c12");
	    
	    
		AdaptiveSwimmer adaptiveSwimmer = new AdaptiveSwimmer(); //AS
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); //c12
		
		//results for old 
		AdaptiveSwimResult result = new AdaptiveSwimResult(true);
		

		double asTolerance = 1.0e-6;
		double c12Tolerance = 1.0e-5;
		
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

	    double delAS = 0;
	    double delC12 = 0;
	    int nsAS = 0;
	    int nsC12 = 0;


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
			
			// AS
			try {
				adaptiveSwimmer.swimCylinder(charge, xo, yo, zo, p, theta, phi,
						data.CLP1, data.CLP2, data.r,
						data.accuracy, data.sMax, data.stepSize, asTolerance, result);
				
				delAS += swimCylinderSwimResult(writer, data, result);
				nsAS += result.getNStep();

			} catch (AdaptiveSwimException e) {
				System.err.println("AS Swimmer Failed." + "  final pathlength = " + result.getS());
				e.printStackTrace();
			}

			result.reset();
			
			// CLAS12Swimmer
			CLAS12SwimResult c12Res = clas12Swimmer.swimCylinder(charge, 100*xo, 100*yo, 100*zo, 
					p, theta, phi, c12Cylinder[idx], 100*data.accuracy, 100*data.sMax, 100*data.stepSize, c12Tolerance);
			
			delC12 += swimC12CylinderSwimResult(writer, data, c12Res, c12Cylinder[idx]);
			nsC12 += c12Res.getNStep();
			
			writer.newLine();

			idx++;
		}
		writer.close();
		delAS /=testData.length;
		delC12 /=testData.length;
		nsAS = (int)(((double)nsAS)/testData.length);
		nsC12 = (int)(((double)nsC12)/testData.length);

		System.err.println("done with main test.");
		System.err.println(String.format("avg delAS = %6.2e cm **  avg delC12 = %6.2e cm  ", delAS, delC12));
		System.err.println(String.format("avg nsAS = %d **  avg nsC12 = %d  ", nsAS, nsC12));
		System.err.println("Now timing test.");

		//timing test
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        bean.setThreadCpuTimeEnabled(true);

		long c12Time;
		long asTime;
		
		int numTestRun = 200;

		
	    long start = bean.getCurrentThreadCpuTime();
	    
	    
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

				// AS
				try {
					adaptiveSwimmer.swimCylinder(charge, xo, yo, zo, p, theta, phi, 
							data.CLP1, data.CLP2, data.r, data.accuracy,
							data.sMax, data.stepSize, asTolerance, result);

				} catch (AdaptiveSwimException e) {
					System.err.println("AS Swimmer Failed." + "  final pathlength = " + result.getS());
					e.printStackTrace();
				}
			}
		}
		asTime = bean.getCurrentThreadCpuTime() - start;
		
	    start = bean.getCurrentThreadCpuTime();
		
	
		for (int i = 0; i < numTestRun; i++) {
			idx = 0;
			for (CylinderTestData data : testData) {

				int charge = data.charge;
				double xo = data.xo;
				double yo = data.yo;
				double zo = data.zo;
				double p = data.p;
				double theta = data.theta;
				double phi = data.phi;

				clas12Swimmer.swimCylinder(charge, 100*xo, 100*yo, 100*zo, 
						p, theta, phi, c12Cylinder[idx], 100*data.accuracy, 100*data.sMax, 100*data.stepSize, c12Tolerance);

				idx++;
			}
		}

		c12Time = bean.getCurrentThreadCpuTime() - start;

		System.err.println("as time: " + asTime);
		System.err.println("c12 time: " + c12Time);
		System.err.println("ratio as/c12 = " + (double)asTime/(double)c12Time);

		System.err.println("done");

	}
	
	
	//swimCylinder results
	private static double swimCylinderSwimResult(CSVWriter writer, CylinderTestData data, AdaptiveSwimResult result) {
		double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString()); 
		double dist = 0;

		//uf is NOT the intersection
		if (result.getStatus() == AdaptiveSwimmer.SWIM_SUCCESS) {
			double[] uf = result.getU();
			dist = data.cylinder.signedDistance(uf[0], uf[1], uf[2]);
			dist = Math.abs(dist);
			writer.writeStartOfRow(100*uf[0], 100*uf[1], 100*uf[2], 100*result.getS(), result.getNStep(), 100*dist);


		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, 0, NaN);
		}
		return 100*dist;
	}
	
	//swimCylinder results
	private static double swimC12CylinderSwimResult(CSVWriter writer, CylinderTestData data, 
			CLAS12SwimResult result, Cylinder cylinder) {
		double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString()); 
		double dist = 0;

		//uf is NOT the intersection
		if (result.getStatus() == CLAS12Swimmer.SWIM_SUCCESS) {
			double[] uf = result.getFinalU();
			dist = cylinder.signedDistance(uf[0], uf[1], uf[2]);
			dist = Math.abs(dist);
			writer.writeStartOfRow(uf[0], uf[1], uf[2], result.getPathLength(), result.getNStep(), dist);


		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, 0, NaN);
		}
		
		return dist;
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
