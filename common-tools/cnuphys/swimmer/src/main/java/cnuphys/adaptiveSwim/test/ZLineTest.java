package cnuphys.adaptiveSwim.test;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.CLAS12Swim.CLAS12Values;
import cnuphys.magfield.FastMath;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
import cnuphys.swimtest.RandomData;

public class ZLineTest {

	// swim to the offset beamline
	public static void zLineTest(int n, long seed) {
		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
		System.err.println("Swim to z-line test");

		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); // c12

		double h = 1.e-3; // starting

		double sMax = 200; // cm
		double c12Tolerance = 1.0e-7;
		double accuracy = 1.0e-4; // cm

		RandomData forwardData = new RandomData(n, seed, -1, 2, -1, 2, -2, 4); // cm!!
		RandomData backwardData = new RandomData(n);
		int bad = -1;

		// generate the backward data
		for (int i = 0; i < n; i++) {
		//	for (int i = bad; i < bad+1; i++) {

			int charge = forwardData.charge[i];
			double xo = forwardData.xo[i];
			double yo = forwardData.yo[i];
			double zo = forwardData.zo[i];
			double p = forwardData.p[i];
			double theta = forwardData.theta[i];
			double phi = forwardData.phi[i];

			CLAS12SwimResult c12Res = clas12Swimmer.swim(charge, xo, yo, zo, p, theta, phi, sMax, h, c12Tolerance);

			if (i == bad) {
				System.err.println("forward swim \n" + c12Res);
			}
			reverse(c12Res, backwardData, i);
		}


		System.err.println("\n\n SWIM TO Z-LINE TEST");


		double d[] = new double[n];
		double dsq[] = new double[n];

		int maxIndex = 0;
		double maxD = 0;

		for (int i = 0; i < n; i++) {
	//	for (int i = bad; i < bad+1; i++) {

			double xtarg = forwardData.xo[i];
			double ytarg = forwardData.yo[i];
			
			//backward data
			int q = backwardData.charge[i];
			double xo = backwardData.xo[i];
			double yo = backwardData.yo[i];
			double zo = backwardData.zo[i];
			double p = backwardData.p[i];
			double theta = backwardData.theta[i];
			double phi = backwardData.phi[i];

			CLAS12SwimResult c12res = clas12Swimmer.swimZLine(q, xo, yo, zo, p, 
					theta, phi, xtarg, ytarg, accuracy, 1.2*sMax, h, c12Tolerance);

			if (i == bad) {
				System.err.println("*** z line swim \n" + c12res);
			}


			double u[] = c12res.getFinalValues().getU();
			
			double dx = u[0] - xtarg;
			double dy = u[1] - ytarg;
			
			d[i] = Math.hypot(dx, dy);

			if (d[i] > maxD) {
				maxD = d[i];
				maxIndex = i;
			}
			dsq[i] = d[i]*d[i];
		}

		statReport("offest beamline final del ", d, dsq);
		System.err.println("max del = " + maxD + " at index " + maxIndex);

		System.err.println("done");
	}

	static private void statReport(String name, double x[], double xsq[]) {
		int n = x.length;
		double xavg = 0;
		double xavg2 = 0;

		for (int i = 0; i < n; i++) {
			xavg += x[i];
			xavg2 += xsq[i];
		}
		xavg /= n;
		xavg2 /= n;
		double rms = Math.sqrt(xavg2 - xavg*xavg);
		System.err.println(name + "avg = " + xavg + " sigma = " + rms);
	}

	//reverse for backward swim
	static private void reverse(CLAS12SwimResult c12Res, RandomData backwardData, int index) {
 		CLAS12Values c12Values = c12Res.getFinalValues();
 		double  uf[] = c12Values.getU();

 		backwardData.charge[index] = -c12Values.q;
 		backwardData.xo[index] = uf[0];
 		backwardData.yo[index] = uf[1];
 		backwardData.zo[index] = uf[2];
 		backwardData.p[index] = c12Values.p;
 		double tx = -uf[3];
 		double ty = -uf[4];
 		double tz = -uf[5];

 		backwardData.theta[index] = FastMath.acos2Deg(tz);
 		backwardData.phi[index] = FastMath.atan2Deg(ty, tx);


	}
}
