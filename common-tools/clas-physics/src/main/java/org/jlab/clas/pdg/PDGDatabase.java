/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.clas.pdg;

import java.util.HashMap;
import java.util.Map;

/**
 * PDG database class. Stores particle information in a Map. Particles information can be searched by LUND ID, GEANT ID, or simply the name of the particle.
 * 
 * @author gavalian
 */
public class PDGDatabase {

	static final HashMap<Integer, PDGParticle> particleDatabase = initDatabase();

	public PDGDatabase() {
	}

	public static boolean isValidId(int pid) {
		return particleDatabase.containsKey(pid);
		// ArrayList<String> ff = null;
	}

	public static boolean isValidPid(int pid) {
		return particleDatabase.containsKey(pid);
	}

	static public PDGParticle getParticleById(int pid) {
		if (particleDatabase.containsKey(pid) == true) {
			return particleDatabase.get(pid);
		}
		System.err.println("PDGDatabase::Error -> there is no particle with pid " + pid);
		return null;
	}

	public static void addParticle(PDGParticle part) {
		if (particleDatabase.containsKey(part.pid()) == true) {
			System.out.println("PDGDatabase::Error -> Particle with PID " + part.pid() + " already exists in the database.");
			return;
		}

		particleDatabase.put(part.pid(), part);
	}

	public static void addParticle(String name, int pid, double mass, int charge) {
		if (particleDatabase.containsKey(pid) == true) {
			System.out.println("PDGDatabase::Error -> Particle with PID " + pid + " already exists in the database.");
			return;
		}
		particleDatabase.put(pid, new PDGParticle(name, pid, mass, charge));
	}

	private static HashMap<Integer, PDGParticle> initDatabase() {
		HashMap<Integer, PDGParticle> particleMap = new HashMap<Integer, PDGParticle>();
		particleMap.put(11, new PDGParticle("e-", 11, 3, 0.0005, -1));
		particleMap.put(-11, new PDGParticle("e+", -11, 2, 0.0005, 1));
		particleMap.put(12, new PDGParticle("nue-", 12, 4, 0.320e-6, 0));
		particleMap.put(-12, new PDGParticle("nue+", -12, 4, 0.320e-6, 0));
		particleMap.put(13, new PDGParticle("mu-", 13, 6, 0.1056583715, -1));
		particleMap.put(-13, new PDGParticle("mu+", -13, 5, 0.1056583715, 1));
                particleMap.put(12, new PDGParticle("nue-", 14, 4, 0.320e-6, 0));
		particleMap.put(-12, new PDGParticle("nue+", -14, 4, 0.320e-6, 0));
		particleMap.put(22, new PDGParticle("gamma", 22, 1, 0.000, 0));
		particleMap.put(45, new PDGParticle("d", 45, 45,1.87705, 1));
                
		//Light mesons
                particleMap.put(211, new PDGParticle("pi+", 211, 8, 0.13957018, 1));
                particleMap.put(-211, new PDGParticle("pi-", -211, 9, 0.13957018, -1));
                particleMap.put(111, new PDGParticle("pi0", 111, 7, 0.134977, 0));
                particleMap.put(321, new PDGParticle("K+", 321, 11, 0.49367716, 1));
                particleMap.put(-321, new PDGParticle("K-", -321, 12, 0.49367716, -1));
                particleMap.put(311, new PDGParticle("K0", 311,0.497611, 0));
                particleMap.put(-311, new PDGParticle("K0bar", -311, 0.497611, 0));
                particleMap.put(130, new PDGParticle("K0_L", 130,  10,0.497611, 0));
                particleMap.put(310, new PDGParticle("K0_S", 310, 16, 0.497611, 0));
                particleMap.put(221, new PDGParticle("eta", 221, 17, 0.547862, 0));
                particleMap.put(331, new PDGParticle("eta'", 331,  0.95778, 0));
                particleMap.put(223, new PDGParticle("omega", 223, 0.78265, 0));
                particleMap.put(113, new PDGParticle("rho0", 113, 0.77526, 0));
                particleMap.put(213, new PDGParticle("rho+", 213, 0.77526, 1));
                particleMap.put(-213, new PDGParticle("rho-", -213, 12, 0.77526, -1));
                particleMap.put(333, new PDGParticle("phi", 333, 1.019461, 0));
                
                // a0 scalar mesons (I=1, J^PC = 0++), mass ~ 0.980 GeV
                particleMap.put(9000211, new PDGParticle("a0(980)+", 9000211, 0.980, 1));
                particleMap.put(9000111, new PDGParticle("a0(980)0", 9000111, 0.980, 0));
                particleMap.put(-9000211, new PDGParticle("a0(980)-", -9000211, 0.980, -1));

                // a1 axial vector mesons (I=1, J^PC = 1++), mass ~ 1.230 GeV
                particleMap.put(20213, new PDGParticle("a1(1260)+", 20213, 1.230, 1));
                particleMap.put(20113, new PDGParticle("a1(1260)0", 20113, 1.230, 0));
                particleMap.put(-20213, new PDGParticle("a1(1260)-", -20213, 1.230, -1));

                // b1 axial vector mesons (I=1, J^PC = 1+−), mass ~ 1.229 GeV
                particleMap.put(10213, new PDGParticle("b1(1235)+", 10213, 1.229, 1));
                particleMap.put(10113, new PDGParticle("b1(1235)0", 10113, 1.229, 0));
                particleMap.put(-10213, new PDGParticle("b1(1235)-", -10213, 1.229, -1));

                // f0(980) mesons (I=0, J^PC = 0++)
                particleMap.put(9000221, new PDGParticle("f0(980)", 9000221, 0.980, 0));
                // f1 axial vector mesons (I=0, J^PC = 1++), neutral only
                particleMap.put(20223, new PDGParticle("f1(1285)", 20223, 1.2819, 0));
                particleMap.put(10333, new PDGParticle("f1(1420)", 10333, 1.4264, 0));

                // h1 axial vector mesons (I=0, J^PC = 1+−), neutral only
                particleMap.put(10223, new PDGParticle("h1(1170)", 10223,  1.170, 0));
                particleMap.put(10331, new PDGParticle("h1(1380)", 10331, 1.380, 0));

                // Excited kaon resonances (K* states)
                particleMap.put(323, new PDGParticle("K*(892)+", 323, 0.892, 1));
                particleMap.put(-323, new PDGParticle("K*(892)-", -323, 0.892, -1));
                particleMap.put(313, new PDGParticle("K*(892)0", 313, 0.892, 0));
                particleMap.put(-313, new PDGParticle("K*(892)0bar", -313,  0.892, 0));
                particleMap.put(315, new PDGParticle("K*(1410)+", 315, 1.410, 1));
                particleMap.put(-315, new PDGParticle("K*(1410)-", -315, 1.410, -1));
                particleMap.put(325, new PDGParticle("K*(1410)0", 325, 1.410, 0));
                particleMap.put(-325, new PDGParticle("K*(1410)0bar", -325,  1.410, 0));
                
                // Charmonium mesons 
                // J/psi mesons (1S state, 1^-)
                particleMap.put(443, new PDGParticle("J/psi", 443, 3.0969, 0));
                // eta_c mesons (1S state, 0^-)
                particleMap.put(441, new PDGParticle("eta_c", 441, 2.981, 0));
                // chi_c0 (2P state, 0++)
                particleMap.put(444, new PDGParticle("chi_c0", 444, 3.414, 0));
                // chi_c1 (2P state, 1++)
                particleMap.put(445, new PDGParticle("chi_c1", 445, 3.510, 0));
                // chi_c2 (2P state, 2++)
                particleMap.put(446, new PDGParticle("chi_c2", 446, 3.556, 0));
                // eta_c(2S) (2S state, 0^-)
                particleMap.put(447, new PDGParticle("eta_c(2S)", 447,  3.634, 0));
                // J/psi(2S) (2S state, 1^-)
                particleMap.put(448, new PDGParticle("J/psi(2S)", 448, 3.686, 0));
                // h_c (1P state, 1^+)
                particleMap.put(4415, new PDGParticle("h_c", 4415, 3.525, 0));
                // psi(2S) (1^-)
                particleMap.put(100443, new PDGParticle("psi(2S)", 100443, 3.686, 0));
                // psi(3770) (2S state, 1^-)
                particleMap.put(100443, new PDGParticle("psi(3770)", 100443,  3.770, 0));
                // eta_c(3S) (3S state, 0^-)
                particleMap.put(100447, new PDGParticle("eta_c(3S)", 100447,  3.750, 0));

                // Light baryons
                
		particleMap.put(2212, new PDGParticle("p", 2212, 14, 0.938272046, 1));
		particleMap.put(-2212, new PDGParticle("pbar", -2212, 15, 0.938272046, -1));
                particleMap.put(2112, new PDGParticle("n", 2112, 13, 0.939565379, 0));
                particleMap.put(-2112, new PDGParticle("nbar", -2112, 25, 0.939565379, 0));
                particleMap.put(2224, new PDGParticle("Delta++", 2224, 1.232, 2));
                particleMap.put(2214, new PDGParticle("Delta+", 2214,  1.232, 1));
                particleMap.put(2114, new PDGParticle("Delta0", 2114,  1.232, 0));
                particleMap.put(1114, new PDGParticle("Delta-", 1114,  1.232, -1));
                particleMap.put(-2224, new PDGParticle("AntiDelta--", -2224,  1.232, -2));
                particleMap.put(-2214, new PDGParticle("AntiDelta-", -2214,  1.232, -1));
                particleMap.put(-2114, new PDGParticle("AntiDelta0", -2114,  1.232, 0));
                particleMap.put(-1114, new PDGParticle("AntiDelta+", -1114,  1.232, 1));
                particleMap.put(3122, new PDGParticle("Lambda", 3122, 18, 1.115683, 0));
                particleMap.put(-3122, new PDGParticle("AntiLambda", -3122, 26, 1.115683, 0));
                particleMap.put(3222, new PDGParticle("Sigma+", 3222, 19, 1.18937, 1));
                particleMap.put(3212, new PDGParticle("Sigma0", 3212, 20, 1.192642, 0));
                particleMap.put(3112, new PDGParticle("Sigma-", 3112, 21, 1.197449, -1));
                particleMap.put(-3222, new PDGParticle("AntiSigma-", -3222, 27, 1.18937, -1));
                particleMap.put(-3212, new PDGParticle("AntiSigma0", -3212, 28, 1.192642, 0));
                particleMap.put(-3112, new PDGParticle("AntiSigma+", -3112, 29, 1.197449, 1));
                particleMap.put(3322, new PDGParticle("Xi0", 3322, 22, 1.31486, 0));
                particleMap.put(3312, new PDGParticle("Xi-", 3312, 23, 1.32171, -1));
                particleMap.put(-3322, new PDGParticle("AntiXi0", -3322, 30, 1.31486, 0));
                particleMap.put(-3312, new PDGParticle("AntiXi+", -3312, 31, 1.32171, 1));
                particleMap.put(3224, new PDGParticle("Sigma*+", 3224,  1.3828, 1));
                particleMap.put(3214, new PDGParticle("Sigma*0", 3214,  1.3846, 0));
                particleMap.put(3114, new PDGParticle("Sigma*-", 3114,  1.3872, -1));
                particleMap.put(3324, new PDGParticle("Xi*0", 3324,  1.5318, 0));
                particleMap.put(3314, new PDGParticle("Xi*-", 3314,  1.5334, -1));
                particleMap.put(-3224, new PDGParticle("AntiSigma*-", -3224,  1.3828, -1));
                particleMap.put(-3214, new PDGParticle("AntiSigma*0", -3214,  1.3846, 0));
                particleMap.put(-3114, new PDGParticle("AntiSigma*+", -3114,  1.3872, 1));
                particleMap.put(-3324, new PDGParticle("AntiXi*0", -3324,  1.5318, 0));
                particleMap.put(-3314, new PDGParticle("AntiXi*+", -3314,  1.5334, -1));
                particleMap.put(3334, new PDGParticle("Omega-", 3334, 24, 1.67245, -1));
                particleMap.put(-3334, new PDGParticle("AntiOmega+", -3334, 32, 1.67245, 1));

                
		return particleMap;
	}

	static public PDGParticle getParticleByName(String name) {
		for (Map.Entry<Integer, PDGParticle> entry : particleDatabase.entrySet()) {
			Integer key = entry.getKey();
			PDGParticle value = (PDGParticle) entry.getValue();
			if (value.name().compareTo(name) == 0)
				return value;
			// ...
		}
		return null;
		// particleDatabase
	}

	public static void show() {
		for (Map.Entry<Integer, PDGParticle> items : particleDatabase.entrySet()) {
			System.out.println(items.getValue().toString());
		}
	}

	public static double getParticleMass(int pid)  {
                double mass =0.0;
		if (particleDatabase.containsKey(pid) == true) {
			mass = particleDatabase.get(pid).mass();
		}
                else {
                    System.out.println("PDGDatabase::Error -> there is no particle with pid " + pid);
                }
		return mass;
	}
}
