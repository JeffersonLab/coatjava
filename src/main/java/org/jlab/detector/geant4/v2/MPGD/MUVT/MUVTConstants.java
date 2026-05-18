package org.jlab.detector.geant4.v2.MPGD.MUVT;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.detector.geant4.v2.MPGD.trapezoid.MPGDTrapezoidConstants;

/**
 * MUVT-specific constants.
 */
public final class MUVTConstants extends MPGDTrapezoidConstants {

    private MUVTConstants() {
            super(
                    "/test/muvt/",       // CCDB base path
                    "muvt_global",       // global table name
                    "muvt_material",     // material table name
                    "muvt"               // detector nams
            );
    }
    
    public MUVTConstants(int run, String variation) {
        this();
        DatabaseConstantProvider cp = new DatabaseConstantProvider(run, variation);
        this.load(cp);
        cp.disconnect();
    }
    
    public MUVTConstants(DatabaseConstantProvider cp) {
        this();
        this.load(cp);
    }

}
