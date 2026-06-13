/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation;

import java.util.Objects;

/**
 * Detector-neutral identity of one measured tracking hit.
 *
 * detectorId is DetectorType.getDetectorId(); hitId is the detector bank hit id.
 * The remaining coordinates make the key robust when hit ids are local to a bank.
 * 
 * @author veronique
 */

public final class HitKey implements Comparable<HitKey> {
    private final int detectorId;
    private final int sector;
    private final int layer;
    private final int component;
    private final int hitId;

    public HitKey(int detectorId, int sector, int layer, int component, int hitId) {
        this.detectorId = detectorId;
        this.sector = sector;
        this.layer = layer;
        this.component = component;
        this.hitId = hitId;
    }

    public int getDetectorId() { return detectorId; }
    public int getSector() { return sector; }
    public int getLayer() { return layer; }
    public int getComponent() { return component; }
    public int getHitId() { return hitId; }

    @Override
    public int compareTo(HitKey other) {
        int c = Integer.compare(detectorId, other.detectorId);
        if (c != 0) return c;
        c = Integer.compare(sector, other.sector);
        if (c != 0) return c;
        c = Integer.compare(layer, other.layer);
        if (c != 0) return c;
        c = Integer.compare(component, other.component);
        if (c != 0) return c;
        return Integer.compare(hitId, other.hitId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof HitKey)) return false;
        HitKey other = (HitKey) obj;
        return detectorId == other.detectorId
                && sector == other.sector
                && layer == other.layer
                && component == other.component
                && hitId == other.hitId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(detectorId, sector, layer, component, hitId);
    }

    @Override
    public String toString() {
        return detectorId + ":" + sector + ":" + layer + ":" + component + ":" + hitId;
    }
}
