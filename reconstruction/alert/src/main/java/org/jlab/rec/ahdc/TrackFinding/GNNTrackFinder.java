package org.jlab.rec.ahdc.TrackFinding;

import org.jlab.rec.ahdc.AI.GNNPrediction;
import org.jlab.rec.ahdc.AI.ModelTrackFindingGNN;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.Track.AtofHitStub;
import org.jlab.rec.ahdc.Track.TrackCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** GravNet-based track finder. Builds a per-event hit graph from the AHDC
 *  hits and (when present) the ATOF::hits bank, runs the exported edge
 *  scorer, extracts tracks via connected components on edges with score
 *  &ge; 0.1, and packages each surviving track as a {@link TrackCandidate}
 *  backed by per-superlayer {@link org.jlab.rec.ahdc.AHDCCluster.AHDCCluster}s.
 */
public class GNNTrackFinder implements TrackFinder {

    private static final Logger LOGGER = Logger.getLogger(GNNTrackFinder.class.getName());

    /** Above this hit count the graph builder + GNN inference is too slow to
     *  be useful, so the event is skipped (no GNN tracks produced). */
    private static final int MAX_HITS_FOR_GNN = 500;

    private final ModelTrackFindingGNN model;
    private final GNNPrediction        predictor;

    public GNNTrackFinder() {
        this.model     = new ModelTrackFindingGNN();
        this.predictor = new GNNPrediction();
    }

    /** With an empty {@code atofHits} list the GNN still runs on AHDC-only graphs. */
    @Override
    public TrackFinderResult findTracks(ArrayList<Hit> ahdcHits, List<AtofHitStub> atofHits) {
        if (ahdcHits == null || ahdcHits.size() > MAX_HITS_FOR_GNN) {
            if (ahdcHits != null) {
                LOGGER.info("Too many AHDC_Hits in AHDC::hits, skipping GNN track finding for this event");
            }
            return TrackFinderResult.ok(new ArrayList<>());
        }

        ArrayList<TrackCandidate> tracks = predictor.prediction(ahdcHits, atofHits, model);
        return TrackFinderResult.ok(tracks);
    }
}
