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

public class ModelPrePID {

    private static final Logger LOGGER = Logger.getLogger(ModelPrePID.class.getName());
    private static final int[] CLASS_IDS = {2212, 45, 46, 49, 47};

    private final ZooModel<float[], float[]> ahdcModel;
    private final ZooModel<float[], float[]> atofModel;

    public ModelPrePID() {
        System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
        System.setProperty("ai.djl.pytorch.num_threads", "1");
        System.setProperty("ai.djl.pytorch.graph_optimizer", "false");

        ahdcModel = loadModel("model_prePID_AHDC", 11);
        atofModel = loadModel("model_prePID_ATOF", 16);
    }

    public ZooModel<float[], float[]> getModel() {
        return ahdcModel;
    }

    public float[] prediction(float[] features) throws TranslateException {
        if (features != null && features.length == 16) {
            return predictionATOF(features);
        }
        return predictionAHDC(features);
    }

    public float[] predictionAHDC(float[] features) throws TranslateException {
        return predict(ahdcModel, features, 11);
    }

    public float[] predictionATOF(float[] features) throws TranslateException {
        return predict(atofModel, features, 16);
    }

    private static float[] predict(ZooModel<float[], float[]> model, float[] features,
            int expectedSize) throws TranslateException {
        if (features == null || features.length != expectedSize) {
            LOGGER.warning("PrePID input must be float[" + expectedSize + "]");
            return null;
        }
        try (Predictor<float[], float[]> predictor = model.newPredictor()) {
            return predictor.predict(features);
        }
    }

    private static ZooModel<float[], float[]> loadModel(String directory, int inputSize) {
        String path = CLASResources.getResourcePath("etc/data/nnet/rg-l/" + directory + "/");
        Criteria<float[], float[]> criteria = Criteria.builder()
                .setTypes(float[].class, float[].class)
                .optModelPath(Paths.get(path))
                .optEngine("PyTorch")
                .optTranslator(translator(inputSize))
                .optProgress(new ProgressBar())
                .build();
        try {
            return criteria.loadModel();
        } catch (IOException | ModelNotFoundException | MalformedModelException e) {
            throw new RuntimeException(e);
        }
    }

    private static Translator<float[], float[]> translator(int inputSize) {
        return new Translator<>() {
            @Override
            public NDList processInput(TranslatorContext ctx, float[] features) {
                return new NDList(ctx.getNDManager().create(features, new Shape(1, inputSize)));
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
