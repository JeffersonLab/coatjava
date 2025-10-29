/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.decay.analysis;

/**
 *
 * @author veronique
 */
public class Constants {

    public static double CHI2PIDCUT=15.0;
    public static double rCUT = 5.5;
    
 // private constructor for a singleton
    private Constants() {
    }
    
    // singleton
    private static Constants instance = null;
    
    /**
     * public access to the singleton
     * 
     * @return the constants singleton
     */
    public static Constants getInstance() {
            if (instance == null) {
                    instance = new Constants();
            }
            return instance;
    }

    
//    public static HashMap<Integer, PDGParticle> particleMap = new HashMap<Integer, PDGParticle>();
//    public static void Load() {
//        particleMap.put(11, new PDGParticle("e-", 11, 3, 0.0005, -1));
//        particleMap.put(-11, new PDGParticle("e+", -11, 2, 0.0005, 1));
//        particleMap.put(12, new PDGParticle("nue", 12, 0, 0.320e-6, 0));
//        particleMap.put(-12, new PDGParticle("nue-", -12, 0, 0.320e-6, 0));
//
//        particleMap.put(13, new PDGParticle("mu-", 13, 6, 0.1056583715, -1));
//        particleMap.put(-13, new PDGParticle("mu+", -13, 5, 0.1056583715, 1));
//        particleMap.put(22, new PDGParticle("gamma", 22, 1, 0.000, 0));
//        particleMap.put(45, new PDGParticle("d", 45, 0, 1.87705, 1));
//        particleMap.put(111, new PDGParticle("pi0", 111, 7, 0.1349766, 0));
//        particleMap.put(113, new PDGParticle("rho0", 113, 0.7754, 0));
//        particleMap.put(221, new PDGParticle("eta", 221, 0.547853, 0));
//        particleMap.put(331, new PDGParticle("etap", 331, 0.95766, 0));
//        particleMap.put(223, new PDGParticle("omega", 223, 0.78265, 0));
//        particleMap.put(333, new PDGParticle("phi", 333, 0.1019455, 0));
//        particleMap.put(213, new PDGParticle("rho+", 213, 0.7754, 1));
//        particleMap.put(211, new PDGParticle("pi+", 211, 8, 0.13957018, 1));
//        particleMap.put(-211, new PDGParticle("pi-", -211, 9, 0.13957018, -1));
//        particleMap.put(321, new PDGParticle("K+", 321, 11, 0.49367716, 1));
//        particleMap.put(-321, new PDGParticle("K-", -321, 12, 0.49367716, -1));
//        particleMap.put(311, new PDGParticle("K0", 311, 10, 0.49761424, 0));
//        particleMap.put(130, new PDGParticle("K0L", 130, 10, 0.49761424, 0));
//        particleMap.put(310, new PDGParticle("K0S", 310, 10, 0.49761424, 0));
//        particleMap.put(2212, new PDGParticle("p", 2212, 14, 0.938272046, 1));
//        particleMap.put(-2212, new PDGParticle("p-", -2212, 15, 0.938272046, -1));
//        particleMap.put(2112, new PDGParticle("n", 2112, 13, 0.939565379, 0));
//        particleMap.put(-2112, new PDGParticle("nbar", -2112, 13, 0.939565379, 0));
//        particleMap.put(3122, new PDGParticle("Lambda0", 3122, 100, 1.115683, 0));
//        particleMap.put(-3122, new PDGParticle("antiLambda0", -3122, 100, 1.115683, 0));
//
//        particleMap.put(3212, new PDGParticle("Sigma0", 3212, 1.192, 0));
//        particleMap.put(-3212, new PDGParticle("antiSigma0", -3212, 1.192, 0));
//
//        particleMap.put(3222, new PDGParticle("Sigma+", 3222, 1.189, 1));
//        particleMap.put(-3222, new PDGParticle("antiSigma+", -3222, 1.189, -1));
//
//        particleMap.put(3112, new PDGParticle("Sigma-", 3112, 1.197, -1));
//        particleMap.put(-3112, new PDGParticle("antiSigma-", -3112, 1.197, 1));
//
//        particleMap.put(3312, new PDGParticle("Xi-", 3312, 1.321, -1));
//        particleMap.put(-3312, new PDGParticle("antiXi-", -3312, 1.321, 1));
//        particleMap.put(3322, new PDGParticle("Xi0", 3322, 1.315, 0));
//    }
//    
    
}

