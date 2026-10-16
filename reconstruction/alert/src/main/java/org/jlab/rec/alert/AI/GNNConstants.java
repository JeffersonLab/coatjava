package org.jlab.rec.alert.AI;

/** Normalization and graph-construction constants for the GNN track finder.
 *  Mirrors track-finding/gnn/config.py — keep in sync with the training config.
 */
final class GNNConstants {
    private GNNConstants() {}

    static final int   NODE_FEAT_DIM      = 10;
    static final int   EDGE_FEAT_DIM      = 9;

    // Model architecture parameters (control the minimum graph size at inference).
    // GravNet progressive-k reaches 2*k, topk uses k+1 → N_nodes >= 2*k + 2.
    // The exported model clamps topk(k+1) to N internally (see
    // track-finding/export_torchscript.py::_knn_indices), so any graph with
    // >=3 nodes runs without crashing. Smaller graphs can't form any edge
    // with the MAX_LAYER_GAP rule anyway, so we skip them here.
    static final int   MIN_NODES          = 3;

    // Graph construction
    static final int   MAX_LAYER_GAP      = 2;
    static final double MAX_EDGE_DISTANCE = 35.0;              // mm
    static final double MAX_EDGE_DIST_SQ  = MAX_EDGE_DISTANCE * MAX_EDGE_DISTANCE;

    // Feature normalization
    static final double MAX_R             = 100.0;             // mm
    static final double DOCA_STD          = 10.0;              // mm
    static final double Z_HALF_LENGTH     = 200.0;             // mm
    static final double STEREO_ANGLE_MAX  = 0.03;              // rad
    static final double STEREO_SCALE      = 1.0 / STEREO_ANGLE_MAX;

    // ATOF abs_layer convention from Python's build_graph
    static final int   ATOF_BAR_ABS_LAYER   = 10;   // component == 10
    static final int   ATOF_WEDGE_ABS_LAYER = 11;   // all other components

    // Track extraction: connected components at a single score threshold, matching
    // gnn/evaluate.py (extract_tracks(..., method="cc", threshold=0.1)). Drop tracks
    // with fewer than MIN_TRACK_NODES total nodes — same filter evaluate.py applies
    // after the method call.
    static final double TRACK_SCORE_THRESHOLD = 0.1;
    static final int    MIN_TRACK_NODES       = 3;
}
