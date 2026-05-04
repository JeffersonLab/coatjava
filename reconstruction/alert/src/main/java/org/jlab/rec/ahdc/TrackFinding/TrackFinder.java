package org.jlab.rec.ahdc.TrackFinding;

import org.jlab.rec.ahdc.Hit.Hit;

import java.util.ArrayList;

public interface TrackFinder {
    TrackFinderResult findTracks(ArrayList<Hit> hits);
}
