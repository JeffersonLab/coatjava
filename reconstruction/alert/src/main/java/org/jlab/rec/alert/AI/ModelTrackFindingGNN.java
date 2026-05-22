package org.jlab.rec.alert.AI;

import java.io.IOException;
import java.nio.file.Paths;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.NoopTranslator;
import org.jlab.utils.CLASResources;

/** DJL wrapper around the GravNet TorchScript model exported from
 *  track-finding/export_torchscript.py. Runs per-event edge scoring.
 *
 *  Exported forward signature (see SingleGraphEdgeScorer):
 *    forward(x: float32[N, 10], edge_index: int64[2, E], edge_attr: float32[E, 9])
 *      -&gt; float32[E]   (sigmoid edge scores in [0, 1])
 */
public class ModelTrackFindingGNN {

    private final ZooModel<NDList, NDList> model;
    /** Reused across every call. DJL's Predictor is NOT thread-safe, but the
     *  ALERT reconstruction engine is single-threaded, so one instance is fine
     *  and avoids the allocation/libtorch-graph-prep cost per event that
     *  dominated predictEdgeScores on small graphs.
     */
    private final Predictor<NDList, NDList> predictor;

    public ModelTrackFindingGNN() {
        // Let libtorch pick sensible defaults: GravNet's cdist + topk + gather
        // chain benefits from the graph optimizer and intra-op parallelism the
        // MLP copy-paste was pinning off. Keep num_interop_threads=1 — there is
        // only one event in flight at a time.
        System.setProperty("ai.djl.pytorch.num_interop_threads", "1");

        String path = CLASResources.getResourcePath("etc/data/nnet/rg-l/model_AHDC_GNN/");
        Criteria<NDList, NDList> criteria = Criteria.builder()
                .setTypes(NDList.class, NDList.class)
                .optModelPath(Paths.get(path))
                .optEngine("PyTorch")
                .optTranslator(new NoopTranslator())
                .optProgress(new ProgressBar())
                .build();

        try {
            model = criteria.loadModel();
        } catch (IOException | ModelNotFoundException | MalformedModelException ex) {
            throw new RuntimeException(ex);
        }
        predictor = model.newPredictor(new NoopTranslator());
    }

    /** Score every edge in the input graph.
     *
     *  @param nodeFeatures shape [N, 10] — see GNNConstants.NODE_FEAT_DIM
     *  @param edgeIndex    shape [2, E]  — int64 source / destination node ids
     *  @param edgeAttr     shape [E, 9]  — see GNNConstants.EDGE_FEAT_DIM
     *  @return float[E] of sigmoid edge scores in [0, 1]
     */
    public float[] predictEdgeScores(float[][] nodeFeatures, long[][] edgeIndex, float[][] edgeAttr) throws Exception {
        if (nodeFeatures == null || nodeFeatures.length == 0) return new float[0];
        int n = nodeFeatures.length;
        int e = edgeIndex[0].length;
        if (e == 0) return new float[0];

        try (NDManager manager = NDManager.newBaseManager()) {
            // Flatten x into a contiguous float[] so DJL builds a [N, node_dim] tensor.
            int nodeDim = nodeFeatures[0].length;
            float[] xFlat = new float[n * nodeDim];
            for (int i = 0; i < n; i++) System.arraycopy(nodeFeatures[i], 0, xFlat, i * nodeDim, nodeDim);
            NDArray x = manager.create(xFlat, new Shape(n, nodeDim));

            // edge_index is int64[2, E]; flatten row-major.
            long[] edgeIndexFlat = new long[2 * e];
            System.arraycopy(edgeIndex[0], 0, edgeIndexFlat, 0,     e);
            System.arraycopy(edgeIndex[1], 0, edgeIndexFlat, e,     e);
            NDArray edgeIndexNd = manager.create(edgeIndexFlat, new Shape(2, e));

            int edgeDim = edgeAttr[0].length;
            float[] edgeAttrFlat = new float[e * edgeDim];
            for (int i = 0; i < e; i++) System.arraycopy(edgeAttr[i], 0, edgeAttrFlat, i * edgeDim, edgeDim);
            NDArray edgeAttrNd = manager.create(edgeAttrFlat, new Shape(e, edgeDim));

            NDList output = predictor.predict(new NDList(x, edgeIndexNd, edgeAttrNd));
            NDArray scoresNd = output.get(0);
            return scoresNd.toFloatArray();
        }
    }
}
