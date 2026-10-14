package org.jlab.rec.cvt.cluster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jlab.rec.cvt.hit.Hit;

/**
 *
 *
 *
 */
public class ClusterFinder {

    static final int NSTRIP = 1200; // max number of strips
    static final int NLAYR = 6;
    static final int NSEC = 18;

    static class HitMapPool extends ConcurrentLinkedQueue<Hit[][][]> {
        public HitMapPool() {
            super();
            for (int i=0; i<64; i++)
                add(new Hit[NSTRIP][NLAYR][NSEC]);
        }
        @Override
        public Hit[][][] poll() {
            Hit[][][] x = super.poll();
            for (int i=0; i<NSTRIP; i++) {
                for (int j=0; j<NLAYR; j++) {
                    for (int k=0; k<NSEC; k++) {
                        x[i][j][k] = null;
                    }
                }
            }
            return x;
        }
    }

    static HitMapPool pool = new HitMapPool();
    
    public static ArrayList<Cluster> findClusters(List<Hit> hits2) // the number of strips depends on the layer 
    {
        // a Hit Array is used to identify clusters		
        Hit[][][] HitArray = pool.poll();

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
            
            if (w > 0 && w < NSTRIP) {
                HitArray[w - 1][l - 1][s - 1] = hit; 
            }
        }

        int cid = 1;  // cluster id, will increment with each new good cluster
        ArrayList<Cluster> clusters = new ArrayList<>();

        // for each layer and sector, a loop over the strips
        // is done to define clusters in that module's layer
        // clusters are delimited by strips with no hits 
        for (int s = 0; s < NSEC; s++) {
            for (int l = 0; l < NLAYR; l++) {
                int si = 0;  // strip index in the loop

                // looping over all strips
                while (si < NSTRIP) {
                    // if there's a hit, it's a cluster candidate
                    if (HitArray[si][l][s] != null || (si < NSTRIP - 1 && HitArray[si + 1][l][s] != null)) { 
                        // vector of hits in the cluster candidate
                        ArrayList<Hit> hits = new ArrayList<>();

                        // adding all hits in this and all the subsequent
                        // strip until there's a strip with no hit
                        while ((si < NSTRIP - 1 && HitArray[si + 1][l][s] != null) || (HitArray[si][l][s] != null && si < NSTRIP)) {
                            if (HitArray[si][l][s] != null) { // continue clustering skipping over bad hit
                                hits.add(HitArray[si][l][s]);
                            }
                            si++;
                        }
                    
                        // define new cluster 
                        Cluster this_cluster = new Cluster(hits.get(0).getDetector(), hits.get(0).getType(), hits.get(0).getSector(), l + 1, cid++);
                        this_cluster.setId(clusters.size() + 1);
                        // add hits to the cluster
                        this_cluster.addAll(hits); 
                        if(hits.size()>2) {
                            for(int hi = 1; hi<hits.size()-1; hi++) { //interpolate between neighboring strips
                                if(hits.get(hi).getStrip().getEdep()<hits.get(hi-1).getStrip().getEdep()
                                        && hits.get(hi).getStrip().getEdep()<hits.get(hi+1).getStrip().getEdep()) {
                                    hits.get(hi).getStrip().setEdep(0.5*(hits.get(hi-1).getStrip().getEdep()+hits.get(hi+1).getStrip().getEdep()));
                                }
                            }
                        }
                        for (Hit h : hits) {
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
        pool.offer(HitArray);
        return clusters;

    }

}
