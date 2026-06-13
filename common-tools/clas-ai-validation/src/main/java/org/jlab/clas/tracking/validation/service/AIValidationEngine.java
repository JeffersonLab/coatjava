/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.tracking.validation.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.tracking.validation.DetectorMetrics;
import org.jlab.clas.tracking.validation.MatchResult;
import org.jlab.clas.tracking.validation.PerformanceAccumulator;
import org.jlab.clas.tracking.validation.TruthIndex;
import org.jlab.clas.tracking.validation.TruthMatcher;
import org.jlab.clas.tracking.validation.data.ValidationEvent;
import org.jlab.clas.tracking.validation.data.ValidationObject;
import org.jlab.clas.tracking.validation.io.ValidationEventReader;
import org.jlab.clas.tracking.validation.io.detector.DcAiBankReader;
import org.jlab.clas.tracking.validation.io.detector.DcBankReader;
import org.jlab.clas.tracking.validation.io.mc.McTruthBankReader;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;

/** 
 * COATJAVA service for matching reconstructed tracking objects to MC truth.
 * 
 * @author veronique
 */

public final class AIValidationEngine extends ReconstructionEngine {

    private static final Logger LOGGER =
            Logger.getLogger(AIValidationEngine.class.getName());

    private final PerformanceAccumulator performanceAccumulator =
            new PerformanceAccumulator();

    private ValidationEventReader reader;
    private long processedEvents;
    private long failedEvents;
    private List<MatchResult> currentEventResults = Collections.emptyList();
    private ValidationEvent currentValidationEvent;

    public AIValidationEngine() {
        super("AIValidation", "veronique", "1.0");
    }

    @Override
    public boolean init() {
        reader = new ValidationEventReader();
        reader.addReader(new McTruthBankReader());
        reader.addReader(new DcBankReader());
        reader.addReader(new DcAiBankReader());
        processedEvents = 0L;
        failedEvents = 0L;
        return true;
    }

    @Override
    public boolean processDataEventUser(DataEvent dataEvent) {
        if (dataEvent == null || reader == null) {
            failedEvents++;
            return false;
        }

        try {
            ValidationEvent validationEvent = reader.readEvent(dataEvent);
            processValidationEvent(validationEvent);
            processedEvents++;
            return true;
        } catch (RuntimeException exception) {
            failedEvents++;
            LOGGER.log(Level.SEVERE, "Failed to validate tracking event", exception);
            return false;
        }
    }

    private void processValidationEvent(ValidationEvent validationEvent) {
        if (validationEvent == null) {
            throw new IllegalArgumentException("validationEvent must not be null");
        }

        TruthIndex truthIndex = new TruthIndex(validationEvent.getHits());
        List<MatchResult> eventResults = new ArrayList<>();

        evaluateObjects(validationEvent.getClusters(), truthIndex, eventResults);
        evaluateObjects(validationEvent.getSuggestions(), truthIndex, eventResults);
        evaluateObjects(validationEvent.getSegments(), truthIndex, eventResults);
        evaluateObjects(validationEvent.getCrosses(), truthIndex, eventResults);
        evaluateObjects(validationEvent.getSeeds(), truthIndex, eventResults);
        evaluateObjects(validationEvent.getTracks(), truthIndex, eventResults);

        currentValidationEvent = validationEvent;
        currentEventResults = Collections.unmodifiableList(
                new ArrayList<>(eventResults));
    }

    private void evaluateObjects(
            Collection<? extends ValidationObject> objects,
            TruthIndex truthIndex,
            List<MatchResult> eventResults) {

        if (objects == null) {
            return;
        }

        for (ValidationObject object : objects) {
            if (object == null) {
                continue;
            }

            MatchResult result = TruthMatcher.evaluate(object, truthIndex);
            eventResults.add(result);
            performanceAccumulator.add(makeStageLabel(object), result);
            performanceAccumulator.add("ALL/" + object.getType().name(), result);
        }
    }

    private static String makeStageLabel(ValidationObject object) {
        String algorithm = object.getAlgorithm();
        if (algorithm == null || algorithm.isBlank()) {
            algorithm = "UNKNOWN";
        }
        return algorithm.trim().toUpperCase(Locale.ROOT)
                + "/" + object.getType().name();
    }

    @Override
    public void detectorChanged(int runNumber) {
        // No run-dependent constants are required by the validation service.
    }

    public long getProcessedEvents() { return processedEvents; }
    public long getFailedEvents() { return failedEvents; }
    public ValidationEvent getCurrentValidationEvent() { return currentValidationEvent; }
    public List<MatchResult> getCurrentEventResults() { return currentEventResults; }
    public PerformanceAccumulator getPerformanceAccumulator() { return performanceAccumulator; }
    
    private static void printValidationResults(
            ValidationEvent validationEvent,
            List<MatchResult> results) {

        System.out.println();
        System.out.printf(
                "========== VALIDATION: run %d event %d ==========%n",
                validationEvent.getRun(),
                validationEvent.getEvent());


        Map<String, StageStatistics> statistics =
                new LinkedHashMap<>();

        for (MatchResult result : results) {

            ValidationObject object =
                    result.getObject();

            DetectorMetrics metrics =
                    result.getCombined();

            String label =
                    object.getAlgorithm()
                    + "/"
                    + object.getType().name();

            statistics
                    .computeIfAbsent(
                            label,
                            key -> new StageStatistics())
                    .add(result);

            /*
             * Print detailed information only for the objects that represent
             * complete tracking hypotheses.
             */
            String type =
                    object.getType().name();

            if ("SUGGESTION".equals(type)
                    || "SEED".equals(type)
                    || "TRACK".equals(type)) {

                printObjectResult(
                        result);
            }
        }

        System.out.println();
        System.out.println("Stage summary:");
        System.out.printf(
                "%-28s %7s %7s %10s %10s %10s%n",
                "stage",
                "objects",
                "matched",
                "purity",
                "efficiency",
                "mean hits");

        for (Map.Entry<String, StageStatistics> entry
                : statistics.entrySet()) {

            StageStatistics value =
                    entry.getValue();

            System.out.printf(
                    "%-28s %7d %7d %10.4f %10.4f %10.2f%n",
                    entry.getKey(),
                    value.objectCount,
                    value.matchedObjectCount,
                    value.getCombinedPurity(),
                    value.getCombinedEfficiency(),
                    value.getMeanObjectHits());
        }

        System.out.println(
                "=================================================");
    }
    
    private static void printObjectResult(
            MatchResult result) {

        ValidationObject object =
                result.getObject();

        DetectorMetrics combined =
                result.getCombined();

        System.out.printf(
                "%n%s %s id=%d%n",
                object.getAlgorithm(),
                object.getType(),
                object.getId());

        System.out.printf(
                "  matched MC track = %d%n",
                result.getTruthTrackId());

        System.out.printf(
                "  object hits      = %d%n",
                combined.getObjectHits());

        System.out.printf(
                "  matched hits     = %d%n",
                combined.getMatchedHits());

        System.out.printf(
                "  truth hits       = %d%n",
                combined.getTruthHits());

        System.out.printf(
                "  purity           = %.4f%n",
                combined.getPurity());

        System.out.printf(
                "  efficiency       = %.4f%n",
                combined.getEfficiency());

        if (!result.getByDetector().isEmpty()) {

            System.out.println(
                    "  by detector:");

            result.getByDetector().forEach(
                    (detectorId, metrics) ->
                            System.out.printf(
                                    "    detector=%d "
                                    + "object=%d matched=%d truth=%d "
                                    + "purity=%.4f efficiency=%.4f%n",
                                    detectorId,
                                    metrics.getObjectHits(),
                                    metrics.getMatchedHits(),
                                    metrics.getTruthHits(),
                                    metrics.getPurity(),
                                    metrics.getEfficiency()));
        }
    }
    
    private static final class StageStatistics {

        private int objectCount;
        private int matchedObjectCount;

        /*
         * Purity, efficiency, and mean hit count are defined for objects that
         * have a selected MC truth track. Keep all three quantities on the
         * same matched-object population.
         */
        private int matchedObjectHits;
        private int matchedHits;
        private int truthHits;

        void add(
                MatchResult result) {

            objectCount++;

            if (!result.isMatched()) {
                return;
            }

            matchedObjectCount++;

            DetectorMetrics metrics =
                    result.getCombined();

            matchedObjectHits +=
                    metrics.getObjectHits();

            matchedHits +=
                    metrics.getMatchedHits();

            truthHits +=
                    metrics.getTruthHits();
        }

        double getCombinedPurity() {

            return matchedObjectHits > 0
                    ? (double) matchedHits / matchedObjectHits
                    : 0.0;
        }

        double getCombinedEfficiency() {

            return truthHits > 0
                    ? (double) matchedHits / truthHits
                    : 0.0;
        }

        double getMeanObjectHits() {

            return matchedObjectCount > 0
                    ? (double) matchedObjectHits / matchedObjectCount
                    : 0.0;
        }
    }
    
    public static void main(String[] args) {

        String clas12Dir =
                "/Users/veronique/BASE/Code/coatjava";

        String inputFile =
                "/Users/veronique/BASE/Code/CLAS12/AIValidation/"
                + "Run26_F18In_50nA_clasdis-10731-111-rec.hipo";

        System.setProperty(
                "CLAS12DIR",
                clas12Dir);

        AIValidationEngine engine =
                new AIValidationEngine();

        if (!engine.init()) {
            throw new IllegalStateException(
                    "AIValidationEngine initialization failed");
        }

        HipoDataSource source =
                new HipoDataSource();

        int counter = 0;
        int maximumEvents = 10;

        System.out.println();
        System.out.println(
                "[CLAS12DIR]      " + clas12Dir);

        System.out.println(
                "[PROCESSING FILE] " + inputFile);

        source.open(inputFile);

        try {

            while (source.hasEvent()
                    && counter < maximumEvents) {

                DataEvent event =
                        source.getNextEvent();

                counter++;

                boolean success =
                        engine.processDataEventUser(event);

                System.out.printf(
                        "Event %d: validation %s%n",
                        counter,
                        success ? "completed" : "failed");

                ValidationEvent validationEvent =
                        engine.getCurrentValidationEvent();

                if (validationEvent != null) {

                    System.out.printf(
                            "  run=%d event=%d "
                            + "hits=%d clusters=%d segments=%d "
                            + "crosses=%d seeds=%d tracks=%d%n",
                            validationEvent.getRun(),
                            validationEvent.getEvent(),
                            validationEvent.getHits().size(),
                            validationEvent.getClusters().size(),
                            validationEvent.getSegments().size(),
                            validationEvent.getCrosses().size(),
                            validationEvent.getSeeds().size(),
                            validationEvent.getTracks().size());

                            printValidationResults(
                                validationEvent,
                                engine.getCurrentEventResults());
                }
            }

        } finally {
            source.close();
        }

        System.out.println();
        System.out.printf(
                "Processed events: %d%n",
                engine.getProcessedEvents());

        System.out.printf(
                "Failed events:    %d%n",
                engine.getFailedEvents());
    }
}
