/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlab.clas.tracking.validation.data.Hit;
import org.jlab.clas.tracking.validation.data.ValidationObject;
import org.jlab.detector.base.DetectorType;

/**
 * Detector-independent majority truth matcher and purity/efficiency
 * calculator.
 *
 * Purity:
 *
 *   matched truth hits on reconstructed object
 *   ------------------------------------------
 *   all reconstructed hits on object
 *
 * Efficiency:
 *
 *   matched truth hits on reconstructed object
 *   ------------------------------------------
 *   expected truth hits in the object's scope
 *
 * @author veronique
 */
public final class TruthMatcher {

    private static final int NO_TRUTH = -1;

    private record SectorSuperlayerKey(
            int sector,
            int superlayer) {
    }

    private TruthMatcher() {
    }

    public static MatchResult evaluate(
            ValidationObject object,
            TruthIndex truth) {

        if (object == null) {
            throw new IllegalArgumentException(
                    "object must not be null");
        }

        if (truth == null) {
            throw new IllegalArgumentException(
                    "truth must not be null");
        }

        int truthTrackId =
                dominantTruthTrack(
                        object,
                        truth);

        Set<Integer> detectorIds =
                findDetectorIds(
                        object,
                        truth,
                        truthTrackId);

        List<Integer> sortedDetectors =
                new ArrayList<>(detectorIds);

        Collections.sort(sortedDetectors);

        Map<Integer, DetectorMetrics> perDetector =
                new LinkedHashMap<>();

        int totalObjectHits = 0;
        int totalMatchedHits = 0;
        int totalTruthHits = 0;

        for (int detectorId : sortedDetectors) {

            DetectorMetrics metrics =
                    detectorMetrics(
                            object,
                            truth,
                            truthTrackId,
                            detectorId);

            perDetector.put(
                    detectorId,
                    metrics);

            totalObjectHits +=
                    metrics.getObjectHits();

            totalMatchedHits +=
                    metrics.getMatchedHits();

            totalTruthHits +=
                    metrics.getTruthHits();
        }

        DetectorMetrics combined =
                new DetectorMetrics(
                        totalObjectHits,
                        totalMatchedHits,
                        totalTruthHits);

        return new MatchResult(
                object,
                truthTrackId,
                combined,
                perDetector);
    }

    /**
     * Selects the MC track contributing the largest number of unique hits to
     * the reconstructed object.
     */
    public static int dominantTruthTrack(
            ValidationObject object,
            TruthIndex truth) {

        Map<Integer, Integer> counts =
                new HashMap<>();

        Set<HitKey> uniqueKeys =
                object.getHitKeys() == null
                        ? Collections.emptySet()
                        : new HashSet<>(
                                object.getHitKeys());

        for (HitKey key : uniqueKeys) {

            if (key == null) {
                continue;
            }

            Hit hit =
                    truth.get(key);

            if (hit == null
                    || hit.getTruthTrackId() <= 0) {
                continue;
            }

            counts.merge(
                    hit.getTruthTrackId(),
                    1,
                    Integer::sum);
        }

        return counts.entrySet()
                .stream()
                .max(
                        Comparator
                                .<Map.Entry<Integer, Integer>>
                                comparingInt(
                                        Map.Entry::getValue)
                                .thenComparing(
                                        entry ->
                                                -entry.getKey()))
                .map(Map.Entry::getKey)
                .orElse(NO_TRUTH);
    }

    private static Set<Integer> findDetectorIds(
            ValidationObject object,
            TruthIndex truth,
            int truthTrackId) {

        Set<Integer> detectorIds =
                new LinkedHashSet<>();

        if (object.getDetectorScope() != null) {
            detectorIds.addAll(
                    object.getDetectorScope());
        }

        if (object.getHitKeys() != null) {

            for (HitKey key : object.getHitKeys()) {

                if (key != null) {
                    detectorIds.add(
                            key.getDetectorId());
                }
            }
        }

        if (detectorIds.isEmpty()
                && truthTrackId > 0) {

            for (HitKey key :
                    truth.getTruthHits(
                            truthTrackId)) {

                detectorIds.add(
                        key.getDetectorId());
            }
        }

        return detectorIds;
    }

    private static DetectorMetrics detectorMetrics(
            ValidationObject object,
            TruthIndex truth,
            int truthTrackId,
            int detectorId) {

        Set<HitKey> objectHitKeys =
                uniqueDetectorHits(
                        object,
                        detectorId);

        int objectHits =
                objectHitKeys.size();

        int matchedHits = 0;

        for (HitKey key : objectHitKeys) {

            Hit hit =
                    truth.get(key);

            if (hit != null
                    && truthTrackId > 0
                    && hit.getTruthTrackId()
                    == truthTrackId) {

                matchedHits++;
            }
        }

        Set<HitKey> expectedTruthHits =
                expectedTruthHits(
                        object,
                        truth,
                        truthTrackId,
                        detectorId,
                        objectHitKeys);

        return new DetectorMetrics(
                objectHits,
                matchedHits,
                expectedTruthHits.size());
    }

    private static Set<HitKey> uniqueDetectorHits(
            ValidationObject object,
            int detectorId) {

        Set<HitKey> result =
                new LinkedHashSet<>();

        if (object.getHitKeys() == null) {
            return result;
        }

        for (HitKey key : object.getHitKeys()) {

            if (key != null
                    && key.getDetectorId()
                    == detectorId) {

                result.add(key);
            }
        }

        return result;
    }

    /**
     * Returns the truth hits that form the efficiency denominator.
     *
     * Complete tracking hypotheses use all truth hits belonging to the
     * matched MC track in the detector.
     *
     * Intermediate objects use the exact detector/sector/layer cells occupied
     * by that reconstructed object.
     */
    private static Set<HitKey> expectedTruthHits(
            ValidationObject object,
            TruthIndex truth,
            int truthTrackId,
            int detectorId,
            Set<HitKey> objectHitKeys) {

        if (truthTrackId <= 0) {
            return Collections.emptySet();
        }

        if (usesFullDetectorScope(object)) {

            Set<HitKey> result =
                    new LinkedHashSet<>();

            for (HitKey key :
                    truth.getTruthHits(
                            truthTrackId)) {

                if (key.getDetectorId()
                        == detectorId) {

                    result.add(key);
                }
            }

            return result;
        }

        /*
         * A reconstructed DC cluster is local to one sector and one
         * superlayer. HitKey stores the global DC layer (1...36), so expand
         * every represented DC layer to the complete six-layer superlayer.
         *
         * This gives the requested cluster efficiency denominator:
         * all MC-associated hits from the matched particle in the same
         * sector and superlayer, including layers missed by reconstruction.
         */
        if (object.getType() == TrackingObjectType.CLUSTER
                && detectorId == DetectorType.DC.getDetectorId()) {

            Set<SectorSuperlayerKey> scopes =
                    new LinkedHashSet<>();

            for (HitKey key : objectHitKeys) {

                int superlayer =
                        (key.getLayer() - 1) / 6 + 1;

                scopes.add(
                        new SectorSuperlayerKey(
                                key.getSector(),
                                superlayer));
            }

            Set<HitKey> result =
                    new LinkedHashSet<>();

            for (SectorSuperlayerKey scope : scopes) {

                int firstGlobalLayer =
                        (scope.superlayer() - 1) * 6 + 1;

                int lastGlobalLayer =
                        firstGlobalLayer + 5;

                for (int globalLayer = firstGlobalLayer;
                        globalLayer <= lastGlobalLayer;
                        globalLayer++) {

                    result.addAll(
                            truth.getTruthHitsSectorLayer(
                                    truthTrackId,
                                    detectorId,
                                    scope.sector(),
                                    globalLayer));
                }
            }

            return result;
        }

        /*
         * Other local objects retain the exact detector/sector/layer cells
         * represented by their reconstructed hits.
         */
        Set<TruthIndex.SectorLayerKey> scopes =
                new LinkedHashSet<>();

        for (HitKey key : objectHitKeys) {

            scopes.add(
                    new TruthIndex.SectorLayerKey(
                            detectorId,
                            key.getSector(),
                            key.getLayer()));
        }

        Set<HitKey> result =
                new LinkedHashSet<>();

        for (TruthIndex.SectorLayerKey scope : scopes) {

            result.addAll(
                    truth.getTruthHitsSectorLayer(
                            truthTrackId,
                            scope.detectorId(),
                            scope.sector(),
                            scope.layer()));
        }

        return result;
    }

    /**
     * Suggestions, seeds, and tracks are complete tracking hypotheses.
     * Clusters, segments, and crosses are evaluated within their local
     * sector/layer scope.
     */
    private static boolean usesFullDetectorScope(
            ValidationObject object) {

        switch (object.getType()) {

            case SUGGESTION:
            case SEED:
            case TRACK:
                return true;

            case CLUSTER:
            case SEGMENT:
            case CROSS:
            default:
                return false;
        }
    }
}