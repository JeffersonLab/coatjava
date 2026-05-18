package org.jlab.detector.geant4.v2.MPGD.URWT;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.detector.geant4.v2.MPGD.trapezoid.MPGDTrapezoidConstants;

/**
 * URWT-specific constants.
 */
public final class URWTConstants extends MPGDTrapezoidConstants {

    private final String variation;

    private URWTConstants(String variation) {
            super(
                    "/test/urwt/",       // CCDB base path
                    "urwt_global",       // global table name
                    "urwt_material",     // material table name
                    "urwt"               // detector nams
            );
            this.variation = variation;
    }
    
    public URWTConstants(int run, String variation) {
        this(variation);
        DatabaseConstantProvider cp = new DatabaseConstantProvider(run, variation);
        this.load(cp);
        cp.disconnect();
    }
    
    public URWTConstants(DatabaseConstantProvider cp, String variation) {
        this(variation);
        this.load(cp);
    }
    
    /**
     *
     * @param region
     * @return
     */
    @Override
    public SectorDimensions getSectorActiveVolumeDimensions(int region) {

        if (variation != null && variation.toLowerCase().contains("proto")) {

            double halfThickness = this.getSectorThickness() / 2.0;
            double tiltRad = Math.toRadians(THTILT);

            // da vertici (mm)
            double halfLargeBase = 72.71785;
            double halfSmallBase = 50.44350;
            double halfHeight = 24.74554;

            return new SectorDimensions(halfThickness, halfHeight, halfLargeBase, halfSmallBase, tiltRad);
        }

        return super.getSectorActiveVolumeDimensions(region);
    }


}
