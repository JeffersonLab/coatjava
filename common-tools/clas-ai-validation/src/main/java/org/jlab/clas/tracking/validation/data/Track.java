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
 * Detector-independent representation of a reconstructed track.
 *
 * @author veronique
 */

public final class Track implements ValidationObject {

    private final int id;
    private final String algorithm;
    private final int seedId;

    /*
     * Used to construct ObjectKey. It identifies the tracking subsystem
     * or bank family, not necessarily every detector used by the track.
     */
    private final int keyDetector;

    /*
     * Complete detector coverage of the track. This is used by the
     * truth-efficiency calculation.
     */
    private final Set<Integer> detectorScope;

    private final List<Integer> crossIds;
    private final List<Integer> clusterIds;
    private final List<HitKey> hitKeys;

    private final double px;
    private final double py;
    private final double pz;

    private final double vx;
    private final double vy;
    private final double vz;

    private final double chi2;
    private final int ndf;

    private final int charge;
    private final int status;

    public Track(
            int id,
            String algorithm,
            int seedId,
            int keyDetector,
            Set<Integer> detectorScope,
            List<Integer> crossIds,
            List<Integer> clusterIds,
            List<HitKey> hitKeys,
            double px,
            double py,
            double pz,
            double vx,
            double vy,
            double vz,
            double chi2,
            int ndf,
            int charge,
            int status) {

        if (id < 0) {
            throw new IllegalArgumentException(
                    "Track ID must be non-negative: " + id);
        }

        this.id = id;
        this.algorithm = Objects.requireNonNull(
                algorithm,
                "algorithm must not be null");

        this.seedId = seedId;
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

        this.vx = vx;
        this.vy = vy;
        this.vz = vz;

        this.chi2 = chi2;
        this.ndf = ndf;

        this.charge = charge;
        this.status = status;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public TrackingObjectType getType() {
        return TrackingObjectType.TRACK;
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

    public int getSeedId() {
        return seedId;
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

    public double getPt() {
        return Math.sqrt(px * px + py * py);
    }

    public double getVx() {
        return vx;
    }

    public double getVy() {
        return vy;
    }

    public double getVz() {
        return vz;
    }

    public double getChi2() {
        return chi2;
    }

    public int getNdf() {
        return ndf;
    }

    public double getReducedChi2() {
        return ndf > 0 ? chi2 / ndf : Double.NaN;
    }

    public int getCharge() {
        return charge;
    }

    public int getStatus() {
        return status;
    }

    public ObjectKey key() {
        return new ObjectKey(
                algorithm,
                keyDetector,
                TrackingObjectType.TRACK,
                id);
    }

    @Override
    public String toString() {
        return String.format(
                "ValidationTrack[id=%d, algorithm=%s, seedId=%d, "
                        + "detectorScope=%s, crosses=%d, clusters=%d, "
                        + "hits=%d, p=(%.4f, %.4f, %.4f), "
                        + "v=(%.4f, %.4f, %.4f), chi2=%.4f, "
                        + "ndf=%d, charge=%d, status=%d]",
                id,
                algorithm,
                seedId,
                detectorScope,
                crossIds.size(),
                clusterIds.size(),
                hitKeys.size(),
                px,
                py,
                pz,
                vx,
                vy,
                vz,
                chi2,
                ndf,
                charge,
                status);
    }
}