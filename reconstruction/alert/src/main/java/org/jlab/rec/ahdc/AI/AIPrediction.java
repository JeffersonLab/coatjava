package org.jlab.rec.ahdc.AI;

import java.util.ArrayList;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;

import java.io.IOException;

public class AIPrediction {


    public AIPrediction() throws ModelNotFoundException, MalformedModelException, IOException {
    }

    public ArrayList<TrackPrediction> prediction(ArrayList<ArrayList<PreclusterSuperlayer>> tracks, ZooModel<float[], Float> model) throws TranslateException {
        ArrayList<TrackPrediction> result = new ArrayList<>();
        for (ArrayList<PreclusterSuperlayer> track : tracks) {
            float[] a = new float[]{(float) track.get(0).getX(), (float) track.get(0).getY(),
                    (float) track.get(1).getX(), (float) track.get(1).getY(),
                    (float) track.get(2).getX(), (float) track.get(2).getY(),
                    (float) track.get(3).getX(), (float) track.get(3).getY(),
                    (float) track.get(4).getX(), (float) track.get(4).getY(),
            };

            Predictor<float[], Float> my_predictor = model.newPredictor();
            result.add(new TrackPrediction(my_predictor.predict(a), track));
        }

        return result;
    }


}
