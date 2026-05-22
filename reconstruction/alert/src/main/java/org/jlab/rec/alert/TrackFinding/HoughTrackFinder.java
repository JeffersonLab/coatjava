package org.jlab.rec.alert.TrackFinding;

import org.jlab.rec.ahdc.AHDCCluster.AHDCCluster;
import org.jlab.rec.ahdc.AHDCCluster.AHDCClusterFinder;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.HoughTransform.HoughTransform;
import org.jlab.rec.ahdc.PreCluster.PreCluster;
import org.jlab.rec.ahdc.PreCluster.PreClusterFinder;
import org.jlab.rec.alert.Track.AtofHitStub;
import org.jlab.rec.alert.Track.TrackCandidate;

import java.util.ArrayList;
import java.util.List;

public class HoughTrackFinder implements TrackFinder {

    @Override
    public TrackFinderResult findTracks(ArrayList<Hit> hits, List<AtofHitStub> atofHits) {
        PreClusterFinder pcf = new PreClusterFinder();
        pcf.findPreclusters(hits);
        ArrayList<PreCluster> preclusters = pcf.get_AHDCPreClusters();

        AHDCClusterFinder cf = new AHDCClusterFinder();
        cf.findCluster(preclusters);
        ArrayList<AHDCCluster> clusters = cf.get_AHDCClusters();

        HoughTransform hough = new HoughTransform();
        hough.find_tracks(clusters);
        ArrayList<TrackCandidate> tracks = hough.get_AHDCTrackCandidates();

        return TrackFinderResult.ok(tracks);
    }
}
