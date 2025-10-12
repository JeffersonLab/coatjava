package org.jlab.rec.ahdc.AI;

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
import org.jlab.rec.ahdc.Track.Track;
import org.jlab.utils.CLASResources;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

public class Model_TM {
    private ZooModel<float[], float[]> model;

    public Model_TM() {
        Translator<float[], float[]> my_translator = new Translator<float[], float[]>() {
            @Override
            public float[] processOutput(TranslatorContext translatorContext, NDList ndList) throws Exception {
                NDArray result_sector = ndList.get(0);
                NDArray result_layer = ndList.get(1);
                NDArray result_wedge = ndList.get(2);
                // System.out.println("Output translator sector: " + result_sector);
                // System.out.println("Output translator layer: " + result_layer);
                // System.out.println("Output translator wedge: " + result_wedge);

                // Find the maximum of an array
                long sector_prediction = result_sector.argMax().getLong(); // long because argMax return an array of int64 -> long
                long layer_prediction = result_layer.argMax().getLong();
                long wedge_prediction = result_wedge.argMax().getLong();

                // System.out.println("Sector: " + sector_prediction + " layer: " + layer_prediction + " wedge: " + wedge_prediction);

                return new float[]{sector_prediction, layer_prediction, wedge_prediction};
            }

            @Override
            public NDList processInput(TranslatorContext translatorContext, float[] floats) throws Exception {
                NDManager manager = NDManager.newBaseManager();
                NDArray samples = manager.zeros(new Shape(floats.length));
                samples.set(floats);
                return new NDList(samples);
            }
        };
        System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
        System.setProperty("ai.djl.pytorch.num_threads", "1");
        System.setProperty("ai.djl.pytorch.graph_optimizer", "false");

        String path = CLASResources.getResourcePath("etc/nnet/ALERT/model_TM/");
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

    public float[] prediction(ArrayList<PreclusterSuperlayer> track) throws TranslateException {
        float[] a = new float[]{(float) track.get(0).getX(), (float) track.get(0).getY(),
                (float) track.get(1).getX(), (float) track.get(1).getY(),
                (float) track.get(2).getX(), (float) track.get(2).getY(),
                (float) track.get(3).getX(), (float) track.get(3).getY(),
                (float) track.get(4).getX(), (float) track.get(4).getY(),
        };

        Predictor<float[], float[]> my_predictor = model.newPredictor();
        return my_predictor.predict(a);
    }
}
