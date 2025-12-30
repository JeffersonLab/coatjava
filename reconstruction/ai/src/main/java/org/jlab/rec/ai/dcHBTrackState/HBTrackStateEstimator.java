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
    
    public HBTrackStateEstimator(String modelFile){
        this.modelFile = modelFile;
        
        if(modelFile.contains("inbending")){
            HIT_MEAN = new float[]{0.52949071f, -45.771999f,  -45.744694f,  57.336819f, 373.046356f};
            HIT_STD = new float[]{0.40272677f, 47.928203f, 48.379021f, 32.645191f, 111.54994f};
            STATE_MEAN = new float[]{-33.564308f, 0.010787425f, -0.15567796f, 0.0017755219f, 0.317530721f};
            STATE_STD = new float[]{28.667490f, 17.761129f, 0.11940812f, 0.074460238f, 0.74185127f};        
        }
        else if(modelFile.contains("outbending")){
            HIT_MEAN = new float[]{0.53385729f, -59.236504f,  -59.200584f,  50.136387f, 372.057922f};
            HIT_STD = new float[]{0.40085429f, 51.385536f, 51.840462f, 31.498201f, 111.50029f};
            STATE_MEAN = new float[]{-39.446106f, 0.17583229f, -0.18047817f, 0.0014163271f, -0.082320645f};
            STATE_STD = new float[]{33.733425f, 17.226780f, 0.14071095f, 0.072449364f, 0.72273886f};  
        }
        else{
           Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Name of model file does not include inbending or outbending"); 
        }
        
        System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
        System.setProperty("ai.djl.pytorch.num_threads", "1");
        System.setProperty("ai.djl.pytorch.graph_optimizer", "false");                
        try {
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

