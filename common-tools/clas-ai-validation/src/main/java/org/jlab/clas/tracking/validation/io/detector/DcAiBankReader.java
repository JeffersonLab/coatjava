/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt
 * to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java
 * to edit this template
 */
package org.jlab.clas.tracking.validation.io.detector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlab.clas.tracking.validation.HitKey;
import org.jlab.clas.tracking.validation.data.AiTrackSuggestion;
import org.jlab.clas.tracking.validation.data.Cluster;
import org.jlab.clas.tracking.validation.data.ValidationEvent;
import org.jlab.clas.tracking.validation.io.TrackingBankReader;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 * Reads raw DC AI pattern-recognition suggestions from ai::tracks.
 *
 * The c1 ... c6 fields reference the conventional hit-based clusters used
 * as input to the AI candidate. DcHitBasedBankReader loads those clusters
 * with algorithm label DC-HB before the seed phase begins.
 *
 * @author veronique
 */
public final class DcAiBankReader
        implements TrackingBankReader {

    private static final String SUGGESTION_BANK =
            "ai::tracks";

    private static final String ALGORITHM =
            "DC-AI-SUGGESTION";

    private static final String REFERENCED_CLUSTER_ALGORITHM =
            "DC-HB";

    private static final int DC =
            DetectorType.DC.getDetectorId();

    @Override
    public boolean isApplicable(DataEvent event) {
        return event != null
                && event.hasBank(SUGGESTION_BANK);
    }

    /**
     * Reads suggestions in the seed phase, after all cluster readers have
     * completed.
     */
    @Override
    public void readSeeds(
            DataEvent event,
            ValidationEvent output) {

        if (!event.hasBank(SUGGESTION_BANK)) {
            return;
        }

        Map<Integer, Cluster> hbClustersById =
                buildHitBasedClusterIndex(output);

        DataBank bank =
                event.getBank(SUGGESTION_BANK);

        for (int row = 0; row < bank.rows(); row++) {

            int suggestionId =
                    bank.getInt("id", row);

            int sector =
                    bank.getInt("sector", row);

            double probability =
                    bank.getFloat("prob", row);

            List<Integer> clusterIds =
                    new ArrayList<>(6);

            Set<HitKey> suggestedHitKeys =
                    new LinkedHashSet<>();

            for (int superlayer = 1;
                    superlayer <= 6;
                    superlayer++) {

                int clusterId =
                        bank.getInt(
                                "c" + superlayer,
                                row);

                if (clusterId <= 0) {
                    continue;
                }

                Cluster cluster =
                        hbClustersById.get(clusterId);

                if (cluster == null) {
//                    System.err.printf(
//                            "DcAiBankReader: suggestion %d references "
//                            + "HB cluster %d, but DC-HB cluster %d was "
//                            + "not loaded%n",
//                            suggestionId,
//                            clusterId,
//                            clusterId);
                    continue;
                }

                if (cluster.getSector() != sector
                        || cluster.getLayer() != superlayer) {
//                    System.err.printf(
//                            "DcAiBankReader: suggestion %d cluster %d "
//                            + "geometry mismatch: ai::tracks sector=%d "
//                            + "superlayer=%d, DC-HB sector=%d "
//                            + "superlayer=%d%n",
//                            suggestionId,
//                            clusterId,
//                            sector,
//                            superlayer,
//                            cluster.getSector(),
//                            cluster.getLayer());
                    continue;
                }

                clusterIds.add(clusterId);
                cluster.markAiSuggested(probability);

                for (HitKey hitKey : cluster.getHitKeys()) {
                    suggestedHitKeys.add(hitKey);
                    output.markAiSelected(
                            hitKey,
                            probability);
                }
            }

            output.addSuggestion(
                    new AiTrackSuggestion(
                            suggestionId,
                            ALGORITHM,
                            DC,
                            sector,
                            clusterIds,
                            new ArrayList<>(suggestedHitKeys),
                            probability));
        }
    }

    /** Builds the exact DC-HB cluster-ID namespace referenced by ai::tracks. */
    private static Map<Integer, Cluster> buildHitBasedClusterIndex(
            ValidationEvent event) {

        Map<Integer, Cluster> result =
                new HashMap<>();

        for (Cluster cluster : event.getClusters()) {
            if (cluster.getDetector() != DC
                    || !REFERENCED_CLUSTER_ALGORITHM.equals(
                            cluster.getAlgorithm())) {
                continue;
            }

            Cluster previous =
                    result.putIfAbsent(
                            cluster.getId(),
                            cluster);

            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate DC-HB cluster ID "
                        + cluster.getId());
            }
        }

        return result;
    }
}
