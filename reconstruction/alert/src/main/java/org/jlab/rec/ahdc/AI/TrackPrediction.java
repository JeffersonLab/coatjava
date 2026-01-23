package org.jlab.rec.ahdc.AI;

import org.jlab.rec.ahdc.Cluster.Cluster;
import org.jlab.rec.ahdc.PreCluster.PreCluster;

import java.util.ArrayList;

public class TrackPrediction {

    private float prediction;
    private final ArrayList<InterCluster> interclusters;
    private final ArrayList<PreCluster> preclusters = new ArrayList<>();
    private ArrayList<Cluster> clusters = new ArrayList<>();

    public TrackPrediction(float prediction, ArrayList<InterCluster> interclusters_) {
        this.prediction = prediction;
        this.interclusters = interclusters_;
        for (InterCluster p : this.interclusters) {
            if (p.getPreclusters() != null)
                this.preclusters.addAll(p.getPreclusters());
        }

        // Generate the clusters
        for (PreCluster p : this.preclusters) {
            if (p.get_Super_layer() == 1) {
                for (PreCluster other : this.preclusters) {
                    if (other.get_Super_layer() == 2 && other.get_Layer() == 1)
                        clusters.add(new Cluster(p, other));
                }
            }

            if (p.get_Super_layer() == 2 && p.get_Layer() == 2) {
                for (PreCluster other : this.preclusters) {
                    if (other.get_Super_layer() == 3 && other.get_Layer() == 1)
                        clusters.add(new Cluster(p, other));
                }
            }

            if (p.get_Super_layer() == 3 && p.get_Layer() == 2) {
                for (PreCluster other : this.preclusters) {
                    if (other.get_Super_layer() == 4 && other.get_Layer() == 1)
                        clusters.add(new Cluster(p, other));
                }
            }

            if (p.get_Super_layer() == 4 && p.get_Layer() == 2) {
                for (PreCluster other : this.preclusters) {
                    if (other.get_Super_layer() == 5 && other.get_Layer() == 1)
                        clusters.add(new Cluster(p, other));
                }
            }


        }

    }

    public float getPrediction() {
        return prediction;
    }

    public ArrayList<InterCluster> getSuperpreclusters() {
        return interclusters;
    }

    public ArrayList<PreCluster> getPreclusters() {
        return preclusters;
    }

    public ArrayList<Cluster> getClusters() {
        return clusters;
    }
}
