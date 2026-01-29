package org.jlab.detector.geant4.v2.URWT;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.detector.geant4.v2.mpgd.trapezoid.AbstractMPGDTrapezoidStripFactory;
import org.jlab.detector.volume.Geant4Basic;

import java.util.HashMap;
import java.util.Map;

/**
 * URWT strip factory.
 *
 * It relies entirely on AbstractMPGDTrapezoidStripFactory for:
 *  - strip building (component IDs, endpoints)
 *  - surfaces
 *  - planes
 *
 * The ONLY detector-specific thing here is the mapping "volume name -> Geant4Basic",
 * used by the abstract class to find the sensitive volume (Sensitivity==1) and its transform.
 */
public final class URWTStripFactory extends AbstractMPGDTrapezoidStripFactory {

    private final Map<String, Geant4Basic> volumeByName = new HashMap<>();

    /**
     * Build using an already-configured DatabaseConstantProvider.
     */
public URWTStripFactory(DatabaseConstantProvider cp, String variation) {
    super(URWTConstants.getInstance(), new URWTGeant4Factory(cp, variation));

    URWTConstants.connect(cp);

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
    public URWTStripFactory(String variation, int run) {
        super(URWTConstants.getInstance(), new URWTGeant4Factory(variation, run));

        DatabaseConstantProvider cp = new DatabaseConstantProvider(run, variation);
        URWTConstants.connect(cp);
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

    /**
     * If URWT uses 2D readout (same readout Z for both layers), enable this.
     * Otherwise leave it false (default 1D: z = +/- zHalf).
     */
    @Override
    protected boolean is2DReadout() {
        // change to true if your URWT readout is truly 2D
        return false;
    }

    /**
     * Small test / debug.
     */
    public static void main(String[] args) {

        int run = 11;
        String variation = "urwt1";

        if (args.length > 0) {
            try { run = Integer.parseInt(args[0]); } catch (Exception ignored) {}
        }
        if (args.length > 1) variation = args[1];

        URWTStripFactory sf = new URWTStripFactory(variation, run);

        int sector = 1;
        int layer  = 4;

        System.out.println("URWT strips: sector=" + sector + " layer=" + layer
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
