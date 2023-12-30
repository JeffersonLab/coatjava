package cnuphys.adaptiveSwim.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.adaptiveSwim.geometry.Sphere;
import cnuphys.lund.AsciiReader;
import cnuphys.magfield.FieldProbe;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
import cnuphys.swim.DefaultSphereStopper;
import cnuphys.swim.SwimTrajectory;
import cnuphys.swim.Swimmer;
import cnuphys.swimtest.CSVWriter;

public class SphereTest {

	public static void sphereTest() {
		//get data from csv data file
		SphereTestData testData[] = readDataFile();

		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
	    System.err.println("Swim to sphere test");

	    //for writing results to CSV
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "testResults/swimSphere.csv");

	    CSVWriter writer = new CSVWriter(file);
	    writer.writeRow("Swim to sphere");
	    writer.newLine();

	    //write the header row
	    writer.writeRow("charge", "xo (m)", "yo (m)", "zo (m)", "p (GeV/c)", "theta (deg)", "phi (deg)",
	    		"start", "status", "xf_old", "yf_old", "zf_old", "s_old", "bdl_old", "d_old",
	    		"status", "xf_c12", "yf_c12", "zf_c12", "s_c12", "NumPnt", "bdl_c12", "d_c12");

		Swimmer swimmer = new Swimmer(); //old
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); //c12

		//create the clas12 spheres
		Sphere c12Sphere[] = new Sphere[testData.length];
		for (int i = 0; i < testData.length; i++) {
			SphereTestData data = testData[i];
			c12Sphere[i] = new Sphere(100*data.r);
		}


		double eps = 1.0e-6;

		int idx = 0;

		for (SphereTestData data : testData) {

			int charge = data.charge;
			double xo = data.xo;
			double yo = data.yo;
			double zo = data.zo;
			double p = data.p;
			double theta = data.theta;
			double phi = data.phi;

			double rsq = data.r * data.r;
			boolean inside = xo*xo + yo*yo + zo*zo < rsq;

			writer.writeStartOfRow(charge, 100*xo, 100*yo, 100*zo, p, theta, phi);
			writer.writeStartOfRow(inside ? "inside" : "outside");

	        int dir = (inside) ? 1 : -1;

			DefaultSphereStopper stopper = new DefaultSphereStopper(data.r, dir);
			SwimTrajectory traj = swimmer.swim(charge, xo, yo, zo, p, theta, phi, stopper, data.sMax, data.stepSize,
			        0.0005);

	        if(traj!=null) {
	        	traj.computeBDL(swimmer.getProbe());
	        }
	        else {
	        	System.out.println("Bad sphere swim");
	        }
	        swimCylinderSwimResult(writer, data, traj);

			// CLAS12Swimmer
			CLAS12SwimResult c12Result = clas12Swimmer.swimSphere(charge, 100*xo, 100*yo, 100*zo,
					p, theta, phi, c12Sphere[idx], 100*data.accuracy, 100*data.sMax, 100*data.stepSize, 100*eps);

			swimC12SphereSwimResult(writer, data, c12Result, c12Sphere[idx]);

			writer.newLine();
			idx++;

		}



		writer.close();
		System.err.println("done with main test. Now timing test.");

		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        bean.setThreadCpuTimeEnabled(true);

		//timing test
		long c12Time;
		long oldTime;

	    long start = bean.getCurrentThreadCpuTime();

		for (SphereTestData data : testData) {

			int charge = data.charge;
			double xo = data.xo;
			double yo = data.yo;
			double zo = data.zo;
			double p = data.p;
			double theta = data.theta;
			double phi = data.phi;

			double rsq = data.r * data.r;
			boolean inside = xo*xo + yo*yo + zo*zo < rsq;
	        int dir = (inside) ? 1 : -1;

			DefaultSphereStopper stopper = new DefaultSphereStopper(data.r, dir);
			SwimTrajectory traj = swimmer.swim(charge, xo, yo, zo, p, theta, phi, stopper, data.sMax, data.stepSize,
			        0.0005);

	        if(traj!=null) {
	        	traj.computeBDL(swimmer.getProbe());
	        }
	        else {
	        	System.out.println("Bad sphere swim");
	        }
		}

		oldTime = bean.getCurrentThreadCpuTime() - start;

	    start = bean.getCurrentThreadCpuTime();

	    idx = 0;
		for (SphereTestData data : testData) {
			int charge = data.charge;
			double xo = data.xo;
			double yo = data.yo;
			double zo = data.zo;
			double p = data.p;
			double theta = data.theta;
			double phi = data.phi;

			// CLAS12Swimmer
			clas12Swimmer.swimSphere(charge, 100*xo, 100*yo, 100*zo,
					p, theta, phi, c12Sphere[idx], 100*data.accuracy, 100*data.sMax, 100*data.stepSize, 100*eps);
			idx++;
		}


		c12Time = bean.getCurrentThreadCpuTime() - start;

		System.err.println("old time: " + oldTime);
		System.err.println("c12 time: " + c12Time);
		System.err.println("ratio as/c12 = " + (double)oldTime/(double)c12Time);

		System.err.println("done");

	}

	//swimCylinder results
	private static void swimCylinderSwimResult(CSVWriter writer, SphereTestData data, SwimTrajectory traj) {
		double NaN = Double.NaN;

		writer.writeStartOfRow((traj == null) ? "FAIL" : "Traj_NP=" + traj.size());

//	    writer.writeRow("charge", "xo (m)", "yo (m)", "zo (m)", "p (GeV/c)", "theta (deg)", "phi (deg)",
//	    		"status", "xf_old", "yf_old", "zf_old", "s_old", "bdl_old", "d_old");

		if (traj != null) {
			double[] uf = traj.lastElement();
			double dist = data.sphere.signedDistance(uf[0], uf[1], uf[2]);
			writer.writeStartOfRow(100 * uf[0], 100 * uf[1], 100 * uf[2], 100 * uf[6], uf[7],
					Math.abs(dist));

		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN, NaN, NaN);
		}
	}

	//swimCylinder results
	private static void swimC12SphereSwimResult(CSVWriter writer, SphereTestData data,
			CLAS12SwimResult result, Sphere sphere) {
		double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString());

		//uf is NOT the intersection
		if (result.getStatus() == CLAS12Swimmer.SWIM_SUCCESS) {
			double[] uf = result.getFinalU();
			double dist = sphere.signedDistance(uf[0], uf[1], uf[2]);
			
			result.getTrajectory().computeBDL(FieldProbe.factory());
			double bdl = result.getTrajectory().getComputedBDL();

			writer.writeStartOfRow(uf[0], uf[1], uf[2], result.getPathLength(), 
					result.getNumStep(), bdl/100, Math.abs(dist)/100);


		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN, 0, NaN, NaN);
		}
	}




	//rear a csv data file
	private static SphereTestData[] readDataFile() {

		ArrayList<SphereTestData> data = new ArrayList<>();

		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "swimTestInput/spheredata.csv");

		if (file.exists()) {
			System.out.println("Found sphere data file");
			try {
				new AsciiReader(file) {

					@Override
					protected void processLine(String line) {
						data.add(new SphereTestData(line));
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
			System.out.println("Did not find sphere data file [" + file.getAbsolutePath() + "]");
		}

		SphereTestData array[] = new SphereTestData[data.size()];
		return data.toArray(array);
	}

}
