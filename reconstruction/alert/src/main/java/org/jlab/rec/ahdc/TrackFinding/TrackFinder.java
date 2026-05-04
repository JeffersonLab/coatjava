package org.jlab.rec.ahdc.TrackFinding;

import org.jlab.io.base.DataBank;
import org.jlab.rec.ahdc.Hit.Hit;

import java.util.ArrayList;

public interface TrackFinder {

    /** AHDC-only track finding. Implementations that don't need ATOF context
     *  (MLP / Distance / Hough) only need to override this method. */
    TrackFinderResult findTracks(ArrayList<Hit> hits);

    /** Track finding with ATOF context (e.g. GNN, which builds a joint
     *  AHDC + ATOF hit graph). The default delegates to the AHDC-only
     *  version, ignoring the ATOF bank. */
    default TrackFinderResult findTracks(ArrayList<Hit> ahdcHits, DataBank atofHitsBank) {
        return findTracks(ahdcHits);
    }
}
