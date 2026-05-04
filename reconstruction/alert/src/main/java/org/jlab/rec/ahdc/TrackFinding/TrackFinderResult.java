package org.jlab.rec.ahdc.TrackFinding;

import org.jlab.rec.ahdc.Track.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrackFinderResult {

    private final List<Track> tracks;
    private final boolean valid;

    public TrackFinderResult(List<Track> tracks, boolean valid) {
        this.tracks = tracks;
        this.valid = valid;
    }

    public static TrackFinderResult ok(List<Track> tracks) {
        return new TrackFinderResult(tracks, true);
    }

    public static TrackFinderResult invalid() {
        return new TrackFinderResult(Collections.emptyList(), false);
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public boolean isValid() {
        return valid;
    }
}
