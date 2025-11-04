package org.jlab.rec.ahdc.AI;

import ai.djl.MalformedModelException;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import org.jlab.utils.CLASResources;

import java.io.IOException;
import java.nio.file.Paths;

/** Model of What.
 * 
 *
 * \todo fix class name 
 */
public class ModelTrackFinding {
    private final ZooModel<float[], Float> model;

    public ModelTrackFinding() {
        Translator<float[], Float> my_translator = new Translator<>() {
            @Override
            public Float processOutput(TranslatorContext translatorContext, NDList ndList) throws Exception {
                return ndList.get(0).getFloat();
            }

            @Override
            public NDList processInput(TranslatorContext translatorContext, float[] floats) throws Exception {
                NDManager manager = translatorContext.getNDManager();
                NDArray samples = manager.create(floats);
                return new NDList(samples);
            }
        };
        System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
        System.setProperty("ai.djl.pytorch.num_threads", "1");
        System.setProperty("ai.djl.pytorch.graph_optimizer", "false");

        String path = CLASResources.getResourcePath("etc/data/nnet/ALERT/model_AHDC/");
        Criteria<float[], Float> my_model = Criteria.builder().setTypes(float[].class, Float.class)
                .optModelPath(Paths.get("etc/nnet/ALERT/model_AHDC/"))
                .optEngine("PyTorch")
                .optTranslator(my_translator)
                .optProgress(new ProgressBar())
                .build();


        try {
            model = my_model.loadModel();
        } catch (IOException | ModelNotFoundException | MalformedModelException e) {
            throw new RuntimeException(e);
        }

    }

    public ZooModel<float[], Float> getModel() {
        return model;
    }

    /**
     * Batch prediction for improved performance.
     * Predicts all tracks at once instead of one at a time.
     * This is significantly faster due to reduced overhead and better GPU utilization.
     *
     * @param inputs Array of input features for each track
     * @return Array of predictions for each track
     */
    public float[] batchPredict(float[][] inputs) throws Exception {
        if (inputs == null || inputs.length == 0) {
            return new float[0];
        }

        try (NDManager manager = NDManager.newBaseManager()) {
            int batchSize = inputs.length;
            NDArray batchInput = manager.create(inputs);
            NDList inputList = new NDList(batchInput);
            ai.djl.inference.Predictor<NDList, NDList> rawPredictor = model.newPredictor(new ai.djl.translate.NoopTranslator());
            NDList output = rawPredictor.predict(inputList);

            NDArray outputArray = output.get(0);
            float[] results = new float[batchSize];

            if (outputArray.getShape().dimension() == 2) {
                for (int i = 0; i < batchSize; i++) {
                    results[i] = outputArray.get(i, 0).getFloat();
                }
            } else {
                for (int i = 0; i < batchSize; i++) {
                    results[i] = outputArray.get(i).getFloat();
                }
            }

            return results;
        }
    }
}
