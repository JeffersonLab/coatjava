package org.jlab.detector.geant4.v2.MPGD2.MUVT;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.detector.geant4.v2.MPGD2.trapezoid.MPGDTrapezoidStripFactory;
import org.jlab.detector.volume.Geant4Basic;

import java.util.HashMap;
import java.util.Map;

/**
 * MUVT strip factory.
 *
 * It relies entirely on AbstractMPGDTrapezoidStripFactory for:
 *  - strip building (component IDs, endpoints)
 *  - surfaces
 *  - planes
 *
 * The ONLY detector-specific thing here is the mapping "volume name -> Geant4Basic",
 * used by the abstract class to find the sensitive volume (Sensitivity==1) and its transform.
 */
public final class MUVTStripFactory extends MPGDTrapezoidStripFactory {

    private final Map<String, Geant4Basic> volumeByName = new HashMap<>();

    /**
     * Build using an already-configured DatabaseConstantProvider.
     */
public MUVTStripFactory(DatabaseConstantProvider cp, String variation) {
    super(MUVTConstants.getInstance(), new MUVTGeant4Factory(cp, variation));

    MUVTConstants.connect(cp);

    for (Geant4Basic v : geo.getAllVolumes()) {
        if (v.getName() != null) {
            volumeByName.put(v.getName(), v);
        }
    }

    buildAll();
}

    /**
     * Convenience constructor: internally creates a DatabaseConstantProvider.
     */
    public MUVTStripFactory(String variation, int run) {
        super(MUVTConstants.getInstance(), new MUVTGeant4Factory(variation, run));

        DatabaseConstantProvider cp = new DatabaseConstantProvider(run, variation);
        MUVTConstants.connect(cp);
        cp.disconnect();

        for (Geant4Basic v : geo.getAllVolumes()) {
            if (v.getName() != null) {
                volumeByName.put(v.getName(), v);
            }
        }

        buildAll();
    }

    @Override
    protected Geant4Basic getVolumeByName(String name) {
        return volumeByName.get(name);
    }


    @Override
    protected boolean is2DReadout() {
        // change to true if your MUVT readout is truly 2D
        return true;
    }

    /**
     * Small test / debug.
     */
    public static void main(String[] args) {

        int run = 11;
        String variation = "default";

        if (args.length > 0) {
            try { run = Integer.parseInt(args[0]); } catch (Exception ignored) {}
        }
        if (args.length > 1) variation = args[1];

        MUVTStripFactory sf = new MUVTStripFactory(variation, run);

        int sector = 2;
        int layer  = 12;

        System.out.println("MUVT strips: sector=" + sector + " layer=" + layer
                + " nComponents=" + sf.getNComponents(sector, layer));

        // print first strip global/local/tilted
        int comp = 10;
        System.out.println("Global strip(1): " + sf.getStrip(sector, layer, comp));
        System.out.println("Local  strip(1): " + sf.getStripLocal(sector, layer, comp));
        System.out.println("Tilted strip(1): " + sf.getStripTilted(sector, layer, comp));

        System.out.println("Plane: " + sf.getPlane(sector, layer));
        System.out.println("Surface: " + sf.getSurface(sector, layer));
    }
}
