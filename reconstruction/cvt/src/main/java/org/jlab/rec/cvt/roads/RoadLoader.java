/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 *
 * @author veronique
 */
public class RoadLoader {

    private static final int N_PADDLES = 48;

    // Roads for each paddle
    private final Map<Integer, CompactRoad[]> paddleRoads = new HashMap<>();

    // Per paddle, map: (sector<<8 | layer) -> list of road indices
    private final Map<Integer, Map<Integer, List<Integer>>> paddleIndex = new HashMap<>();

    public void preloadRoads(String dir) throws IOException {
        for (int paddle = 1; paddle <= N_PADDLES; paddle++) {
            Path path = Paths.get(String.format("%s/svt_roads_paddle_%02d.bin.gz", dir, paddle));
            if (!Files.exists(path)) continue;

            List<CompactRoad> roads = new ArrayList<>();
            Map<Integer, List<Integer>> index = new HashMap<>();

            try (InputStream fis = Files.newInputStream(path);
                 GZIPInputStream gis = new GZIPInputStream(fis);
                 BitIO.BitInputStream bis = new BitIO.BitInputStream(gis)) {

                int roadIdx = 0;

                while (bis.hasMore()) {
                    int nElements = (int) bis.readBits(4);
                    CompactElement[] elems = new CompactElement[nElements];

                    Set<Integer> keysInRoad = new HashSet<>();

                    for (int i = 0; i < nElements; i++) {
                        int layer  = (int) bis.readBits(4) + 1; 
                        int sector = (int) bis.readBits(5) + 1;
                        int strip  = (int) bis.readBits(11) + 1;
                        elems[i] = new CompactElement(sector, layer, strip);

                        int key = (sector << 8) | layer;
                        keysInRoad.add(key);
                    }

                    bis.alignToByte();
                    roads.add(new CompactRoad(elems));
                    // Add road index to all (sector, layer) keys it touches
                    for (int key : keysInRoad) {
                        index.computeIfAbsent(key, k -> new ArrayList<>()).add(roadIdx);
                    }
                    roadIdx++;
                }
            }

            paddleRoads.put(paddle, roads.toArray(new CompactRoad[0]));
            paddleIndex.put(paddle, index);

            System.out.printf("✅ Paddle %02d: %d roads, %d indexed keys%n",
                    paddle, roads.size(), index.size());
        }
    }

    public CompactRoad[] getRoadsForPaddle(int paddle) {
        return paddleRoads.getOrDefault(paddle, new CompactRoad[0]);
    }

    public Map<Integer, List<Integer>> getRoadIndexForPaddle(int paddle) {
        return paddleIndex.getOrDefault(paddle, Collections.emptyMap());
    }
}
