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
import cnuphys.adaptiveSwim.geometry.Plane;
import cnuphys.lund.AsciiReader;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
import cnuphys.swimtest.CSVWriter;
import cnuphys.swimtest.SwimTest;

public class PlaneTest {

	//swim to a fixed plane
	public static void planeTest() {

		//get data from csv data file
		PlaneTestData testDataCM[] = readDataFile();
		
		PlaneTestData testDataMeter[] = new PlaneTestData[testDataCM.length];
		for (int i = 0; i < testDataCM.length; i++) {
			testDataMeter[i] = testDataCM[i].toMeters();
		}


		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
	    System.err.println("Swim to fixed plane test");

	    //for writing results to CSV
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "testResults/swimPlane.csv");

	    CSVWriter writer = new CSVWriter(file);
	    writer.writeRow("Swim to fixed Plane");
	    writer.newLine();

	    //write the header row
	    writer.writeRow("charge", "xo (cm)", "yo (cm)", "zo (cm)", "p (GeV/c)", "theta (deg)", "phi (deg)",
	    		"status", "xf_as", "yf_as", "zf_as", "s_as", "npnt_as", "dist_as",
	    		"status", "xf_c12", "yf_c12", "zf_c12", "s_c12", "npnt_c12", "dist_c12");

		AdaptiveSwimmer adaptiveSwimmer = new AdaptiveSwimmer(); //new
		CLAS12Swimmer swimmer = new CLAS12Swimmer();

		CLAS12SwimResult c12Res;

		//results for old and new
		AdaptiveSwimResult result = new AdaptiveSwimResult(true);

		double asTolerance = 1.0e-6;
		double c12Tolerance = 1.0e-5;

		PlaneTestData data;
		
	    double delAS = 0;
	    double delC12 = 0;

	    int nsAS = 0;
	    int nsC12 = 0;
	    
	    cnuphys.CLAS12Swim.geometry.Plane c12Plane[] = new cnuphys.CLAS12Swim.geometry.Plane[testDataCM.length];
		for (int i = 0; i < testDataCM.length; i++) {
			data = testDataCM[i];
			c12Plane[i] = new cnuphys.CLAS12Swim.geometry.Plane(data.plane.a, data.plane.b, data.plane.c, data.plane.d);
		}

		for (int i = 0; i < testDataCM.length; i++) {

			data = testDataMeter[i];
			
			result.reset();
			writer.writeStartOfRow(data.charge, 100*data.xo, 100*data.yo, 100*data.zo, data.p, data.theta, data.phi);

			// AS

			try {
				adaptiveSwimmer.swimPlane(data.charge, data.xo, data.yo, data.zo, data.p,
						data.theta, data.phi, data.plane, data.accuracy,
						data.sMax, data.stepSize,asTolerance, result);

				delAS += planeSwimResult(writer, data.plane, result);
				nsAS += result.getNStep();


			} catch (AdaptiveSwimException e) {
				System.err.println("NEW Swimmer Failed." + "  final pathlength = " + result.getFinalS());
				e.printStackTrace();
			}

			result.reset();

			data = testDataCM[i];
			// C12
			c12Res = swimmer.swimPlane(data.charge, data.xo, data.yo, data.zo, data.p, data.theta,
					data.phi, c12Plane[i],
					data.accuracy, data.sMax, data.stepSize, c12Tolerance);

			delC12 += planeSwimResult(writer, data.plane, c12Res);
			nsC12 += c12Res.getNStep();

			writer.newLine();
		} //for

		writer.close();
		delAS /=testDataCM.length;
		delC12 /=testDataCM.length;
		nsAS = (int)(((double)nsAS)/testDataCM.length);
		nsC12 = (int)(((double)nsC12)/testDataCM.length);

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

		for (int j = 0; j < numTestRun; j++) {
			for (int i = 0; i < testDataCM.length; i++) {

				data = testDataMeter[i];

				result.reset();

				try {
					adaptiveSwimmer.swimPlane(data.charge, data.xo, data.yo, data.zo, data.p, data.theta, data.phi,
							data.plane, data.accuracy, data.sMax, data.stepSize, asTolerance, result);

				} catch (AdaptiveSwimException e) {
					System.err.println("AS Swimmer Failed." + "  final pathlength = " + result.getFinalS());
					e.printStackTrace();
				}
			} // for
		}

		asTime = bean.getCurrentThreadCpuTime() - start;

		start = bean.getCurrentThreadCpuTime();

		for (int j = 0; j < numTestRun; j++) {
			for (int i = 0; i < testDataCM.length; i++) {

				data = testDataCM[i];

				// c12

				swimmer.swimPlane(data.charge, data.xo, data.yo, data.zo, data.p, data.theta, data.phi, c12Plane[i],
						data.accuracy, data.sMax, data.stepSize, c12Tolerance);

			} // for
		}

		c12Time = bean.getCurrentThreadCpuTime() - start;

		System.err.println("as time: " + asTime);
		System.err.println("c12 time: " + c12Time);
		System.err.println("ratio as/c12 = " + (double)asTime/(double)c12Time);

		System.err.println("done");

	}


	private static double planeSwimResult(CSVWriter writer, Plane plane, AdaptiveSwimResult result) {
		double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString());
		double swimDist = 0;

		double[] uf = result.getU();
		int status = result.getStatus();
		if (status == AdaptiveSwimmer.SWIM_SUCCESS) {
			swimDist = plane.distance(uf);
			writer.writeStartOfRow(100*uf[0], 100*uf[1], 100*uf[2], 100*result.getS(), result.getNStep(), 100*swimDist);


		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN, 0, NaN);
		}

		return 100*swimDist;

	}

	private static double planeSwimResult(CSVWriter writer, Plane plane, CLAS12SwimResult result) {
		double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString());
		double swimDist = 0;

		double[] uf = result.getFinalU();
		int status = result.getStatus();
		if (status == CLAS12Swimmer.SWIM_SUCCESS) {
			swimDist = plane.distance(uf);
			writer.writeStartOfRow(uf[0], uf[1], uf[2], result.getPathLength(), result.getNStep(), swimDist);


		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN, NaN, NaN, NaN, NaN, 0, NaN);
		}


		return swimDist;
	}


	//read a csv data file
	private static PlaneTestData[] readDataFile() {

		ArrayList<PlaneTestData> data = new ArrayList<>();
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "swimTestInput/planedata.csv");

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
