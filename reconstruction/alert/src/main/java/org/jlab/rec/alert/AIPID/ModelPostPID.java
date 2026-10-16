package org.jlab.rec.alert.AIPID;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Logger;
import org.jlab.utils.CLASResources;

public class ModelPostPID {

    private static final Logger LOGGER = Logger.getLogger(ModelPostPID.class.getName());
    private static final int[] CLASS_IDS = {2212, 45, 46, 49, 47};
    private static final int INPUT_SIZE = 18;

    private final ZooModel<float[], float[]> model;

    public ModelPostPID() {
        System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
        System.setProperty("ai.djl.pytorch.num_threads", "1");
        System.setProperty("ai.djl.pytorch.graph_optimizer", "false");

        String path = CLASResources.getResourcePath("etc/data/nnet/rg-l/model_PID/");
        Criteria<float[], float[]> criteria = Criteria.builder()
                .setTypes(float[].class, float[].class)
                .optModelPath(Paths.get(path))
                .optEngine("PyTorch")
                .optTranslator(translator())
                .optProgress(new ProgressBar())
                .build();
        try {
            model = criteria.loadModel();
        } catch (IOException | ModelNotFoundException | MalformedModelException e) {
            throw new RuntimeException(e);
        }
    }

    public float[] prediction(float[] features) throws TranslateException {
        if (features == null || features.length != INPUT_SIZE) {
            LOGGER.warning("PostPID input must be float[18]");
            return null;
        }
        try (Predictor<float[], float[]> predictor = model.newPredictor()) {
            return predictor.predict(features);
        }
    }

    private static Translator<float[], float[]> translator() {
        return new Translator<>() {
            @Override
            public NDList processInput(TranslatorContext ctx, float[] features) {
                return new NDList(ctx.getNDManager().create(features, new Shape(1, INPUT_SIZE)));
            }

            @Override
            public float[] processOutput(TranslatorContext ctx, NDList output) {
                float[] probabilities = output.get(0).toFloatArray();
                int bestIndex = 0;
                for (int i = 1; i < probabilities.length; i++) {
                    if (probabilities[i] > probabilities[bestIndex]) {
                        bestIndex = i;
                    }
                }
                return new float[]{
                    CLASS_IDS[bestIndex],
                    probabilities[0], probabilities[1], probabilities[2],
                    probabilities[3], probabilities[4]
                };
            }
        };
    }
}
