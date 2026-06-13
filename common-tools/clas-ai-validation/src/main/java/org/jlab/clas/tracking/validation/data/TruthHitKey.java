/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation.data;

import java.util.Objects;

/**
 * Unique key for a simulated detector hit.
 * 
 * @author veronique
 */

public final class TruthHitKey {

    private final int detector;
    private final int hitId;

    public TruthHitKey(
            int detector,
            int hitId) {

        this.detector = detector;
        this.hitId = hitId;
    }

    public int getDetector() {
        return detector;
    }

    public int getHitId() {
        return hitId;
    }

    @Override
    public boolean equals(Object object) {

        if (this == object) {
            return true;
        }

        if (!(object instanceof TruthHitKey)) {
            return false;
        }

        TruthHitKey other =
                (TruthHitKey) object;

        return detector == other.detector
                && hitId == other.hitId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                detector,
                hitId);
    }

    @Override
    public String toString() {
        return "TruthHitKey[detector="
                + detector
                + ", hitId="
                + hitId
                + "]";
    }
}