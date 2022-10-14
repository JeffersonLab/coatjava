package cnuphys.adaptiveSwim.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import cnuphys.adaptiveSwim.AdaptiveSwimException;
import cnuphys.adaptiveSwim.AdaptiveSwimResult;
import cnuphys.adaptiveSwim.AdaptiveSwimmer;
import cnuphys.adaptiveSwim.geometry.Plane;
import cnuphys.lund.AsciiReader;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
import cnuphys.rk4.RungeKuttaException;
import cnuphys.swim.Swimmer;
import cnuphys.swimtest.CSVWriter;
import cnuphys.swimtest.SwimTest;

public class InterpPlaneTest {

	//swim to a fixed plane
	public static void interpPlaneTest() {
		
		//get data from csv data file
		PlaneTestData testData[] = readDataFile();
	    
		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
	    System.err.println("Interpolate plane test");
	    
	    //for writing results to CSV
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "testResults/inerpolateToPlane.csv");

	    CSVWriter writer = new CSVWriter(file);
	    writer.writeRow("Swim to fixed Plane");
	    writer.newLine();
	    
	    //write the header row
	    writer.writeRow("charge", "xo (m)", "yo (m)", "zo (m)", "p (GeV/c)", "theta (deg)", "phi (deg)", 
	    		"status", "xf_int", "yf_int", "zf_int", "s_int", "dist_int", 
	    		"status", "xf_gag", "yf_gag", "zf_gag", "s_gag", "dist_gag");
	    
//		Swimmer swimmer = new Swimmer(); //old
		AdaptiveSwimmer adaptiveSwimmer = new AdaptiveSwimmer(); //new
		
		//results for old and new
		AdaptiveSwimResult result = new AdaptiveSwimResult(true);
		
		double eps = 1.0e-6;
				

		for (PlaneTestData data : testData) {
			int charge = data.charge;
			double xo = data.xo;
			double yo = data.yo;
			double zo = data.zo;
			double p = data.p;
			double theta = data.theta;
			double phi = data.phi;

			
			result.reset();
			writer.writeStartOfRow(charge, 100 * xo, 100 * yo, 100 * zo, p, theta, phi);

			// acc req
			
			try {
				adaptiveSwimmer.swimPlaneInterp(charge, xo, yo, zo, p, 
						theta, phi, data.plane, data.accuracy,
						data.sMax, data.stepSize, eps, result);
				
				planeSwimResult(writer, data.plane, result);

			} catch (AdaptiveSwimException e1) {
				System.err.println("OLD interp plane swimmer Failed." + "  final pathlength = " + result.getFinalS());
				e1.printStackTrace();
			}

			result.reset();

			// gagik suggestion
			try {
				adaptiveSwimmer.swimPlaneInterp(charge, xo, yo, zo, p, 
						theta, phi, data.plane,
						data.sMax, data.stepSize, eps, result);
				
				planeSwimResult(writer, data.plane, result);
				
			
			} catch (AdaptiveSwimException e) {
				System.err.println("NEW interp plane swimmer Failed." + "  final pathlength = " + result.getFinalS());
				e.printStackTrace();
			}

			writer.newLine();
		} //for
		
		writer.close();
		System.err.println("done with main test. Now timing test.");

		//timing test
		long threadId = Thread.currentThread().getId();
		long intTime;
		long gagTime;
		
		long start = SwimTest.cpuTime(threadId);

		for (PlaneTestData data : testData) {
			
			int charge = data.charge;
			double xo = data.xo;
			double yo = data.yo;
			double zo = data.zo;
			double p = data.p;
			double theta = data.theta;
			double phi = data.phi;

			
			result.reset();

			// INT
			try {
				adaptiveSwimmer.swimPlaneInterp(charge, xo, yo, zo, p, 
						theta, phi, data.plane, data.accuracy,
						data.sMax, data.stepSize, eps, result);
			
			} catch (AdaptiveSwimException e) {
				System.err.println("INT Swimmer Failed." + "  final pathlength = " + result.getS());
				e.printStackTrace();
			}
		} //for
		
		intTime = SwimTest.cpuTime(threadId) - start;
		
		start = SwimTest.cpuTime(threadId);

		for (PlaneTestData data : testData) {
			
			int charge = data.charge;
			double xo = data.xo;
			double yo = data.yo;
			double zo = data.zo;
			double p = data.p;
			double theta = data.theta;
			double phi = data.phi;

			
			result.reset();


			// GAGIK
			
			try {
				adaptiveSwimmer.swimPlaneInterp(charge, xo, yo, zo, p, 
						theta, phi, data.plane,
						data.sMax, data.stepSize, eps, result);
				

			} catch (AdaptiveSwimException e1) {
				System.err.println("INT Swimmer Failed." + "  final pathlength = " + result.getS());
				e1.printStackTrace();
			}

	
		} //for	
		
		gagTime = SwimTest.cpuTime(threadId) - start;

		System.err.println("int time: " + intTime);
		System.err.println("gag time: " + gagTime);
		System.err.println("ratio gag/int = " + (double)gagTime/(double)intTime);

		System.err.println("done");

	}
	
	
	private static void planeSwimResult(CSVWriter writer, Plane plane, AdaptiveSwimResult result) {
		double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString()); 

		double[] uf = result.getU();
		int status = result.getStatus();
		if (status == AdaptiveSwimmer.SWIM_SUCCESS) {
			double swimDist = plane.distance(uf);
			writer.writeStartOfRow(uf[0], uf[1], uf[2], result.getS(), swimDist);


		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN);
		}

		
	}

	//read a csv data file
	private static PlaneTestData[] readDataFile() {
		
		ArrayList<PlaneTestData> data = new ArrayList<>();
		File file = new File("./cnuphys/adaptiveSwim/test/planedata.csv");
		
		if (file.exists()) {
			System.out.println("Found plane data file");
			try {
				new AsciiReader(file) {

					@Override
					protected void processLine(String line) {
						data.add(new PlaneTestData(line));
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
			System.out.println("Did not find plane data file [" + file.getAbsolutePath() + "]");
		}
		
		PlaneTestData array[] = new PlaneTestData[data.size()];
		return data.toArray(array);
	}
}