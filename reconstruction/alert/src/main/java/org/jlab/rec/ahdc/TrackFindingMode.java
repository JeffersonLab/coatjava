package org.jlab.rec.ahdc;

/** AHDC track-finding strategy, selectable via the {@code ALERT.Mode} YAML key
 *  in the reconstruction config.
 *
 *  <p>"AI_*" modes use a trained neural network; "CV_*" modes are conventional algorithms.</p>
 */
public enum TrackFindingMode {

    /** AI: an MLP scores every candidate track built from inter-clusters, then
     *  a greedy non-overlap pass keeps the highest-scoring non-conflicting set.
     */
    AI_MLP,

    /** CV: distance-based association of clusters across
     *  superlayers, with a circle-fit chi^2 selecting the best track among
     *  candidates that share a starting point. */
    CV_Distance,

    /** CV: Hough-transform track finding in the (u, v) plane —
     *  peaks in Hough space define circle parameters from which the track's
     *  clusters are gathered. */
    CV_Hough,

    /** AI: a GravNet graph neural network scores edges in a per-event
     *  AHDC (+ATOF when present) hit graph; tracks are extracted as connected
     *  components on the high-scoring edges. */
    AI_GNN,
}
