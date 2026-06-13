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
 *
 * @author veronique
 */

public final class Cross implements ValidationObject {

    private final int id;
    private final String algorithm;

    private final int detector;
    private final int sector;
    private final int region;

    private final List<Integer> clusterIds;
    private final List<HitKey> hitKeys;

    private final double x;
    private final double y;
    private final double z;

    public Cross(
            int id,
            String algorithm,
            int detector,
            int sector,
            int region,
            List<Integer> clusterIds,
            List<HitKey> hitKeys,
            double x,
            double y,
            double z) {

        if (id < 0) {
            throw new IllegalArgumentException("Cross ID must be non-negative");
        }

        this.id = id;
        this.algorithm = Objects.requireNonNull(
                algorithm,
                "algorithm must not be null");

        this.detector = detector;
        this.sector = sector;
        this.region = region;

        this.clusterIds = Collections.unmodifiableList(
                new ArrayList<>(
                        clusterIds == null
                                ? Collections.emptyList()
                                : clusterIds));

        this.hitKeys = Collections.unmodifiableList(
                new ArrayList<>(
                        hitKeys == null
                                ? Collections.emptyList()
                                : hitKeys));

        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public TrackingObjectType getType() {
        return TrackingObjectType.CROSS;
    }

    @Override
    public String getAlgorithm() {
        return algorithm;
    }

    @Override
    public List<HitKey> getHitKeys() {
        return hitKeys;
    }

    @Override
    public Set<Integer> getDetectorScope() {
        return Collections.singleton(detector);
    }

    public int getDetector() {
        return detector;
    }

    public int getSector() {
        return sector;
    }

    public int getRegion() {
        return region;
    }

    public List<Integer> getClusterIds() {
        return clusterIds;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public ObjectKey key() {
        return new ObjectKey(
                algorithm,
                detector,
                TrackingObjectType.CROSS,
                id);
    }

    @Override
    public String toString() {
        return String.format(
                "ValidationCross[id=%d, algorithm=%s, detector=%d, "
                        + "sector=%d, region=%d, clusters=%d, hits=%d, "
                        + "position=(%.3f, %.3f, %.3f)]",
                id,
                algorithm,
                detector,
                sector,
                region,
                clusterIds.size(),
                hitKeys.size(),
                x,
                y,
                z);
    }
}