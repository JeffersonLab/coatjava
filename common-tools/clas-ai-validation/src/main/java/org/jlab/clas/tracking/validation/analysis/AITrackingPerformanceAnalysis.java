/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt
 * to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java
 * to edit this template
 */
package org.jlab.clas.tracking.validation.analysis;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.jlab.clas.tracking.validation.DetectorMetrics;
import org.jlab.clas.tracking.validation.HitKey;
import org.jlab.clas.tracking.validation.MatchResult;
import org.jlab.clas.tracking.validation.data.AiTrackSuggestion;
import org.jlab.clas.tracking.validation.data.Hit;
import org.jlab.clas.tracking.validation.data.Particle;
import org.jlab.clas.tracking.validation.data.ValidationEvent;
import org.jlab.clas.tracking.validation.data.ValidationObject;
import org.jlab.clas.tracking.validation.service.AIValidationEngine;
import org.jlab.detector.base.DetectorType;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.data.TDirectory;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;

/**
 * Histogram analysis of conventional and AI-assisted tracking performance.
 *
 * <p>The class consumes the already validated objects and MatchResult values
 * produced by {@link AIValidationEngine}. It does not reread reconstruction
 * banks and does not repeat the truth matching.</p>
 *
 * <p>The output contains:</p>
 *
 * <ul>
 *   <li>event-level occupancies;</li>
 *   <li>purity, efficiency, hit counts, fake fraction, and multiplicity for
 *       every algorithm/object stage;</li>
 *   <li>AI-suggestion probability studies;</li>
 *   <li>AI-selected-hit score studies;</li>
 *   <li>truth-particle denominator and stage-specific numerator histograms
 *       versus generated momentum, theta, and phi.</li>
 * </ul>
 *
 * <p>The tracking-efficiency curves are obtained by dividing each stage's
 * numerator histograms by the common truth denominator histograms. A truth
 * particle enters the denominator when it has at least
 * {@code minimumTruthHits} truth-associated DC hits in the validation event.</p>
 *
 * @author veronique
 */
public final class AITrackingPerformanceAnalysis {

    private static final int DC =
            DetectorType.DC.getDetectorId();

    private final int minimumTruthHits;

    private long processedEvents;
    private long failedEvents;

    private final Map<String, StageHistograms> stageHistograms =
            new LinkedHashMap<>();

    /* Event-level histograms. */
    private final H1F hEventHits =
            h1("event_hits", "Measured DC hits per event", "hits", 250, 0.0, 1000.0);

    private final H1F hEventParticles =
            h1("event_truth_particles", "Generated particles per event", "particles", 30, -0.5, 29.5);

    private final H1F hEventSuggestions =
            h1("event_ai_suggestions", "AI suggestions per event", "suggestions", 30, -0.5, 29.5);

    private final H1F hEventSeeds =
            h1("event_seeds", "Reconstructed seeds per event", "seeds", 30, -0.5, 29.5);

    private final H1F hEventTracks =
            h1("event_tracks", "Reconstructed tracks per event", "tracks", 30, -0.5, 29.5);

    /* Common truth-particle denominator histograms. */
    private final H1F hTruthMomentumDenominator =
            h1("truth_p_denominator", "Truth-particle denominator", "p (GeV)", 100, 0.0, 12.0);

    private final H1F hTruthThetaDenominator =
            h1("truth_theta_denominator", "Truth-particle denominator", "theta (deg)", 90, 0.0, 45.0);

    private final H1F hTruthPhiDenominator =
            h1("truth_phi_denominator", "Truth-particle denominator", "phi (deg)", 120, -180.0, 180.0);

    private final H1F hTruthDcHits =
            h1("truth_dc_hits", "Truth-associated DC hits per particle", "DC hits", 60, -0.5, 59.5);

    /* Raw AI-suggestion histograms. */
    private final H1F hAiProbabilityAll =
            h1("ai_probability_all", "AI candidate probability", "probability", 100, 0.0, 1.0);

    private final H1F hAiProbabilityMatched =
            h1("ai_probability_matched", "AI probability: truth matched", "probability", 100, 0.0, 1.0);

    private final H1F hAiProbabilityUnmatched =
            h1("ai_probability_unmatched", "AI probability: unmatched", "probability", 100, 0.0, 1.0);

    private final H1F hAiClustersPerSuggestion =
            h1("ai_clusters_per_suggestion", "Clusters per AI suggestion", "clusters", 8, -0.5, 7.5);

    private final H1F hAiHitsPerSuggestion =
            h1("ai_hits_per_suggestion", "Hits per AI suggestion", "hits", 80, -0.5, 79.5);

    private final H2F hAiPurityVsProbability =
            h2("ai_purity_vs_probability", "AI purity versus probability",
                    "probability", "purity", 50, 0.0, 1.0, 50, 0.0, 1.02);

    private final H2F hAiEfficiencyVsProbability =
            h2("ai_efficiency_vs_probability", "AI efficiency versus probability",
                    "probability", "efficiency", 50, 0.0, 1.0, 50, 0.0, 1.02);

    private final H2F hAiHitsVsProbability =
            h2("ai_hits_vs_probability", "AI hit count versus probability",
                    "probability", "hits", 50, 0.0, 1.0, 80, -0.5, 79.5);

    /* Per-hit AI score histograms. */
    private final H1F hAiHitScoreAll =
            h1("ai_hit_score_all", "AI-selected hit score", "score", 100, 0.0, 1.0);

    private final H1F hAiHitScoreSignal =
            h1("ai_hit_score_signal", "AI-selected signal-hit score", "score", 100, 0.0, 1.0);

    private final H1F hAiHitScoreBackground =
            h1("ai_hit_score_background", "AI-selected background-hit score", "score", 100, 0.0, 1.0);

    public AITrackingPerformanceAnalysis() {
        this(5);
    }

    public AITrackingPerformanceAnalysis(
            int minimumTruthHits) {

        if (minimumTruthHits < 1) {
            throw new IllegalArgumentException(
                    "minimumTruthHits must be positive");
        }

        this.minimumTruthHits = minimumTruthHits;
    }

    /**
     * Fills all histograms for one successfully validated event.
     */
    public void processEvent(
            ValidationEvent event,
            List<MatchResult> results) {

        if (event == null) {
            throw new IllegalArgumentException(
                    "ValidationEvent must not be null");
        }

        if (results == null) {
            throw new IllegalArgumentException(
                    "MatchResult list must not be null");
        }

        processedEvents++;

        fillEventHistograms(event);
        fillAiHitHistograms(event);

        Map<String, EventStageCounts> eventCounts =
                new LinkedHashMap<>();

        Map<String, Set<Integer>> recoveredTruthTracks =
                new LinkedHashMap<>();

        for (MatchResult result : results) {

            if (result == null) {
                continue;
            }

            ValidationObject object =
                    result.getObject();

            String stage =
                    stageLabel(object);

            StageHistograms histograms =
                    stageHistograms.computeIfAbsent(
                            stage,
                            StageHistograms::new);

            histograms.fillObject(result);

            EventStageCounts counts =
                    eventCounts.computeIfAbsent(
                            stage,
                            key -> new EventStageCounts());

            counts.objectCount++;

            if (result.isMatched()) {

                counts.matchedCount++;

                counts.multiplicityByTruthTrack.merge(
                        result.getTruthTrackId(),
                        1,
                        Integer::sum);

                recoveredTruthTracks
                        .computeIfAbsent(
                                stage,
                                key -> new LinkedHashSet<>())
                        .add(result.getTruthTrackId());
            } else {
                counts.unmatchedCount++;
            }

            if (object instanceof AiTrackSuggestion) {
                fillAiSuggestionHistograms(
                        (AiTrackSuggestion) object,
                        result);
            }
        }

        for (Map.Entry<String, EventStageCounts> entry
                : eventCounts.entrySet()) {

            StageHistograms histograms =
                    stageHistograms.get(entry.getKey());

            histograms.fillEventCounts(
                    entry.getValue());
        }

        fillTruthEfficiencyHistograms(
                event,
                recoveredTruthTracks);
    }

    public void recordFailedEvent() {
        failedEvents++;
    }

    private void fillEventHistograms(
            ValidationEvent event) {

        hEventHits.fill(event.getHits().size());
        hEventParticles.fill(event.getParticles().size());
        hEventSuggestions.fill(event.getSuggestions().size());
        hEventSeeds.fill(event.getSeeds().size());
        hEventTracks.fill(event.getTracks().size());
    }

    private void fillAiSuggestionHistograms(
            AiTrackSuggestion suggestion,
            MatchResult result) {

        double probability =
                suggestion.getProbability();

        DetectorMetrics metrics =
                result.getCombined();

        if (Double.isFinite(probability)) {

            hAiProbabilityAll.fill(probability);
            hAiPurityVsProbability.fill(
                    probability,
                    metrics.getPurity());
            hAiEfficiencyVsProbability.fill(
                    probability,
                    metrics.getEfficiency());
            hAiHitsVsProbability.fill(
                    probability,
                    metrics.getObjectHits());

            if (result.isMatched()) {
                hAiProbabilityMatched.fill(probability);
            } else {
                hAiProbabilityUnmatched.fill(probability);
            }
        }

        hAiClustersPerSuggestion.fill(
                suggestion.getClusterIds().size());

        hAiHitsPerSuggestion.fill(
                metrics.getObjectHits());
    }

    private void fillAiHitHistograms(
            ValidationEvent event) {

        for (Hit hit : event.getHits()) {

            if (hit == null
                    || !hit.isAiSelected()
                    || !Double.isFinite(hit.getAiScore())) {
                continue;
            }

            double score =
                    hit.getAiScore();

            hAiHitScoreAll.fill(score);

            if (hit.getTruthTrackId() > 0) {
                hAiHitScoreSignal.fill(score);
            } else {
                hAiHitScoreBackground.fill(score);
            }
        }
    }

    /**
     * Fills one common truth denominator and one numerator per stage.
     *
     * A truth track is counted only once per event and per stage, even when
     * several reconstructed objects from that stage match the same particle.
     */
    private void fillTruthEfficiencyHistograms(
            ValidationEvent event,
            Map<String, Set<Integer>> recoveredTruthTracks) {

        Map<Integer, Set<HitKey>> dcTruthHits =
                new LinkedHashMap<>();

        for (Hit hit : event.getHits()) {

            if (hit == null
                    || hit.getDetector() != DC
                    || hit.getTruthTrackId() <= 0) {
                continue;
            }

            dcTruthHits
                    .computeIfAbsent(
                            hit.getTruthTrackId(),
                            key -> new LinkedHashSet<>())
                    .add(hit.key());
        }

        for (Map.Entry<Integer, Set<HitKey>> entry
                : dcTruthHits.entrySet()) {

            int truthTrackId =
                    entry.getKey();

            int numberOfTruthHits =
                    entry.getValue().size();

            hTruthDcHits.fill(numberOfTruthHits);

            if (numberOfTruthHits < minimumTruthHits) {
                continue;
            }

            Particle particle =
                    event.getParticle(truthTrackId);

            if (particle == null) {
                continue;
            }

            double momentum =
                    particle.getMomentum();

            double theta =
                    thetaDegrees(particle);

            double phi =
                    phiDegrees(particle);

            hTruthMomentumDenominator.fill(momentum);
            hTruthThetaDenominator.fill(theta);
            hTruthPhiDenominator.fill(phi);

            for (Map.Entry<String, Set<Integer>> recovered
                    : recoveredTruthTracks.entrySet()) {

                if (!recovered.getValue().contains(truthTrackId)) {
                    continue;
                }

                StageHistograms histograms =
                        stageHistograms.get(recovered.getKey());

                if (histograms != null) {
                    histograms.fillTruthNumerator(
                            momentum,
                            theta,
                            phi);
                }
            }
        }
    }

    /**
     * Writes all histograms into a GROOT TDirectory file.
     */
    public void write(
            String outputFile) {

        if (outputFile == null
                || outputFile.isBlank()) {
            throw new IllegalArgumentException(
                    "outputFile must not be blank");
        }

        TDirectory directory =
                new TDirectory();

        addEventHistograms(directory);
        addTruthHistograms(directory);
        addAiHistograms(directory);

        directory.mkdir("/stages");

        for (Map.Entry<String, StageHistograms> entry
                : stageHistograms.entrySet()) {

            String folder =
                    "/stages/"
                    + safeName(entry.getKey());

            directory.mkdir(folder);
            directory.cd(folder);
            entry.getValue().addTo(directory);
        }

        directory.writeFile(outputFile);
    }

    private void addEventHistograms(
            TDirectory directory) {

        directory.mkdir("/event");
        directory.cd("/event");
        directory.addDataSet(hEventHits);
        directory.addDataSet(hEventParticles);
        directory.addDataSet(hEventSuggestions);
        directory.addDataSet(hEventSeeds);
        directory.addDataSet(hEventTracks);
    }

    private void addTruthHistograms(
            TDirectory directory) {

        directory.mkdir("/truth");
        directory.cd("/truth");
        directory.addDataSet(hTruthMomentumDenominator);
        directory.addDataSet(hTruthThetaDenominator);
        directory.addDataSet(hTruthPhiDenominator);
        directory.addDataSet(hTruthDcHits);
    }

    private void addAiHistograms(
            TDirectory directory) {

        directory.mkdir("/ai");
        directory.cd("/ai");
        directory.addDataSet(hAiProbabilityAll);
        directory.addDataSet(hAiProbabilityMatched);
        directory.addDataSet(hAiProbabilityUnmatched);
        directory.addDataSet(hAiClustersPerSuggestion);
        directory.addDataSet(hAiHitsPerSuggestion);
        directory.addDataSet(hAiPurityVsProbability);
        directory.addDataSet(hAiEfficiencyVsProbability);
        directory.addDataSet(hAiHitsVsProbability);
        directory.addDataSet(hAiHitScoreAll);
        directory.addDataSet(hAiHitScoreSignal);
        directory.addDataSet(hAiHitScoreBackground);
    }

    public long getProcessedEvents() {
        return processedEvents;
    }

    public long getFailedEvents() {
        return failedEvents;
    }

    public int getMinimumTruthHits() {
        return minimumTruthHits;
    }

    private static String stageLabel(
            ValidationObject object) {

        String algorithm =
                object.getAlgorithm();

        if (algorithm == null
                || algorithm.isBlank()) {
            algorithm = "UNKNOWN";
        }

        return algorithm.trim().toUpperCase(Locale.ROOT)
                + "/"
                + object.getType().name();
    }

    private static String safeName(
            String value) {

        return value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static double thetaDegrees(
            Particle particle) {

        double momentum =
                particle.getMomentum();

        if (!(momentum > 0.0)) {
            return 0.0;
        }

        double cosine =
                particle.getPz() / momentum;

        cosine = Math.max(-1.0, Math.min(1.0, cosine));

        return Math.toDegrees(
                Math.acos(cosine));
    }

    private static double phiDegrees(
            Particle particle) {

        return Math.toDegrees(
                Math.atan2(
                        particle.getPy(),
                        particle.getPx()));
    }

    private static H1F h1(
            String name,
            String title,
            String xTitle,
            int bins,
            double minimum,
            double maximum) {

        H1F histogram =
                new H1F(
                        name,
                        bins,
                        minimum,
                        maximum);

        histogram.setTitle(title);
        histogram.setTitleX(xTitle);
        histogram.setTitleY("counts");
        return histogram;
    }

    private static H2F h2(
            String name,
            String title,
            String xTitle,
            String yTitle,
            int xBins,
            double xMinimum,
            double xMaximum,
            int yBins,
            double yMinimum,
            double yMaximum) {

        H2F histogram =
                new H2F(
                        name,
                        xBins,
                        xMinimum,
                        xMaximum,
                        yBins,
                        yMinimum,
                        yMaximum);

        histogram.setTitle(title);
        histogram.setTitleX(xTitle);
        histogram.setTitleY(yTitle);
        return histogram;
    }

    private static final class EventStageCounts {

        private int objectCount;
        private int matchedCount;
        private int unmatchedCount;

        private final Map<Integer, Integer> multiplicityByTruthTrack =
                new HashMap<>();
    }

    /**
     * Histograms belonging to one algorithm/object stage.
     */
    private static final class StageHistograms {

        private final String label;

        private final H1F hPurity;
        private final H1F hEfficiency;
        private final H1F hFakeFraction;
        private final H1F hObjectHits;
        private final H1F hMatchedHits;
        private final H1F hTruthHits;

        private final H2F hPurityVsEfficiency;
        private final H2F hMatchedVsTruthHits;

        private final H1F hObjectsPerEvent;
        private final H1F hMatchedObjectsPerEvent;
        private final H1F hUnmatchedObjectsPerEvent;
        private final H1F hObjectsPerTruthTrack;

        private final H1F hTruthMomentumNumerator;
        private final H1F hTruthThetaNumerator;
        private final H1F hTruthPhiNumerator;

        private StageHistograms(
                String label) {

            this.label = label;

            String prefix =
                    safeName(label);

            hPurity = h1(
                    prefix + "_purity",
                    label + " purity",
                    "purity",
                    100,
                    0.0,
                    1.02);

            hEfficiency = h1(
                    prefix + "_efficiency",
                    label + " efficiency",
                    "efficiency",
                    100,
                    0.0,
                    1.02);

            hFakeFraction = h1(
                    prefix + "_fake_fraction",
                    label + " fake-hit fraction",
                    "fake-hit fraction",
                    100,
                    0.0,
                    1.02);

            hObjectHits = h1(
                    prefix + "_object_hits",
                    label + " reconstructed hits",
                    "object hits",
                    80,
                    -0.5,
                    79.5);

            hMatchedHits = h1(
                    prefix + "_matched_hits",
                    label + " truth-matched hits",
                    "matched hits",
                    80,
                    -0.5,
                    79.5);

            hTruthHits = h1(
                    prefix + "_truth_hits",
                    label + " expected truth hits",
                    "truth hits",
                    80,
                    -0.5,
                    79.5);

            hPurityVsEfficiency = h2(
                    prefix + "_purity_vs_efficiency",
                    label + " purity versus efficiency",
                    "purity",
                    "efficiency",
                    50,
                    0.0,
                    1.02,
                    50,
                    0.0,
                    1.02);

            hMatchedVsTruthHits = h2(
                    prefix + "_matched_vs_truth_hits",
                    label + " matched versus expected truth hits",
                    "truth hits",
                    "matched hits",
                    60,
                    -0.5,
                    59.5,
                    60,
                    -0.5,
                    59.5);

            hObjectsPerEvent = h1(
                    prefix + "_objects_per_event",
                    label + " objects per event",
                    "objects",
                    60,
                    -0.5,
                    59.5);

            hMatchedObjectsPerEvent = h1(
                    prefix + "_matched_objects_per_event",
                    label + " matched objects per event",
                    "matched objects",
                    30,
                    -0.5,
                    29.5);

            hUnmatchedObjectsPerEvent = h1(
                    prefix + "_unmatched_objects_per_event",
                    label + " unmatched objects per event",
                    "unmatched objects",
                    60,
                    -0.5,
                    59.5);

            hObjectsPerTruthTrack = h1(
                    prefix + "_objects_per_truth_track",
                    label + " reconstructed multiplicity per truth track",
                    "objects / truth track",
                    10,
                    0.5,
                    10.5);

            hTruthMomentumNumerator = h1(
                    prefix + "_truth_p_numerator",
                    label + " recovered truth particles",
                    "p (GeV)",
                    100,
                    0.0,
                    12.0);

            hTruthThetaNumerator = h1(
                    prefix + "_truth_theta_numerator",
                    label + " recovered truth particles",
                    "theta (deg)",
                    90,
                    0.0,
                    45.0);

            hTruthPhiNumerator = h1(
                    prefix + "_truth_phi_numerator",
                    label + " recovered truth particles",
                    "phi (deg)",
                    120,
                    -180.0,
                    180.0);
        }

        private void fillObject(
                MatchResult result) {

            DetectorMetrics metrics =
                    result.getCombined();

            hObjectHits.fill(metrics.getObjectHits());
            
            if (result.isMatched())
                hMatchedHits.fill(metrics.getMatchedHits());
            hTruthHits.fill(metrics.getTruthHits());

            //if (!result.isMatched()) {
            //    return;
            //}

            hPurity.fill(metrics.getPurity());
            hEfficiency.fill(metrics.getEfficiency());
            hFakeFraction.fill(metrics.getFakeFraction());
            hPurityVsEfficiency.fill(
                    metrics.getPurity(),
                    metrics.getEfficiency());
            if (result.isMatched())
                hMatchedVsTruthHits.fill(
                    metrics.getTruthHits(),
                    metrics.getMatchedHits());
        }

        private void fillEventCounts(
                EventStageCounts counts) {

            hObjectsPerEvent.fill(counts.objectCount);
            hMatchedObjectsPerEvent.fill(counts.matchedCount);
            hUnmatchedObjectsPerEvent.fill(counts.unmatchedCount);

            for (int multiplicity
                    : counts.multiplicityByTruthTrack.values()) {
                hObjectsPerTruthTrack.fill(multiplicity);
            }
        }

        private void fillTruthNumerator(
                double momentum,
                double theta,
                double phi) {

            hTruthMomentumNumerator.fill(momentum);
            hTruthThetaNumerator.fill(theta);
            hTruthPhiNumerator.fill(phi);
        }

        private void addTo(
                TDirectory directory) {

            directory.addDataSet(hPurity);
            directory.addDataSet(hEfficiency);
            directory.addDataSet(hFakeFraction);
            directory.addDataSet(hObjectHits);
            directory.addDataSet(hMatchedHits);
            directory.addDataSet(hTruthHits);
            directory.addDataSet(hPurityVsEfficiency);
            directory.addDataSet(hMatchedVsTruthHits);
            directory.addDataSet(hObjectsPerEvent);
            directory.addDataSet(hMatchedObjectsPerEvent);
            directory.addDataSet(hUnmatchedObjectsPerEvent);
            directory.addDataSet(hObjectsPerTruthTrack);
            directory.addDataSet(hTruthMomentumNumerator);
            directory.addDataSet(hTruthThetaNumerator);
            directory.addDataSet(hTruthPhiNumerator);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Standalone HIPO-file analysis.
     *
     * Arguments:
     *
     * 0: input HIPO file. When omitted, a file chooser is shown.
     * 1: output histogram file. Optional.
     * 2: maximum number of events. Optional; <= 0 means all events.
     * 3: minimum number of truth-associated DC hits. Optional; default 5.
     */
    public static void main(
            String[] args) {

        String inputFile =
                args.length > 0
                        ? args[0]
                        : chooseInputFile();

        if (inputFile == null) {
            System.out.println("No input file selected.");
            return;
        }

        String outputFile =
                args.length > 1
                        ? args[1]
                        : defaultOutputFile(inputFile);

        int maximumEvents =
                args.length > 2
                        ? Integer.parseInt(args[2])
                        : -1;

        int minimumTruthHits =
                args.length > 3
                        ? Integer.parseInt(args[3])
                        : 5;

        AIValidationEngine engine =
                new AIValidationEngine();

        if (!engine.init()) {
            throw new IllegalStateException(
                    "AIValidationEngine initialization failed");
        }

        AITrackingPerformanceAnalysis analysis =
                new AITrackingPerformanceAnalysis(
                        minimumTruthHits);

        HipoDataSource source =
                new HipoDataSource();

        source.open(inputFile);

        int eventCounter = 0;

        try {

            while (source.hasEvent()
                    && (maximumEvents <= 0
                    || eventCounter < maximumEvents)) {

                DataEvent dataEvent =
                        source.getNextEvent();

                eventCounter++;

                boolean success =
                        engine.processDataEventUser(
                                dataEvent);

                if (!success) {
                    analysis.recordFailedEvent();
                    continue;
                }

                analysis.processEvent(
                        engine.getCurrentValidationEvent(),
                        engine.getCurrentEventResults());

                if (eventCounter % 1000 == 0) {
                    System.out.printf(
                            "Processed %,d events; failures=%,d%n",
                            eventCounter,
                            analysis.getFailedEvents());
                }
            }

        } finally {
            source.close();
        }

        analysis.write(outputFile);

        System.out.println();
        System.out.printf(
                "Input:              %s%n",
                inputFile);
        System.out.printf(
                "Histogram output:   %s%n",
                outputFile);
        System.out.printf(
                "Validated events:   %,d%n",
                analysis.getProcessedEvents());
        System.out.printf(
                "Failed events:      %,d%n",
                analysis.getFailedEvents());
        System.out.printf(
                "Minimum truth hits: %d%n",
                analysis.getMinimumTruthHits());
    }

    private static String chooseInputFile() {

        JFileChooser chooser =
                new JFileChooser();

        chooser.setDialogTitle(
                "Select reconstructed HIPO file");

        chooser.setFileFilter(
                new FileNameExtensionFilter(
                        "HIPO files",
                        "hipo"));

        int choice =
                chooser.showOpenDialog(null);

        if (choice != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        return chooser.getSelectedFile()
                .getAbsolutePath();
    }

    private static String defaultOutputFile(
            String inputFile) {

        File input =
                new File(inputFile);

        String name =
                input.getName();

        int extension =
                name.toLowerCase(Locale.ROOT)
                        .lastIndexOf(".hipo");

        if (extension >= 0) {
            name = name.substring(0, extension);
        }

        File parent =
                input.getAbsoluteFile().getParentFile();

        return new File(
                parent == null ? new File(".") : parent,
                name + "_ai_validation_histos.hipo")
                .getAbsolutePath();
    }
}
