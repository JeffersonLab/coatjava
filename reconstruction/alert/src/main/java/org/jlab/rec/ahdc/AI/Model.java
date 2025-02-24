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

public class Model {
    private ZooModel<float[], Float> model;

    public Model() {
        Translator<float[], Float> my_translator = new Translator<float[], Float>() {
            @Override
            public Float processOutput(TranslatorContext translatorContext, NDList ndList) throws Exception {
                return ndList.get(0).getFloat();
            }

            @Override
            public NDList processInput(TranslatorContext translatorContext, float[] floats) throws Exception {
                NDManager manager = NDManager.newBaseManager();
                NDArray samples = manager.zeros(new Shape(floats.length));
                samples.set(floats);
                return new NDList(samples);
            }
        };

        String path = CLASResources.getResourcePath("etc/nnet/ALERT/model_AHDC/");
        Criteria<float[], Float> my_model = Criteria.builder().setTypes(float[].class, Float.class)
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

    public ZooModel<float[], Float> getModel() {
        return model;
    }
}
