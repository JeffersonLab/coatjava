/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 *
 * @author veronique
/**
 * Compact (memory-efficient) RoadIndexManager.
 *
 * Index structure:
 *   compactIndex: Map<paddle, Map<key, long[]>>
 * where key = (phiBin<<16) | (sector<<8) | layer
 *
 * Each long[] is an array of packed entries; each packed entry is a single long:
 *   packed = ( ((long)offsetBytes) << 6 ) | (nElements & 0x3F)
 *
 * offsetBytes is the byte offset to the first element bits (i.e. bit position AFTER reading the 4-bit nElements).
 *
 * This class offers:
 *   - buildCompactUberIndex(dir)
 *   - save/load (optional)
 *   - findRoad(paddle, keys) -> RoadIndex.UberEntry (returns best matching road or null)
 *   - getRoad(paddleBytes, ue) -> CompactRoad
 */
public class RoadIndexManager {

    private static final int N_PADDLES = 48;

    // per paddle, per key -> packed entries (packed long = (offsetBytes<<6) | nElements)
    private final Map<Integer, Map<Long, long[]>> compactIndexByPaddle = new HashMap<>();

    // LRU cache of decoded roads keyed by (paddle<<48) | (offsetBytes<<16) | nElements
    private final LinkedHashMap<Long, CompactRoad> roadCache;

    // cache capacity (tune as needed)
    private final int CACHE_CAPACITY = 20000;
    
    // FINAL storage (after build)
    private final Map<Integer, Map<Long, int[]>> uberIndexByPaddle = new HashMap<>();

    // Flat array of all road entries
    private RoadIndex.UberEntry[] roadTable;

    // Number of roads stored
    private int roadCount = 0;


    public RoadIndexManager() {
        // small LRU cache
        this.roadCache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, CompactRoad> eldest) {
                return size() > CACHE_CAPACITY;
            }
        };
    }

    public void buildUberIndex(String dir) throws IOException {

        // TEMP storage: full lists before compaction
        Map<Integer, Map<Long, IntArray>> temp = new HashMap<>();
        List<RoadIndex.UberEntry> roads = new ArrayList<>(350_000);
        for (int paddle = 1; paddle <= N_PADDLES; paddle++) {

            Path path = Paths.get(String.format("%s/svt_roads_paddle_%02d.bin.gz", dir, paddle));
            if (!Files.exists(path)) continue;

            byte[] bytes;
            try (InputStream fis = Files.newInputStream(path);
                 GZIPInputStream gis = new GZIPInputStream(fis)) {
                bytes = gis.readAllBytes();
            }
            System.out.println("Bytes read for paddle "+paddle);
            BitIO.BitInputStream bis = new BitIO.BitInputStream(new ByteArrayInputStream(bytes));

            while (bis.hasMore()) {

                int nElements;

                try { nElements = (int) bis.readBits(4); }
                catch (EOFException e) { break; }

                long roadStart = bis.getBitPosition();

                Set<Long> keys = new HashSet<>(8);

                for (int i = 0; i < nElements; i++) {

                    int layer  = (int) bis.readBits(4) + 1;
                    int sector = (int) bis.readBits(5) + 1;
                    int strip  = (int) bis.readBits(11) + 1;

                    int phiBin = Util.getPhiBin(layer, sector, strip);
                    long key   = makeIndexKey(phiBin, sector, layer);

                    keys.add(key);
                }

                bis.alignToByte();

                int roadId = (int) roads.size();
                roads.add(new RoadIndex.UberEntry(paddle, roadStart, nElements));

                Map<Long, IntArray> pmap =
                        temp.computeIfAbsent(paddle, k -> new HashMap<>());

                for (long ek : keys)
                    pmap.computeIfAbsent(ek, k -> new IntArray()).add(roadId);
            }

            System.gc();   // ← CRITICAL (keeps build RAM < 1 GB)

        }

        //
        // 🔻 FINAL COMPACTION
        //
        roadTable = roads.toArray(new RoadIndex.UberEntry[0]);
        roadCount = roadTable.length;

        for (var ePaddle : temp.entrySet()) {
            Map<Long, int[]> compactMap = new HashMap<>();

            for (var eKey : ePaddle.getValue().entrySet())
                compactMap.put(eKey.getKey(), eKey.getValue().toArray());  // <-- packs ints

            uberIndexByPaddle.put(ePaddle.getKey(), compactMap);
        }

        System.out.printf("✅ UBER index built: %,d roads, %,d paddles%n",
                roadCount, uberIndexByPaddle.size());
    }
    
    static final class IntArray {
        int[] a = new int[8];
        int size = 0;
        void add(int v) {
            if (size == a.length)
                a = Arrays.copyOf(a, size << 1);   // grow 8→16→32…
            a[size++] = v;
        }
        int[] toArray() { return Arrays.copyOf(a, size); }
    }


public RoadIndex.UberEntry findRoad(int paddle, Set<Long> clusterKeys) {

    Map<Long, int[]> map = uberIndexByPaddle.get(paddle);
    if (map == null) return null;

    int[] score = new int[roadCount];

    for (long ck : clusterKeys) {
        int[] roads = map.get(ck);
        if (roads == null) continue;
        for (int r : roads) score[r]++;
    }

    int bestScore = 0;
    int bestId = -1;

    for (int i = 0; i < roadCount; i++) {
        if (score[i] > bestScore) {
            bestScore = score[i];
            bestId = i;
        }
    }

    return (bestScore >= 4 ? roadTable[bestId] : null);
}

    /** Decode / return a CompactRoad for a given UberEntry using paddleBytes (and caching) */
    public CompactRoad getRoad(Map<Integer, byte[]> paddleBytes, RoadIndex.UberEntry ue) throws IOException {
        int paddle = ue.paddle();
        long offsetBytes = ue.offsetBits() >>> 3;
        int nElements = ue.nElements();

        long cacheKey = (((long)paddle) << 48) | ((offsetBytes & 0xFFFFFFFFL) << 16) | (nElements & 0xFFFFL);
        synchronized (roadCache) {
            CompactRoad cached = roadCache.get(cacheKey);
            if (cached != null) return cached;
        }

        byte[] bytes = paddleBytes.get(paddle);
        if (bytes == null) return null;

        // create BitInputStream over the byte array
        BitIO.BitInputStream bis = new BitIO.BitInputStream(new ByteArrayInputStream(bytes));

        // skip to offsetBits (we use readBits in chunks)
        long bitsToSkip = ue.offsetBits();
        while (bitsToSkip > 0) {
            int take = (int) Math.min(bitsToSkip, 32);
            bis.readBits(take);
            bitsToSkip -= take;
        }

        // Now read nElements elements
        List<CompactElement> elems = new ArrayList<>(nElements);
        for (int i = 0; i < nElements; i++) {
            int layer = (int)bis.readBits(4) + 1;
            int sector = (int)bis.readBits(5) + 1;
            int strip = (int)bis.readBits(11) + 1;
            elems.add(new CompactElement(sector, layer, strip));
        }
        // no need to align here

        CompactRoad cr = new CompactRoad(elems.toArray(new CompactElement[0]));

        synchronized (roadCache) {
            roadCache.put(cacheKey, cr);
        }
        return cr;
    }

    @SuppressWarnings("unchecked")
    public void saveUberIndex(String dir) throws IOException {
        Path p = Paths.get(dir, "svt_roads_uber.idx");

        try (ObjectOutputStream oos =
                new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(p)))) {

            oos.writeInt(roadCount);
            oos.writeObject(roadTable);
            oos.writeObject(uberIndexByPaddle);
        }

        System.out.printf("💾 Saved UBER index: %,d roads in %s%n", roadCount, p);
    }

    @SuppressWarnings("unchecked")
    public void loadUberIndex(String dir) throws IOException, ClassNotFoundException {
        Path p = Paths.get(dir, "svt_roads_uber.idx");
        if (!Files.exists(p)) {
            System.out.println("⚠ No saved UBER index found.");
            return;
        }

        try (ObjectInputStream ois =
                 new ObjectInputStream(new BufferedInputStream(Files.newInputStream(p)))) {

            roadCount = ois.readInt();
            roadTable = (RoadIndex.UberEntry[]) ois.readObject();

            // Read into a temp var (we cannot reassign the final field)
            Map<Integer, Map<Long, int[]>> loaded =
                    (Map<Integer, Map<Long, int[]>>) ois.readObject();

            // Replace contents of the final map by mutating it
            uberIndexByPaddle.clear();
            for (var entry : loaded.entrySet()) {
                // make a new HashMap for the inner map so we don't keep a reference to 'loaded''s inner maps
                uberIndexByPaddle.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
        }

        System.out.printf("🔥 Loaded prebuilt UBER index: %,d roads, %,d paddles%n",
                roadCount, uberIndexByPaddle.size());
    }



    /** Make 32-bit-ish index key: (phiBin<<16) | (sector<<8) | layer */
    public static long makeIndexKey(int phiBin, int sector, int layer) {
        return (((long)phiBin & 0xFFFFL) << 16)
                | (((long)sector & 0xFFL) << 8)
                | (((long)layer & 0xFFL));
    }

    // -------------------------
    // Small internal helper map that groups packed longs & counts
    // to avoid using giant HashMaps while scoring. It's intentionally
    // compact and specific to usage.
    private static class IntLongMap {
        final Map<Integer, List<Long>> map = new HashMap<>();
        final Map<Integer, Integer> counts = new HashMap<>();
        void recordPacked(int id, long packed) {
            map.computeIfAbsent(id, k -> new ArrayList<>()).add(packed);
        }
        void increment(int id) { counts.put(id, counts.getOrDefault(id, 0) + 1); }
    }
}
