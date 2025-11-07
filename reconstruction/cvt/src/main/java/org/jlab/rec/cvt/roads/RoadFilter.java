/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.jlab.rec.cvt.cluster.Cluster;
/**
 *
 * @author veronique
 */


public class RoadFilter {

    private static final int MAX_PADDLES_IN_MEMORY = 4; // tune to fit your RAM
    private static final int MIN_N_CLUSTERS = 4;        // your threshold
    private static final String TARGET_DIR_NAME = "roads/"; // your directory

    // LRU cache for road data
    private static final Map<Integer, List<CompactRoad>> paddleRoadCache =
        Collections.synchronizedMap(new LinkedHashMap<>(MAX_PADDLES_IN_MEMORY, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, List<CompactRoad>> eldest) {
                boolean remove = size() > MAX_PADDLES_IN_MEMORY;
                if (remove) {
                    System.out.printf("🧹 Evicting paddle %d from memory%n", eldest.getKey());
                }
                return remove;
            }
        });

    // ---------------------
    // Load and cache roads
    // ---------------------
    private static List<CompactRoad> loadPaddleRoads(int paddle) throws IOException {
        String fileName = String.format(TARGET_DIR_NAME + "svt_roads_paddle_%02d.bin.gz", paddle);
        List<CompactRoad> roads = new ArrayList<>();

        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(
                        new GZIPInputStream(new FileInputStream(fileName))))) {

            while (dis.available() > 0) {
                int q = dis.readInt();
                double p = dis.readFloat();
                double theta = dis.readFloat();
                double phi = dis.readFloat();
                double ztar = dis.readFloat();
                int nElements = dis.readInt();

                CompactRoad road = new CompactRoad();
                road.q = q;
                road.p = p;
                road.theta = theta;
                road.phi = phi;
                road.z = ztar;

                for (int i = 0; i < nElements; i++) {
                    int sector = dis.readInt();
                    int layer = dis.readInt();
                    int strip = dis.readInt();
                    int phiBin = dis.readInt();
                    double x = dis.readDouble();
                    double y = dis.readDouble();
                    double z = dis.readDouble();
                    road.elements.add(new CompactElement(sector, layer, strip, phiBin, x, y, z));
                }
                roads.add(road);
            }
        }

        System.out.printf("✅ Loaded %d roads for paddle %d%n", roads.size(), paddle);
        return roads;
    }

    // ---------------------
    // Cached access
    // ---------------------
    private static List<CompactRoad> getPaddleRoads(int paddle) throws IOException {
        return paddleRoadCache.computeIfAbsent(paddle, p -> {
            try {
                return loadPaddleRoads(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    // ---------------------
    // Main filtering logic
    // ---------------------
    public Map<CompactRoad, List<Cluster>> filterUsingRoads(
            Map<Integer, List<Cluster>> clusters,
            Set<Integer> paddles) throws IOException {

        Map<CompactRoad, List<Cluster>> savedClusters = new HashMap<>();

        for (int paddle : paddles) {
            List<CompactRoad> roads = getPaddleRoads(paddle); // cached load

            for (CompactRoad road : roads) {
                for (CompactElement ce : road.elements) {
                    Cluster cl = getClosestStripCluster(ce.sector, ce.layer, ce.strip, clusters);

                    if (cl != null) {
                        cl.associatedRoadKey = road.getIdentifier();
                        savedClusters.computeIfAbsent(road, k -> new ArrayList<>()).add(cl);
                    }
                }
            }
        }

        // Keep only roads with ≥ MIN_N_CLUSTERS
        savedClusters.entrySet().removeIf(e -> e.getValue().size() < MIN_N_CLUSTERS);

        return savedClusters;
    }

    // Dummy method to compile — replace with your implementation
    private Cluster getClosestStripCluster(int sector, int layer, int strip, Map<Integer, List<Cluster>> clusters) {
        List<Cluster> clList = clusters.get(layer);
        if (clList == null) return null;
        for (Cluster c : clList) {
            if (Math.abs(c.getCentroid() - strip) < 2) return c; // example
        }
        return null;
    }
}

