package org.jlab.detector.geant4.v2.recoil.tof;


import org.jlab.detector.calib.utils.DatabaseConstantProvider; 
import org.jlab.geom.prim.Point3D;


public class RTOFConstants {

    public final static int NSECTORS    = 2;    //number of sectors
    public final static int NROWS     = 5;    //number of rows of bars in a sector
    public final static int NCOLUMNS   = 63;    //number of columns of bars in a sector

    public final static double LONG_BAR_LENGTH = 27.5; // cm
    public final static double SHORT_BAR_LENGTH = 4; // cm
    
    public final static double BAR_WIDTH = 1; // cm
    public final static double BAR_THICKNESS = 0.5; // cm
    
    public final static double HORIZONTAL_STARTING_ANGLE = 40.;
    public final static double HORIZONTAL_OPENING_ANGLE = 29.;
    public final static double RADIUS = 122.; // cm

    public final static double WIDTH = NCOLUMNS * BAR_WIDTH;                                                                    
    public final static double LENGTH = (NROWS-1) * LONG_BAR_LENGTH + SHORT_BAR_LENGTH; 
    public final static double THICKNESS = 0.5; // cm


    public static DatabaseConstantProvider connect( DatabaseConstantProvider cp )
    {
     
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
	//WIDTH = NCOLUMNS * BAR_WIDTH;                                                                        
        //LENGTH = (NROWS-1) * LONG_BAR_LENGTH + SHORT_BAR_LENGTH;                                            
        //THICKNESS = 0.5; // cm
    }

}


