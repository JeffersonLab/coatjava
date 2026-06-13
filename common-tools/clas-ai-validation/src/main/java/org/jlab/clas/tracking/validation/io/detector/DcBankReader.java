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
import org.jlab.clas.tracking.validation.data.Cluster;
import org.jlab.clas.tracking.validation.data.Cross;
import org.jlab.clas.tracking.validation.data.Hit;
import org.jlab.clas.tracking.validation.data.Seed;
import org.jlab.clas.tracking.validation.data.Segment;
import org.jlab.clas.tracking.validation.data.Track;
import org.jlab.clas.tracking.validation.data.ValidationEvent;
import org.jlab.clas.tracking.validation.io.TrackingBankReader;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 * Reads reconstructed DC banks.
 *
 * Supported configurations:
 *
 * Conventional-only:
 *   HitBasedTrkg::HB*
 *   TimeBasedTrkg::TB*
 *
 * AI-assisted only:
 *   ai::tracks
 *   HitBasedTrkg::HB*
 *   TimeBasedTrkg::TB*
 *
 * Conventional and AI-assisted branches:
 *   HitBasedTrkg::HB*
 *   TimeBasedTrkg::TB*
 *   HitBasedTrkg::AI*
 *   TimeBasedTrkg::AI*
 *
 * Raw AI suggestions in ai::tracks are handled by DcAiBankReader.
 *
 * @author veronique
 */
public final class DcBankReader implements TrackingBankReader {

    private static final int DC =
            DetectorType.DC.getDetectorId();

    private static final String AI_SUGGESTION_BANK =
            "ai::tracks";

    private static final String HIT_BASED_INPUT_HIT_BANK =
            "HitBasedTrkg::Hits";

    private static final String[] BANK_SUFFIXES = {
        "Hits",
        "Clusters",
        "Segments",
        "Crosses",
        "Tracks"
    };

    private enum ReconstructionStage {
        HIT_BASED,
        TIME_BASED
    }

    /**
     * Event-local cluster membership for each reconstruction-bank family.
     *
     * This is retained for the conventional HB/TB bank families, whose
     * cluster membership is obtained from the clusterID field in their own
     * Hits bank.
     */
    private final Map<String, Map<Integer, List<HitKey>>>
            hitKeysByClusterByPrefix =
            new HashMap<>();

    /**
     * Event-local lookup of the complete hit-based input collection.
     *
     * HitBasedTrkg::AIClusters.Hit1_ID ... Hit12_ID reference hit IDs from
     * HitBasedTrkg::Hits, including hits that are not copied into AIHits.
     *
     * These hits are indexed without replacing the existing HB, TB, or AI
     * branch-specific hit objects.
     */
    private final Map<Integer, Hit> hitBasedInputHitsById =
            new HashMap<>();

    /** Describes one reconstruction-bank family. */
    private static final class DcBankSet {

        private final String prefix;
        private final String algorithm;
        private final ReconstructionStage stage;

        private DcBankSet(
                String prefix,
                String algorithm,
                ReconstructionStage stage) {

            this.prefix = prefix;
            this.algorithm = algorithm;
            this.stage = stage;
        }

        private String bank(String suffix) {
            return prefix + suffix;
        }

        private boolean isHitBased() {
            return stage == ReconstructionStage.HIT_BASED;
        }

        private boolean isTimeBased() {
            return stage == ReconstructionStage.TIME_BASED;
        }

        @Override
        public String toString() {
            return algorithm + " [" + prefix + "]";
        }
    }

    @Override
    public boolean isApplicable(DataEvent event) {
        return event != null
                && !resolveBankSets(event).isEmpty();
    }

    @Override
    public void readHits(
            DataEvent event,
            ValidationEvent output) {

        /* This reader is reused across events. Both lookups are event-local. */
        hitKeysByClusterByPrefix.clear();
        hitBasedInputHitsById.clear();

        /* Preserve the existing branch-specific hit reading. */
        for (DcBankSet bankSet : resolveBankSets(event)) {
            readHits(
                    event,
                    output,
                    bankSet);
        }

        /*
         * AIClusters.HitN_ID references HitBasedTrkg::Hits, including hits
         * absent from HitBasedTrkg::AIHits. Build a separate lookup without
         * altering the branch-specific hits already stored in ValidationEvent.
         */
        indexHitBasedInputHits(
                event,
                output);
    }

    @Override
    public void readClusters(
            DataEvent event,
            ValidationEvent output) {

        for (DcBankSet bankSet : resolveBankSets(event)) {
            readClusters(
                    event,
                    output,
                    bankSet);
        }
    }

    @Override
    public void readSegments(
            DataEvent event,
            ValidationEvent output) {

        for (DcBankSet bankSet : resolveBankSets(event)) {
            readSegments(
                    event,
                    output,
                    bankSet);
        }
    }

    @Override
    public void readCrosses(
            DataEvent event,
            ValidationEvent output) {

        for (DcBankSet bankSet : resolveBankSets(event)) {
            readCrosses(
                    event,
                    output,
                    bankSet);
        }
    }

    @Override
    public void readSeeds(
            DataEvent event,
            ValidationEvent output) {

        for (DcBankSet bankSet : resolveBankSets(event)) {
            if (bankSet.isHitBased()) {
                readHitBasedTracksAsSeeds(
                        event,
                        output,
                        bankSet);
            }
        }
    }

    @Override
    public void readTracks(
            DataEvent event,
            ValidationEvent output) {

        for (DcBankSet bankSet : resolveBankSets(event)) {
            if (bankSet.isTimeBased()) {
                readTimeBasedTracks(
                        event,
                        output,
                        bankSet);
            }
        }
    }

    private static List<DcBankSet> resolveBankSets(
            DataEvent event) {

        List<DcBankSet> bankSets =
                new ArrayList<>(4);

        if (event == null) {
            return bankSets;
        }

        boolean hasAiSuggestions =
                event.hasBank(AI_SUGGESTION_BANK);

        boolean hasExplicitAiHitBased =
                hasBankFamily(
                        event,
                        "HitBasedTrkg::AI");

        boolean hasExplicitAiTimeBased =
                hasBankFamily(
                        event,
                        "TimeBasedTrkg::AI");

        boolean hasAnyExplicitAi =
                hasExplicitAiHitBased
                || hasExplicitAiTimeBased;

        if (hasBankFamily(
                event,
                "HitBasedTrkg::HB")) {

            String algorithm =
                    hasAiSuggestions && !hasAnyExplicitAi
                            ? "DC-AI-HB"
                            : "DC-HB";

            bankSets.add(
                    new DcBankSet(
                            "HitBasedTrkg::HB",
                            algorithm,
                            ReconstructionStage.HIT_BASED));
        }

        if (hasBankFamily(
                event,
                "TimeBasedTrkg::TB")) {

            String algorithm =
                    hasAiSuggestions && !hasAnyExplicitAi
                            ? "DC-AI-TB"
                            : "DC-TB";

            bankSets.add(
                    new DcBankSet(
                            "TimeBasedTrkg::TB",
                            algorithm,
                            ReconstructionStage.TIME_BASED));
        }

        if (hasExplicitAiHitBased) {
            bankSets.add(
                    new DcBankSet(
                            "HitBasedTrkg::AI",
                            "DC-AI-HB",
                            ReconstructionStage.HIT_BASED));
        }

        if (hasExplicitAiTimeBased) {
            bankSets.add(
                    new DcBankSet(
                            "TimeBasedTrkg::AI",
                            "DC-AI-TB",
                            ReconstructionStage.TIME_BASED));
        }

        return bankSets;
    }

    private static boolean hasBankFamily(
            DataEvent event,
            String prefix) {

        for (String suffix : BANK_SUFFIXES) {
            if (event.hasBank(prefix + suffix)) {
                return true;
            }
        }

        return false;
    }

    /** Reads one reconstruction family's Hits bank. */
    private void readHits(
            DataEvent event,
            ValidationEvent output,
            DcBankSet bankSet) {

        String bankName =
                bankSet.bank("Hits");

        if (!event.hasBank(bankName)) {
            return;
        }

        DataBank bank =
                event.getBank(bankName);

        Map<Integer, List<HitKey>> hitKeysByCluster =
                hitKeysByClusterByPrefix.computeIfAbsent(
                        bankSet.prefix,
                        prefix -> new HashMap<>());

        for (int row = 0; row < bank.rows(); row++) {

            int id =
                    bank.getShort(
                            "id",
                            row);

            if (id < 0) {
                continue;
            }

            int clusterId =
                    getShort(
                            bank,
                            "clusterID",
                            row,
                            -1);

            Hit hit =
                    createDcHit(
                            bank,
                            row,
                            output);

            HitKey key =
                    hit.key();

            if (clusterId > 0) {
                hitKeysByCluster
                        .computeIfAbsent(
                                clusterId,
                                value -> new ArrayList<>())
                        .add(key);
            }

            if (findDcHit(
                    output,
                    key) == null) {

                output.addHit(hit);
            }
        }
    }

    /**
     * Indexes the complete hit-based input collection by hit ID.
     *
     * The indexed hits are not automatically added to ValidationEvent. They
     * are used only when an AI cluster explicitly references a hit through
     * HitN_ID that is absent from the branch-specific AIHits bank.
     */
    private void indexHitBasedInputHits(
            DataEvent event,
            ValidationEvent output) {

        if (!event.hasBank(HIT_BASED_INPUT_HIT_BANK)) {
            return;
        }

        DataBank bank =
                event.getBank(HIT_BASED_INPUT_HIT_BANK);

        for (int row = 0; row < bank.rows(); row++) {

            int id =
                    bank.getShort(
                            "id",
                            row);

            Hit hit =
                    createDcHit(
                            bank,
                            row,
                            output);

            hitBasedInputHitsById.put(
                    id,
                    hit);
        }
    }

    /** Creates a generic DC Hit from one reconstructed hit-bank row. */
    private static Hit createDcHit(
            DataBank bank,
            int row,
            ValidationEvent output) {

        int id =
                bank.getShort(
                        "id",
                        row);

        int sector =
                bank.getByte(
                        "sector",
                        row);

        int superlayer =
                bank.getByte(
                        "superlayer",
                        row);

        int layerWithinSuperlayer =
                bank.getByte(
                        "layer",
                        row);

        int globalLayer =
                (superlayer - 1) * 6
                + layerWithinSuperlayer;

        int wire =
                bank.getShort(
                        "wire",
                        row);

        int clusterId =
                getShort(
                        bank,
                        "clusterID",
                        row,
                        -1);

        int trackId =
                getByte(
                        bank,
                        "trkID",
                        row,
                        -1);

        double localX =
                getFloat(
                        bank,
                        "X",
                        row,
                        Double.NaN);

        double z =
                getFloat(
                        bank,
                        "Z",
                        row,
                        Double.NaN);

        double phi =
                Math.toRadians(
                        (sector - 1) * 60.0);

        double x =
                Double.isFinite(localX)
                        ? localX * Math.cos(phi)
                        : Double.NaN;

        double y =
                Double.isFinite(localX)
                        ? localX * Math.sin(phi)
                        : Double.NaN;

        int truthTrackId =
                output.getTruthTrackId(
                        DC,
                        id);

        int truthHitId =
                truthTrackId > 0
                        ? id
                        : -1;

        return new Hit(
                id,
                DC,
                sector,
                globalLayer,
                wire,
                clusterId,
                trackId,
                truthTrackId,
                truthHitId,
                x,
                y,
                z,
                false,
                Double.NaN);
    }

    /** Reads reconstructed clusters. */
    private void readClusters(
            DataEvent event,
            ValidationEvent output,
            DcBankSet bankSet) {

        String bankName =
                bankSet.bank("Clusters");

        if (!event.hasBank(bankName)) {
            return;
        }

        DataBank bank =
                event.getBank(bankName);

        for (int row = 0; row < bank.rows(); row++) {

            int id =
                    bank.getShort(
                            "id",
                            row);

            int sector =
                    bank.getByte(
                            "sector",
                            row);

            int superlayer =
                    bank.getByte(
                            "superlayer",
                            row);

            int size =
                    getByte(
                            bank,
                            "size",
                            row,
                            0);

            /* Skip an allocated but completely unfilled bank row. */
            if (id == 0
                    && sector == 0
                    && superlayer == 0
                    && size == 0) {

                continue;
            }

            List<HitKey> hitKeys;

            /*
             * HitBasedTrkg::AIClusters.HitN_ID refers to HitBasedTrkg::Hits,
             * not necessarily HitBasedTrkg::AIHits.
             */
            if (bankSet.isHitBased()) {
                
                hitKeys =
                        readAiClusterHitKeys(
                                bank,
                                row,
                                output);

            } else {

                hitKeys =
                        getClusterHitKeys(
                                bankSet,
                                id);
            }

            if (hitKeys.size() != size) {
//                System.err.printf(
//                        "%s cluster id=%d row=%d: "
//                        + "bank size=%d, resolved hits=%d%n",
//                        bankSet.algorithm,
//                        id,
//                        row,
//                        size,
//                        hitKeys.size());
            }

            output.addCluster(
                    new Cluster(
                            id,
                            bankSet.algorithm,
                            DC,
                            sector,
                            superlayer,
                            hitKeys,
                            getFloat(
                                    bank,
                                    "avgWire",
                                    row,
                                    Double.NaN),
                            0.0,
                            Double.NaN));
        }
    }

    /**
     * Resolves Hit1_ID ... Hit12_ID from HitBasedTrkg::AIClusters through
     * HitBasedTrkg::Hits.
     */
    private List<HitKey> readAiClusterHitKeys(
            DataBank clusterBank,
            int row,
            ValidationEvent output) {

        Set<HitKey> hitKeys =
                new LinkedHashSet<>();

        for (int index = 1; index <= 12; index++) {

            int hitId =
                    getShort(
                            clusterBank,
                            "Hit" + index + "_ID",
                            row,
                            -1);

            if (hitId <= 0) {
                continue;
            }

            Hit inputHit =
                    hitBasedInputHitsById.get(
                            hitId);

            if (inputHit == null) {
//                System.err.printf(
//                        "Cannot resolve AI-cluster hit id=%d "
//                        + "from %s at cluster row=%d%n",
//                        hitId,
//                        HIT_BASED_INPUT_HIT_BANK,
//                        row);
                continue;
            }

            /* Prefer an existing branch-specific physical hit. */
            Hit storedHit =
                    findDcHit(
                            output,
                            inputHit.key());

            if (storedHit == null) {
                output.addHit(
                        inputHit);
                storedHit = inputHit;
            }

            hitKeys.add(
                    storedHit.key());
        }

        return new ArrayList<>(
                hitKeys);
    }

    /** Reads reconstructed segments. */
    private void readSegments(
            DataEvent event,
            ValidationEvent output,
            DcBankSet bankSet) {

        String bankName =
                bankSet.bank("Segments");

        if (!event.hasBank(bankName)) {
            return;
        }

        DataBank bank =
                event.getBank(bankName);

        Map<Integer, Integer> firstRowById =
                new HashMap<>();

        for (int row = 0; row < bank.rows(); row++) {

            int id =
                    bank.getShort(
                            "id",
                            row);

            int sector =
                    bank.getByte(
                            "sector",
                            row);

            int superlayer =
                    bank.getByte(
                            "superlayer",
                            row);

            int clusterId =
                    getShort(
                            bank,
                            "Cluster_ID",
                            row,
                            -1);

            int status =
                    getShort(
                            bank,
                            "status",
                            row,
                            0);

            double avgWire =
                    getFloat(
                            bank,
                            "avgWire",
                            row,
                            Double.NaN);

            double timeSum =
                    getFloat(
                            bank,
                            "timeSum",
                            row,
                            Double.NaN);

            List<HitKey> hitKeys =
                    clusterHitKeysForBankSet(
                            output,
                            bankSet,
                            clusterId);

            Integer firstRow =
                    firstRowById.putIfAbsent(
                            id,
                            row);

            if (firstRow != null) {

                int firstSector =
                        bank.getByte(
                                "sector",
                                firstRow);

                int firstSuperlayer =
                        bank.getByte(
                                "superlayer",
                                firstRow);

                int firstClusterId =
                        getShort(
                                bank,
                                "Cluster_ID",
                                firstRow,
                                -1);

                int firstStatus =
                        getShort(
                                bank,
                                "status",
                                firstRow,
                                0);

                double firstAvgWire =
                        getFloat(
                                bank,
                                "avgWire",
                                firstRow,
                                Double.NaN);

                double firstTimeSum =
                        getFloat(
                                bank,
                                "timeSum",
                                firstRow,
                                Double.NaN);

                List<HitKey> firstHitKeys =
                        clusterHitKeysForBankSet(
                                output,
                                bankSet,
                                firstClusterId);

                boolean identical =
                        sector == firstSector
                        && superlayer == firstSuperlayer
                        && clusterId == firstClusterId
                        && status == firstStatus
                        && sameDouble(
                                avgWire,
                                firstAvgWire)
                        && sameDouble(
                                timeSum,
                                firstTimeSum)
                        && new LinkedHashSet<>(hitKeys).equals(
                                new LinkedHashSet<>(firstHitKeys));

                if (identical) {
//                    System.out.printf(
//                            "%s: ignoring duplicate segment id=%d "
//                            + "at row=%d; identical to row=%d%n",
//                            bankSet.algorithm,
//                            id,
//                            row,
//                            firstRow);
                    continue;
                }

                throw new IllegalStateException(
                        String.format(
                                "%s contains conflicting rows for "
                                + "segment id=%d: rows %d and %d",
                                bankName,
                                id,
                                firstRow,
                                row));
            }

            output.addSegment(
                    new Segment(
                            id,
                            bankSet.algorithm,
                            DC,
                            sector,
                            superlayer,
                            clusterId,
                            status,
                            hitKeys,
                            avgWire,
                            timeSum));
        }
    }

    /** Selects the correct cluster-hit source for one reconstruction family. */
    private List<HitKey> clusterHitKeysForBankSet(
            ValidationEvent output,
            DcBankSet bankSet,
            int clusterId) {

        if ("HitBasedTrkg::AI".equals(
                bankSet.prefix)) {

            return findClusterHitKeys(
                    output,
                    bankSet.algorithm,
                    clusterId);
        }

        return getClusterHitKeys(
                bankSet,
                clusterId);
    }

    /** Returns the hit membership already stored on a reconstructed Cluster. */
    private static List<HitKey> findClusterHitKeys(
            ValidationEvent event,
            String algorithm,
            int clusterId) {

        for (Cluster cluster : event.getClusters()) {
            if (cluster.getDetector() == DC
                    && cluster.getId() == clusterId
                    && algorithm.equals(
                            cluster.getAlgorithm())) {

                return new ArrayList<>(
                        cluster.getHitKeys());
            }
        }

        return new ArrayList<>();
    }

    private static boolean sameDouble(
            double first,
            double second) {

        return Double.doubleToLongBits(first)
                == Double.doubleToLongBits(second);
    }

    private static void readCrosses(
            DataEvent event,
            ValidationEvent output,
            DcBankSet bankSet) {

        String bankName =
                bankSet.bank("Crosses");

        if (!event.hasBank(bankName)) {
            return;
        }

        DataBank bank =
                event.getBank(bankName);

        Map<Integer, Integer> firstRowById =
                new HashMap<>();

        for (int row = 0; row < bank.rows(); row++) {

            int segment1Id =
                    getShort(
                            bank,
                            "Segment1_ID",
                            row,
                            -1);

            int segment2Id =
                    getShort(
                            bank,
                            "Segment2_ID",
                            row,
                            -1);

            int id =
                    segment1Id * 1000
                    + segment2Id;

            int sector =
                    bank.getByte(
                            "sector",
                            row);

            int region =
                    bank.getByte(
                            "region",
                            row);

            List<Integer> segmentIds =
                    positiveIds(
                            segment1Id,
                            segment2Id);

            List<HitKey> hitKeys =
                    output.resolveHitKeysFromSegments(
                            bankSet.algorithm,
                            DC,
                            segmentIds);

            double x =
                    getFloat(
                            bank,
                            "x",
                            row,
                            Double.NaN);

            double y =
                    getFloat(
                            bank,
                            "y",
                            row,
                            Double.NaN);

            double z =
                    getFloat(
                            bank,
                            "z",
                            row,
                            Double.NaN);

            Integer firstRow =
                    firstRowById.putIfAbsent(
                            id,
                            row);

            if (firstRow != null) {

                int firstSector =
                        bank.getByte(
                                "sector",
                                firstRow);

                int firstRegion =
                        bank.getByte(
                                "region",
                                firstRow);

                List<Integer> firstSegmentIds =
                        positiveIds(
                                getShort(
                                        bank,
                                        "Segment1_ID",
                                        firstRow,
                                        -1),
                                getShort(
                                        bank,
                                        "Segment2_ID",
                                        firstRow,
                                        -1));

                List<HitKey> firstHitKeys =
                        output.resolveHitKeysFromSegments(
                                bankSet.algorithm,
                                DC,
                                firstSegmentIds);

                double firstX =
                        getFloat(
                                bank,
                                "x",
                                firstRow,
                                Double.NaN);

                double firstY =
                        getFloat(
                                bank,
                                "y",
                                firstRow,
                                Double.NaN);

                double firstZ =
                        getFloat(
                                bank,
                                "z",
                                firstRow,
                                Double.NaN);

                boolean identical =
                        sector == firstSector
                        && region == firstRegion
                        && new LinkedHashSet<>(segmentIds).equals(
                                new LinkedHashSet<>(firstSegmentIds))
                        && new LinkedHashSet<>(hitKeys).equals(
                                new LinkedHashSet<>(firstHitKeys))
                        && sameDouble(x, firstX)
                        && sameDouble(y, firstY)
                        && sameDouble(z, firstZ);

                if (identical) {
//                    System.out.printf(
//                            "%s: ignoring duplicate cross id=%d "
//                            + "at row=%d; identical to row=%d%n",
//                            bankSet.algorithm,
//                            id,
//                            row,
//                            firstRow);
                    continue;
                }

                throw new IllegalStateException(
                        String.format(
                                "%s contains conflicting rows for "
                                + "cross id=%d: rows %d and %d",
                                bankName,
                                id,
                                firstRow,
                                row));
            }

            output.addCross(
                    new Cross(
                            id,
                            bankSet.algorithm,
                            DC,
                            sector,
                            region,
                            segmentIds,
                            hitKeys,
                            x,
                            y,
                            z));
        }
    }

    private static void readHitBasedTracksAsSeeds(
            DataEvent event,
            ValidationEvent output,
            DcBankSet bankSet) {

        String bankName =
                bankSet.bank("Tracks");

        if (!event.hasBank(bankName)) {
            return;
        }

        DataBank bank =
                event.getBank(bankName);

        for (int row = 0; row < bank.rows(); row++) {

            int id =
                    bank.getShort(
                            "id",
                            row);

            if (id < 0) {
                continue;
            }

            List<Integer> crossIds =
                    readCrossIds(
                            bank,
                            row);

            List<Integer> segmentIds =
                    readTrackSegmentIds(
                            bank,
                            row);

            List<Integer> clusterIds =
                    resolveClusterIdsFromSegments(
                            output,
                            bankSet.algorithm,
                            segmentIds);

            List<HitKey> hitKeys =
                    resolveTrackHitKeys(
                            output,
                            bankSet.algorithm,
                            crossIds,
                            segmentIds);

            output.addSeed(
                    new Seed(
                            id,
                            bankSet.algorithm,
                            DC,
                            dcScope(),
                            crossIds,
                            clusterIds,
                            hitKeys,
                            getFloat(bank, "p0_x", row, 0.0),
                            getFloat(bank, "p0_y", row, 0.0),
                            getFloat(bank, "p0_z", row, 0.0),
                            getByte(bank, "q", row, 0)));
        }
    }

    private static void readTimeBasedTracks(
            DataEvent event,
            ValidationEvent output,
            DcBankSet bankSet) {

        String bankName =
                bankSet.bank("Tracks");

        if (!event.hasBank(bankName)) {
            return;
        }

        DataBank bank =
                event.getBank(bankName);

        for (int row = 0; row < bank.rows(); row++) {

            int id =
                    bank.getShort(
                            "id",
                            row);

            if (id < 0) {
                continue;
            }

            List<Integer> crossIds =
                    readCrossIds(
                            bank,
                            row);

            List<Integer> segmentIds =
                    readTrackSegmentIds(
                            bank,
                            row);

            List<Integer> clusterIds =
                    resolveClusterIdsFromSegments(
                            output,
                            bankSet.algorithm,
                            segmentIds);

            List<HitKey> hitKeys =
                    resolveTrackHitKeys(
                            output,
                            bankSet.algorithm,
                            crossIds,
                            segmentIds);

            output.addTrack(
                    new Track(
                            id,
                            bankSet.algorithm,
                            -1,
                            DC,
                            dcScope(),
                            crossIds,
                            clusterIds,
                            hitKeys,
                            getFloat(bank, "p0_x", row, 0.0),
                            getFloat(bank, "p0_y", row, 0.0),
                            getFloat(bank, "p0_z", row, 0.0),
                            getFloat(bank, "Vtx0_x", row, Double.NaN),
                            getFloat(bank, "Vtx0_y", row, Double.NaN),
                            getFloat(bank, "Vtx0_z", row, Double.NaN),
                            getFloat(bank, "chi2", row, Double.NaN),
                            getShort(bank, "ndf", row, 0),
                            getByte(bank, "q", row, 0),
                            getShort(bank, "status", row, 0)));
        }
    }

    private static List<Integer> readCrossIds(
            DataBank bank,
            int row) {

        List<Integer> result =
                new ArrayList<>(3);

        for (int index = 1; index <= 3; index++) {

            int id =
                    getShort(
                            bank,
                            "Cross" + index + "_ID",
                            row,
                            -1);

            if (id >= 0) {
                result.add(id);
            }
        }

        return uniqueIntegers(result);
    }

    private static List<Integer> readTrackSegmentIds(
            DataBank bank,
            int row) {

        List<Integer> result =
                new ArrayList<>(6);

        for (int index = 1; index <= 6; index++) {

            int id =
                    getShort(
                            bank,
                            "Cluster" + index + "_ID",
                            row,
                            -1);

            if (id >= 0) {
                result.add(id);
            }
        }

        return uniqueIntegers(result);
    }

    private static List<Integer> resolveClusterIdsFromSegments(
            ValidationEvent event,
            String algorithm,
            List<Integer> segmentIds) {

        Set<Integer> clusterIds =
                new LinkedHashSet<>();

        for (int segmentId : segmentIds) {

            Segment segment =
                    findSegment(
                            event,
                            algorithm,
                            segmentId);

            if (segment != null
                    && segment.getClusterId() >= 0) {

                clusterIds.add(
                        segment.getClusterId());
            }
        }

        return new ArrayList<>(clusterIds);
    }

    private static List<HitKey> resolveTrackHitKeys(
            ValidationEvent event,
            String algorithm,
            List<Integer> crossIds,
            List<Integer> segmentIds) {

        Set<HitKey> hitKeys =
                new LinkedHashSet<>();

        for (int crossId : crossIds) {

            Cross cross =
                    findCross(
                            event,
                            algorithm,
                            crossId);

            if (cross != null) {
                hitKeys.addAll(
                        cross.getHitKeys());
            }
        }

        hitKeys.addAll(
                event.resolveHitKeysFromSegments(
                        algorithm,
                        DC,
                        segmentIds));

        return new ArrayList<>(hitKeys);
    }

    private static Segment findSegment(
            ValidationEvent event,
            String algorithm,
            int segmentId) {

        for (Segment segment : event.getSegments()) {
            if (segment.getDetector() == DC
                    && segment.getId() == segmentId
                    && algorithm.equals(
                            segment.getAlgorithm())) {

                return segment;
            }
        }

        return null;
    }

    private static Cross findCross(
            ValidationEvent event,
            String algorithm,
            int crossId) {

        for (Cross cross : event.getCrosses()) {
            if (cross.getDetector() == DC
                    && cross.getId() == crossId
                    && algorithm.equals(
                            cross.getAlgorithm())) {

                return cross;
            }
        }

        return null;
    }

    /**
     * Returns the hits whose clusterID in this branch's Hits bank equals the
     * requested cluster ID.
     */
    private List<HitKey> getClusterHitKeys(
            DcBankSet bankSet,
            int clusterId) {

        if (clusterId < 0) {
            return new ArrayList<>();
        }

        Map<Integer, List<HitKey>> hitKeysByCluster =
                hitKeysByClusterByPrefix.get(
                        bankSet.prefix);

        if (hitKeysByCluster == null) {
            return new ArrayList<>();
        }

        List<HitKey> hitKeys =
                hitKeysByCluster.get(
                        clusterId);

        if (hitKeys == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(
                new LinkedHashSet<>(hitKeys));
    }

    private static Hit findDcHit(
            ValidationEvent event,
            HitKey key) {

        for (Hit hit : event.getHits()) {
            if (hit.getDetector() == DC
                    && hit.key().equals(key)) {

                return hit;
            }
        }

        return null;
    }

    private static Set<Integer> dcScope() {

        Set<Integer> scope =
                new LinkedHashSet<>();

        scope.add(DC);

        return scope;
    }

    private static List<Integer> positiveIds(
            int... ids) {

        List<Integer> result =
                new ArrayList<>();

        for (int id : ids) {
            if (id >= 0) {
                result.add(id);
            }
        }

        return uniqueIntegers(result);
    }

    private static List<Integer> uniqueIntegers(
            List<Integer> values) {

        return new ArrayList<>(
                new LinkedHashSet<>(values));
    }

    private static boolean hasColumn(
            DataBank bank,
            String name) {

        return bank.getDescriptor() != null
                && bank.getDescriptor().hasEntry(name);
    }

    private static int getByte(
            DataBank bank,
            String name,
            int row,
            int defaultValue) {

        return hasColumn(bank, name)
                ? bank.getByte(name, row)
                : defaultValue;
    }

    private static int getShort(
            DataBank bank,
            String name,
            int row,
            int defaultValue) {

        return hasColumn(bank, name)
                ? bank.getShort(name, row)
                : defaultValue;
    }

    private static double getFloat(
            DataBank bank,
            String name,
            int row,
            double defaultValue) {

        return hasColumn(bank, name)
                ? bank.getFloat(name, row)
                : defaultValue;
    }
}
