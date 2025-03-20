package org.jlab.detector.geant4.v2.recoil;


import org.jlab.detector.calib.utils.DatabaseConstantProvider; 
import org.jlab.geom.prim.Point3D;


public class RecoilConstants {

    private final static String CCDBPATH = "/geometry/recoil/";
    
    public final static int NMAXREGIONS = 3;    //max number of regions 
    public final static int NREGIONS    = 3;    //number of regions 
    public final static int NSECTORS    = 2;    //number of sectors
    public final static int NLAYERS     = 2;    //number of layers
    public final static int NCHAMBERS   = 1;    //number of chambers in a sector

    public final static double HORIZONTHAL_STARTING_ANGLE = 40.;
    public final static double HORIZONTHAL_OPENING_ANGLE = 29.;
    public final static double VERTICAL_OPENING_ANGLE = 50.;
    public final static double RADIUS[] = {44,72,100};
    public final static double WIDTH[] = new double[NMAXREGIONS];
    public final static double HEIGHT[] = new double[NMAXREGIONS];

    public final static double THTILT = 0;            // theta tilt (deg)
  
    // Chamber volumes  and materials (units are cm)
    public final static double[] CHAMBERVOLUMESTHICKNESS = {0.0025, 0.0005,0.3,                                // window
                                                            0.0025, 0.0005,0.4,                                // cathode
                                                            0.0005, 0.005, 0.0005,                             // uRWell + DlC
                                                            0.0005, 0.005, 0.0005,                             // Capacitive sharing layer1
                                                            0.0005, 0.005, 0.0005,                             // Capacitive sharing layer2
                                                            0.005,  0.0005,0.005, 0.005,  0.0005,0.005, 0.005, // Readout
                                                            0.0127, 0.3, 0.0125};                              // support
    public final static String[] CHAMBERVOLUMESNAME = {"window_kapton", "window_Al", "window_gas",
           "cathode_kapton", "cathode_Al", "cathode_gas",
           "muRwell_Cu", "muRwell_kapton", "muRwell_dlc", 
           "capa_sharing_layer1_glue","capa_sharing_layer1_Cr","capa_sharing_layer1_kapton",
           "capa_sharing_layer2_glue","capa_sharing_layer2_Cr","capa_sharing_layer2_kapton",
           "readout1_glue", "readout1_Cu", "readout1_kapton", "readout2_glue", "readout2_Cu", "readout2_kapton", "readout3_glue",
           "support_skin1_g10", "support_honeycomb_nomex", "support_skin2_g10"};
    
    public final static double PITCH = 0.1 ;       // cm
    public final static double STEREOANGLE = 90;   // deg
       
     /*
     * @return String a path to a directory in CCDB of the format {@code "/geometry/detector/"}
     */
    public static String getCcdbPath()
    {
            return CCDBPATH;
    }

     /**
     * Loads the the necessary tables for the URWELL geometry for a given DatabaseConstantProvider.
     * 
     * @return DatabaseConstantProvider the same thing
     */
    public static DatabaseConstantProvider connect( DatabaseConstantProvider cp )
    {
          //  cp.loadTable( CCDBPATH +"RWELL");

            load(cp  );
            return cp;
    }

    /**
     * Reads all the necessary constants from CCDB into static variables.
     * Please use a DatabaseConstantProvider to access CCDB and load the following tables:
     * @param cp a ConstantProvider that has loaded the necessary tables
     */
    
    public static synchronized void load( DatabaseConstantProvider cp )
    {
            // read constants from svt table
//            NREGIONS = cp.getInteger( CCDBPATH+"svt/nRegions", 0 );

             for (int i=0; i<NMAXREGIONS; i++){
                
		 WIDTH[i]=2.*RADIUS[i]*Math.tan(Math.toRadians(HORIZONTHAL_OPENING_ANGLE)/2);
		 HEIGHT[i]=2*RADIUS[i]*Math.tan(Math.toRadians(VERTICAL_OPENING_ANGLE)/2);
	     }
    }
}
