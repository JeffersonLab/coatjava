package org.jlab.rec.rtof.constants;

/**
 *
 * @author npilleux, Nilanga Wickramaarachchi
 */
public class Parameters {

    public static final int NROWS     = 5;    //number of rows of bars in a sector
    public static final int NCOLUMNS   = 63;    //number of columns of bars in a sector
    
    public static final double LONG_BAR_LENGTH = 275; // mm
    public static final double SHORT_BAR_LENGTH = 40; // mm

    public static final double BAR_WIDTH = 10; // mm

    public static final double WIDTH = NCOLUMNS * BAR_WIDTH;                                                                    
    public static final double LENGTH = (NROWS-1) * LONG_BAR_LENGTH + SHORT_BAR_LENGTH;

    public static final double HORIZONTAL_STARTING_ANGLE = 40.;
    public static final double HORIZONTAL_OPENING_ANGLE = 29.;
    public static final double RADIUS = 1220; // mm
    
    public static final double VEFF = 200.0;//mm/ns
    public static final double TDC2TIME = 0.015625;//ns per channel bin
    public static final double ATT_L = 1600.0;//mm
    public static final double TOT2ENERGY = 1.956 * 0.5 /1000;//to MeV

    //public static double SIGMA_Y_TRACK_MATCHING_BAR = 200;//in mm
    public static double SIGMA_Y_CLUSTERING = 40;//in mm
    public static double SIGMA_T_CLUSTERING = 100;// in ns
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
    }

}
