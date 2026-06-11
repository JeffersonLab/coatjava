package org.jlab.service.recoil.tof;

import java.util.ArrayList;
import org.jlab.io.base.DataEvent;

/**
 * The {@code ClusterFinder} class builds clusters in the recoil tof
 *
 * <p>
 * Uses found hits information. Creates a {@link RTOFCluster} matching them.
 * </p>
 *
 * @author pilleux, Nilanga Wickramaarachchi
 */
public class ClusterFinder {

    /**
     * list of clusters.
     */
    private ArrayList<RTOFCluster> clusters;

    /**
     * Sets the list of clusters.
     *
     * @param clusters a {@link ArrayList} of {@link RTOFCluster}.
     *
     */
    public void setClusters(ArrayList<RTOFCluster> clusters) {
        this.clusters = clusters;
    }

    /**
     * Gets the list of clusters.
     *
     * @return a {@link ArrayList} of {@link RTOFCluster}.
     *
     */
    public ArrayList<RTOFCluster> getClusters() {
        return clusters;
    }

    
    /**
     * Cluster hits around a given hit, based on the time and geometric
     * proximity.
     *
     * Hits are compared based on their y difference, which
     * is distance in cm and time difference.
     *
     * If the hit satisfies all conditions, it is marked as clustered and added
     * to the cluster hit list.
     *
     *
     * @param <T> The type of the hit objects, which must extend
     * {@link RTOFRawHit}. This allows the method to work with different types of
     * hits that are subclasses of {@link RTOFRawHit} (e.g., {@link RTOFHit}).
     * @param i The index from which hits are read in the list to compare
     * against the current hit.
     * @param hits The list of hits to be clustered, can be any subclass of
     * {@link RTOFRawHit}.
     * @param this_hit The hit currently being considered for clustering.
     * @param sigma_y The threshold for the y-distance [cm] between the hits.
     * @param sigma_t The threshold for the time difference [ns] between the
     * hits.
     * @param cluster_id The ID of the cluster being formed.
     * @param this_cluster_hits The list that will store the clustered hits.
     * This list can accept hits of type RTOFRawHit or RTOFHit. Clustered hits are
     * added to this list.
     *
     */
    public <T extends RTOFRawHit> void clusterHits(int i, ArrayList<T> hits, RTOFRawHit this_hit, Number sigma_y, double sigma_t, int cluster_id, ArrayList<? super T> this_cluster_hits) {
        // Loop through less energetic clusters
        for (int j = i + 1; j < hits.size(); j++) {
            T other_hit = hits.get(j);
            // Skip already clustered hits
            if (other_hit.getIsInACluster()) {
                continue;
            }
            // Check the distance between the hits
            double delta_T = Math.abs(this_hit.getTime() - other_hit.getTime());
            //The y distance is a distance in cm 
            Boolean condition_y;
	    double delta_Y = Math.abs(this_hit.getY() - other_hit.getY());
	    condition_y = (delta_Y <= sigma_y.doubleValue());
	
            //If hit is within limits, it is clustered
	    if (condition_y) {
		if (delta_T < sigma_t) {
		    other_hit.setIsInACluster(true);
		    other_hit.setAssociatedClusterIndex(cluster_id);
		    this_cluster_hits.add(other_hit);
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
     * @param sigma_y the tolerance for clustering in y [cm]
     *
     * @param sigma_t the tolerance for clustering in time [ns]
     *
     */
    public void makeClusters(HitFinder hitfinder, double sigma_y, double sigma_t, DataEvent event) {

        //A list of clusters is built for each event
        clusters.clear();
        int cluster_id = 1;

        //Getting the list of hits, they must have been ordered by energy already
        ArrayList<RTOFHit> rtof_hits = hitfinder.getRTOFHits();

        //Loop through all bar hits
        for (int i_bar = 0; i_bar < rtof_hits.size(); i_bar++) {
            RTOFHit this_rtof_hit = rtof_hits.get(i_bar);
            //Skip hits that have already been clustered
            if (this_rtof_hit.getIsInACluster()) {
                continue;
            }

            ArrayList<RTOFHit> this_cluster_rtof_hits = new ArrayList<>();
            this_rtof_hit.setIsInACluster(true);
            this_rtof_hit.setAssociatedClusterIndex(cluster_id);
            this_cluster_rtof_hits.add(this_rtof_hit);

            //Matching bar hits in clusters
            clusterHits(i_bar, rtof_hits, this_rtof_hit, sigma_y, sigma_t, cluster_id, this_cluster_rtof_hits);

            RTOFCluster cluster = new RTOFCluster(this_cluster_rtof_hits, event);
            clusters.add(cluster);
            cluster_id++;
        }
    }

    /**
     * Builds clusters in the {@link DataEvent} using hits found and stored in a
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
                Parameters.SIGMA_Y_CLUSTERING,
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
