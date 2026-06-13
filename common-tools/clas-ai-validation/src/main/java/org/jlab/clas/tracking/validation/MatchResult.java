/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlab.clas.tracking.validation.data.ValidationObject;

/**
 * Immutable truth-match result for one reconstructed object.
 * 
 * @author veronique
 */

public final class MatchResult {

    private final ValidationObject object;
    private final int truthTrackId;

    private final DetectorMetrics combined;

    private final Map<Integer, DetectorMetrics>
            byDetector;

    public MatchResult(
            ValidationObject object,
            int truthTrackId,
            DetectorMetrics combined,
            Map<Integer, DetectorMetrics> byDetector) {

        if (object == null) {
            throw new IllegalArgumentException(
                    "object must not be null");
        }

        if (combined == null) {
            throw new IllegalArgumentException(
                    "combined metrics must not be null");
        }

        this.object = object;
        this.truthTrackId = truthTrackId;
        this.combined = combined;

        this.byDetector =
                Collections.unmodifiableMap(
                        new LinkedHashMap<>(
                                byDetector));
    }

    public ValidationObject getObject() {
        return object;
    }

    public int getTruthTrackId() {
        return truthTrackId;
    }

    public boolean isMatched() {
        return truthTrackId > 0;
    }

    public DetectorMetrics getCombined() {
        return combined;
    }

    public Map<Integer, DetectorMetrics>
            getByDetector() {

        return byDetector;
    }
}
