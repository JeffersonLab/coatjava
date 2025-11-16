/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;

import java.io.IOException;
import java.util.*;
import org.jlab.detector.base.DetectorType;
import org.jlab.rec.cvt.cluster.Cluster;
/**
 *
 * @author veronique
 */


/**
 * RoadSeeder — gives seed clusters per paddle using the compact index manager.
 */
public class RoadSeeder {

    private final RoadIndexManager rim;

    public RoadSeeder(RoadIndexManager rim) {
        this.rim = rim;
    }
    public static int MINNCLUSTERS = 4;
    /**
     * Build seeds for this event.
     *
     * @param clusters  list of clusters in SVT (all layers)
     * @param ctofpaddles paddles hit in event
     * @param paddleBytes map of paddle->byte[] gz-decoded data (already loaded at init)
     * @return map: paddle -> Map<CompactRoad, List<Cluster>>
     */
    public Map<Integer, Map<CompactRoad, List<Cluster>>> seedUsingRoads(
            List<Cluster> clusters,
            Set<Integer> ctofpaddles,
            Map<Integer, byte[]> paddleBytes) throws IOException {

        Map<Integer, Map<CompactRoad, List<Cluster>>> seedClusters = new HashMap<>();

        // Build cluster keys for matching: phiBin+sector+layer keys
        // BUT we need to produce keys per paddle when doing findRoad, we'll loop per paddle.
        Map<Integer, Set<Long>> paddleClusterKeys = new HashMap<>();
        for (int paddle : ctofpaddles) {
            paddleClusterKeys.put(paddle, new HashSet<>());
        }

        for (Cluster cl : clusters) {
            if (cl.getDetector() == DetectorType.BMT) continue;
            int sector = cl.getSector();
            int layer = cl.getLayer();
            int seedStrip = (int) Math.round(cl.getCentroid()); // or use getSeedStrip().getStrip()
            int phiBin = Util.getPhiBin(layer, sector, seedStrip);

            long key = RoadIndexManager.makeIndexKey(phiBin, sector, layer);
            for (int paddle : ctofpaddles) {
                paddleClusterKeys.get(paddle).add(key);
            }
        }

        // For each paddle, ask rim.findRoad(...) and getRoad(...) then match
        for (int paddle : ctofpaddles) {
            Set<Long> keys = paddleClusterKeys.get(paddle);
            if (keys == null || keys.isEmpty()) continue;

            RoadIndex.UberEntry ue = rim.findRoad(paddle, keys);
            if (ue == null) continue;
            CompactRoad road = rim.getRoad(paddleBytes, ue);
            if (road == null) continue;
            List<Cluster> matched = matchRoadToClusters(road.elements, clusters);

            if (matched.size() >= MINNCLUSTERS) {
                seedClusters.computeIfAbsent(paddle, k -> new HashMap<>())
                        .put(road, matched);
            }
        }

        return seedClusters;
    }

    
    /**
     * Match clusters to road by sector/layer and strip distance.
     * Return matched clusters (one per element ideally).
     */
    private List<Cluster> matchRoadToClusters(List<CompactElement> roadElements, List<Cluster> clusters) {
        List<Cluster> matched = new ArrayList<>();
        // build quick lookup map for clusters keyed by (sector<<8 | layer)
        Map<Integer, List<Cluster>> map = new HashMap<>();
        for (Cluster c : clusters) {
            int key = (c.getSector() << 8) | c.getLayer();
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }

        for (CompactElement e : roadElements) {
            int key = (e.sector << 8) | e.layer;
            List<Cluster> candidates = map.get(key);
            if (candidates == null) continue;
            Cluster best = null;
            double bestDist = Double.POSITIVE_INFINITY;
            for (Cluster c : candidates) {
                double d = Math.abs(c.getCentroid() - e.strip);
                if (d < bestDist) {
                    bestDist = d;
                    best = c;
                }
            }
            if (best != null) matched.add(best);
        }
        return matched;
    }
}
