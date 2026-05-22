package org.jlab.rec.alert.AI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Track extraction from per-edge scores via union-find connected components
 *  at a single threshold. Ports the {@code method="cc"} branch of
 *  {@code track-finding/gnn/inference.py::extract_tracks}, which is the
 *  extractor that gnn/evaluate.py uses.
 */
final class SeedExtendTrackExtractor {

    private SeedExtendTrackExtractor() {}

    /** @return list of node-index arrays, one per connected component of size
     *          &ge; {@link GNNConstants#MIN_TRACK_NODES}. */
    static List<int[]> extract(float[] scores, long[][] edgeIndex, int nNodes) {
        if (nNodes <= 0 || scores == null || edgeIndex == null || edgeIndex[0].length != scores.length) {
            return new ArrayList<>();
        }
        long[] src = edgeIndex[0];
        long[] dst = edgeIndex[1];

        int[] parent = new int[nNodes];
        for (int i = 0; i < nNodes; i++) parent[i] = i;
        for (int e = 0; e < scores.length; e++) {
            if (scores[e] >= GNNConstants.TRACK_SCORE_THRESHOLD) {
                union(parent, (int) src[e], (int) dst[e]);
            }
        }

        Map<Integer, List<Integer>> groups = new HashMap<>();
        for (int i = 0; i < nNodes; i++) {
            int r = find(parent, i);
            groups.computeIfAbsent(r, k -> new ArrayList<>()).add(i);
        }

        List<int[]> out = new ArrayList<>();
        for (List<Integer> members : groups.values()) {
            if (members.size() < GNNConstants.MIN_TRACK_NODES) continue;
            int[] arr = new int[members.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = members.get(i);
            out.add(arr);
        }
        return out;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }
}
