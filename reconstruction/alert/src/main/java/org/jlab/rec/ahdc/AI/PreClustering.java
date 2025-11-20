package org.jlab.rec.ahdc.AI;

import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.PreCluster.PreCluster;

import java.util.*;

public class PreClustering {
    static final double DISTANCE_MAX = 8.0;

    public ArrayList<InterCluster> mergePreclusters(ArrayList<PreCluster> preclusters) {
        ArrayList<InterCluster> interclusters = new ArrayList<>();
        for (PreCluster precluster : preclusters) {
            if (!precluster.is_Used()) {
                ArrayList<PreCluster> tmp = new ArrayList<>();
                tmp.add(precluster);
                precluster.set_Used(true);
                for (PreCluster other : preclusters) {
                    if (precluster.get_hits_list().getLast().getSuperLayerId() == other.get_hits_list().getLast().getSuperLayerId()
                            && precluster.get_hits_list().getLast().getLayerId() != other.get_hits_list().getLast().getLayerId()
                            && !other.is_Used()) {
                        if (Math.hypot(precluster.get_X() - other.get_X(), precluster.get_Y() - other.get_Y()) < DISTANCE_MAX) {
                            other.set_Used(true);
                            tmp.add(other);
                        }
                    }
                }
                if (!tmp.isEmpty()) interclusters.add(new InterCluster(tmp));
            }
        }
        return interclusters;
    }




}
