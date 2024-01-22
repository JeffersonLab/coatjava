package cnuphys.adaptiveSwim.test;

import java.io.File;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.CLAS12Swim.CLAS12Values;
import cnuphys.magfield.FieldProbe;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
import cnuphys.swimtest.CSVWriter;
import cnuphys.swimtest.RandomData;

public class FixedTest {

	private static FieldProbe _probe;


	//swim to a final path length
	public static void fixedSwimTest(int n, long seed) {

		n = 3;

		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);

		_probe = FieldProbe.factory(MagneticFields.getInstance().getActiveField());

	    System.err.println("Swim to fixed pathlength test");

	    //for writing results to CSV
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "testResults/fixedSwim.csv");

	    CSVWriter writer = new CSVWriter(file);
	    writer.writeRow("Swim to final path length");
	    writer.newLine();

	    //write the header row
	    writer.writeRow("charge", "xo (m)", "yo (m)", "zo (m)", "p (GeV/c)", "theta (deg)", "phi (deg)",
	    		"status", "xf_fix", "yf_fix", "zf_fix", "s_fix", "npnt_fix", "bdl_fix",
	    		"status", "xf_c12", "yf_c12", "zf_c12", "s_c12", "npnt_as", "bdl_c12");

		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); //new

		double hinit = 1e-5; // starting stepsize in cm
		double hfixed = 0.13; // fixed stepsize in cm

		double sMax = 800; // cm
		double c12Tolerance = 1.0e-5;

		//generate some random data
		RandomData data = new RandomData(n, seed);

	    int nsFix = 0;
	    int nsC12 = 0;


		for (int i = 0; i < n; i++) {

			int charge = data.charge[i];
			double xo = data.xo[i];
			double yo = data.yo[i];
			double zo = data.zo[i];
			double p = data.p[i];
			double theta = data.theta[i];
			double phi = data.phi[i];


			writer.writeStartOfRow(charge, xo, yo, zo, p, theta, phi);

			//fixed
			CLAS12SwimResult c12Res = clas12Swimmer.swimFixed(charge, xo, yo, zo, p, theta, phi, sMax, hfixed);
			c12SwimResult(writer, c12Res);
			nsFix += c12Res.getNStep();


			// c12
			c12Res = clas12Swimmer.swim(charge, xo, yo, zo, p, theta, phi, sMax, hinit, c12Tolerance);
			c12SwimResult(writer, c12Res);
			nsC12 += c12Res.getNStep();


			writer.newLine();


		}

		writer.close();

		nsFix = (int)(((double)nsFix)/n);
		nsC12 = (int)(((double)nsC12)/n);

		System.err.println("done with main test.");
		System.err.println(String.format("avg nsFix = %d **  avg nsC12 = %d  ", nsFix, nsC12));
		System.err.println("Now timing test.");


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
