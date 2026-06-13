/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jlab.clas.tracking.validation.HitKey;
import org.jlab.clas.tracking.validation.TrackingObjectType;

/** 
 * Detector-independent representation of a reconstructed tracking segment. 
 * 
 * @author veronique
 */

public final class Segment implements ValidationObject {

    private final int id;
    private final String algorithm;
    private final int detector;
    private final int sector;
    private final int superlayer;
    private final int clusterId;
    private final int status;
    private final List<HitKey> hitKeys;
    private final double centroid;
    private final double time;

    public Segment(
            int id,
            String algorithm,
            int detector,
            int sector,
            int superlayer,
            int clusterId,
            int status,
            List<HitKey> hitKeys,
            double centroid,
            double time) {

        if (id < 0) {
            throw new IllegalArgumentException("Segment ID must be non-negative: " + id);
        }
        this.id = id;
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
        this.detector = detector;
        this.sector = sector;
        this.superlayer = superlayer;
        this.clusterId = clusterId;
        this.status = status;
        this.hitKeys = Collections.unmodifiableList(new ArrayList<>(
                hitKeys == null ? Collections.emptyList() : hitKeys));
        this.centroid = centroid;
        this.time = time;
    }

    @Override
    public int getId() { return id; }

    @Override
    public TrackingObjectType getType() { return TrackingObjectType.SEGMENT; }

    @Override
    public String getAlgorithm() { return algorithm; }

    @Override
    public List<HitKey> getHitKeys() { return hitKeys; }

    @Override
    public Set<Integer> getDetectorScope() { return Collections.singleton(detector); }

    public int getDetector() { return detector; }
    public int getSector() { return sector; }
    public int getSuperlayer() { return superlayer; }
    public int getClusterId() { return clusterId; }
    public int getStatus() { return status; }
    public double getCentroid() { return centroid; }
    public double getTime() { return time; }

    public ObjectKey key() {
        return new ObjectKey(algorithm, detector, TrackingObjectType.SEGMENT, id);
    }

    @Override
    public String toString() {
        return String.format(
                "Segment[id=%d, algorithm=%s, sector=%d, superlayer=%d, hits=%d]",
                id, algorithm, sector, superlayer, hitKeys.size());
    }
}
