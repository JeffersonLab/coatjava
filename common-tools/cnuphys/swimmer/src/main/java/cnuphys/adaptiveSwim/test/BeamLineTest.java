package cnuphys.adaptiveSwim.test;

import java.io.File;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Swimmer;
import cnuphys.CLAS12Swim.CLAS12Values;
import cnuphys.magfield.FastMath;
import cnuphys.magfield.MagneticFields;
import cnuphys.magfield.MagneticFields.FieldType;
import cnuphys.swimtest.CSVWriter;
import cnuphys.swimtest.RandomData;

public class BeamLineTest {

	

	//swim to a fixed rho
	public static void beamLineTest(int n, long seed) {
		MagneticFields.getInstance().setActiveField(FieldType.COMPOSITE);
	    System.err.println("Swim to beamline test");
	    
	    //for writing results to CSV
		String homeDir = System.getProperty("user.home");
		File file = new File(homeDir, "testResults/swimRho.csv");

	    CSVWriter writer = new CSVWriter(file);
	    writer.writeRow("Swim to fixed Rho");
	    writer.newLine();
	    
	    
	    //write the header row
	    writer.writeRow("charge", "xo (m)", "yo (m)", "zo (m)", "p (GeV/c)", "theta (deg)", "phi (deg)",
	    		"start", "status", "xf_old", "yf_old", "zf_old", "s_old", "bdl_old", "npnt_old", "dist_old",
	    		"status", "xf_c12", "yf_c12", "zf_c12", "s_c12", "bdl_c12", "npnt_c12", "dist_c12");

		CLAS12Swimmer clas12Swimmer = new CLAS12Swimmer(); //c12
		
		double h = 1.e-3; // starting

		double sMax = 200; // cm
		double c12Tolerance = 1.0e-8;

		
		RandomData forwardData = new RandomData(n, seed, 0, 0, 0, 0, -2, 4); //cm!!
		RandomData backwardData = new RandomData(n);
		
		//generate the backward data
		for (int i = 0; i < n; i++) {
			
			int charge = forwardData.charge[i];
			double xo = forwardData.xo[i];
			double yo = forwardData.yo[i];
			double zo = forwardData.zo[i];
			double p = forwardData.p[i];
			double theta = forwardData.theta[i];
			double phi = forwardData.phi[i];

			
			CLAS12SwimResult c12Res = clas12Swimmer.swim(charge, xo, yo, zo, p, theta, phi, sMax, h, c12Tolerance);
			
			reverse(c12Res, backwardData, i);
			
//			System.err.println("forward swim \n" + c12Res);
		}
		
		
		
		//test the backward swims
		
		double maxDiff = 0;
		double avgDiff = 0;
		int maxIndex = 0;
		
		for (int i = 0; i < n; i++) {
			//forward data
			int qf = forwardData.charge[i];
			double xf = forwardData.xo[i];
			double yf = forwardData.yo[i];
			double zf = forwardData.zo[i];
			double pf = forwardData.p[i];
			double thetaf = forwardData.theta[i];
			double phif = forwardData.phi[i];
			
			//backward data
			int qb = backwardData.charge[i];
			double xb = backwardData.xo[i];
			double yb = backwardData.yo[i];
			double zb = backwardData.zo[i];
			double pb = backwardData.p[i];
			double thetab = backwardData.theta[i];
			double phib = backwardData.phi[i];

			//forward swim
			CLAS12SwimResult c12ResF = clas12Swimmer.swim(qf, xf, yf, zf, pf, thetaf, phif, sMax, h, c12Tolerance);
//			System.err.println("forward swim \n" + c12ResF);
			
			//backward swim
			CLAS12SwimResult c12ResB = clas12Swimmer.swim(qb, xb, yb, zb, pb, thetab, phib, sMax, h, c12Tolerance);
//			System.err.println("backward swim \n" + c12ResB);

			double ufb[] = c12ResB.getFinalValues().getU();
			double dx = xf - ufb[0];
			double dy = yf - ufb[1];
			double dz = zf - ufb[2];
			double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
			
			if (dist > maxDiff) {
				maxDiff = dist;
				maxIndex = i;
			}
			avgDiff += dist;
			
			
		}
		

		avgDiff /= n;
		
		System.err.println("maxDiff = " + maxDiff);
		System.err.println("avgDiff = " + avgDiff);
		
		
	//spell out worst case
		
		//forward data
		int qf = forwardData.charge[maxIndex];
		double xf = forwardData.xo[maxIndex];
		double yf = forwardData.yo[maxIndex];
		double zf = forwardData.zo[maxIndex];
		double pf = forwardData.p[maxIndex];
		double thetaf = forwardData.theta[maxIndex];
		double phif = forwardData.phi[maxIndex];
		
		//backward data
		int qb = backwardData.charge[maxIndex];
		double xb = backwardData.xo[maxIndex];
		double yb = backwardData.yo[maxIndex];
		double zb = backwardData.zo[maxIndex];
		double pb = backwardData.p[maxIndex];
		double thetab = backwardData.theta[maxIndex];
		double phib = backwardData.phi[maxIndex];

		//forward swim
		CLAS12SwimResult c12ResF = clas12Swimmer.swim(qf, xf, yf, zf, pf, thetaf, phif, sMax, h, c12Tolerance);
		System.err.println("WORST forward swim \n" + c12ResF);
		
		//backward swim
		CLAS12SwimResult c12ResB = clas12Swimmer.swim(qb, xb, yb, zb, pb, thetab, phib, sMax, h, c12Tolerance);
		System.err.println("WORST backward swim \n" + c12ResB);

		double ufb[] = c12ResB.getFinalValues().getU();
		double dx = xf - ufb[0];
		double dy = yf - ufb[1];
		double dz = zf - ufb[2];
		double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
		System.err.println("WORST dist = " + dist);


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
