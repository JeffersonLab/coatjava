package org.jlab.rec.ai.dcHBTrackState;

import ai.djl.MalformedModelException;
import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.*;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.*;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.*;
import org.jlab.utils.system.ClasUtilsFile;
import org.jlab.service.ai.PredictorPool;


public class HBTrackStateEstimator{
    // ---------------- Configuration ----------------
    private String modelFile;

    ZooModel<float[][], float[]> model;
    PredictorPool predictors;

    // ---------------- Statistics for normalization of inputs and outputs of training samples ----------------
    //// Note: Statistics of hits and track states depends on training samples, so need to be renewed when training samples change!!!
    // Statistics of hits: doca, xm, xr, yr, z
    private float[] HIT_MEAN;
    private float[] HIT_STD;

    // Statistics of track state: x, y, tx, ty, Q at z = 229 cm in the tilted sector frame
    private float[] STATE_MEAN;
    private float[] STATE_STD;
    
    public HBTrackStateEstimator(String modelFile, String hitStatsFile, String stateStatsFile){
        this.modelFile = modelFile;
                
        System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
        System.setProperty("ai.djl.pytorch.num_threads", "1");
        System.setProperty("ai.djl.pytorch.graph_optimizer", "false"); 
        
        try {            
            String hitStatsFilePath = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/data/nnet/hbTSE/" + hitStatsFile);            
            String stateStatsFilePath = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/data/nnet/hbTSE/" + stateStatsFile);
            
            loadHitStats(hitStatsFilePath);
            loadStateStats(stateStatsFilePath);
            
            String modelPath = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/data/nnet/hbTSE/" + modelFile);
            
            Criteria<float[][], float[]> criteria = Criteria.builder()
                .setTypes(float[][].class, float[].class)
                .optModelPath(Paths.get(modelPath))
                .optEngine("PyTorch")
                .optTranslator(getTranslator())
                .optProgress(new ProgressBar())
                .build();

            model = criteria.loadModel();

            int threads = 64;
            predictors = new PredictorPool(threads, model);

        } catch (IOException | ModelException e) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, e);
        }                
    }

    private void loadHitStats(String filePath) throws IOException {
        String json = java.nio.file.Files.readString(Paths.get(filePath));

        String[] keys = {"doca", "xm", "xr", "yr", "z"};

        HIT_MEAN = new float[5];
        HIT_STD = new float[5];

        for (int i = 0; i < keys.length; i++) {
            HIT_MEAN[i] = getJsonValue(json, keys[i] + "_mean");
            HIT_STD[i]  = getJsonValue(json, keys[i] + "_std");

            if (!Float.isFinite(HIT_MEAN[i]) || !Float.isFinite(HIT_STD[i]) || HIT_STD[i] <= 0) {
                throw new IOException("Invalid hit statistics: " + keys[i]);
            }
        }
    }
    
    private float getJsonValue(String json, String key) throws IOException {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)");

        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            throw new IOException("Missing JSON key: " + key);
        }

        try {
            return Float.parseFloat(matcher.group(1));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid JSON value: " + key, e);
        }
    }    
    
    private void loadStateStats(String filePath) throws IOException {
        String json = java.nio.file.Files.readString(Paths.get(filePath));

        String[] keys = {"x", "y", "tx", "ty", "Q"};

        STATE_MEAN = new float[5];
        STATE_STD = new float[5];

        for (int i = 0; i < keys.length; i++) {
            float[] values = getJsonArray(json, keys[i]);

            if (values.length != 2 || !Float.isFinite(values[0]) || !Float.isFinite(values[1]) || values[1] <= 0) {
                throw new IOException("Invalid state statistics: " + keys[i]);
            }

            STATE_MEAN[i] = values[0];
            STATE_STD[i] = values[1];
        }
    }    

    private float[] getJsonArray(String json, String key) throws IOException{
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[([^\\]]+)\\]");

        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            throw new IOException("Missing JSON key: " + key);
        }

        String[] values = matcher.group(1).split(",");

        float[] result = new float[values.length];

        try {
            for (int i = 0; i < values.length; i++) {
                result[i] = Float.parseFloat(values[i].trim());
            }
        } catch (NumberFormatException e) {
            throw new IOException("Invalid JSON values for key: " + key, e);
        }

        return result;
    }    
    
    // ---------------- Translator ----------------
    private Translator<float[][], float[]> getTranslator() {
        return new Translator<float[][], float[]>() {

            @Override
            public NDList processInput(TranslatorContext ctx, float[][] hits) {
                NDManager manager = ctx.getNDManager();
                int n = hits.length;

                float[][] norm = new float[n][5];
                for (int i = 0; i < n; i++)
                    for (int j = 0; j < 5; j++)
                        norm[i][j] = (hits[i][j] - HIT_MEAN[j]) / HIT_STD[j];

                NDArray x = manager.create(norm);    
                x = x.reshape(1, n, 5); 
                return new NDList(x);
            }

            @Override
            public float[] processOutput(TranslatorContext ctx, NDList list) {
                NDArray out = list.get(0); // [1,5]
                float[] y = out.toFloatArray();

                for (int i = 0; i < 5; i++)
                    y[i] = y[i] * STATE_STD[i] + STATE_MEAN[i];

                return y;
            }

            @Override
            public Batchifier getBatchifier() {
                return null;
            }
        };
    }
    
    
    public float[] predict(float[][] hits) {
        if (hits == null) return null;
                
        if (hits.length == 0) {
            throw new IllegalArgumentException("HBInitialStateEstimator: empty hits");
        }
        
        for (int i = 0; i < hits.length; i++) {
            if (hits[i].length != 5) {
                throw new IllegalArgumentException(
                    "Expect 5 features per hit, got " + hits[i].length
                );
            }
        }        

        try {
            Predictor<float[][], float[]> predictor = predictors.take();
            try {
                return predictor.predict(hits);
            } finally {
                predictors.put(predictor);
            }
        } catch (TranslateException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }   
}

