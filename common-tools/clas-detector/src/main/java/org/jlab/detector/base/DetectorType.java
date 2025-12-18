package org.jlab.detector.base;

import java.util.HashMap;

/**
 *
 * @author gavalian
 */
public enum DetectorType {
      
    UNDEFINED ( 0, "UNDEF"),
    BMT       ( 1, "BMT"),    
    BST       ( 2, "BST"),
    CND       ( 3, "CND"),
    CTOF      ( 4, "CTOF"),
    CVT       ( 5, "CVT"),
    DC        ( 6, "DC"),
    ECAL      ( 7, "ECAL"),
    FMT       ( 8, "FMT"),
    FT        ( 9, "FT"),
    FTCAL     (10, "FTCAL"),
    FTHODO    (11, "FTHODO"),
    FTOF      (12, "FTOF"),
    FTTRK     (13, "FTTRK"),
    HTCC      (15, "HTCC"),
    LTCC      (16, "LTCC"),
    RF        (17, "RF"),
    RICH      (18, "RICH"),
    RTPC      (19, "RTPC"),
    HEL       (20, "HEL"),
    BAND      (21, "BAND"),
    RASTER    (22, "RASTER"),
    URWELL    (23, "URWELL"),
    AHDC      (24, "AHDC"),
    ATOF      (25, "ATOF"),
    RECOIL    (26, "RECOIL"),
    MUCAL     (28, "MUCAL"),
    MUVT      (29, "MUVT"),
    MURT      (30, "MURT"),
    MURH      (31, "MURH"),
    TARGET    (100, "TARGET"),
    MAGNETS   (101, "MAGNETS");
    
    private final int detectorId;
    private final String detectorName;
  
    private static final HashMap<String,DetectorType> stringLookup = new HashMap<>();
    private static final HashMap<Integer,DetectorType> intLookup = new HashMap<>();

    static {
        for (DetectorType t : values()) {
            stringLookup.put(t.getName(), t);
            intLookup.put(t.getDetectorId(), t);
        }
    }
    
    DetectorType(){
        detectorId = 0;
        detectorName = "UNDEFINED";
    }
    
    DetectorType(int id, String name){
        detectorId = id;
        detectorName = name;
    }
    
    /**
     * Returns the name of the detector.
     * @return the name of the detector
     */
    public String getName() {
        return detectorName;
    }
    
    /**
     * Returns the id number of the detector.
     * @return the id number of the detector
     */
    public int getDetectorId() {
        return detectorId;
    }
    
    /**
     * Get type from string name
     * @param name
     * @return 
     */
    public static DetectorType getType(String name) {
        return stringLookup.getOrDefault(name.trim(), UNDEFINED);
    }

    /**
     * Get type from integer id 
     * @param detId
     * @return 
     */
    public static DetectorType getType(Integer detId) {
        return intLookup.getOrDefault(detId, UNDEFINED);
    }
}
