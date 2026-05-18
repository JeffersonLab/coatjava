package org.jlab.rec.atof.cluster;

import java.util.ArrayList;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.atof.constants.Parameters;
import org.jlab.rec.atof.hit.ATOFHit;
import org.jlab.rec.atof.hit.BarHit;
import org.jlab.rec.atof.hit.HitFinder;

/**
 * The {@code ClusterFinder} class builds clusters in the atof
 *
 * <p>
 * Uses found hits information. Creates a {@link ATOFCluster} matching them.
 * </p>
 *
 * @author pilleux
 */
public class ClusterFinder {

    /**
     * list of clusters.
     */
    private ArrayList<ATOFCluster> clusters;

    /**
     * Sets the list of clusters.
     *
     * @param clusters a {@link ArrayList} of {@link ATOFCluster}.
     *
     */
    public void setClusters(ArrayList<ATOFCluster> clusters) {
        this.clusters = clusters;
    }

    /**
     * Gets the list of clusters.
     *
     * @return a {@link ArrayList} of {@link ATOFCluster}.
     *
     */
    public ArrayList<ATOFCluster> getClusters() {
        return clusters;
    }

    /**
     * Computes the module difference between two {@link ATOFHit}.
     *
     * @param hit the first {@link ATOFHit}.
     *
     * @param otherhit the second {@link ATOFHit}.
     *
     */
    public int computeDeltaModule(ATOFHit hit, ATOFHit otherhit) {
        int delta_module = Math.abs(hit.computeModuleIndex() - otherhit.computeModuleIndex());
        if (delta_module > 30) {
            delta_module = 60 - delta_module;
        }
        return delta_module;
    }

    /**
     * Cluster hits around a given hit, based on the time and geometric
     * proximity.
     *
     * Hits are compared based on their module difference, z difference, which
     * is distance in mm or component difference for wedge hits, and time
     * difference.
     *
     * If the hit satisfies all conditions, it is marked as clustered and added
     * to the cluster hit list.
     *
     *
     * @param <T> The type of the hit objects, which must extend
     * {@link ATOFHit}. This allows the method to work with different types of
     * hits that are subclasses of {@link ATOFHit} (e.g., {@link BarHit}).
     * @param i The index from which hits are read in the list to compare
     * against the current hit.
     * @param hits The list of hits to be clustered, can be any subclass of
     * {@link ATOFHit}.
     * @param this_hit The hit currently being considered for clustering.
     * @param sigma_module The threshold for the module difference between the
     * hits. If the difference exceeds this value, the hits are not clustered.
     * @param sigma_z The threshold for the z-distance [mm] (or component
     * difference for "wedge" type hits) between the hits.
     * @param sigma_t The threshold for the time difference [ns] between the
     * hits.
     * @param cluster_id The ID of the cluster being formed.
     * @param this_cluster_hits The list that will store the clustered hits.
     * This list can accept hits of type ATOFHit or BarHit. Clustered hits are
     * added to this list.
     *
     */
    public <T extends ATOFHit> void clusterHits(int i, ArrayList<T> hits, ATOFHit this_hit, double sigma_module, Number sigma_z, double sigma_t, int cluster_id, ArrayList<? super T> this_cluster_hits) {
        // Loop through less energetic clusters
        for (int j = i + 1; j < hits.size(); j++) {
            T other_hit = hits.get(j);
            // Skip already clustered hits
            if (other_hit.getIsInACluster()) {
                continue;
            }
            // Check the distance between the hits
            int delta_module = computeDeltaModule(this_hit, other_hit);
            double delta_T = Math.abs(this_hit.getTime() - other_hit.getTime());
            //The z distance can be a distance in mm or a difference in components for wedge hits
            Boolean condition_z;
            if ("wedge".equals(this_hit.getType()) & "wedge".equals(other_hit.getType())) {
                int delta_Z = Math.abs(this_hit.getComponent() - other_hit.getComponent());
                condition_z = (delta_Z <= sigma_z.intValue());
            } else {
                double delta_Z = Math.abs(this_hit.getZ() - other_hit.getZ());
                condition_z = (delta_Z <= sigma_z.doubleValue());
            }
            //If hit is within limits, it is clustered
            if (delta_module <= sigma_module) {
                if (condition_z) {
                    if (delta_T < sigma_t) {
                        other_hit.setIsInACluster(true);
                        other_hit.setAssociatedClusterIndex(cluster_id);
                        this_cluster_hits.add(other_hit);
                    }
                }
            }
        }
    }

    /**
     * Builds clusters in the {@link DateEvent} using hits found and stored in a
     * {@link HitFinder}.
     *
     * @param hitfinder the {@link HitFinder} containing the hits that were
     * found
     *
     * @param sigma_module the tolerance for clustering between modules
     *
     * @param sigma_component the tolerance for clustering between components
     *
     * @param sigma_z the tolerance for clustering in z [mm]
     *
     * @param sigma_t the tolerance for clustering in time [ns]
     *
     */
    public void makeClusters(HitFinder hitfinder, double sigma_module, double sigma_component, double sigma_z, double sigma_t, DataEvent event) {

        //A list of clusters is built for each event
        clusters.clear();
        int cluster_id = 1;

        //Getting the list of hits, they must have been ordered by energy already
        ArrayList<ATOFHit> wedge_hits = hitfinder.getWedgeHits();
        ArrayList<BarHit> bar_hits = hitfinder.getBarHits();

        //Looping through wedge hits first
        for (int i_wedge = 0; i_wedge < wedge_hits.size(); i_wedge++) {
            ATOFHit this_wedge_hit = wedge_hits.get(i_wedge);
            //Make a cluster for each wedge hit that has not been previously clustered
            if (this_wedge_hit.getIsInACluster()) {
                continue;
            }

            //Holding onto the hits composing the cluster
            ArrayList<ATOFHit> this_cluster_wedge_hits = new ArrayList<>();
            ArrayList<BarHit> this_cluster_bar_hits = new ArrayList<>();

            //Indicate that this hit now is in a cluster
            this_wedge_hit.setIsInACluster(true);
            this_wedge_hit.setAssociatedClusterIndex(cluster_id);
            //And store it
            this_cluster_wedge_hits.add(this_wedge_hit);

            //Check if other wedge hits should be clustered with the current one
            clusterHits(i_wedge, wedge_hits, this_wedge_hit, sigma_module, sigma_component, sigma_t, cluster_id, this_cluster_wedge_hits);
            //After clustering wedge hits, check if bar hits should be clustered with them
            //We start from the beginning of the bar hit list, hence i=-1 to start at 0.
            clusterHits(-1, bar_hits, this_wedge_hit, sigma_module, sigma_z, sigma_t, cluster_id, this_cluster_bar_hits);

            //After all wedge and bar hits have been grouped, build the cluster
            ATOFCluster cluster = new ATOFCluster(this_cluster_bar_hits, this_cluster_wedge_hits,event);
            //And add it to the list of clusters
            clusters.add(cluster);
            cluster_id++;
        }//End loop on all wedge hits

        //Now make clusters from bar hits that are not associated with wedge hits
        //Loop through all bar hits
        for (int i_bar = 0; i_bar < bar_hits.size(); i_bar++) {
            BarHit this_bar_hit = bar_hits.get(i_bar);
            //Skip hits that have already been clustered
            if (this_bar_hit.getIsInACluster()) {
                continue;
            }

            ArrayList<ATOFHit> this_cluster_wedge_hits = new ArrayList<>();
            ArrayList<BarHit> this_cluster_bar_hits = new ArrayList<>();
            this_bar_hit.setIsInACluster(true);
            this_bar_hit.setAssociatedClusterIndex(cluster_id);
            this_cluster_bar_hits.add(this_bar_hit);

            //Matching bar hits in clusters
            clusterHits(i_bar, bar_hits, this_bar_hit, sigma_module, sigma_z, sigma_t, cluster_id, this_cluster_bar_hits);

            ATOFCluster cluster = new ATOFCluster(this_cluster_bar_hits, this_cluster_wedge_hits, event);
            clusters.add(cluster);
            cluster_id++;
        }
    }

    /**
     * Builds clusters in the {@link DateEvent} using hits found and stored in a
     * {@link HitFinder}.
     *
     * @param event the {@link DataEvent} containing the clusters to be built
     *
     * @param hitfinder the {@link HitFinder} containing the hits that were
     * found
     *
     */
    public void makeClusters(DataEvent event, HitFinder hitfinder) {
        makeClusters(hitfinder,
                Parameters.SIGMA_MODULE_CLUSTERING,
                Parameters.SIGMA_COMPONENT_CLUSTERING,
                Parameters.SIGMA_Z_CLUSTERING,
                Parameters.SIGMA_T_CLUSTERING, event);
    }

    /**
     * Default constructor that initializes the list clusters as new empty list.
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
