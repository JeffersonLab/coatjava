package cnuphys.adaptiveSwim.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.CLAS12Swim.geometry.Sphere;
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
	    		"start", "status", "xf_old", "yf_old", "zf_old", "s_old", "bdl_old", "npnt_old", "dist_old",
	    		"status", "xf_c12", "yf_c12", "zf_c12", "s_c12", "bdl_c12", "npnt_c12", "dist_c12");

		Swimmer swimmer = new Swimmer(); //old
		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); //c12

		//create the clas12 spheres
		Sphere c12Sphere[] = new Sphere[testData.length];
		for (int i = 0; i < testData.length; i++) {
			SphereTestData data = testData[i];
			c12Sphere[i] = new Sphere(100*data.r);
		}


		double c12Tolerance = 1.0e-5;
	    double delOld = 0;
	    double delC12 = 0;
	    int nsOld = 0;
	    int nsC12 = 0;


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
	        	nsOld += traj.size();
	        }
	        else {
	        	System.out.println("Bad sphere swim");
	        }
	        delOld += swimSphereSwimResult(writer, data, traj);

			// CLAS12Swimmer
			CLAS12SwimResult c12res = clas12Swimmer.swimSphere(charge, 100*xo, 100*yo, 100*zo,
					p, theta, phi, c12Sphere[idx], 100*data.accuracy, 100*data.sMax, 100*data.stepSize, c12Tolerance);

			delC12 += swimC12SphereSwimResult(writer, data, c12res, c12Sphere[idx]);
			nsC12 += c12res.getNStep();

			writer.newLine();
			idx++;

		}



		writer.close();
		delOld /=testData.length;
		delC12 /=testData.length;
		nsOld = (int)(((double)nsOld)/testData.length);
		nsC12 = (int)(((double)nsC12)/testData.length);

		System.err.println("done with main test.");
		System.err.println(String.format("avg delOld = %6.2e cm **  avg delC12 = %6.2e cm  ", delOld, delC12));
		System.err.println(String.format("avg nsOld = %d **  avg nsC12 = %d  ", nsOld, nsC12));
		System.err.println("Now timing test.");

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
					p, theta, phi, c12Sphere[idx], 100*data.accuracy, 100*data.sMax, 100*data.stepSize, c12Tolerance);
			idx++;
		}


		c12Time = bean.getCurrentThreadCpuTime() - start;

		System.err.println("old time: " + oldTime);
		System.err.println("c12 time: " + c12Time);
		System.err.println("ratio as/c12 = " + (double)oldTime/(double)c12Time);

		System.err.println("done");

	}

	//swimSphere results
	private static double swimSphereSwimResult(CSVWriter writer, SphereTestData data, SwimTrajectory traj) {
		double NaN = Double.NaN;

		writer.writeStartOfRow("N/A");
		double dist = 0;

		if (traj != null) {
			double[] uf = traj.lastElement();
			dist = data.sphere.signedDistance(uf[0], uf[1], uf[2]);
			dist = Math.abs(dist);

			//a big distance indicates a failure for old swimmer
			if (dist > 1) {
				writer.writeStartOfRow(100 * uf[0], 100 * uf[1], 100 * uf[2], 100 * uf[6], 100 * uf[7], traj.size(),
						NaN);
			} else {
				writer.writeStartOfRow(100 * uf[0], 100 * uf[1], 100 * uf[2], 100 * uf[6], 100 * uf[7], traj.size(),
						100 * dist);
			}

		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN, NaN, 0, NaN);
		}

		//a big distance indicates a failure for old swimmer
		if (dist > 1) {
			dist = 0;
		}

		return 100*dist;
	}

	//swimCylinder results
	private static double swimC12SphereSwimResult(CSVWriter writer, SphereTestData data,
			CLAS12SwimResult result, Sphere sphere) {
		double NaN = Double.NaN;

		writer.writeStartOfRow(result.statusString());
		double dist = 0;

		//uf is NOT the intersection
		if (result.getStatus() == CLAS12Swimmer.SWIM_SUCCESS) {
			double[] uf = result.getFinalU();
			dist = sphere.signedDistance(uf[0], uf[1], uf[2]);
			dist = Math.abs(dist);

			result.getTrajectory().computeBDL(FieldProbe.factory());
			double bdl = result.getTrajectory().getComputedBDL();

			writer.writeStartOfRow(uf[0], uf[1], uf[2], result.getPathLength(),
					bdl, result.getNStep(), dist);


		} else {
			writer.writeStartOfRow(NaN, NaN, NaN, NaN, NaN, 0, NaN);
		}

		return dist;
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
