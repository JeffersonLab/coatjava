package org.jlab.rec.ahdc.AI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.jlab.io.base.DataBank;
import org.jlab.rec.ahdc.AHDCCluster.AHDCCluster;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.PreCluster.PreCluster;
import org.jlab.rec.ahdc.PreCluster.PreClusterFinder;
import org.jlab.rec.ahdc.Track.AtofHitStub;
import org.jlab.rec.ahdc.Track.CandidateType;
import org.jlab.rec.ahdc.Track.TrackCandidate;

/** Orchestrates GNN-based track finding: builds the graph, runs the exported
 *  edge scorer, extracts tracks via connected components on edge scores
 *  thresholded at 0.1, and converts each node-set back into a
 *  {@link TrackCandidate} carrying per-superlayer Clusters so the downstream
 *  helix fit / Kalman stages can consume it. Components that include ATOF
 *  graph nodes yield {@code AHDC_ATOF} candidates with those ATOF hits
 *  attached; the rest are {@code AHDC_ONLY}.
 */
public final class GNNPrediction {

    private static final Logger LOGGER = Logger.getLogger(GNNPrediction.class.getName());

    public ArrayList<TrackCandidate> prediction(List<Hit> ahdcHits,
                                                DataBank atofHitsBank,
                                                ModelTrackFindingGNN model) {
        ArrayList<TrackCandidate> out = new ArrayList<>();
        if (ahdcHits == null || ahdcHits.isEmpty() || model == null) return out;

        GNNGraphBuilder.GraphInput g = GNNGraphBuilder.build(ahdcHits, atofHitsBank);
        int nNodes = g.nodeToSource.length;
        int nEdges = g.edgeIndex[0].length;
        if (nNodes < GNNConstants.MIN_NODES || nEdges == 0) {
            return out;   // model cannot run on graphs this small
        }

        float[] edgeScores;
        try {
            edgeScores = model.predictEdgeScores(g.nodeFeatures, g.edgeIndex, g.edgeAttr);
        } catch (Exception ex) {
            LOGGER.warning(() -> "GNN inference failed: " + ex);
            return out;
        }

        // Connected components at TRACK_SCORE_THRESHOLD, filtered to
        // components of size >= MIN_TRACK_NODES.
        List<int[]> trackNodeSets = SeedExtendTrackExtractor.extract(edgeScores, g.edgeIndex, nNodes);

        for (int[] nodes : trackNodeSets) {
            // Split the component's nodes: AHDC Hits become the candidate's hits,
            // ATOF nodes (when present) are attached so the candidate is typed
            // AHDC_ATOF. Only AHDC hits feed AHDC::track / AHDC::hits.
            ArrayList<Hit> trackHits = new ArrayList<>(nodes.length);
            ArrayList<AtofHitStub> trackAtof = new ArrayList<>();
            for (int n : nodes) {
                Hit h = g.nodeToSource[n];
                if (h != null) { trackHits.add(h); continue; }
                AtofHitStub a = g.nodeToAtof[n];
                if (a != null) trackAtof.add(a);
            }
            if (trackHits.isEmpty()) continue;

            ArrayList<AHDCCluster> clusters = buildSuperlayerClusters(trackHits);
            if (clusters.size() < 3) continue;   // matches the downstream >=3 filter

            TrackCandidate candidate = new TrackCandidate(clusters);
            if (!trackAtof.isEmpty()) {
                candidate.setType(CandidateType.AHDC_ATOF);
                for (AtofHitStub a : trackAtof) candidate.addAtofHit(a);
            }
            out.add(candidate);
        }

        return out;
    }

    /** One {@link AHDCCluster} per superlayer built from two {@link PreCluster}s (one
     *  per layer within the superlayer). Using real PreClusters — instead of the
     *  3-arg {@code AHDCCluster(x,y,z)} constructor — keeps
     *  {@code Track.generateHitList()} and {@code DocaClusterRefiner}'s stereo
     *  pairing working for GNN-discovered tracks just like they do for MLP tracks.
     */
    private static ArrayList<AHDCCluster> buildSuperlayerClusters(List<Hit> hits) {
        // Feed the track's hits through the same preclustering the MLP path uses.
        // findPreclusters mutates its input (it calls setUse(true) on consumed
        // hits), so pass a copy and ensure each hit starts unmarked.
        ArrayList<Hit> hitsForPre = new ArrayList<>(hits.size());
        for (Hit h : hits) { h.setUse(false); hitsForPre.add(h); }
        PreClusterFinder pcf = new PreClusterFinder();
        pcf.findPreclusters(hitsForPre);
        ArrayList<PreCluster> preclusters = pcf.get_AHDCPreClusters();

        // Index by (superlayer, layer). If the GNN assigns two PreClusters of the
        // same superlayer+layer to one track (rare — it would mean two disjoint
        // wire runs on the same layer), keep the largest and drop the rest.
        Map<Integer, PreCluster[]> bySuperlayer = new HashMap<>();
        for (PreCluster pc : preclusters) {
            int sl = pc.get_Super_layer();
            int layerIdx = pc.get_Layer() - 1;   // layer is 1-based, slots are [0,1]
            if (layerIdx < 0 || layerIdx > 1) continue;
            PreCluster[] slot = bySuperlayer.computeIfAbsent(sl, k -> new PreCluster[2]);
            PreCluster prev = slot[layerIdx];
            if (prev == null || pc.get_Num_wire() > prev.get_Num_wire()) slot[layerIdx] = pc;
        }

        ArrayList<AHDCCluster> clusters = new ArrayList<>();
        // Iterate superlayers in ascending order to keep downstream output stable.
        // If both stereo layers have a PreCluster, pair them (full stereo cluster).
        // If only one has hits, use the single-layer AHDCCluster(PreCluster) ctor —
        // DocaClusterRefiner handles PreClusters_list.size() != 2 with a
        // degenerate DocaCluster fallback, so the helix fit still runs.
        for (int sl = 1; sl <= 5; sl++) {
            PreCluster[] slot = bySuperlayer.get(sl);
            if (slot == null) continue;
            if (slot[0] != null && slot[1] != null) {
                clusters.add(new AHDCCluster(slot[0], slot[1]));
            } else {
                PreCluster single = (slot[0] != null) ? slot[0] : slot[1];
                if (single != null) clusters.add(new AHDCCluster(single));
            }
        }
        return clusters;
    }
}
