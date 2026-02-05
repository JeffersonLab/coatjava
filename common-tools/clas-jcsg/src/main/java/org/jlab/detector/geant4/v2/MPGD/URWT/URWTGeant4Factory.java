package org.jlab.detector.geant4.v2.MPGD.URWT;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.detector.geant4.v2.MPGD.trapezoid.MPGDTrapezoidGeant4Factory;

/**
 * Geant4 factory for the uRWell Tracker (URWT).
 *
 * This class specializes the generic {@link MPGDTrapezoidGeant4Factory}
 * by: - passing the URWT-specific constants - using "uRWT" as detector name in
 * volume names
 *
 * All the geometry construction (sectors, regions, material stack) is
 * implemented in the base class.
 */
public final class URWTGeant4Factory extends MPGDTrapezoidGeant4Factory {

    private final String variation;

    public URWTGeant4Factory(DatabaseConstantProvider cp, String variation) {
        super(URWTConstants.getInstance(), "urwt");
        this.variation = variation;
        URWTConstants.connect(cp);
        init();
    }

    public URWTGeant4Factory(String variation, int run) {
        super(URWTConstants.getInstance(), "urwt");
        this.variation = variation;

        DatabaseConstantProvider cp = new DatabaseConstantProvider(run, variation);
        URWTConstants.connect(cp);
        cp.disconnect();

        init();
    }

    /**
     *
     * @param region
     * @return
     */
    @Override
    public SectorDimensions getSectorDimensionsPhysical(int region) {

        if (variation != null && variation.toLowerCase().contains("proto")) {

            double halfThickness = this.getSectorThickness() / 2.0;
            double tiltRad = Math.toRadians(C.THTILT);

            // da vertici (mm)
            double halfLargeBase = 72.71785;
            double halfSmallBase = 50.44350;
            double halfHeight = 24.74554;

            return new SectorDimensions(halfThickness, halfHeight, halfLargeBase, halfSmallBase, tiltRad);
        }

        return super.getSectorDimensionsPhysical(region);
    }

    /**
     * Standalone test: builds the URWT geometry and prints all volumes in GEMC
     * format.
     *
     * Usage: java org.jlab.detector.geant4.v2.URWT.URWTGeant4Factory [run]
     * [variation]
     *
     * Defaults: run = 11 variation = "default"
     */
    public static void main(String[] args) {

        int run = 11;
        String variation = "default";

        if (args.length > 0) {
            try {
                run = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid run number \"" + args[0] + "\", using default 11.");
            }
        }
        if (args.length > 1) {
            variation = args[1];
        }

        URWTGeant4Factory factory = new URWTGeant4Factory(variation, run);

        System.out.println("uRWT geometry for run " + run + " (variation=\"" + variation + "\")");
        System.out.println("Total volumes: " + factory.getAllVolumes().size());

        factory.getAllVolumes().forEach(volume -> {
            System.out.println(volume.gemcString());
        });
    }
}
