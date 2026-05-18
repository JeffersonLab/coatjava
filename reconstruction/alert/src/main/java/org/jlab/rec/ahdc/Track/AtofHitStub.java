package org.jlab.rec.ahdc.Track;

/** A minimal stand-in for an ATOF hit, attached to a {@link TrackCandidate} by
 *  the GNN graph.
 *
 *  <p>Carries only the fields the GNN graph builder already extracts from the
 *  {@code ATOF::hits} bank. It is deliberately <em>not</em>
 *  {@code org.jlab.rec.atof.hit.ATOFHit}: constructing a real {@code ATOFHit}
 *  requires the ATOF {@code Detector} geometry and a calibration table, neither
 *  of which is available where the graph is built. Keeping this a plain value
 *  class also keeps the {@code Track} package free of any dependency on the
 *  {@code atof} package.</p>
 */
public final class AtofHitStub {

    private final int    sector;
    private final int    layer;
    private final int    component;
    private final double x;
    private final double y;

    public AtofHitStub(int sector, int layer, int component, double x, double y) {
        this.sector    = sector;
        this.layer     = layer;
        this.component = component;
        this.x         = x;
        this.y         = y;
    }

    public int    getSector()    { return sector; }
    public int    getLayer()     { return layer; }
    public int    getComponent() { return component; }
    public double getX()         { return x; }
    public double getY()         { return y; }
}
