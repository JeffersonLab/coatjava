/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation.data;

import org.jlab.clas.tracking.validation.HitKey;

/**
 *
 * @author veronique
 */

public final class Hit {

    public static final int NO_TRUTH = -1;

    private final int id;
    private final int detector;
    private final int sector;
    private final int layer;
    private final int component;

    private final int clusterId;
    private final int trackId;

    private final int truthTrackId;
    private final int truthHitId;

    private final double x;
    private final double y;
    private final double z;

    private boolean aiSelected;
    /**
     * aiScore:
     * CVT:
     * score assigned directly to a hit by the CVT AI
     * DC:
     * score of the best AI track candidate containing the hit's cluster  */ 
    private double aiScore; 

    public Hit(
            int id,
            int detector,
            int sector,
            int layer,
            int component,
            int clusterId,
            int trackId,
            int truthTrackId,
            int truthHitId,
            double x,
            double y,
            double z,
            boolean aiSelected,
            double aiScore) {

        this.id = id;
        this.detector = detector;
        this.sector = sector;
        this.layer = layer;
        this.component = component;
        this.clusterId = clusterId;
        this.trackId = trackId;
        this.truthTrackId = truthTrackId;
        this.truthHitId = truthHitId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.aiSelected = aiSelected;
        this.aiScore = aiScore;
    }

    public HitKey key() {
        return new HitKey(detector, sector, layer, component, id);
    }

    public int getId() {
        return id;
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

    public int getComponent() {
        return component;
    }

    public int getClusterId() {
        return clusterId;
    }

    public int getTrackId() {
        return trackId;
    }

    public int getTruthTrackId() {
        return truthTrackId;
    }

    public int getTruthHitId() {
        return truthHitId;
    }

    public boolean isAiSelected() {
        return aiSelected;
    }

    public double getAiScore() {
        return aiScore;
    }

    /**
    * Marks this hit as selected by an AI algorithm.
    *
    * When several AI candidates contain the same hit, retain the largest
    * finite candidate score.
    */
    public void markAiSelected(
           double score) {

        aiSelected = true;

        if (!Double.isFinite(score)) {
            return;
        }

        if (!Double.isFinite(aiScore)
                || score > aiScore) {

            aiScore = score;
        }
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

    public double getR() {
        return Math.hypot(x, y);
    }
    
}
