package org.jlab.service.recoil.trk;

/**
 *
 * @author bondi, devita,niccolai
 */
public class RTRKParameters {
    
    // geometry
   /*public final static int NSECTOR  = 2;
    public final static int NLAYER   = 6;
    public final static int NREGION  = 3;
    public final static int NCHAMBER = 1;
    public final static int[] NSTRIPS  = { 351, 582, 834}; // number of strips for the three chambers
    public final static int[] STRIPMIN = {   1, 352, 583}; // lower strip number
    public final static int[] STRIPMAX = { 351, 582, 834}; // higher strip number
    public final static double PITCH = 0.1; // mm
    public final static double[] STEREO = { 0.0, 90.0 };*/
    
    // strips
    public final static double THRESHOLD = 0;
    public final static double ADCTOENERGY = 25/1E4; // in eV, values from gemc ADC = (uRwellC.gain*1e6*tInfos.eTot/uRwellC.w_i); with gain = 10^4 and w_i = 25 eV
    public final static double TDCTOTIME = 1;
    
    // cluster
    public final static double COINCTIME = 100;
    
}
