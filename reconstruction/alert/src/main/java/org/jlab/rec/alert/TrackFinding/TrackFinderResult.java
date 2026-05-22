package org.jlab.rec.alert.TrackFinding;

import org.jlab.rec.alert.Track.TrackCandidate;

import java.util.Collections;
import java.util.List;

public class TrackFinderResult {

    private final List<TrackCandidate> tracks;
    private final boolean valid;

    public TrackFinderResult(List<TrackCandidate> tracks, boolean valid) {
        this.tracks = tracks;
        this.valid = valid;
    }

    public static TrackFinderResult ok(List<TrackCandidate> tracks) {
        return new TrackFinderResult(tracks, true);
    }

    public static TrackFinderResult invalid() {
        return new TrackFinderResult(Collections.emptyList(), false);
    }

    public List<TrackCandidate> getTracks() {
        return tracks;
    }

    public boolean isValid() {
        return valid;
    }
}
