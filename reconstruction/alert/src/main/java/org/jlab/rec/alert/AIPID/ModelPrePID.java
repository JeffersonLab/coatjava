package org.jlab.rec.alert.AIPID;

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
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

import org.jlab.utils.CLASResources;

import java.io.IOException;
import java.nio.file.Paths;

public class ModelPrePID {

    // Must match training class order
    private static final int[] CLASS_IDS = new int[]{2212, 45, 46, 47, 49};

    private final ZooModel<float[], float[]> model;

    public ModelPrePID() {

        Translator<float[], float[]> my_translator = new Translator<>() {

            @Override
            public NDList processInput(TranslatorContext ctx, float[] floats) {
                NDManager manager = ctx.getNDManager();

                // IMPORTANT: model expects (batch, 23). Provide (1, 23).
                NDArray x = manager.create(floats, new Shape(1, 23));
                return new NDList(x);
            }

            @Override
            public float[] processOutput(TranslatorContext ctx, NDList ndList) {
                NDArray logits = ndList.get(0);      // (1,5)
                NDArray probs = logits.softmax(1);   // (1,5)

                float[] p = probs.toFloatArray();    // length 5 (row-major)

                // argmax
                int bestIdx = 0;
                float best = p[0];
                for (int k = 1; k < 5; k++) {
                    if (p[k] > best) { best = p[k]; bestIdx = k; }
                }
                int prepid = CLASS_IDS[bestIdx];

                // Return: prepid + probabilities in fixed class order
                return new float[]{
                    (float) prepid,
                    p[0], p[1], p[2], p[3], p[4]
                };
            }
        };

        System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
        System.setProperty("ai.djl.pytorch.num_threads", "1");
        System.setProperty("ai.djl.pytorch.graph_optimizer", "false");

        String path = CLASResources.getResourcePath("etc/data/nnet/rg-l/model_PrePID/");

        Criteria<float[], float[]> criteria = Criteria.builder()
                .setTypes(float[].class, float[].class)
                .optModelPath(Paths.get(path))
                .optEngine("PyTorch")
                .optTranslator(my_translator)
                .optProgress(new ProgressBar())
                .build();

        try {
            model = criteria.loadModel();
        } catch (IOException | ModelNotFoundException | MalformedModelException e) {
            throw new RuntimeException(e);
        }
    }

    public ZooModel<float[], float[]> getModel() {
        return model;
    }

    /** Returns float[]{prepid} where prepid in {2212,45,46,47,49}. */
    public float[] prediction(float[] features23) throws TranslateException {
        if (features23 == null || features23.length != 23) {
            throw new IllegalArgumentException("PrePID input must be float[23]");
        }
        Predictor<float[], float[]> predictor = model.newPredictor();
        return predictor.predict(features23);
    }
}
