package org.jlab.rec.atof.constants;

/**
 *
 * @author npilleux
 */
public class Parameters {

    //In millimiters
    public static final double BAR_INNER_RADIUS = 77;//mm
    public static final double WEDGE_INNER_RADIUS = 80;//mm
    public static final double BAR_THICKNESS = 3;//mm
    public static final double WEDGE_THICKNESS = 20;//mm
    public static final double BAR_MIDDLE_RADIUS = BAR_INNER_RADIUS + BAR_THICKNESS / 2;
    public static final double WEDGE_MIDDLE_RADIUS = WEDGE_INNER_RADIUS + WEDGE_THICKNESS / 2;
    
    
    public static final double VEFF = 200.0;//mm/ns
    public static final double TDC2TIME = 0.015625;//ns per channel bin
    public static final double ATT_L = 1600.0;//mm
    public static final double TOT2ENERGY_BAR = 1.956 * 0.3 /1000;//to MeV
    public static final double TOT2ENERGY_WEDGE = 1.956 * 2.0 /1000;//to MeV

    public static double LENGTH_ATOF = 279.7; //detector length in mm
    
    public static double SIGMA_PHI_TRACK_MATCHING_BAR = 180 * Math.PI/180.;//in rad
    public static double SIGMA_PHI_TRACK_MATCHING_WEDGE = 180 * Math.PI/180.;//in rad
    
    public static double SIGMA_Z_TRACK_MATCHING_BAR = 200;//in mm
    public static double SIGMA_Z_TRACK_MATCHING_WEDGE = 200;//in mm
    
    public static double SIGMA_PHI_CLUSTERING = 6;//in deg
    public static double SIGMA_Z_CLUSTERING = 200;//in mm
    public static double SIGMA_MODULE_CLUSTERING = 1;
    public static double SIGMA_COMPONENT_CLUSTERING = 1;
    public static double SIGMA_T_CLUSTERING = 100;// in ns
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
    }

}
