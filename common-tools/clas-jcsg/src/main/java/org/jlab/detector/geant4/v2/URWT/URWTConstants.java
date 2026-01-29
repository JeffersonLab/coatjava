package org.jlab.detector.geant4.v2.URWT;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.detector.geant4.v2.mpgd.trapezoid.AbstractMPGDTrapezoidConstants;

/**
 * URWT-specific constants.
 */
public final class URWTConstants extends AbstractMPGDTrapezoidConstants {


    private static final URWTConstants INSTANCE =
            new URWTConstants(
                    "/test/urwt/",       // CCDB base path
                    "urwt_global",       // global table name
                    "urwt_material"  // material table name
            );

    /**
     * Private constructor: only the singleton instance is used.
     */
    private URWTConstants(String ccdbPath,
                          String globalTable,
                          String materialTable) {
        super(ccdbPath, globalTable, materialTable);
    }

    /**
     * Returns the singleton instance for URWT constants.
     * @return 
     */
    public static URWTConstants getInstance() {
        return INSTANCE;
    }

    /**
     * Convenience method to load URWT constants using the given
     * {@link DatabaseConstantProvider}.Usage:
   DatabaseConstantProvider cp = new DatabaseConstantProvider(run, variation);
   URWTConstants.connect(cp);
   // now URWTConstants.getInstance() is fully initialized
     *
     * @param cp
     * @return 
     */
    public static DatabaseConstantProvider connect(DatabaseConstantProvider cp) {
        return INSTANCE.loadFrom(cp);
    }

}
