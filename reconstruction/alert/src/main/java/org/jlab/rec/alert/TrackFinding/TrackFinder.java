package org.jlab.rec.alert.TrackFinding;

import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.alert.Track.AtofHitStub;

import java.util.ArrayList;
import java.util.List;

public interface TrackFinder {

    /** Find tracks from AHDC hits, optionally using attached ATOF hits as
     *  additional context (e.g. GNN builds a joint AHDC + ATOF hit graph).
     *  Implementations that don't use ATOF should ignore the
     *  {@code atofHits} parameter; it may be empty when no ATOF bank is
     *  present in the event. */
    TrackFinderResult findTracks(ArrayList<Hit> ahdcHits, List<AtofHitStub> atofHits);
}
