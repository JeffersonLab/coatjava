package org.jlab.service.urwell;

/**
 *
 * @author bondi, devita
 */
public class URWellConstants {
    
    
    // geometry
    public final static int NSECTOR  = 6;
    public final static int NLAYER   = 4;
    public final static int NREGION  = 2;
    public final static int NCHAMBER = 3;
    public final static int[] NSTRIPS  = { 542,   628, 714}; // number of strips for the three chambers
    public final static int[] STRIPMIN = {   1,  543, 1171}; // lower strip number
    public final static int[] STRIPMAX = { 542, 1170, 1884}; // higher strip number
    public final static double PITCH = 0.1; // mm
    public final static double[] STEREO = { 10.0, 10.0 };

    // strips
    public final static double THRESHOLD = 0;
    public final static double ADCTOENERGY = 25/1E4; // in eV, values from gemc ADC = (uRwellC.gain*1e6*tInfos.eTot/uRwellC.w_i); with gain = 10^4 and w_i = 25 eV
    public final static double TDCTOTIME = 1;

    // cluster
    public final static double COINCTIME = 100;
    
    // cross
    public final static double deltaE = 200;
    public final static double deltaT = 50;
    public final static double meanT = 180 + 550;
    
     //Todo: Should come from uRWell geometry package
    public static final double URWELLLOCALZR1  = 226.0464; // cm
    public static final double URWELLXRESOLUTION = 0.024; // cm
    public static final double URWELLYRESOLUTION = 0.15; // cm
    
    public static final double URWELLXRESOLUTIONHB = 0.12; // cm
    public static final double URWELLYRESOLUTIONHB = 0.88; // cm
    
    // CalCulate uRWell crosses in LC
    public static double INTERVALDCSL1L1L2TSC = 1.15848; // cm
    public static double DCSL1L1ZTSC = 229.27948; // cm
    public static double DCSL1L1W1XTSC = -83.7509153; // cm
    public static double YDCSL1L1W1LC = 1.7320508075688772;

}
