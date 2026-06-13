/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jlab.clas.tracking.validation.HitKey;
import org.jlab.clas.tracking.validation.TrackingObjectType;

/**
 * Detector-independent representation of a reconstructed seed.
 *
 * @author veronique
 */
public final class Seed implements ValidationObject {

    private final int id;
    private final String algorithm;

    /*
     * Used only as part of ObjectKey. This identifies the tracking
     * subsystem or bank family associated with the seed.
     */
    private final int keyDetector;

    /*
     * Detectors covered by this seed. This determines which truth hits
     * enter the efficiency denominator.
     */
    private final Set<Integer> detectorScope;

    private final List<Integer> crossIds;
    private final List<Integer> clusterIds;
    private final List<HitKey> hitKeys;

    private final double px;
    private final double py;
    private final double pz;

    private final int charge;

    public Seed(
            int id,
            String algorithm,
            int keyDetector,
            Set<Integer> detectorScope,
            List<Integer> crossIds,
            List<Integer> clusterIds,
            List<HitKey> hitKeys,
            double px,
            double py,
            double pz,
            int charge) {

        if (id < 0) {
            throw new IllegalArgumentException(
                    "Seed ID must be non-negative: " + id);
        }

        this.id = id;
        this.algorithm = Objects.requireNonNull(
                algorithm,
                "algorithm must not be null");

        this.keyDetector = keyDetector;

        this.detectorScope = Collections.unmodifiableSet(
                new LinkedHashSet<>(
                        detectorScope == null
                                ? Collections.emptySet()
                                : detectorScope));

        this.crossIds = Collections.unmodifiableList(
                new ArrayList<>(
                        crossIds == null
                                ? Collections.emptyList()
                                : crossIds));

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

        this.px = px;
        this.py = py;
        this.pz = pz;
        this.charge = charge;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public TrackingObjectType getType() {
        return TrackingObjectType.SEED;
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
        return detectorScope;
    }

    public int getKeyDetector() {
        return keyDetector;
    }

    public List<Integer> getCrossIds() {
        return crossIds;
    }

    public List<Integer> getClusterIds() {
        return clusterIds;
    }

    public double getPx() {
        return px;
    }

    public double getPy() {
        return py;
    }

    public double getPz() {
        return pz;
    }

    public double getMomentum() {
        return Math.sqrt(px * px + py * py + pz * pz);
    }

    public int getCharge() {
        return charge;
    }

    public ObjectKey key() {
        return new ObjectKey(
                algorithm,
                keyDetector,
                TrackingObjectType.SEED,
                id);
    }

    @Override
    public String toString() {
        return String.format(
                "ValidationSeed[id=%d, algorithm=%s, detectorScope=%s, "
                        + "crosses=%d, clusters=%d, hits=%d, "
                        + "p=(%.4f, %.4f, %.4f), charge=%d]",
                id,
                algorithm,
                detectorScope,
                crossIds.size(),
                clusterIds.size(),
                hitKeys.size(),
                px,
                py,
                pz,
                charge);
    }
}