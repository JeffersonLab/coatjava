package org.jlab.service.urwt;

/**
 *
 * @author bondi, devita
 */
public class URWTConstants {
    
    
    // geometry
    public final static int NSECTOR  = 6;
    public final static int NLAYER   = 4;
    public final static int NREGION  = 2;
    public final static int NCHAMBER = 3;
    public final static int[] NSTRIPS  = { 542,   628, 714}; // number of strips for the three chambers
    public final static int[] STRIPMIN = {   1,  543, 1171}; // lower strip number
    public final static int[] STRIPMAX = { 542, 1170, 1884}; // higher strip number
    public final static double PITCH = 0.1; // mm
    public final static double[] STEREO = { 29.5, 29.5};

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
    
     //// Todo: Should come from uRWell geometry package
    public static final double URWELLLOCALZ[]  = {223.0, 226.0}; // cm
    
    // resolution for cross measures
    public static final double URWELLXRESOLUTIONHB = 0.17; // cm
    public static final double URWELLYRESOLUTIONHB = 0.75; // cm
    
    // resolution for cluster measures
    public static final double URWELLRESOLUTIONHB[] = {0.67, 0.67}; // cm
    public static final double URWELLRESOLUTIONTB[] = {0.11, 0.11}; // cm
    
    
    //
    public static final double URWELLDIFFX = 1.0; // cm; cut for absolute x difference between crosses of R1 and R2
    public static final double URWELLDIFFY = 0.6; // cm; cut for absolute y difference between crosses of R1 and R2
    
    // DC-SL1 clustering with joint of uRWell
    // For conversion of uRWell cross measurement relative to DC-SL1 local coordinates
    public static double INTERVALDCSL1L1L2TSC = 1.15848; // cm; distance between layer1 & layer2 of SL1 in TSC
    public static double DCSL1L1ZTSC = 229.27948; // cm; z for layer1 of SL1 in TSC
    public static double DCSL1L1W1XTSC = -83.7509153; // cm; x for layer1 of SL1 in TSC
    public static double YDCSL1L1W1LC = 1.7320508075688772; // cm; y for layer1 of SL1 in TSC
      
    public static final double YDISTURWELLTOMOSTLEFTLAYERLC[] = {2.4, 1.8}; // Absolute of Y difference between uRWell and most left layer of DC-SL1 in LC, separately for R1 and R2
    public static final double URWELLRESIDUALCUT[] = {0.84, 0.1}; // 3-sigma cut for absolute uRWell resdial after DC-uRWell fitting in LC, separately for R1 and R2

}
