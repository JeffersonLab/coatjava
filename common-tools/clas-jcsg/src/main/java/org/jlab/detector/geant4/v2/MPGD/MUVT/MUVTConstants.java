package org.jlab.detector.geant4.v2.MPGD.MUVT;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.detector.geant4.v2.MPGD.trapezoid.MPGDTrapezoidConstants;

/**
 * URWT-specific constants.
 */
public final class MUVTConstants extends MPGDTrapezoidConstants {


    private static final MUVTConstants INSTANCE =
            new MUVTConstants(
                    "/test/muvt/",       // CCDB base path
                    "muvt_global",       // global table name
                    "muvt_material"     // material table name
            );

    /**
     * Private constructor: only the singleton instance is used.
     */
    private MUVTConstants(String ccdbPath,
                          String globalTable,
                          String materialTable) {
        super(ccdbPath, globalTable, materialTable);
    }

    /**
     * Returns the singleton instance for URWT constants.
     * @return 
     */
    public static MUVTConstants getInstance() {
        return INSTANCE;
    }

    /**
     * Convenience method to load URWT constants using the given
     * {@link DatabaseConstantProvider}.Usage:
   DatabaseConstantProvider cp = new DatabaseConstantProvider(run, variation);
   URWTConstants.connect(cp);
   // now MUVTConstants.getInstance() is fully initialized
     *
     * @param cp
     * @return 
     */
    public static DatabaseConstantProvider connect(DatabaseConstantProvider cp) {
        return INSTANCE.loadFrom(cp);
    }

}
