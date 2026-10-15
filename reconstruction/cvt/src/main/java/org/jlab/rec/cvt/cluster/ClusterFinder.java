package org.jlab.rec.cvt.cluster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jlab.detector.base.DetectorType;
import org.jlab.rec.cvt.hit.Hit;

/**
 *
 *
 *
 */
public class ClusterFinder {

    public ClusterFinder() {

    }
    
    // cluster finding algorithm
    // the loop is done over sectors 
    Hit[][][] HitArray;
    int nstrip = 1200; // max number of strips
    int nlayr = 6;
    int nsec = 18;
    public ArrayList<Cluster> findClusters(List<Hit> hits2) // the number of strips depends on the layer 
    {
        ArrayList<Cluster> clusters = new ArrayList<>();

        // a Hit Array is used to identify clusters		
        HitArray = new Hit[nstrip][nlayr][nsec];

        // initializing non-zero Hit Array entries
        // with valid hits
        for (Hit hit : hits2) {

            if (hit.getStrip().getStrip() == -1) {
                continue;
            }

            if (hit.getStrip().getStatus()>0 && hit.getStrip().getStatus()<5) {
                continue;
            }

            int w = hit.getStrip().getStrip();
            int l = hit.getLayer();
            int s = hit.getSector();
            
            if (w > 0 && w < nstrip) {
                HitArray[w - 1][l - 1][s - 1] = hit; 
            }

        }
        int cid = 1;  // cluster id, will increment with each new good cluster

        // for each layer and sector, a loop over the strips
        // is done to define clusters in that module's layer
        // clusters are delimited by strips with no hits 
        for (int s = 0; s < nsec; s++) {
            for (int l = 0; l < nlayr; l++) {
                int si = 0;  // strip index in the loop

                // looping over all strips
                while (si < nstrip) {
                    // if there's a hit, it's a cluster candidate
                    if (HitArray[si][l][s] != null || (si < nstrip - 1 && HitArray[si + 1][l][s] != null)) { 
                        
                        Cluster this_cluster = null;
                        
                        // adding all hits in this and all the subsequent
                        // strip until there's a strip with no hit
                        while ((si < nstrip - 1 && HitArray[si + 1][l][s] != null) || (HitArray[si][l][s] != null && si < nstrip)) {
                            if (HitArray[si][l][s] != null) { // continue clustering skipping over bad hit
                                if (this_cluster == null) {
                                    Hit seed = HitArray[si][l][s];
                                    this_cluster = new Cluster(seed.getDetector(), seed.getType(), seed.getSector(), l + 1, cid++);
                                    this_cluster.setId(clusters.size() + 1);
                                }
                                this_cluster.add(HitArray[si][l][s]);
                            }
                            si++;
                        }

                        if (this_cluster == null) break;
                    
                        if (this_cluster.size() > 2) {
                            for(int hi = 1; hi<this_cluster.size()-1; hi++) { //interpolate between neighboring strips
                                if(this_cluster.get(hi).getStrip().getEdep()<this_cluster.get(hi-1).getStrip().getEdep()
                                        && this_cluster.get(hi).getStrip().getEdep()<this_cluster.get(hi+1).getStrip().getEdep()) {
                                    this_cluster.get(hi).getStrip().setEdep(0.5*(this_cluster.get(hi-1).getStrip().getEdep()+this_cluster.get(hi+1).getStrip().getEdep()));
                                }
                            }
                        }
                        for (Hit h : this_cluster) {
                            h.setAssociatedClusterID(this_cluster.getId());
                            //h.newClustering = true; //RDV fix me!
                        }
                        
                        this_cluster.calc_CentroidParams();
                        //if(this_cluster.getDetector()==DetectorType.BST) {
                        //    for (Hit h : this_cluster) {
                        //        h.newClustering = false;
                        //    }
                        //}
                        Collections.sort(this_cluster);
                       
                        //make list of clusters
                        clusters.add(this_cluster);
                    }
                    
                    // if no hits, check for next wire coordinate
                    si++;
                }
            }
        }
        return clusters;

    }

}
