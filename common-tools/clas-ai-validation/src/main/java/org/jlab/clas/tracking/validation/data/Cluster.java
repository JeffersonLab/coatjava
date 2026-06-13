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
 * Detector-independent representation of a reconstructed cluster.
 *
 * The object is filled by a detector-specific bank reader, such as
 * CvtBankReader or DcBankReader.
 *
 * @author veronique
 */
public final class Cluster implements ValidationObject {

    private final int id;
    private final String algorithm;

    private final int detector;
    private final int sector;
    private final int layer;

    private final List<HitKey> hitKeys;

    private final double centroid;
    private final double energy;
    private final double time;

    public Cluster(
            int id,
            String algorithm,
            int detector,
            int sector,
            int layer,
            List<HitKey> hitKeys,
            double centroid,
            double energy,
            double time) {

        if (id < 0) {
            throw new IllegalArgumentException(
                    "Cluster ID must be non-negative: " + id);
        }

        this.id = id;
        this.algorithm = Objects.requireNonNull(
                algorithm,
                "algorithm must not be null");

        this.detector = detector;
        this.sector = sector;
        this.layer = layer;

        this.hitKeys = Collections.unmodifiableList(
                new ArrayList<>(
                        hitKeys == null
                                ? Collections.emptyList()
                                : hitKeys));

        this.centroid = centroid;
        this.energy = energy;
        this.time = time;
        
        this.aiSuggested = false;
        this.aiScore = Double.NaN;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public TrackingObjectType getType() {
        return TrackingObjectType.CLUSTER;
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

    public int getLayer() {
        return layer;
    }

    public double getCentroid() {
        return centroid;
    }

    public double getEnergy() {
        return energy;
    }

    public double getTime() {
        return time;
    }

    public ObjectKey key() {
        return new ObjectKey(
                algorithm,
                detector,
                TrackingObjectType.CLUSTER,
                id);
    }

    /* AI scoring */
    private boolean aiSuggested;
    private double aiScore;
    
    public boolean isAiSuggested() {
        return aiSuggested;
    }

    public double getAiScore() {
        return aiScore;
    }

    /**
     * Marks this cluster as being included in at least one AI track suggestion.
     *
     * If the cluster appears in several AI candidates, the largest candidate
     * probability is retained.
     *
     * @param probability probability associated with the AI candidate
     */
    public void markAiSuggested(double probability) {

        aiSuggested = true;

        if (!Double.isFinite(probability)) {
            return;
        }

        if (!Double.isFinite(aiScore)) {
            aiScore = probability;
        } else {
            aiScore = Math.max(aiScore, probability);
        }
    }
    
    @Override
    public String toString() {
        return String.format(
                "Cluster[id=%d, algorithm=%s, detector=%d, "
                        + "sector=%d, layer=%d, hits=%d, centroid=%.4f, "
                        + "energy=%.4f, time=%.4f, aiSuggested=%s, aiScore=%.4f]",
                id,
                algorithm,
                detector,
                sector,
                layer,
                hitKeys.size(),
                centroid,
                energy,
                time,
                aiSuggested,
                aiScore);
    }
    
}
