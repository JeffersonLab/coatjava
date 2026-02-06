package org.jlab.detector.geant4.v2.MPGD.URWT;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.detector.geant4.v2.MPGD.trapezoid.MPGDTrapezoidConstants;

/**
 * URWT-specific constants.
 */
public final class URWTConstants extends MPGDTrapezoidConstants {

    private URWTConstants() {
            super(
                    "/test/urwt/",       // CCDB base path
                    "urwt_global",       // global table name
                    "urwt_material",     // material table name
                    "urwt"               // detector nams
            );
    }
    
    public URWTConstants(int run, String variation) {
        this();
        DatabaseConstantProvider cp = new DatabaseConstantProvider(run, variation);
        this.load(cp);
        cp.disconnect();
    }
    
    public URWTConstants(DatabaseConstantProvider cp) {
        this();
        this.load(cp);
    }

}
