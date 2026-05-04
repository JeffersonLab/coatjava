package org.jlab.rec.ahdc.AI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.io.base.DataBank;
import org.jlab.rec.ahdc.Hit.Hit;

/** Builds the graph tensors expected by the exported GNN edge scorer.
 *  Ports track-finding/gnn/dataset.py::build_graph — must stay byte-compatible
 *  with the training-time feature layout and normalization.
 */
final class GNNGraphBuilder {

    /** Container for the tensors + node provenance that the caller needs. */
    static final class GraphInput {
        final float[][] nodeFeatures;   // shape [N, 10]
        final long[][]  edgeIndex;      // shape [2, E]
        final float[][] edgeAttr;       // shape [E, 9]
        /** nodeToSource[i] is the backing Hit for AHDC nodes, or null for ATOF nodes. */
        final Hit[]     nodeToSource;

        GraphInput(float[][] nodeFeatures, long[][] edgeIndex, float[][] edgeAttr, Hit[] nodeToSource) {
            this.nodeFeatures = nodeFeatures;
            this.edgeIndex    = edgeIndex;
            this.edgeAttr     = edgeAttr;
            this.nodeToSource = nodeToSource;
        }
    }

    private GNNGraphBuilder() {}

    /** Build a graph from AHDC hits (required) plus the ATOF::hits bank (optional). */
    static GraphInput build(List<Hit> ahdcHits, DataBank atofHitsBank) {
        int nAhdc = ahdcHits == null ? 0 : ahdcHits.size();

        // Node state buffers (grow as we append AHDC then ATOF nodes).
        List<double[]> nodeBuf = new ArrayList<>();   // per-node raw floats (see NodeField indexes)
        List<Line3D>   nodeLine = new ArrayList<>();  // wire line for AHDC; null for ATOF
        List<Hit>      nodeHit  = new ArrayList<>();  // backing Hit for AHDC; null for ATOF

        // --- AHDC nodes -------------------------------------------------------------
        for (int i = 0; i < nAhdc; i++) {
            Hit h = ahdcHits.get(i);
            Line3D line = h.getLine();
            if (line == null) continue; // missing geometry → skip (shouldn't happen after setWirePosition)

            Point3D mid = line.midpoint();
            Vector3D dir = line.toVector();
            double len = Math.max(dir.mag(), 1e-12);
            double ux = dir.x() / len, uy = dir.y() / len, uz = dir.z() / len;
            double stereo = Math.atan2(Math.sqrt(ux*ux + uy*uy), uz);

            int absLayer = (h.getSuperLayerId() - 1) * 2 + (h.getLayerId() - 1);
            nodeBuf.add(new double[]{
                    absLayer,          // 0: abs_layer
                    h.getPhi(),        // 1: phi
                    h.getRadius(),     // 2: r
                    stereo,            // 3: stereo_angle
                    mid.x(),           // 4: x_mid
                    mid.y(),           // 5: y_mid
                    mid.z(),           // 6: z_mid
                    ux,                // 7: ux
                    uy,                // 8: uy
                    uz,                // 9: uz
                    h.getX(),          // 10: x (raw, for edge distance mask)
                    h.getY(),          // 11: y (raw, for edge distance mask)
                    0.0,               // 12: det_type = 0 (AHDC)
            });
            nodeLine.add(line);
            nodeHit.add(h);
        }

        // --- ATOF nodes -------------------------------------------------------------
        // Deduplicate by (sector, layer, component) — inference-time variant of the
        // Python dedup which also keys on track id (only needed at training time).
        if (atofHitsBank != null) {
            Set<Long> seen = new HashSet<>();
            int rows = atofHitsBank.rows();
            for (int r = 0; r < rows; r++) {
                int sector    = atofHitsBank.getInt("sector", r);
                int layer     = atofHitsBank.getInt("layer", r);
                int component = atofHitsBank.getInt("component", r);
                long key = (((long)sector * 1000L) + layer) * 1000L + component;
                if (!seen.add(key)) continue;

                double x = atofHitsBank.getFloat("x", r);
                double y = atofHitsBank.getFloat("y", r);
                double radius = Math.hypot(x, y);
                double phi = Math.atan2(y, x);
                int absLayer = (component == 10) ? GNNConstants.ATOF_BAR_ABS_LAYER
                                                 : GNNConstants.ATOF_WEDGE_ABS_LAYER;

                nodeBuf.add(new double[]{
                        absLayer, phi, radius,
                        0.0,                // stereo
                        x, y, 0.0,          // mid
                        0.0, 0.0, 1.0,      // (ux, uy, uz)
                        x, y,               // raw x, y (for edge mask)
                        1.0,                // det_type = 1 (ATOF)
                });
                nodeLine.add(null);
                nodeHit.add(null);
            }
        }

        int n = nodeBuf.size();
        if (n < 2) {
            return new GraphInput(new float[0][GNNConstants.NODE_FEAT_DIM],
                                  new long[][]{new long[0], new long[0]},
                                  new float[0][GNNConstants.EDGE_FEAT_DIM],
                                  new Hit[0]);
        }

        // --- Node feature tensor [N, 10] --------------------------------------------
        float[][] nodeFeatures = new float[n][GNNConstants.NODE_FEAT_DIM];
        for (int i = 0; i < n; i++) {
            double[] v = nodeBuf.get(i);
            nodeFeatures[i][0] = (float)(v[0] / 11.0);
            nodeFeatures[i][1] = (float)(v[1] / Math.PI);
            nodeFeatures[i][2] = (float)(v[2] / GNNConstants.DOCA_STD);
            nodeFeatures[i][3] = (float)(v[3] / GNNConstants.STEREO_ANGLE_MAX);
            nodeFeatures[i][4] = (float)(v[4] / GNNConstants.MAX_R);
            nodeFeatures[i][5] = (float)(v[5] / GNNConstants.MAX_R);
            nodeFeatures[i][6] = (float)(v[6] / GNNConstants.Z_HALF_LENGTH);
            nodeFeatures[i][7] = (float)(v[7] * GNNConstants.STEREO_SCALE);
            nodeFeatures[i][8] = (float)(v[8] * GNNConstants.STEREO_SCALE);
            nodeFeatures[i][9] = (float)(v[9]);
        }

        // --- Edge construction (directed, layer_gap in [1, MAX_LAYER_GAP]) -----------
        // Mirrors Python's np.where(mask) on a non-symmetric mask.
        int[] absLayer = new int[n];
        double[] xRaw = new double[n];
        double[] yRaw = new double[n];
        double[] rRaw = new double[n];
        double[] phiRaw = new double[n];
        double[] stereoRaw = new double[n];
        double[] detTypeRaw = new double[n];
        for (int i = 0; i < n; i++) {
            double[] v = nodeBuf.get(i);
            absLayer[i]   = (int) v[0];
            phiRaw[i]     = v[1];
            rRaw[i]       = v[2];
            stereoRaw[i]  = v[3];
            xRaw[i]       = v[10];
            yRaw[i]       = v[11];
            detTypeRaw[i] = v[12];
        }

        List<long[]> edgePairs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                int gap = absLayer[j] - absLayer[i];
                if (gap < 1 || gap > GNNConstants.MAX_LAYER_GAP) continue;
                double dx = xRaw[i] - xRaw[j];
                double dy = yRaw[i] - yRaw[j];
                if (dx*dx + dy*dy > GNNConstants.MAX_EDGE_DIST_SQ) continue;
                edgePairs.add(new long[]{i, j});
            }
        }

        int e = edgePairs.size();
        long[][] edgeIndex = new long[2][e];
        float[][] edgeAttr = new float[e][GNNConstants.EDGE_FEAT_DIM];

        for (int k = 0; k < e; k++) {
            long[] p = edgePairs.get(k);
            int s = (int) p[0];
            int d = (int) p[1];
            edgeIndex[0][k] = s;
            edgeIndex[1][k] = d;

            // dphi wrapped into [-pi, pi]
            double dphi = phiRaw[s] - phiRaw[d];
            dphi = ((dphi + Math.PI) % (2.0 * Math.PI) + 2.0 * Math.PI) % (2.0 * Math.PI) - Math.PI;
            double dlayer = (double)(absLayer[d] - absLayer[s]) / GNNConstants.MAX_LAYER_GAP;

            double doca, z1, z2;
            Line3D ls = nodeLine.get(s);
            Line3D ld = nodeLine.get(d);
            if (ls != null && ld != null) {
                doca = ls.distance(ld).length();
                // Python: z1 = cp_d.z, where cp_d is the point on line_s closest to line_d's midpoint
                //         z2 = cp_s.z, where cp_s is the point on line_d closest to line_s's midpoint
                z1 = clampZ(ls.distance(ld.midpoint()).origin().z());
                z2 = clampZ(ld.distance(ls.midpoint()).origin().z());
            } else {
                double ex = xRaw[s] - xRaw[d];
                double ey = yRaw[s] - yRaw[d];
                doca = Math.hypot(ex, ey);
                z1 = 0.0;
                z2 = 0.0;
            }

            double edgeDetType = 0.5 * (detTypeRaw[s] + detTypeRaw[d]);

            edgeAttr[k][0] = (float)(dphi / Math.PI);
            edgeAttr[k][1] = (float) dlayer;
            edgeAttr[k][2] = (float)(doca / GNNConstants.MAX_R);
            edgeAttr[k][3] = (float)(z1 / GNNConstants.Z_HALF_LENGTH);
            edgeAttr[k][4] = (float)(z2 / GNNConstants.Z_HALF_LENGTH);
            edgeAttr[k][5] = (float)(rRaw[s] / GNNConstants.DOCA_STD);
            edgeAttr[k][6] = (float)(rRaw[d] / GNNConstants.DOCA_STD);
            edgeAttr[k][7] = (float)((stereoRaw[s] - stereoRaw[d]) / (2.0 * GNNConstants.STEREO_ANGLE_MAX));
            edgeAttr[k][8] = (float) edgeDetType;
        }

        Hit[] nodeToHit = nodeHit.toArray(new Hit[0]);
        return new GraphInput(nodeFeatures, edgeIndex, edgeAttr, nodeToHit);
    }

    private static double clampZ(double z) {
        if (z < -GNNConstants.Z_HALF_LENGTH) return -GNNConstants.Z_HALF_LENGTH;
        if (z >  GNNConstants.Z_HALF_LENGTH) return  GNNConstants.Z_HALF_LENGTH;
        return z;
    }
}
