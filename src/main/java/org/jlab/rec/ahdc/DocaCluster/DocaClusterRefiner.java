package org.jlab.rec.ahdc.DocaCluster;

import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.PreCluster.PreCluster;
import org.jlab.rec.ahdc.Cluster.Cluster;

import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * Build refined cluster space points using DOCA circles.
 *
 * For each original Cluster (2 PreClusters with stereo angle),
 * if the hit multiplicity is (1,1), (1,2)/(2,1) or (2,2), we
 * construct new space points from circle-circle tangents.
 * Otherwise we fall back to the original (x,y,z) with weight 1.
 */
public class DocaClusterRefiner {

    // --- internal small 2D helpers ------------------------------------------------

    private static class Vec2 {
        double x;
        double y;
        Vec2(double x, double y) { this.x = x; this.y = y; }

        Vec2 add(Vec2 o)   { return new Vec2(this.x + o.x, this.y + o.y); }
        Vec2 sub(Vec2 o)   { return new Vec2(this.x - o.x, this.y - o.y); }
        Vec2 scale(double s){ return new Vec2(this.x * s, this.y * s); }
    }

    private static class Vec3 {
        double x, y, z;

        Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        //plus
        Vec3 add(Vec3 o) { 
            return new Vec3(this.x + o.x, this.y + o.y, this.z + o.z); 
        }
        //minus
        Vec3 sub(Vec3 o) { 
            return new Vec3(this.x - o.x, this.y - o.y, this.z - o.z); 
        }
        //scale
        Vec3 scale(double s) { 
            return new Vec3(this.x * s, this.y * s, this.z * s); 
        }
        //Dot Product
        double dot(Vec3 o) {
            return this.x * o.x + this.y * o.y + this.z * o.z;
        }
        //Cross Product
        Vec3 cross(Vec3 o) {
            return new Vec3(
                this.y * o.z - this.z * o.y,
                this.z * o.x - this.x * o.z,
                this.x * o.y - this.y * o.x
            );
        }
        @Override
        public String toString() {
            return String.format("Vec3(%.2f, %.2f, %.2f)", x, y, z);
        }
    }

    // line: n·x + c = 0, n is unit normal
    private static class Line {
        Vec2 n;
        double c;
        Line(Vec2 n, double c) { this.n = n; this.c = c; }
    }

    // Given a common tangent line to two circles (c1,r1) and (c2,r2),
    // compute the two tangent points on each circle.
    private static Vec2[] tangentSegmentPoints(Vec2 c1, double r1,
                                               Vec2 c2, double r2,
                                               Line line) {
        Vec2 n = line.n;
        double c = line.c;

        // signed distance from centers to line
        double d1 = n.x * c1.x + n.y * c1.y + c;
        double d2 = n.x * c2.x + n.y * c2.y + c;

        // projection of centers onto the line (tangent points)
        Vec2 t1 = new Vec2(c1.x - n.x * d1, c1.y - n.y * d1);
        Vec2 t2 = new Vec2(c2.x - n.x * d2, c2.y - n.y * d2);

        return new Vec2[]{t1, t2};
    }
    // Tangent lines
    private static class Tangents {
        Line[] internalLines = new Line[2];
        Line[] externalLines = new Line[2];
        boolean hasInternal = false;
        boolean hasExternal = false;
    }
    //To see whether a point is on one side of a line or the other
    private static double side_ql(Vec2 q, Line l) {
        return l.n.x * q.x + l.n.y * q.y + l.c;
    }

    /**
     * Project the 3D DOCA of a hit onto the transverse (x,y) plane,
     * by multiplying DOCA with cos(angle between wire direction and beamline).
     * Beamline is assumed to be along the z-axis.
     */
    private static double getProjectedDocaXY(Hit h) {
        double doca = Math.abs(h.getDoca());
        Line3D wire = h.getLine();

        // If the wire line is not available, fall back to full DOCA
        if (wire == null) {
            return doca;
        }

        Point3D o = wire.origin();
        Point3D e = wire.end();

        double dx = e.x() - o.x();
        double dy = e.y() - o.y();
        double dz = e.z() - o.z();

        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) {
            // degenerate line, also fall back
            return doca;
        }

        // unit vector along wire
        double ux = dx / len;
        double uy = dy / len;
        double uz = dz / len;

        // beamline direction = (0,0,1), so cos(angle) = |uz|
        double cosAlpha = Math.abs(uz);

        // projected DOCA radius in XY plane
        return doca / cosAlpha;
    }

    private static Vec3 getDirection(Hit h) {
        Line3D wire = h.getLine();

        Point3D o = wire.origin();
        Point3D e = wire.end();

        double dx = e.x() - o.x();
        double dy = e.y() - o.y();
        double dz = e.z() - o.z();

        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // unit vector along wire
        double ux = dx / len;
        double uy = dy / len;
        double uz = dz / len;

        // beamline direction = (0,0,1), so cos(angle) = |uz|
        double cosAlpha = Math.abs(uz);

        Vec3 u = new Vec3(ux, uy, uz);

        // projected DOCA radius in XY plane
        return u;
    }

    // Select up to maxN hits with the largest ADC from a hit list.
    // This does NOT modify the original list in PreCluster.
    private static ArrayList<Hit> selectTopHitsByADC(ArrayList<Hit> hits, int maxN) {
        ArrayList<Hit> result = new ArrayList<>();
        if (hits == null || hits.isEmpty() || maxN <= 0) {
            return result;
        }

        ArrayList<Hit> copy = new ArrayList<>(hits);

        Collections.sort(copy, (h1, h2) -> Double.compare(h2.getADC(), h1.getADC()));

        int n = Math.min(maxN, copy.size());
        for (int i = 0; i < n; i++) {
            result.add(copy.get(i));
        }

        return result;
    }

    /**
     * Build a 3D line by shifting the original wire line in the (x,y) plane
     * so that its origin passes through the given tangent point (tx, ty),
     * while keeping the same direction and z-coordinate at the origin.
     */
    private static Line3D buildShiftedWireLine(Hit h, Vec2 tangentXY) {
        Line3D wire = h.getLine();

        Point3D o = wire.origin();
        Point3D e = wire.end();

        double dx = e.x() - o.x();
        double dy = e.y() - o.y();
        double dz = e.z() - o.z();

        // New origin: (tangent.x, tangent.y, original_z)
        //Point3D oShift = new Point3D(tangentXY.x, tangentXY.y, o.z());
        //Point3D eShift = new Point3D(oShift.x() + dx, oShift.y() + dy, oShift.z() + dz);
        Point3D eShift = new Point3D(tangentXY.x, tangentXY.y, e.z());
        Point3D oShift = new Point3D(tangentXY.x - dx, tangentXY.y - dy, e.z() - dz);
        return new Line3D(oShift, eShift);
    }

    /**
     * Compute the midpoint of the shortest segment between two 3D lines.
     * Returns null if the lines are (almost) parallel.
     */
    private static Point3D midpointOfCommonPerpendicular(Line3D L1, Line3D L2) {
        Point3D p1 = L1.origin();
        Point3D p2 = L2.origin();

        double v1x = L1.end().x() - p1.x();
        double v1y = L1.end().y() - p1.y();
        double v1z = L1.end().z() - p1.z();

        double v2x = L2.end().x() - p2.x();
        double v2y = L2.end().y() - p2.y();
        double v2z = L2.end().z() - p2.z();

        // Normalize directions
        double len1 = Math.sqrt(v1x*v1x + v1y*v1y + v1z*v1z);
        double len2 = Math.sqrt(v2x*v2x + v2y*v2y + v2z*v2z);
        if (len1 < 1e-6 || len2 < 1e-6) {
            return null;
        }
        v1x /= len1; v1y /= len1; v1z /= len1;
        v2x /= len2; v2y /= len2; v2z /= len2;

        // Solve for s,t that minimize |(p1 + s v1) - (p2 + t v2)|^2
        double w0x = p1.x() - p2.x();
        double w0y = p1.y() - p2.y();
        double w0z = p1.z() - p2.z();

        double a = v1x*v1x + v1y*v1y + v1z*v1z;
        double b = v1x*v2x + v1y*v2y + v1z*v2z;
        double c = v2x*v2x + v2y*v2y + v2z*v2z;
        double d = v1x*w0x + v1y*w0y + v1z*w0z;
        double e = v2x*w0x + v2y*w0y + v2z*w0z;

        double denom = a*c - b*b;
        if (Math.abs(denom) < 1e-6) {
            // Lines nearly parallel, no stable unique common perpendicular
            return null;
        }

        double s = (b*e - c*d) / denom;
        double t = (a*e - b*d) / denom;

        double q1x = p1.x() + s*v1x;
        double q1y = p1.y() + s*v1y;
        double q1z = p1.z() + s*v1z;

        double q2x = p2.x() + t*v2x;
        double q2y = p2.y() + t*v2y;
        double q2z = p2.z() + t*v2z;

        // Midpoint of the shortest segment
        return new Point3D(0.5*(q1x + q2x),
                        0.5*(q1y + q2y),
                        0.5*(q1z + q2z));
    }

    private static final double STEREO_ANGLE_DEG = 20.0;
    private static final double DELTA_Z = 300.0;
    private static final double Z_OFFSET = 150.0;

    // =====================================================================
    // public API
    // =====================================================================

    /**
     * Build a list of DocaCluster objects from the original list of Cluster.
     * Each Cluster may generate multiple DocaCluster points.
     */

    public static ArrayList<DocaCluster> buildRefinedClusters(List<Cluster> clusters) {

        ArrayList<DocaCluster> out = new ArrayList<>();

        if (clusters == null) return out;

        for (int idx = 0; idx < clusters.size(); idx++) {
            Cluster cl = clusters.get(idx);

            ArrayList<PreCluster> pcs = cl.get_PreClusters_list();
            if (pcs == null || pcs.size() != 2) {
                // Fallback: one degenerate DocaCluster at original position
                out.add(new DocaCluster(cl.get_X(), cl.get_Y(), cl.get_Z(),
                                        1.0, 0, idx));
                continue;
            }

            PreCluster pc1 = pcs.get(0);
            PreCluster pc2 = pcs.get(1);

            ArrayList<Hit> hits1 = pc1.get_hits_list();
            ArrayList<Hit> hits2 = pc2.get_hits_list();

            // -----------------------------------------------------------------
            // NEW: reduce each precluster to at most 2 hits by keeping only
            //      the hits with the largest ADC.
            // -----------------------------------------------------------------
            ArrayList<Hit> effHits1 = selectTopHitsByADC(hits1, 2);
            ArrayList<Hit> effHits2 = selectTopHitsByADC(hits2, 2);

            int n1 = effHits1.size();
            int n2 = effHits2.size();

            // Now we only need to handle (1,1), (1,2)/(2,1), (2,2).
            // Any case with 0 hits falls back to the original cluster.
            if (n1 == 1 && n2 == 1) {

                out.addAll(refine11(cl,
                                    pc1, effHits1.get(0),
                                    pc2, effHits2.get(0),
                                    idx));

            } else if (n1 == 1 && n2 == 2) {

                out.addAll(refine12(cl,
                                    pc1, effHits1,
                                    pc2, effHits2,
                                    true,   // pc1 is the single-hit side
                                    idx));

            } else if (n1 == 2 && n2 == 1) {

                out.addAll(refine12(cl,
                                    pc2, effHits2,
                                    pc1, effHits1,
                                    false,  // pc2 is the single-hit side
                                    idx));

            } else if (n1 == 2 && n2 == 2) {

                out.addAll(refine22(cl,
                                    pc1, effHits1,
                                    pc2, effHits2,
                                    idx));

            } else {
                // At least one precluster ended up with 0 hits after selection:
                // fall back to a single DocaCluster at the original position.
                out.add(new DocaCluster(cl.get_X(), cl.get_Y(), cl.get_Z(),
                                        1.0, 0, idx));
            }
        }

        return out;
    }
    // =====================================================================
    // (1,1) case
    // =====================================================================

    private static List<DocaCluster> refine11(Cluster oldCluster,
                                            PreCluster pc1, Hit h1,
                                            PreCluster pc2, Hit h2,
                                            int clusterIndex) {

        List<DocaCluster> out = new ArrayList<>();
        //double Proj = Math.cos(Math.toRadians(10)); //project to xy plane
        // Centers of the two DOCA circles from the two single hits
        Vec2 c1 = new Vec2(h1.getX(), h1.getY());
        Vec2 c2 = new Vec2(h2.getX(), h2.getY());
        //double r1 = Math.abs(h1.getDoca()*Proj);
        //double r2 = Math.abs(h2.getDoca()*Proj);
        double r1 = getProjectedDocaXY(h1);
        double r2 = getProjectedDocaXY(h2);

        // Compute internal and external tangents between the two circles
        Tangents tans = computeTangents(c1, r1, c2, r2);

        // If we cannot build both internal and external tangents,
        // fall back to a single midpoint with the original cluster Z
        if (!tans.hasInternal || !tans.hasExternal) {
            Vec2 mid = c1.add(c2).scale(0.5);
            out.add(new DocaCluster(mid.x, mid.y, oldCluster.get_Z(),
                                    1.0, 11, clusterIndex));
            return out;
        }

        // Collect the four tangent lines: 2 internal + 2 external
        Line[] lines = new Line[]{
                tans.internalLines[0],
                tans.internalLines[1],
                tans.externalLines[0],
                tans.externalLines[1]
        };

        // For each tangent line:
        //  - compute the two tangent points on the two circles;
        //  - take their midpoint as the DocaCluster (X,Y);
        //  - compute phi1, phi2 from the two tangent points and use
        //    computeZ(pc1,pc2,phi1,phi2) for Z;
        //  - assign weight 0.25 (we will renormalize if fewer than 4).
        for (Line L : lines) {
            if (L == null) continue;

            Vec2[] tpts = tangentSegmentPoints(c1, r1, c2, r2, L);
            Vec2 t_on_pc1 = tpts[0];
            Vec2 t_on_pc2 = tpts[1];

            // midpoint of the two tangent points -> DocaCluster position
            //Vec2 mid = t_on_pc1.add(t_on_pc2).scale(0.5);

            // Build shifted 3D wires
            Line3D wire1 = buildShiftedWireLine(h1, t_on_pc1);
            Line3D wire2 = buildShiftedWireLine(h2, t_on_pc2);           

            Point3D mid3D = midpointOfCommonPerpendicular(wire1, wire2);
            if (mid3D == null) {
                // If the lines are almost parallel, fall back to 2D midpoint
                Vec2 mid2D = t_on_pc1.add(t_on_pc2).scale(0.5);
                out.add(new DocaCluster(mid2D.x, mid2D.y, oldCluster.get_Z(),
                                        1.0, 11, clusterIndex));
            } else {
                out.add(new DocaCluster(mid3D.x(), mid3D.y(), mid3D.z(),
                                        1.0, 11, clusterIndex));
            }

            // compute z using the phi of the two tangent points
            //double phi1 = computePhiFromXY(t_on_pc1.x, t_on_pc1.y);
            //double phi2 = computePhiFromXY(t_on_pc2.x, t_on_pc2.y);
            //double z    = computeZ(pc1, pc2, phi1, phi2);

            //out.add(new DocaCluster(mid.x, mid.y, z,
            //                        0.25, 11, clusterIndex));
        }

        // Safety: if some tangents failed and we ended up with 0 points,
        // fall back to a single midpoint with original Z.
        if (out.isEmpty()) {
            Vec2 mid = c1.add(c2).scale(0.5);
            out.add(new DocaCluster(mid.x, mid.y, oldCluster.get_Z(),
                                    1.0, 11, clusterIndex));
            return out;
        }

        return out;
    }

    // =====================================================================
    // (1,2) or (2,1) case
    // =====================================================================

    /**
     * One precluster has 1 hit (singlePc, singleHits), the other has 2 hits (doublePc, doubleHits).
     * singleIsFirst indicates whether in the original Cluster list ordering we had:
     *   - true  : (pc1, pc2) = (singlePc, doublePc)
     *   - false : (pc1, pc2) = (doublePc, singlePc)
     */
    private static List<DocaCluster> refine12(Cluster oldCluster,
                                              PreCluster singlePc, ArrayList<Hit> singleHits,
                                              PreCluster doublePc, ArrayList<Hit> doubleHits,
                                              boolean singleIsFirst,
                                              int clusterIndex) {

        List<DocaCluster> out = new ArrayList<>();
        //double Proj = Math.cos(Math.toRadians(10));
        if (singleHits.size() != 1 || doubleHits.size() != 2) {
            return out;
        }

        Hit hs  = singleHits.get(0);
        Hit h2a = doubleHits.get(0);
        Hit h2b = doubleHits.get(1);

        Vec2 cs = new Vec2(hs.getX(), hs.getY());
        //double rs = Math.abs(hs.getDoca()*Proj);
        double rs = getProjectedDocaXY(hs);
        

        Vec2 c2a = new Vec2(h2a.getX(), h2a.getY());
        Vec2 c2b = new Vec2(h2b.getX(), h2b.getY());
        double costheta = Math.abs(getDirection(h2a).z);
        Vec2 c2bp = new Vec2((h2b.getX()-h2a.getX())*costheta+h2a.getX(), (h2b.getY()-h2a.getY())*costheta+h2a.getY());        
        double r2a = Math.abs(h2a.getDoca());
        double r2b = Math.abs(h2b.getDoca());
        //double r2a = getProjectedDocaXY(h2a);
        //double r2b = getProjectedDocaXY(h2b);

        // 1) internal tangents intersection for the 2-hit precluster
        Tangents tans2 = computeTangents(c2a, r2a, c2bp, r2b);
        if (!tans2.hasInternal) {
            // Fallback: use the average of all three centers
            Vec2 avg = cs.add(c2a).add(c2b).scale(1.0 / 3.0);
            out.add(new DocaCluster(avg.x, avg.y, oldCluster.get_Z(),
                                    1.0, singleIsFirst ? 12 : 21, clusterIndex));
            return out;
        }

        Vec2 p2p = intersectLines(tans2.internalLines[0], tans2.internalLines[1]);
        if (p2p == null) {
            Vec2 avg = cs.add(c2a).add(c2b).scale(1.0 / 3.0);
            out.add(new DocaCluster(avg.x, avg.y, oldCluster.get_Z(),
                                    1.0, singleIsFirst ? 12 : 21, clusterIndex));
            return out;
        }
        Vec2 p2 = new Vec2((p2p.x-h2a.getX())/costheta+h2a.getX(), (p2p.y-h2a.getY())/costheta+h2a.getY());

        // 2) from p2 draw two tangents to the single-hit doca circle
        List<Vec2> tangPoints = tangentPointsFromPointToCircle(p2, cs, rs);
        if (tangPoints == null || tangPoints.size() < 2) {
            // No tangents: fallback to p2 only
            out.add(new DocaCluster(p2.x, p2.y, oldCluster.get_Z(),
                                    1.0, singleIsFirst ? 12 : 21, clusterIndex));
            return out;
        }
        Line line_cs_p2 = lineThroughPoints(cs, p2);
        
        Vec2 t1, t2;//tangPoints.get(0) & c2a on the same side
        if (side_ql(tangPoints.get(0), line_cs_p2)*side_ql(c2a, line_cs_p2)>0){
            t1 = tangPoints.get(0);
            t2 = tangPoints.get(1);
        }
        else {
            t1 = tangPoints.get(1);
            t2 = tangPoints.get(0);
        }

        //Vec2 m1 = t1.add(p2).scale(0.5); // midpoint on the single-hit doca circle
        //Vec2 m2 = t2.add(p2).scale(0.5);

        // 3) compute z using p2 (double precluster) and m (single precluster)
        //    with correct ordering of preclusters
        //PreCluster pc1, pc2;
        //Vec2 p1Geo, p2Geo;
        //Vec2 p1Geo2;
        //Vec2 p2Geo2;
        //if (singleIsFirst) {
            // original order: (pc1, pc2) = (singlePc, doublePc)
        //    pc1 = singlePc;
        //    pc2 = doublePc;
        //    p1Geo = t1;   // single
        //    p2Geo = p2;  // double
        //    p1Geo2 = t2;
        //    p2Geo2 = p2;

        //} else {
            // original order: (pc1, pc2) = (doublePc, singlePc)
        //    pc1 = doublePc;
        //    pc2 = singlePc;
        //    p1Geo = p2;  // double
        //    p2Geo = t1;   // single
        //    p2Geo2 = t2;
        //    p1Geo2 = p2;

        //}

        //double phi1 = computePhiFromXY(p1Geo.x, p1Geo.y);
        //double phi2 = computePhiFromXY(p2Geo.x, p2Geo.y);
        //double phi1r = computePhiFromXY(p1Geo2.x, p1Geo2.y);
        //double phi2r = computePhiFromXY(p2Geo2.x, p2Geo2.y);
        //double zRef1 = computeZ(pc1, pc2, phi1, phi2);
        //double zRef2 = computeZ(pc1, pc2, phi1r, phi2r);

        // 4) compute weights via distance to the two tangent lines

        Line L1 = tans2.internalLines[0]; 
        Line L2 = tans2.internalLines[1];
        double d1, d2;
        if (side_ql(cs, L1)*side_ql(c2a, L1)<0){
            d1 = distancePointToLine(cs, L1);
            d2 = distancePointToLine(cs, L2);
        }
        else {
            d1 = distancePointToLine(cs, L2);
            d2 = distancePointToLine(cs, L1);
        }

        double r1 = Math.abs(d1 - rs);
        double r2 = Math.abs(d2 - rs);

        double w1, w2;
        if (r1 + r2 > 1e-9) {
            // weight for p2, m
            w1 = r2 / (r1 + r2);
            w2 = r1 / (r1 + r2);
        } else {
            w1 = 0.5;
            w2 = 0.5;
        }

        int pattern = singleIsFirst ? 12 : 21;

        Line3D wires1 = buildShiftedWireLine(hs, t1);
        Line3D wires2 = buildShiftedWireLine(hs, t2);
        Line3D wired = buildShiftedWireLine(h2a, p2);

        Point3D mid3D1 = midpointOfCommonPerpendicular(wires1, wired);
        if (mid3D1 == null) {
            // If the lines are almost parallel, fall back to 2D midpoint
            Vec2 mid2D1 = t1.add(p2).scale(0.5);
            out.add(new DocaCluster(mid2D1.x, mid2D1.y, oldCluster.get_Z(),
                                    1.0, pattern, clusterIndex));
        } else {
            out.add(new DocaCluster(mid3D1.x(), mid3D1.y(), mid3D1.z(),
                                    1.0, pattern, clusterIndex));
        }

        Point3D mid3D2 = midpointOfCommonPerpendicular(wires2, wired);
        if (mid3D2 == null) {
            // If the lines are almost parallel, fall back to 2D midpoint
            Vec2 mid2D2 = t2.add(p2).scale(0.5);
            out.add(new DocaCluster(mid2D2.x, mid2D2.y, oldCluster.get_Z(),
                                    1.0, pattern, clusterIndex));
        } else {
            out.add(new DocaCluster(mid3D2.x(), mid3D2.y(), mid3D2.z(),
                                    1.0, pattern, clusterIndex));
        }

        // Two DocaCluster points: p2 and m
        //out.add(new DocaCluster(m1.x, m1.y, zRef1,
        //                        w1, pattern, clusterIndex));
        //out.add(new DocaCluster(m2.x, m2.y, zRef2,
        //                        w2, pattern, clusterIndex));

        return out;
    }

    // =====================================================================
    // (2,2) case
    // =====================================================================

    private static List<DocaCluster> refine22(Cluster oldCluster,
                                              PreCluster pc1, ArrayList<Hit> hits1,
                                              PreCluster pc2, ArrayList<Hit> hits2,
                                              int clusterIndex) {

        List<DocaCluster> out = new ArrayList<>();
        //double Proj = Math.cos(Math.toRadians(10));
        if (hits1.size() != 2 || hits2.size() != 2) return out;

        // precluster 1
        Hit h1a = hits1.get(0);
        Hit h1b = hits1.get(1);
        Vec2 c1a = new Vec2(h1a.getX(), h1a.getY());
        Vec2 c1b = new Vec2(h1b.getX(), h1b.getY());
        double costheta1 = Math.abs(getDirection(h1a).z);
        Vec2 c1bp = new Vec2((h1b.getX()-h1a.getX())*costheta1+h1a.getX(), (h1b.getY()-h1a.getY())*costheta1+h1a.getY());
        double r1a = Math.abs(h1a.getDoca());
        double r1b = Math.abs(h1b.getDoca());
        //double r1a = Math.abs(h1a.getDoca());
        //double r1b = Math.abs(h1b.getDoca());

        // precluster 2
        Hit h2a = hits2.get(0);
        Hit h2b = hits2.get(1);
        Vec2 c2a = new Vec2(h2a.getX(), h2a.getY());
        Vec2 c2b = new Vec2(h2b.getX(), h2b.getY());
        double costheta2 = Math.abs(getDirection(h2a).z);
        Vec2 c2bp = new Vec2((h2b.getX()-h2a.getX())*costheta2+h2a.getX(), (h2b.getY()-h2a.getY())*costheta2+h2a.getY());
        double r2a = Math.abs(h2a.getDoca());
        double r2b = Math.abs(h2b.getDoca());
        //double r2a = getProjectedDocaXY(h2a);
        //double r2b = getProjectedDocaXY(h2b);

        Tangents t1 = computeTangents(c1a, r1a, c1bp, r1b);
        Tangents t2 = computeTangents(c2a, r2a, c2bp, r2b);

        if (!t1.hasInternal || !t2.hasInternal) {
            // Fallback: average of hit centers
            Vec2 avg = c1a.add(c1b).add(c2a).add(c2b).scale(0.25);
            out.add(new DocaCluster(avg.x, avg.y, oldCluster.get_Z(),
                                    1.0, 22, clusterIndex));
            return out;
        }

        Vec2 p1p = intersectLines(t1.internalLines[0], t1.internalLines[1]);
        Vec2 p2p = intersectLines(t2.internalLines[0], t2.internalLines[1]);
        if (p1p == null || p2p == null) {
            Vec2 avg = c1a.add(c1b).add(c2a).add(c2b).scale(0.25);
            out.add(new DocaCluster(avg.x, avg.y, oldCluster.get_Z(),
                                    1.0, 22, clusterIndex));
            return out;
        }
        Vec2 p1 = new Vec2((p1p.x-h1a.getX())/costheta1+h1a.getX(), (p1p.y-h1a.getY())/costheta1+h1a.getY());
        Vec2 p2 = new Vec2((p2p.x-h2a.getX())/costheta2+h2a.getX(), (p2p.y-h2a.getY())/costheta2+h2a.getY());

        Line3D wire1 = buildShiftedWireLine(h1a, p1);
        Line3D wire2 = buildShiftedWireLine(h2a, p2);

        Point3D mid3D = midpointOfCommonPerpendicular(wire1, wire2);
        if (mid3D == null) {
            // If the lines are almost parallel, fall back to 2D midpoint
            Vec2 mid2D = p1.add(p2).scale(0.5);
            out.add(new DocaCluster(mid2D.x, mid2D.y, oldCluster.get_Z(),
                                    1.0, 22, clusterIndex));
        } else {
            out.add(new DocaCluster(mid3D.x(), mid3D.y(), mid3D.z(),
                                    1.0, 22, clusterIndex));
        }
        
        return out;
    }

    // =====================================================================
    // geometry helpers
    // =====================================================================

    /** Compute internal and external tangents between two circles. */
    private static Tangents computeTangents(Vec2 c1, double r1, Vec2 c2, double r2) {
        Tangents result = new Tangents();

        Vec2 dc = c2.sub(c1);
        double d2 = dc.x * dc.x + dc.y * dc.y;
        double d = Math.sqrt(d2);
        if (d < 1e-6) {
            return result; // concentric: no real tangents
        }

        Vec2 ex = new Vec2(dc.x / d, dc.y / d);
        Vec2 ey = new Vec2(-ex.y, ex.x);

        // ---- external tangents (same side) ----
        double rExt = (r2 - r1) / d;
        if (Math.abs(rExt) <= 1.0 - 1e-9) {
            double hExt = Math.sqrt(1.0 - rExt * rExt);

            Vec2 n1_loc = new Vec2(rExt, +hExt);
            Vec2 n2_loc = new Vec2(rExt, -hExt);
            // normal vector component
            Vec2 n1 = new Vec2(
                    ex.x * n1_loc.x + ey.x * n1_loc.y,
                    ex.y * n1_loc.x + ey.y * n1_loc.y
            );
            Vec2 n2 = new Vec2(
                    ex.x * n2_loc.x + ey.x * n2_loc.y,
                    ex.y * n2_loc.x + ey.y * n2_loc.y
            );
            //distance between c1 and tangent lines == r1
            double cLoc = r1;
            double c1Line = cLoc - (n1.x * c1.x + n1.y * c1.y);
            double c2Line = cLoc - (n2.x * c1.x + n2.y * c1.y);

            result.externalLines[0] = new Line(n1, c1Line);
            result.externalLines[1] = new Line(n2, c2Line);
            result.hasExternal = true;
        }

        // ---- internal tangents (opposite side) ----
        double rIntEff = (-r2 - r1) / d;
        if (Math.abs(rIntEff) <= 1.0 - 1e-9) {
            double hInt = Math.sqrt(1.0 - rIntEff * rIntEff);

            Vec2 n3_loc = new Vec2(rIntEff, +hInt);
            Vec2 n4_loc = new Vec2(rIntEff, -hInt);

            Vec2 n3 = new Vec2(
                    ex.x * n3_loc.x + ey.x * n3_loc.y,
                    ex.y * n3_loc.x + ey.y * n3_loc.y
            );
            Vec2 n4 = new Vec2(
                    ex.x * n4_loc.x + ey.x * n4_loc.y,
                    ex.y * n4_loc.x + ey.y * n4_loc.y
            );

            double cLoc = r1;
            double c3 = cLoc - (n3.x * c1.x + n3.y * c1.y);
            double c4 = cLoc - (n4.x * c1.x + n4.y * c1.y);

            result.internalLines[0] = new Line(n3, c3);
            result.internalLines[1] = new Line(n4, c4);
            result.hasInternal = true;
        }

        return result;
    }

    /** Intersection of two lines n1·x + c1 = 0 and n2·x + c2 = 0. */
    private static Vec2 intersectLines(Line l1, Line l2) {
        Vec2 n1 = l1.n;
        Vec2 n2 = l2.n;
        double c1 = l1.c;
        double c2 = l2.c;

        double det = n1.x * n2.y - n1.y * n2.x;
        if (Math.abs(det) < 1e-9) return null;

        double x = (c2 * n1.y - c1 * n2.y) / det;
        double y = (n2.x * c1 - n1.x * c2) / det;
        return new Vec2(x, y);
    }

    /** Midpoint of the tangent segment between two circles for a given tangent line. */
    private static Vec2 midpointOfTangentSegment(Vec2 c1, double r1,
                                                 Vec2 c2, double r2,
                                                 Line line) {
        Vec2 n = line.n;
        double c = line.c;

        double d1 = n.x * c1.x + n.y * c1.y + c;
        double d2 = n.x * c2.x + n.y * c2.y + c;

        Vec2 t1 = new Vec2(c1.x - n.x * d1, c1.y - n.y * d1);
        Vec2 t2 = new Vec2(c2.x - n.x * d2, c2.y - n.y * d2);

        return t1.add(t2).scale(0.5);
    }

    /** Tangent points from point p to circle (center c, radius r). */
    private static List<Vec2> tangentPointsFromPointToCircle(Vec2 p, Vec2 c, double r) {
        Vec2 pc = p.sub(c);
        double d2 = pc.x * pc.x + pc.y * pc.y;
        double d = Math.sqrt(d2);
        if (d < r + 1e-9) {
            // p inside the circle, no real tangents
            return null;
        }

        // unit vector from center to point
        Vec2 u = new Vec2(pc.x / d, pc.y / d);

        // decomposition: CT along u and perpendicular w
        double l = r * r / d;
        double h = r * Math.sqrt(1.0 - (r * r) / d2);

        Vec2 w1 = new Vec2(-u.y, u.x);
        Vec2 w2 = new Vec2(u.y, -u.x);

        Vec2 t1 = c.add(u.scale(l)).add(w1.scale(h));
        Vec2 t2 = c.add(u.scale(l)).add(w2.scale(h));

        List<Vec2> res = new ArrayList<>(2);
        res.add(t1);
        res.add(t2);
        return res;
    }

    private static Line lineThroughPoints(Vec2 p1, Vec2 p2) {
        Vec2 v = p2.sub(p1);
        double len = Math.sqrt(v.x * v.x + v.y * v.y);
        if (len < 1e-9) {
            return new Line(new Vec2(1.0, 0.0), -p1.x);
        }
        Vec2 n = new Vec2(-v.y / len, v.x / len);
        double c = -(n.x * p1.x + n.y * p1.y);
        return new Line(n, c);
    }

    private static double distancePointToLine(Vec2 p, Line line) {
        return Math.abs(line.n.x * p.x + line.n.y * p.y + line.c);
    }

    /** Same φ definition as PreCluster: φ = mod(-π/2 - atan2(y,x), 2π). */
    private static double computePhiFromXY(double x, double y) {
        double phi = -Math.PI / 2.0 - Math.atan2(y, x);
        return mod(phi, 2.0 * Math.PI);
    }

    private static double mod(double a, double b) {
        return a - b * Math.floor(a / b);
    }

    /** Compute Z using the same relation as Cluster constructor but with new φ values. */
    private static double computeZ(PreCluster pre1, PreCluster pre2,
                                   double phi1, double phi2) {

        double theta = Math.toRadians(STEREO_ANGLE_DEG);
        double term1 = theta * Math.pow(-1.0, pre1.get_Super_layer() - 1);
        double term2 = theta * Math.pow(-1.0, pre2.get_Super_layer() - 1);
        double denom = term1 - term2;

        if (Math.abs(denom) < 1e-9) {
            // Should not happen with a valid stereo geometry
            return 0.0;
        }

        return ((phi2 - phi1) / denom) * DELTA_Z - Z_OFFSET;
    }
}
