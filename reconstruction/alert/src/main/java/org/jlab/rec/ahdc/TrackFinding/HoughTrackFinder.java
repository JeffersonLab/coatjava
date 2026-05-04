package org.jlab.rec.ahdc.TrackFinding;

import org.jlab.rec.ahdc.Cluster.Cluster;
import org.jlab.rec.ahdc.Cluster.ClusterFinder;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.HoughTransform.HoughTransform;
import org.jlab.rec.ahdc.PreCluster.PreCluster;
import org.jlab.rec.ahdc.PreCluster.PreClusterFinder;
import org.jlab.rec.ahdc.Track.Track;

import java.util.ArrayList;

public class HoughTrackFinder implements TrackFinder {

    @Override
    public TrackFinderResult findTracks(ArrayList<Hit> hits) {
        PreClusterFinder pcf = new PreClusterFinder();
        pcf.findPreclusters(hits);
        ArrayList<PreCluster> preclusters = pcf.get_AHDCPreClusters();

        ClusterFinder cf = new ClusterFinder();
        cf.findCluster(preclusters);
        ArrayList<Cluster> clusters = cf.get_AHDCClusters();

        HoughTransform hough = new HoughTransform();
        hough.find_tracks(clusters);
        ArrayList<Track> tracks = hough.get_AHDCTracks();

        return TrackFinderResult.ok(tracks);
    }
}
