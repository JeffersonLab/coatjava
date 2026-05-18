package org.jlab.rec.ahdc.TrackFinding;

import org.jlab.rec.ahdc.Cluster.Cluster;
import org.jlab.rec.ahdc.Cluster.ClusterFinder;
import org.jlab.rec.ahdc.Distance.Distance;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.PreCluster.PreCluster;
import org.jlab.rec.ahdc.PreCluster.PreClusterFinder;
import org.jlab.rec.ahdc.Track.TrackCandidate;

import java.util.ArrayList;

public class DistanceTrackFinder implements TrackFinder {

    @Override
    public TrackFinderResult findTracks(ArrayList<Hit> hits) {
        PreClusterFinder pcf = new PreClusterFinder();
        pcf.findPreclusters(hits);
        ArrayList<PreCluster> preclusters = pcf.get_AHDCPreClusters();

        ClusterFinder cf = new ClusterFinder();
        cf.findCluster(preclusters);
        ArrayList<Cluster> clusters = cf.get_AHDCClusters();

        Distance distance = new Distance();
        distance.find_track(clusters);
        ArrayList<TrackCandidate> tracks = distance.get_AHDCTrackCandidates();

        return TrackFinderResult.ok(tracks);
    }
}
