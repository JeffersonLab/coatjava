package cnuphys.adaptiveSwim.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.CLAS12Swim.geometry.Cylinder;
import cnuphys.lund.AsciiReader;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
import cnuphys.swimtest.CSVWriter;

public class BadCylinderTest {

	
	
	public static void badCylinderTest() {

		//get data from csv data file
		CylinderTestData testData[] = readDataFile();

		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
		System.err.println("Swim to z-line test");
		
		readDataFile();
		
	    //for writing results to CSV
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "testResults/badCylinder.csv");


	    CSVWriter writer = new CSVWriter(file);
	    writer.writeRow("Swim to cylinder");
	    writer.newLine();

		// write the header row
		writer.writeRow("charge", "xo (cm)", "yo (cm)", "zo (cm)", "p (GeV/c)", "theta (deg)", "phi (deg)", "status",
				"xf_rho", "yf_rho", "zf_rho", "s_rho;", "npnt_rho", "dist_rho",
				"status", "xf_cyl", "yf_cyl", "zf_cyl", "s_cyl;", "npnt_cyl", "dist_cyl");


		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); //c12
		double c12Tolerance = 1.0e-5;
		
	    double delC12 = 0;
	    int nsC12 = 0;

		
		for (CylinderTestData data : testData) {
			
			Cylinder cylinder = new Cylinder(data.CLP1, data.CLP2, data.r);

			int charge = data.charge;
			double xo = data.xo;
			double yo = data.yo;
			double zo = data.zo;
			double p = data.p;
			double theta = data.theta;
			double phi = data.phi;
			
			writer.writeStartOfRow(charge, xo, yo, zo, p, theta, phi);

			CLAS12SwimResult c12Res = clas12Swimmer.swimRho(charge, xo, yo, zo,
					p, theta, phi, data.r, data.accuracy, 
					data.sMax, data.stepSize,c12Tolerance);

			delC12 += swimC12CylinderSwimResult(writer, data, c12Res, cylinder);
			nsC12 += c12Res.getNStep();


			c12Res = clas12Swimmer.swimCylinder(charge, xo, yo, zo,
					p, theta, phi, cylinder, data.accuracy, 
					data.sMax, data.stepSize,c12Tolerance);

			delC12 += swimC12CylinderSwimResult(writer, data, c12Res, cylinder);
			nsC12 += c12Res.getNStep();

			writer.newLine();

		}



		writer.close();
		System.err.println("done");

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
		File file = new File(homeDir, "swimTestInput/badCylinderSwim.csv");

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


