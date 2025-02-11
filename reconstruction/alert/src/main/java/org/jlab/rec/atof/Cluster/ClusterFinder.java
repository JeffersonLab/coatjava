package org.jlab.rec.atof.cluster;

import java.util.ArrayList;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.atof.constants.Parameters;
import org.jlab.rec.atof.hit.AtofHit;
import org.jlab.rec.atof.hit.BarHit;
import org.jlab.rec.atof.hit.HitFinder;

/**
 * The {@code ClusterFinder} class builds clusters in the atof
 *
 * <p>
 * Uses found hits information.
 * Creates a {@link AtofCluster} matching them.
 * </p>
 *
 * @author pilleux
 */
public class ClusterFinder {

    /**
     * list of clusters.
     */
    private ArrayList<AtofCluster> clusters;

    /**
     * Sets the list of clusters.
     *
     * @param clusters a {@link ArrayList} of {@link AtofCluster}.
     * 
     */
    public void setClusters(ArrayList<AtofCluster> clusters) {
        this.clusters = clusters;
    }

    /**
     * Gets the list of clusters.
     *
     * @return a {@link ArrayList} of {@link AtofCluster}.
     * 
     */
    public ArrayList<AtofCluster> getClusters() {
        return clusters;
    }

    /**
     * Builds clusters in the {@link DateEvent} using hits found and
     * stored in a {@link HitFinder}.
     *
     * @param event the {@link DataEvent} containing the clusters to be built
     * 
     * @param hitfinder the {@link HitFinder} containing the hits that were found
     * 
     */
    public void makeClusters(DataEvent event, HitFinder hitfinder) {

        //A list of clusters is built for each event
        clusters.clear();

        //Getting the list of hits, they must have been ordered by energy already
        ArrayList<AtofHit> wedge_hits = hitfinder.getWedgeHits();
        ArrayList<BarHit> bar_hits = hitfinder.getBarHits();

        //Looping through wedge hits first
        for (int i_wedge = 0; i_wedge < wedge_hits.size(); i_wedge++) {
            AtofHit this_wedge_hit = wedge_hits.get(i_wedge);
            //Make a cluster for each wedge hit that has not been previously clustered
            if (this_wedge_hit.getIsInACluster()) {
                continue;
            }

            //Holding onto the hits composing the cluster
            ArrayList<AtofHit> this_cluster_wedge_hits = new ArrayList<>();
            ArrayList<BarHit> this_cluster_bar_hits = new ArrayList<>();

            //Indicate that this hit now is in a cluster
            this_wedge_hit.setIsInACluster(true);
            //And store it
            this_cluster_wedge_hits.add(this_wedge_hit);

            //Check if other wedge hits should be clustered with the current one
            //Start from the index of the current one and look at less energetic hits
            for (int j_wedge = i_wedge + 1; j_wedge < wedge_hits.size(); j_wedge++) {
                AtofHit other_wedge_hit = wedge_hits.get(j_wedge);
                //If that other hit is already involved in a cluster, skip it
                if (other_wedge_hit.getIsInACluster()) {
                    continue;
                }
                //Check the distance between the hits
                //For now we use phi module and z component differences from what is observed in simu
                int delta_module = Math.abs(this_wedge_hit.computeModuleIndex() - other_wedge_hit.computeModuleIndex());
                if (delta_module > 30) {
                    delta_module = 60 - delta_module;
                }
                int delta_component = Math.abs(this_wedge_hit.getComponent() - other_wedge_hit.getComponent());
                //Later we could use z and phi threshold
                //double delta_Phi = Math.abs(this_wedge_hit.getPhi() - other_wedge_hit.getPhi());
                //double delta_Z = Math.abs(this_wedge_hit.getZ() - other_wedge_hit.getZ());
                //Time matching
                double delta_T = Math.abs(this_wedge_hit.getTime() - other_wedge_hit.getTime());

                if (delta_module <= Parameters.SIGMA_MODULE_CLUSTERING) {
                    if (delta_component <= Parameters.SIGMA_COMPONENT_CLUSTERING)//delta_Z <= sigma_Z)
                    {
                        if (delta_T < Parameters.SIGMA_T_CLUSTERING) {
                            other_wedge_hit.setIsInACluster(true);
                            this_cluster_wedge_hits.add(other_wedge_hit);
                        }
                    }
                }
            }

            //After clustering wedge hits, check if bar hits should be clustered with them
            for (int j_bar = 0; j_bar < bar_hits.size(); j_bar++) {
                BarHit other_bar_hit = bar_hits.get(j_bar);
                //Skip already clustered hits
                if (other_bar_hit.getIsInACluster()) {
                    continue;
                }
                //Check the distance between the hits
                //For now we use phi module difference from what is observed in simu
                int delta_module = Math.abs(this_wedge_hit.computeModuleIndex() - other_bar_hit.computeModuleIndex());
                if (delta_module > 30) {
                    delta_module = 60 - delta_module;
                }
                //Later we could use phi threshold
                //double delta_Phi = Math.abs(this_wedge_hit.getPhi() - other_wedge_hit.getPhi());
                double delta_Z = Math.abs(this_wedge_hit.getZ() - other_bar_hit.getZ());
                //Time matching
                double delta_T = Math.abs(this_wedge_hit.getTime() - other_bar_hit.getTime());
                if (delta_module <= Parameters.SIGMA_MODULE_CLUSTERING) {
                    if (delta_Z < Parameters.SIGMA_Z_CLUSTERING) {
                        if (delta_T < Parameters.SIGMA_T_CLUSTERING) {
                            other_bar_hit.setIsInACluster(true);
                            this_cluster_bar_hits.add(other_bar_hit);
                        }
                    }
                }
            }//End loop bar hits
      
            //After all wedge and bar hits have been grouped, build the cluster
            AtofCluster cluster = new AtofCluster(this_cluster_bar_hits, this_cluster_wedge_hits);
            //And add it to the list of clusters
            clusters.add(cluster);
        }//End loop on all wedge hits
        //Now make clusters from bar hits that are not associated with wedge hits
        //Loop through all bar hits
        for (int i_bar = 0; i_bar < bar_hits.size(); i_bar++) {
            BarHit this_bar_hit = bar_hits.get(i_bar);
            //Skip hits that have already been clustered
            if (this_bar_hit.getIsInACluster()) {
                continue;
            }

            ArrayList<AtofHit> this_cluster_wedge_hits = new ArrayList<>();
            ArrayList<BarHit> this_cluster_bar_hits = new ArrayList<>();
            this_bar_hit.setIsInACluster(true);
            this_cluster_bar_hits.add(this_bar_hit);

            //Loop through less energetic clusters
            for (int j_bar = i_bar + 1; j_bar < bar_hits.size(); j_bar++) {
                BarHit other_bar_hit = bar_hits.get(j_bar);
                //Skip already clustered hits
                if (other_bar_hit.getIsInACluster()) {
                    continue;
                }

                //Check the distance between the hits
                //For now we use phi module difference from what is observed in simu
                int delta_module = Math.abs(this_bar_hit.computeModuleIndex() - other_bar_hit.computeModuleIndex());
                if (delta_module > 30) {
                    delta_module = 60 - delta_module;
                }
                //Later we could use phi threshold
                //double delta_Phi = Math.abs(this_wedge_hit.getPhi() - other_wedge_hit.getPhi());
                double delta_Z = Math.abs(this_bar_hit.getZ() - other_bar_hit.getZ());
                //Time matching
                double delta_T = Math.abs(this_bar_hit.getTime() - other_bar_hit.getTime());

                if (delta_module <= Parameters.SIGMA_MODULE_CLUSTERING) {
                    if (delta_Z < Parameters.SIGMA_Z_CLUSTERING) {
                        if (delta_T < Parameters.SIGMA_T_CLUSTERING) {
                            other_bar_hit.setIsInACluster(true);
                            this_cluster_bar_hits.add(other_bar_hit);
                        }
                    }
                }
            }
            AtofCluster cluster = new AtofCluster(this_cluster_bar_hits, this_cluster_wedge_hits);
            clusters.add(cluster);
        }
    }

    /**
     * Default constructor that initializes the list clusters as new empty
     * list.
     */
    public ClusterFinder() {
        clusters = new ArrayList<>();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    }
}
