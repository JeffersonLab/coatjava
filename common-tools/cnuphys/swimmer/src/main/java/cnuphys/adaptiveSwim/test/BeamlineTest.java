package cnuphys.adaptiveSwim.test;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.CLAS12Swim.CLAS12Values;
import cnuphys.magfield.FastMath;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
import cnuphys.swimtest.RandomData;

public class BeamlineTest {

	// swim to the beamline
	public static void beamLineTest(int n, long seed) {
		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
		System.err.println("Swim to beamline test");

		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); // c12

		double h = 1.e-3; // starting

		double sMax = 200; // cm
		double c12Tolerance = 1.0e-6;
		double accuracy = 1.0e-4; // cm

		RandomData forwardData = new RandomData(n, seed, 0, 0, 0, 0, -2, 4); // cm!!
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


		//test the backward swims

//		double diff[] = new double[n];
//		double diffsq[] = new double[n];
//
////		for (int i = 0; i < n; i++) {
//		for (int i = bad; i < bad+1; i++) {
//			//forward data (original vertex
//			double xf = forwardData.xo[i];
//			double yf = forwardData.yo[i];
//			double zf = forwardData.zo[i];
//
//			//backward data
//			int qb = backwardData.charge[i];
//			double xb = backwardData.xo[i];
//			double yb = backwardData.yo[i];
//			double zb = backwardData.zo[i];
//			double pb = backwardData.p[i];
//			double thetab = backwardData.theta[i];
//			double phib = backwardData.phi[i];
//
//
//			//backward swim
//			CLAS12SwimResult c12ResB = clas12Swimmer.swim(qb, xb, yb, zb, pb, thetab, phib, sMax, h, c12Tolerance);
//			System.err.println("backward swim \n" + c12ResB);
//
//			double ufb[] = c12ResB.getFinalValues().getU();
//			double dx = xf - ufb[0];
//			double dy = yf - ufb[1];
//			double dz = zf - ufb[2];
//			diffsq[i] = dx*dx + dy*dy + dz*dz;
//			diff[i] = Math.sqrt(diffsq[i]);
//		}
//
//		statReport("backward swim delta ", diff, diffsq);
//
		//now the real test
		System.err.println("\n\n SWIM TO BEAMLINE TEST");


		double rho[] = new double[n];
		double rhosq[] = new double[n];

		int maxIndex = 0;
		double maxRho = 0;

		for (int i = 0; i < n; i++) {
	//	for (int i = bad; i < bad+1; i++) {

			//backward data
			int q = backwardData.charge[i];
			double xo = backwardData.xo[i];
			double yo = backwardData.yo[i];
			double zo = backwardData.zo[i];
			double p = backwardData.p[i];
			double theta = backwardData.theta[i];
			double phi = backwardData.phi[i];

			CLAS12SwimResult c12res = clas12Swimmer.swimBeamline(q, xo, yo, zo, p, theta, phi, accuracy, 1.2*sMax, h, c12Tolerance);

			if (i == bad) {
				System.err.println("*** beamline swim \n" + c12res);
			}


			double u[] = c12res.getFinalValues().getU();
			rho[i] = Math.hypot(u[0], u[1]);

			if (rho[i] > maxRho) {
				maxRho = rho[i];
				maxIndex = i;
			}
			rhosq[i] = rho[i]*rho[i];
		}

		statReport("beamline final rho ", rho, rhosq);
		System.err.println("max rho = " + maxRho + " at index " + maxIndex);

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
