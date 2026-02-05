package org.jlab.detector.geant4.v2.MPGD.MUVT;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.detector.geant4.v2.MPGD.trapezoid.MPGDTrapezoidGeant4Factory;

/**
 * Geant4 factory for the muCLAS Forward Vertex Tracker (muVT).
 *
 * This class specializes the generic
 * {@link MPGDTrapezoidGeant4Factory} by:
 *  - passing the MUVT-specific constants
 *  - using "MUVT" as detector name in volume names
 *
 * All the geometry construction (sectors, regions, material stack)
 * is implemented in the base class.
 */
public final class MUVTGeant4Factory extends MPGDTrapezoidGeant4Factory {

    private final String variation;

    public MUVTGeant4Factory(DatabaseConstantProvider cp, String variation) {
        super(MUVTConstants.getInstance(), "muvt");
        this.variation = variation;
        MUVTConstants.connect(cp);
        init();
    }

    public MUVTGeant4Factory(String variation, int run) {
        super(MUVTConstants.getInstance(), "muvt");
        this.variation = variation;

        DatabaseConstantProvider cp = new DatabaseConstantProvider(run, variation);
        MUVTConstants.connect(cp);
        cp.disconnect();

        init();
    }


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

        MUVTGeant4Factory factory = new MUVTGeant4Factory(variation, run);

        System.out.println("MUVT geometry for run " + run + " (variation=\"" + variation + "\")");
        System.out.println("Total volumes: " + factory.getAllVolumes().size());

        factory.getAllVolumes().forEach(volume -> {
            System.out.println(volume.gemcString());
        });
    }
}


