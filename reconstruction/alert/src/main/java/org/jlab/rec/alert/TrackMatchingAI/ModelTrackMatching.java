package org.jlab.rec.alert.TrackMatchingAI;

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
import ai.djl.util.Pair;

import org.jlab.rec.alert.AI.InterCluster;
import org.jlab.utils.CLASResources;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;

public class ModelTrackMatching {

    private final ZooModel<float[], float[]> model;

    public ModelTrackMatching() {
        Translator<float[], float[]> my_translator = new Translator<>() {
            @Override
            public float[] processOutput(TranslatorContext translatorContext, NDList ndList) throws Exception {
                NDArray result_sector = ndList.get(0);
                NDArray result_layer = ndList.get(1);
                NDArray result_wedge = ndList.get(2);
                // Find the maximum of an array
                long sector_prediction = result_sector.argMax().getLong(); // long because argMax return an array of int64 -> long
                long layer_prediction = result_layer.argMax().getLong();
                long wedge_prediction = result_wedge.argMax().getLong();
                return new float[]{sector_prediction, layer_prediction, wedge_prediction};
            }

            @Override
            public NDList processInput(TranslatorContext translatorContext, float[] floats) throws Exception {
                NDManager manager = translatorContext.getNDManager();
                NDArray samples = manager.create(floats);
                samples.set(floats);
                return new NDList(samples);
            }
        };
        System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
        System.setProperty("ai.djl.pytorch.num_threads", "1");
        System.setProperty("ai.djl.pytorch.graph_optimizer", "false");

        String path = CLASResources.getResourcePath("etc/data/nnet/rg-l/model_TM/");
        Criteria<float[], float[]> my_model = Criteria.builder().setTypes(float[].class, float[].class)
                .optModelPath(Paths.get(path))
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

    public ZooModel<float[], float[]> getModel() {
        return model;
    }

    public float[] prediction(ArrayList<Pair<Float, Float>> track) throws TranslateException {
        float[] a = new float[]{(float) track.get(0).getKey(), (float) track.get(0).getValue(),
                (float) track.get(1).getKey(), (float) track.get(1).getValue(),
                (float) track.get(2).getKey(), (float) track.get(2).getValue(),
                (float) track.get(3).getKey(), (float) track.get(3).getValue(),
                (float) track.get(4).getKey(), (float) track.get(4).getValue(),
        };

        Predictor<float[], float[]> my_predictor = model.newPredictor();
        return my_predictor.predict(a);
    }

}
