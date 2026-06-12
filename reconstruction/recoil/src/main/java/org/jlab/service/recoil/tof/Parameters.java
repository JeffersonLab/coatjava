package org.jlab.service.recoil.tof;

/**
 *
 * @author npilleux, Nilanga Wickramaarachchi
 */
public class Parameters {
    
    public static final double VEFF = 20.0;//cm/ns
    public static final double TDC2TIME = 0.015625;//ns per channel bin
    public static final double ATT_L = 160.0;//cm
    public static final double TOT2ENERGY = 1.956 * 0.5 /1000;//to MeV
    
    //public static double SIGMA_Y_TRACK_MATCHING_BAR = 20.0;//in cm
    public static double SIGMA_Y_CLUSTERING = 4.0;//in cm
    public static double SIGMA_T_CLUSTERING = 100;// in ns
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
    }
    
}
