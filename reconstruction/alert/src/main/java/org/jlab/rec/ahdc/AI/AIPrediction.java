package org.jlab.rec.ahdc.AI;

import java.util.ArrayList;

public class AIPrediction {


    public AIPrediction() {}

    public ArrayList<TrackPrediction> prediction(ArrayList<ArrayList<PreclusterSuperlayer>> tracks, ModelTrackFinding modelTrackFinding) throws Exception {
        ArrayList<TrackPrediction> result = new ArrayList<>();

        if (tracks.isEmpty()) return result;

        float[][] batchInput = new float[tracks.size()][10];
        for (int i = 0; i < tracks.size(); i++) {
            ArrayList<PreclusterSuperlayer> track = tracks.get(i);
            batchInput[i][0] = (float) track.get(0).getX();
            batchInput[i][1] = (float) track.get(0).getY();
            batchInput[i][2] = (float) track.get(1).getX();
            batchInput[i][3] = (float) track.get(1).getY();
            batchInput[i][4] = (float) track.get(2).getX();
            batchInput[i][5] = (float) track.get(2).getY();
            batchInput[i][6] = (float) track.get(3).getX();
            batchInput[i][7] = (float) track.get(3).getY();
            batchInput[i][8] = (float) track.get(4).getX();
            batchInput[i][9] = (float) track.get(4).getY();
        }

        float[] predictions = modelTrackFinding.batchPredict(batchInput);
        for (int i = 0; i < tracks.size(); i++) {
            result.add(new TrackPrediction(predictions[i], tracks.get(i)));
        }

        return result;
    }


}
