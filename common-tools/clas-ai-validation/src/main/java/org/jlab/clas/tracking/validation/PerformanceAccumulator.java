/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt
 * to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java
 * to edit this template
 */
package org.jlab.clas.tracking.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight accumulator for comparing algorithms and reconstruction stages.
 *
 * Purity and efficiency are accumulated from their underlying hit counts:
 *
 * purity =
 *     matched reconstructed hits
 *     --------------------------
 *     reconstructed hits on matched objects
 *
 * efficiency =
 *     matched reconstructed hits
 *     --------------------------
 *     expected truth hits
 *
 * Mean hits is calculated over every reconstructed object:
 *
 * mean hits =
 *     all reconstructed object hits
 *     -----------------------------
 *     number of reconstructed objects
 *
 * @author veronique
 */
public final class PerformanceAccumulator {

    private final Map<String, MutableSummary> summaries =
            new LinkedHashMap<>();

    public void add(
            String binLabel,
            MatchResult result) {

        if (binLabel == null
                || result == null) {
            return;
        }

        summaries
                .computeIfAbsent(
                        binLabel,
                        key -> new MutableSummary())
                .add(result);
    }

    public Map<String, Summary> snapshot() {

        Map<String, Summary> output =
                new LinkedHashMap<>();

        for (Map.Entry<String, MutableSummary> entry
                : summaries.entrySet()) {

            output.put(
                    entry.getKey(),
                    entry.getValue().snapshot());
        }

        return Collections.unmodifiableMap(output);
    }

    public void clear() {
        summaries.clear();
    }

    private static final class MutableSummary {

        /*
         * Number of reconstructed objects in this stage.
         */
        private long objectCount;

        /*
         * Number of objects having a valid dominant MC-track match.
         */
        private long matchedObjectCount;

        /*
         * Sum of reconstructed hits on all objects.
         *
         * This is used only for mean hits/object.
         */
        private long totalObjectHits;

        /*
         * Sum of reconstructed hits on matched objects.
         *
         * This is the purity denominator.
         */
        private long totalObjectHitsOnMatchedObjects;

        /*
         * Sum of hits on matched objects belonging to their selected
         * dominant MC tracks.
         *
         * This is the numerator for both purity and efficiency.
         */
        private long totalMatchedHits;

        /*
         * Sum of expected MC-associated hits in the corresponding object
         * scopes.
         *
         * For a DC cluster, this means the matched MC track's hits in the
         * same sector and superlayer.
         */
        private long totalTruthHits;

        void add(
                MatchResult result) {

            objectCount++;

            DetectorMetrics metrics =
                    result.getCombined();

            if (metrics == null) {
                return;
            }

            int objectHits =
                    metrics.getObjectHits();

            /*
             * Every reconstructed object contributes to mean hits/object,
             * whether or not it has a valid truth match.
             */
            totalObjectHits +=
                    objectHits;

            if (!result.isMatched()) {
                return;
            }

            matchedObjectCount++;

            /*
             * Only matched objects enter purity and efficiency.
             */
            totalObjectHitsOnMatchedObjects +=
                    objectHits;

            totalMatchedHits +=
                    metrics.getMatchedHits();

            totalTruthHits +=
                    metrics.getTruthHits();
        }

        Summary snapshot() {

            return new Summary(
                    objectCount,
                    matchedObjectCount,
                    totalObjectHits,
                    totalObjectHitsOnMatchedObjects,
                    totalMatchedHits,
                    totalTruthHits);
        }
    }

    public static final class Summary {

        private final long count;
        private final long matchedCount;

        private final long totalObjectHits;
        private final long totalObjectHitsOnMatchedObjects;
        private final long totalMatchedHits;
        private final long totalTruthHits;

        private Summary(
                long count,
                long matchedCount,
                long totalObjectHits,
                long totalObjectHitsOnMatchedObjects,
                long totalMatchedHits,
                long totalTruthHits) {

            this.count = count;
            this.matchedCount = matchedCount;
            this.totalObjectHits = totalObjectHits;
            this.totalObjectHitsOnMatchedObjects =
                    totalObjectHitsOnMatchedObjects;
            this.totalMatchedHits = totalMatchedHits;
            this.totalTruthHits = totalTruthHits;
        }

        public long getCount() {
            return count;
        }

        public long getMatchedCount() {
            return matchedCount;
        }

        public double getMatchedFraction() {

            return count == 0
                    ? 0.0
                    : (double) matchedCount / count;
        }

        /**
         * Hit-weighted purity over truth-matched objects.
         */
        public double getMeanPurity() {

            return totalObjectHitsOnMatchedObjects == 0
                    ? 0.0
                    : (double) totalMatchedHits
                    / totalObjectHitsOnMatchedObjects;
        }

        /**
         * Hit-weighted efficiency over truth-matched objects.
         */
        public double getMeanEfficiency() {

            return totalTruthHits == 0
                    ? 0.0
                    : (double) totalMatchedHits
                    / totalTruthHits;
        }

        public double getMeanFakeFraction() {

            return totalObjectHitsOnMatchedObjects == 0
                    ? 0.0
                    : (double) (
                            totalObjectHitsOnMatchedObjects
                            - totalMatchedHits)
                    / totalObjectHitsOnMatchedObjects;
        }

        /**
         * Average reconstructed hit count over every object in the stage.
         */
        public double getMeanHits() {

            return count == 0
                    ? 0.0
                    : (double) totalObjectHits / count;
        }

        public long getTotalObjectHits() {
            return totalObjectHits;
        }

        public long getTotalObjectHitsOnMatchedObjects() {
            return totalObjectHitsOnMatchedObjects;
        }

        public long getTotalMatchedHits() {
            return totalMatchedHits;
        }

        public long getTotalTruthHits() {
            return totalTruthHits;
        }
    }
}