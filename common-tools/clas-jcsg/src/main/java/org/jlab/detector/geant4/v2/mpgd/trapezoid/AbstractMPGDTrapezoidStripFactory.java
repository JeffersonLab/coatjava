package org.jlab.detector.geant4.v2.mpgd.trapezoid;

import eu.mihosoft.vrl.v3d.Vector3d;
import org.jlab.detector.volume.Geant4Basic;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Plane3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Trap3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.utils.groups.IndexedList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Base class implementing strip/surface/plane geometry for trapezoidal MPGD
 * detectors.
 */
public abstract class AbstractMPGDTrapezoidStripFactory {

    // ------------------------------------------------------------------------
    //  Inputs
    // ------------------------------------------------------------------------
    protected final AbstractMPGDTrapezoidConstants C;
    protected final AbstractMPGDTrapezoidGeant4Factory geo;

    protected AbstractMPGDTrapezoidStripFactory(AbstractMPGDTrapezoidConstants constants,
            AbstractMPGDTrapezoidGeant4Factory geantFactory) {
        this.C = constants;
        this.geo = geantFactory;
    }

    /**
     * Detector-specific hook: Must return the volume object given its
     * name.Typically implemented by indexing geo.getAllVolumes() into a
     * Map<String,Geant4Basic>.
     *
     * @param name
     * @return
     */
    protected abstract Geant4Basic getVolumeByName(String name);

    // ------------------------------------------------------------------------
    //  Cached geometry (CLAS12 indices)
    // ------------------------------------------------------------------------
    protected final IndexedList<Line3D> globalStrips = new IndexedList<>(3); // (sector, layer, component)
    protected final IndexedList<Line3D> localStrips = new IndexedList<>(3); // (sector, layer, component) sensitive-local
    protected final IndexedList<Line3D> tiltedStrips = new IndexedList<>(3); // (sector, layer, component)
    protected final IndexedList<Plane3D> planes = new IndexedList<>(2); // (sector, layer)
    protected final IndexedList<Trap3D> surfaceLayers = new IndexedList<>(2); // (sector, layer)
    protected final IndexedList<Integer> nComponents = new IndexedList<>(2); // (sector, layer)

    // ------------------------------------------------------------------------
    //  Internal geometry containers (mirror the C++ digitizer)
    // ------------------------------------------------------------------------
    protected static class StripConstants {

        public double xHalfSmall;     // cm
        public double xHalfLarge;     // cm
        public double yHalf;          // cm
        public double zReadoutLocal;  // cm (in sensitive-local)
        public double pitch;          // cm
        public double width;          // cm
        public double stereoAngle;    // rad
    }

    protected static class Trap2D {

        Vector3d bl, br, tl, tr;

        static Trap2D fromConstants(StripConstants c) {
            Trap2D t = new Trap2D();
            double z = c.zReadoutLocal;
            t.bl = new Vector3d(-c.xHalfSmall, -c.yHalf, z);
            t.br = new Vector3d(c.xHalfSmall, -c.yHalf, z);
            t.tl = new Vector3d(-c.xHalfLarge, c.yHalf, z);
            t.tr = new Vector3d(c.xHalfLarge, c.yHalf, z);
            return t;
        }

        List<Vector3d[]> edges() {
            List<Vector3d[]> e = new ArrayList<>(4);
            e.add(new Vector3d[]{bl, br});
            e.add(new Vector3d[]{br, tr});
            e.add(new Vector3d[]{tr, tl});
            e.add(new Vector3d[]{tl, bl});
            return e;
        }
    }

    protected static class StripGeom {

        int internalIndex;
        int component;
        Vector3d p1Local, p2Local; // endpoints in sensitive-local
        double orderXLocal;        // local midpoint X ordering key
    }

    // ------------------------------------------------------------------------
    //  Options / defaults
    // ------------------------------------------------------------------------
    /**
     * If true: readout is 2D => same zReadoutLocal for both layers.Override in
     * detector.
     *
     * @return
     */
    protected boolean is2DReadout() {
        return false;
    }

    protected int baseLayerForCCDB(int layer) {
        if (is2DReadout()) {
            return 1;
        }
        return (layer % 2 == 0) ? 2 : 1;
    }

    /**
     * Pitch in cm (CCDB stored in mm). Override if detector is different.
     */
    protected double defaultPitchCm() {
        return C.PITCH * 0.1;
    }

    /**
     * Width in cm (CCDB stored in mm). Override if detector is different.
     */
    protected double defaultWidthCm() {
        return C.WIDTH * 0.1;
    }

    /**
     * Stereo angle in rad (CCDB stored in deg). Override if detector is
     * layer-dependent.
     */
    protected double defaultStereoAngleRad() {
        return Math.toRadians(C.STEREOANGLE);
    }

    // ------------------------------------------------------------------------
    //  Sensitive volume discovery (Sensitivity==1)
    // ------------------------------------------------------------------------
    /**
     * Return the CCDB "Component" index (matC) whose Sensitivity==1 for a given
     * layer.
     *
     * @param layer
     * @return
     */
    protected int getSensitiveMatComponentId(int layer) {
        int baseLayer = baseLayerForCCDB(layer);
        var layerMap = C.getDetectorStructure().get(baseLayer);
        if (layerMap == null) {
            throw new IllegalStateException("No detectorStructure entry for layer=" + layer);
        }
        for (var e : layerMap.entrySet()) {
            int matC = e.getKey();
            var info = e.getValue();
            if (info.sensitivity == 1) {
                return matC;
            }
        }
        throw new IllegalStateException("No Sensitivity==1 found for layer=" + layer);
    }

    /**
     * Compute the sensitive half thickness (cm) from CCDB (Sensitivity==1).
     *
     * @param layer
     * @return
     */
    protected double getSensitiveHalfThicknessCm(int layer) {
        int baseLayer = baseLayerForCCDB(layer);
        var layerMap = C.getDetectorStructure().get(baseLayer);
        if (layerMap == null) {
            throw new IllegalStateException("No detectorStructure entry for layer=" + layer);
        }
        for (var e : layerMap.entrySet()) {
            var info = e.getValue();
            if (info.sensitivity == 1) {
                return info.thickness / 2.0; // thickness already cm in your loader
            }
        }
        throw new IllegalStateException("No sensitive thickness (Sensitivity==1) for layerInRegion=" + layer);
    }

    /**
     * Readout plane z in sensitive-local.Convention: - 1D: even layerInRegion
     * => +zHalf, odd => -zHalf - 2D: always +zHalf
     *
     * @param layer
     * @return
     */
    protected double findReadoutZLocal(int layer) {
        double zHalf = getSensitiveHalfThicknessCm(layer);
        if (is2DReadout()) {
            return +zHalf;
        }
        return (layer % 2 == 0) ? (+zHalf) : (-zHalf);
    }

    /**
     * Build expected sensitive-volume name using the naming convention from
     * AbstractMPGDTrapezoidGeant4Factory.populateSectorWithDetectorStructure():
     *
     * rg{region}_s{sector}_l{layerGlobal0Based}_matC{matC}
     *
     * where: - region is 1-based in the name - sector is 1-based in the name -
     * matC is the CCDB material "Component" id for Sensitivity==1
     *
     * @param region
     * @param sector
     * @param layer
     * @return
     */
    protected String getSensitiveVolumeName(int region, int sector, int layer) {
        int matC = getSensitiveMatComponentId(layer);
   if (is2DReadout()) {
    if (layer % 2 == 0) {   // layer pari
        layer = layer - 1;
    }
}
        
    return "rg_" + geo.detectorName + "_" + region
            + "_s" + sector
            + "_l" + layer
            + "_matC" + matC;
    }

    /**
     * Transform a point from sensitive-local -> global using the sensitive
     * volume transform.
     *
     * @param region
     * @param sector
     * @param layer
     * @param local
     * @return
     */
    protected Vector3d toGlobalSensitive(int region, int sector, int layer, Vector3d local) {
        String name = getSensitiveVolumeName(region, sector, layer);
        Geant4Basic v = getVolumeByName(name);
        if (v == null) {
            throw new IllegalStateException("Sensitive volume not found: " + name);
        }

        return v.getGlobalTransform().transform(local);
    }

    // ------------------------------------------------------------------------
    //  Strip constants builder (GENERAL: uses geo physical dims + CCDB thickness)
    // ------------------------------------------------------------------------
    /**
     * Build StripConstants using: - XY trapezoid from
     * geo.getSectorDimensionsPhysical(region) (NOT enlarged) - zReadoutLocal
     * from CCDB sensitive thickness (findReadoutZLocal) - pitch/width/stereo
     * from CCDB
     *
     * @param region
     * @param layer
     * @return
     */
    protected StripConstants buildStripConstants(int region, int layer) {

        AbstractMPGDTrapezoidGeant4Factory.SectorDimensions phys
                = geo.getSectorDimensionsPhysical(region - 1);

        StripConstants sc = new StripConstants();
        sc.yHalf = phys.halfHeight();
        sc.xHalfSmall = phys.halfSmallBase();
        sc.xHalfLarge = phys.halfLargeBase();

        //  sc.zReadoutLocal = findReadoutZLocal(layer);
        sc.zReadoutLocal = 0.0;
        sc.pitch = defaultPitchCm();
        sc.width = defaultWidthCm();
        if (layer % 2 == 0) {
            // layer pari
            sc.stereoAngle = -defaultStereoAngleRad();
        } else {
            // layer dispari
            sc.stereoAngle = defaultStereoAngleRad();
        }

        
        return sc;
    }

    // ------------------------------------------------------------------------
    //  Strip-frame transforms (IDENTICAL to C++)
    // ------------------------------------------------------------------------
    protected static double[] toStripFrameXY(double x, double y, StripConstants c) {
        double sa = Math.sin(c.stereoAngle);
        double ca = Math.cos(c.stereoAngle);
        double xs = sa * x + ca * y;
        double ys = ca * x - sa * y;
        return new double[]{xs, ys};
    }

    // ------------------------------------------------------------------------
    //  Line / edge intersection (normal form)
    // ------------------------------------------------------------------------
    protected static boolean intersectEdgeWithLineNormal(
            Vector3d A, Vector3d B,
            Vector3d nXY, double rhs,
            Vector3d out
    ) {
        double nA = nXY.x * A.x + nXY.y * A.y;
        double nB = nXY.x * B.x + nXY.y * B.y;
        double denom = nB - nA;

        if (Math.abs(denom) < 1e-12) {
            return false;
        }

        double t = (rhs - nA) / denom;
        if (t < 0.0 || t > 1.0) {
            return false;
        }

        out.x = A.x + t * (B.x - A.x);
        out.y = A.y + t * (B.y - A.y);
        out.z = A.z + t * (B.z - A.z);
        return true;
    }

    protected static boolean buildStripSegment(int internalIndex, StripConstants c, Vector3d out1, Vector3d out2) {

        Trap2D tr = Trap2D.fromConstants(c);

        double sa = Math.sin(c.stereoAngle);
        double ca = Math.cos(c.stereoAngle);

        // line: ca*x - sa*y = rhs
        Vector3d nXY = new Vector3d(ca, -sa, 0.0);
        double rhs = (internalIndex + 0.5) * c.pitch;

        List<Vector3d> inters = new ArrayList<>(4);

        for (Vector3d[] e : tr.edges()) {
            Vector3d P = new Vector3d(0, 0, 0);
            if (intersectEdgeWithLineNormal(e[0], e[1], nXY, rhs, P)) {
                inters.add(new Vector3d(P.x, P.y, P.z));
            }
        }

        if (inters.size() < 2) {
            return false;
        }

        // choose farthest pair (robust at vertices)
        double best = -1;
        Vector3d a = null, b = null;
        for (int i = 0; i < inters.size(); i++) {
            for (int j = i + 1; j < inters.size(); j++) {
                double dx = inters.get(i).x - inters.get(j).x;
                double dy = inters.get(i).y - inters.get(j).y;
                double d2 = dx * dx + dy * dy;
                if (d2 > best) {
                    best = d2;
                    a = inters.get(i);
                    b = inters.get(j);
                }
            }
        }

        out1.set(a.x, a.y, a.z);
        out2.set(b.x, b.y, b.z);
        return true;
    }

    /**
     * Build full strip list (LOCAL) and assign CLAS12 component IDs by sorting
     * on local X midpoint.
     *
     * @param c
     * @return
     */
    protected List<StripGeom> buildStripCache(StripConstants c) {

        Trap2D tr = Trap2D.fromConstants(c);

        double ys1 = toStripFrameXY(tr.bl.x, tr.bl.y, c)[1];
        double ys2 = toStripFrameXY(tr.br.x, tr.br.y, c)[1];
        double ys3 = toStripFrameXY(tr.tl.x, tr.tl.y, c)[1];
        double ys4 = toStripFrameXY(tr.tr.x, tr.tr.y, c)[1];

        double ysMin = Math.min(Math.min(ys1, ys2), Math.min(ys3, ys4));
        double ysMax = Math.max(Math.max(ys1, ys2), Math.max(ys3, ys4));

        int iMin = (int) Math.floor(ysMin / c.pitch) - 2;
        int iMax = (int) Math.ceil(ysMax / c.pitch) + 2;

        List<StripGeom> tmp = new ArrayList<>();

        for (int idx = iMin; idx <= iMax; idx++) {

            Vector3d p1 = new Vector3d(0, 0, 0);
            Vector3d p2 = new Vector3d(0, 0, 0);

            if (!buildStripSegment(idx, c, p1, p2)) {
                continue;
            }

            StripGeom s = new StripGeom();
            s.internalIndex = idx;
            s.p1Local = p1;
            s.p2Local = p2;

            Vector3d mid = new Vector3d(
                    0.5 * (p1.x + p2.x),
                    0.5 * (p1.y + p2.y),
                    0.5 * (p1.z + p2.z)
            );
            s.orderXLocal = mid.x;

            tmp.add(s);
        }

        tmp.sort(Comparator
                .comparingDouble((StripGeom s) -> s.orderXLocal)
                .thenComparingInt(s -> s.internalIndex));

        int comp = 1;
        for (StripGeom s : tmp) {
            s.component = comp++;
        }

        return tmp;
    }

    // ------------------------------------------------------------------------
    //  Tilted frame 
    // ------------------------------------------------------------------------
    /**
     *
     * @param sector
     * @param global
     * @return
     */
    protected Line3D toTilted(int sector, Line3D global) {
        Line3D tilted = new Line3D();
        tilted.copy(global);

        double dPhi = 360.0 / C.NSECTORS;
        double phi = -Math.toRadians(dPhi * (sector - 1));

        tilted.rotateZ(phi);
        tilted.rotateY(Math.toRadians(-C.THTILT));

        return tilted;
    }

    // ------------------------------------------------------------------------
    //  Surface + Plane builders (GLOBAL)
    // ------------------------------------------------------------------------
    /**
     *
     * @param sector
     * @param layer
     * @return
     */
    protected Trap3D createSurface(int sector, int layer) {

        int region = (layer + 1) / 2;

        StripConstants c = buildStripConstants(region, layer);

        Vector3d blL = new Vector3d(-c.xHalfSmall, -c.yHalf, c.zReadoutLocal);
        Vector3d brL = new Vector3d(c.xHalfSmall, -c.yHalf, c.zReadoutLocal);
        Vector3d tlL = new Vector3d(-c.xHalfLarge, c.yHalf, c.zReadoutLocal);
        Vector3d trL = new Vector3d(c.xHalfLarge, c.yHalf, c.zReadoutLocal);

        Vector3d blG = toGlobalSensitive(region, sector, layer, blL);
        Vector3d brG = toGlobalSensitive(region, sector, layer, brL);
        Vector3d tlG = toGlobalSensitive(region, sector, layer, tlL);
        Vector3d trG = toGlobalSensitive(region, sector, layer, trL);

        return new Trap3D(
                blG.x, blG.y, blG.z,
                brG.x, brG.y, brG.z,
                tlG.x, tlG.y, tlG.z,
                trG.x, trG.y, trG.z
        );
    }

    /**
     *
     * @param sector
     * @param layer
     * @return
     */
    protected Plane3D createPlane(int sector, int layer) {

        int region = (layer + 1) / 2;

        StripConstants c = buildStripConstants(region, layer);

        // 3 points on the readout plane in sensitive-local
        Vector3d p1L = new Vector3d(-c.xHalfSmall, -c.yHalf, c.zReadoutLocal);
        Vector3d p2L = new Vector3d(c.xHalfSmall, -c.yHalf, c.zReadoutLocal);
        Vector3d p3L = new Vector3d(-c.xHalfLarge, c.yHalf, c.zReadoutLocal);

        // transform to global
        Vector3d p1G = toGlobalSensitive(region, sector, layer, p1L);
        Vector3d p2G = toGlobalSensitive(region, sector, layer, p2L);
        Vector3d p3G = toGlobalSensitive(region, sector, layer, p3L);

        Point3D P1 = new Point3D(p1G.x, p1G.y, p1G.z);
        Point3D P2 = new Point3D(p2G.x, p2G.y, p2G.z);
        Point3D P3 = new Point3D(p3G.x, p3G.y, p3G.z);

        // normal = (P1->P2) x (P1->P3)
        Vector3D v1 = P1.vectorTo(P2);
        Vector3D v2 = P1.vectorTo(P3);
        Vector3D n = v1.cross(v2);
        n.unit();

        // If you want a stable sign convention, uncomment:
        // if (n.z() < 0) n.setXYZ(-n.x(), -n.y(), -n.z());
        return new Plane3D(P1, n);
    }

    // ------------------------------------------------------------------------
    //  Build orchestration
    // ------------------------------------------------------------------------
    public final void buildAll() {
        clearCaches();
        fillStripLists();
        fillSurfaceLists();
        fillPlaneLists();
    }

    protected void clearCaches() {
        globalStrips.clear();
        localStrips.clear();
        tiltedStrips.clear();
        planes.clear();
        surfaceLayers.clear();
        nComponents.clear();
    }

    protected void fillStripLists() {

        for (int sector = 1; sector <= C.NSECTORS; sector++) {
            for (int layer = 1; layer <= C.NLAYERS; layer++) {

                int region = (layer + 1) / 2;

                StripConstants sc = buildStripConstants(region, layer);
                List<StripGeom> strips = buildStripCache(sc);

                nComponents.add(strips.size(), sector, layer);

                for (StripGeom s : strips) {

                    // local line (sensitive-local)
                    Line3D local = new Line3D(
                            s.p1Local.x, s.p1Local.y, s.p1Local.z,
                            s.p2Local.x, s.p2Local.y, s.p2Local.z
                    );

                    // global endpoints
                    Vector3d g1 = toGlobalSensitive(region, sector, layer, s.p1Local);

                    Vector3d g2 = toGlobalSensitive(region, sector, layer, s.p2Local);

                    Line3D global = new Line3D(
                            g1.x, g1.y, g1.z,
                            g2.x, g2.y, g2.z
                    );

                    Line3D tilted = toTilted(sector, global);

                    // cache with CLAS12 indices: (sector, layer, component)
                    localStrips.add(local, sector, layer, s.component);
                    globalStrips.add(global, sector, layer, s.component);
                    tiltedStrips.add(tilted, sector, layer, s.component);
                }
            }
        }
    }

    protected void fillSurfaceLists() {
        for (int sector = 1; sector <= C.NSECTORS; sector++) {
            for (int layer = 1; layer <= C.NLAYERS; layer++) {
                surfaceLayers.add(createSurface(sector, layer), sector, layer);
            }
        }
    }

    protected void fillPlaneLists() {
        for (int sector = 1; sector <= C.NSECTORS; sector++) {
            for (int layer = 1; layer <= C.NLAYERS; layer++) {
                planes.add(createPlane(sector, layer), sector, layer);
            }
        }
    }

    // ------------------------------------------------------------------------
    //  Public API (cached getters)
    // ------------------------------------------------------------------------
    /**
     * Global strip line in CLAS12 frame.
     *
     * @param sector
     * @param layer
     * @param component
     * @return
     */
    public Line3D getStrip(int sector, int layer, int component) {
        return globalStrips.getItem(sector, layer, component);
    }

    /**
     * Strip line in sensitive-volume local frame.
     *
     * @param sector
     * @param layer
     * @param component
     * @return
     */
    public Line3D getStripLocal(int sector, int layer, int component) {
        return localStrips.getItem(sector, layer, component);
    }

    /**
     * Strip line in legacy tilted frame (debug/plot).
     *
     * @param sector
     * @param layer
     * @param component
     * @return
     */
    public Line3D getStripTilted(int sector, int layer, int component) {
        return tiltedStrips.getItem(sector, layer, component);
    }

    /**
     * Readout plane in global frame.
     *
     * @param sector
     * @param layer
     * @return
     */
    public Plane3D getPlane(int sector, int layer) {
        return planes.getItem(sector, layer);
    }

    /**
     * Readout trapezoid surface in global frame.
     *
     * @param sector
     * @param layer
     * @return
     */
    public Trap3D getSurface(int sector, int layer) {
        return surfaceLayers.getItem(sector, layer);
    }

    /**
     * Number of components (strips) for this (sector, layer).
     *
     * @param sector
     * @param layer
     * @return
     */
    public int getNComponents(int sector, int layer) {
        Integer n = nComponents.getItem(sector, layer);
        return (n == null) ? 0 : n;
    }
}
