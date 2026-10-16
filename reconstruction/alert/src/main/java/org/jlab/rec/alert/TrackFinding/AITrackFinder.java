package org.jlab.rec.alert.TrackFinding;

import org.jlab.rec.alert.AI.AIPrediction;
import org.jlab.rec.alert.AI.InterCluster;
import org.jlab.rec.alert.AI.ModelTrackFinding;
import org.jlab.rec.alert.AI.PreClustering;
import org.jlab.rec.alert.AI.TrackCandidatesGenerator;
import org.jlab.rec.alert.AI.TrackPrediction;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.PreCluster.PreCluster;
import org.jlab.rec.ahdc.PreCluster.PreClusterFinder;
import org.jlab.rec.alert.Track.AtofHitStub;
import org.jlab.rec.alert.Track.TrackCandidate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class AITrackFinder implements TrackFinder {

    private static final Logger LOGGER = Logger.getLogger(AITrackFinder.class.getName());

    private static final double TRACK_FINDING_AI_THRESHOLD = 0.2;
    private static final int    MAX_HITS_FOR_AI            = 300;

    private final ModelTrackFinding model;
    private final TrackFinder fallback;

    public AITrackFinder() {
        this.model = new ModelTrackFinding();
        this.fallback = new DistanceTrackFinder();
    }

    @Override
    public TrackFinderResult findTracks(ArrayList<Hit> hits, List<AtofHitStub> atofHits) {
        // Safety: too many hits → fall back to conventional track finding for this event
        if (hits.size() > MAX_HITS_FOR_AI) {
            LOGGER.info("Too many AHDC_Hits in AHDC::adc, rely on conventional track finding for this event");
            return fallback.findTracks(hits, atofHits);
        }

        // Preclustering
        PreClusterFinder pcf = new PreClusterFinder();
        pcf.findPreclusters(hits);
        ArrayList<PreCluster> preclusters = pcf.get_AHDCPreClusters();

        // 1) Create inter-clusters from pre-clusters
        PreClustering preClustering = new PreClustering();
        ArrayList<InterCluster> inter_clusters = preClustering.mergePreclusters(preclusters);

        // 2) Create track candidates from inter-clusters
        ArrayList<ArrayList<InterCluster>> tracks_candidates = new ArrayList<>();
        TrackCandidatesGenerator trackCandidatesGenerator = new TrackCandidatesGenerator();
        boolean success = trackCandidatesGenerator.getAllPossibleTrack(inter_clusters, tracks_candidates);

        if (!success) {
            LOGGER.severe("Too many track candidates find by the AI, exiting...");
            return TrackFinderResult.invalid();
        }

        // 3) Use AI model to evaluate track candidates
        ArrayList<TrackPrediction> predictions;
        try {
            AIPrediction aiPrediction = new AIPrediction();
            predictions = aiPrediction.prediction(tracks_candidates, model);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 4) Select good tracks via greedy non-overlap: sort predictions by score
        //    descending, accept the highest-scoring prediction, mark its PreClusters
        //    as claimed, and skip any later prediction that reuses a claimed PreCluster.
        //    The AI candidate generator routinely emits overlapping predictions (each
        //    PreCluster can feed several combinations), and because set_trackId mutates
        //    the shared Hit references in place, a naive "accept all above threshold"
        //    pass would let later tracks silently steal earlier tracks' hits and leave
        //    them orphaned in AHDC::hits. Greedy selection enforces one-hit-one-track.
        predictions.sort((a, b) -> Float.compare(b.getPrediction(), a.getPrediction()));
        Set<PreCluster> claimedPreclusters = new HashSet<>();
        ArrayList<TrackCandidate> tracks = new ArrayList<>();
        for (TrackPrediction t : predictions) {
            if (t.getPrediction() <= TRACK_FINDING_AI_THRESHOLD) continue;
            boolean overlaps = false;
            for (PreCluster pc : t.getPreclusters()) {
                if (claimedPreclusters.contains(pc)) { overlaps = true; break; }
            }
            if (overlaps) continue;
            claimedPreclusters.addAll(t.getPreclusters());
            tracks.add(new TrackCandidate(t.getClusters()));
        }
        return TrackFinderResult.ok(tracks);
    }
}
