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
 * Raw AI pattern-recognition suggestion before track fitting.
 *
 * For DC, the suggestion consists of the clusters referenced by c1 ... c6
 * in ai::tracks. Its hit list is the union of the hits in those clusters.
 * 
 * @author veronique
 */
public final class AiTrackSuggestion implements ValidationObject {

    private final int id;
    private final String algorithm;
    private final int detector;
    private final int sector;

    private final List<Integer> clusterIds;
    private final List<HitKey> hitKeys;
    private final Set<Integer> detectorScope;

    private final double probability;

    public AiTrackSuggestion(
            int id,
            String algorithm,
            int detector,
            int sector,
            List<Integer> clusterIds,
            List<HitKey> hitKeys,
            double probability) {

        this.id = id;
        this.algorithm = Objects.requireNonNull(
                algorithm,
                "algorithm must not be null");

        this.detector = detector;
        this.sector = sector;

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

        Set<Integer> scope = new LinkedHashSet<>();
        scope.add(detector);
        this.detectorScope =
                Collections.unmodifiableSet(scope);

        this.probability = probability;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public TrackingObjectType getType() {
        return TrackingObjectType.SUGGESTION;
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

    public int getDetector() {
        return detector;
    }

    public int getSector() {
        return sector;
    }

    public List<Integer> getClusterIds() {
        return clusterIds;
    }

    public double getProbability() {
        return probability;
    }

    public ObjectKey key() {
        return new ObjectKey(
                algorithm,
                detector,
                TrackingObjectType.SUGGESTION,
                id);
    }

    @Override
    public String toString() {
        return String.format(
                "%s suggestion %d sector=%d clusters=%d hits=%d prob=%.5f",
                algorithm,
                id,
                sector,
                clusterIds.size(),
                hitKeys.size(),
                probability);
    }
}