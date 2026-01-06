package org.jlab.service.ai;

import ai.djl.MalformedModelException;
import java.nio.file.Paths;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import ai.djl.translate.Batchifier;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.translate.TranslateException;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.system.ClasUtilsFile;

public class DCDenoiseEngine extends ReconstructionEngine {

    final static String[] BANK_NAMES = {"DC::tot","DC::tdc"};
    final static String CONF_MODEL_FILE = "modelFile";
    final static String CONF_THRESHOLD = "threshold";  
    final static String CONF_THREADS = "threads";

    final static int LAYERS = 36;
    final static int WIRES = 112;
    final static int SECTORS= 6;

    String modelFile = "cnn_autoenc_allSectors_2b_48f_4x6k.pt";
    float threshold = 0.025f;
    Criteria<float[][][], float[][][]> criteria;
    ZooModel<float[][][], float[][][]> model;
    PredictorPool predictors;

    public DCDenoiseEngine() {
        super("DenoiseEngine","lleztlab","1.0");
    }

    @Override
    public boolean init() {
        System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
        System.setProperty("ai.djl.pytorch.num_threads", "1");
        System.setProperty("ai.djl.pytorch.graph_optimizer", "false");
        if (getEngineConfigString(CONF_THRESHOLD) != null)
            threshold = Float.parseFloat(getEngineConfigString(CONF_THRESHOLD));
        if (getEngineConfigString(CONF_MODEL_FILE) != null)
            modelFile = getEngineConfigString(CONF_MODEL_FILE);

        try {
            String modelPath = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/data/nnet/dn/" + modelFile);

            criteria = Criteria.builder()
                .setTypes(float[][][].class, float[][][].class)
                .optModelPath(Paths.get(modelPath))
                .optEngine("PyTorch")
                .optTranslator(DCDenoiseEngine.getBatchTranslator())
                .optProgress(new ProgressBar())
                .build();

            model = criteria.loadModel();

            int threads = Integer.parseInt(getEngineConfigString(CONF_THREADS,"64"));
            predictors = new PredictorPool(threads, model);
            return true;
        } catch (NullPointerException | MalformedModelException | IOException | ModelNotFoundException ex) {
            Logger.getLogger(DCDenoiseEngine.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        for (String bankName : BANK_NAMES) {
            if (!event.hasBank(bankName)) continue;

            DataBank bank = event.getBank(bankName);
            try {
                // Build batch for 6 sectors
                float[][][] batchInput = new float[SECTORS][LAYERS][WIRES];
                boolean anySectorPresent = false;
                int rows = bank.rows();
                for (int r=0; r<rows; r++) {
                    int sector = bank.getByte(0,r); // 1..6
                    if (sector < 1 || sector > SECTORS) continue;
                    int layer = bank.getByte(1,r);
                    int wire = bank.getShort(2,r);
                    byte order = bank.getByte(3,r);
                    if ((order==0)||(order==10)||(order==40)||(order==50)||(order==60)||(order==70)||(order==80)||(order==90)) {
                        batchInput[sector-1][layer-1][wire-1]=1.0f;
                        anySectorPresent = true;
                    }
                }

                if (!anySectorPresent) continue;

                Predictor<float[][][], float[][][]> predictor = predictors.take();
                float[][][] batchOutput;
                try {
                    batchOutput = predictor.predict(batchInput);
                } finally {
                    predictors.put(predictor);
                }

                update(bank, threshold, batchOutput);

                event.removeBank(bankName);
                event.appendBank(bank);
            } catch (TranslateException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            break;
        }
        return true;
    }

    // -------- Translator for batch --------
    public static Translator<float[][][], float[][][]> getBatchTranslator() {
        return new Translator<float[][][], float[][][]>() {
            @Override
            public NDList processInput(TranslatorContext ctx, float[][][] input) {
                int batch = input.length;
                int height = input[0].length;
                int width = input[0][0].length;
                float[] flat = new float[batch*height*width];
                int pos=0;
                for (int b=0; b<batch; b++)
                    for (int h=0; h<height; h++) {
                        System.arraycopy(input[b][h],0,flat,pos,width);
                        pos+=width;
                    }
                NDManager manager = ctx.getNDManager();
                NDArray x = manager.create(flat, new Shape(batch,1,height,width));
                return new NDList(x);
            }

            @Override
            public float[][][] processOutput(TranslatorContext ctx, NDList list) {
                NDArray result = list.get(0);
                long[] shape = result.getShape().getShape();
                int batch = (int)shape[0];
                int height, width;
                if (shape.length==4 && shape[1]==1) {
                    height=(int)shape[2]; width=(int)shape[3];
                    result = result.squeeze(1);
                } else if (shape.length==3) {
                    height=(int)shape[1]; width=(int)shape[2];
                } else throw new IllegalStateException("Unexpected output shape: "+java.util.Arrays.toString(shape));
                float[] flat = result.toFloatArray();
                float[][][] out = new float[batch][height][width];
                int pos=0;
                for (int b=0;b<batch;b++)
                    for (int h=0;h<height;h++) {
                        System.arraycopy(flat,pos,out[b][h],0,width);
                        pos+=width;
                    }
                return out;
            }

            @Override
            public Batchifier getBatchifier() { return null; }
        };
    }

    // -------- Update single sector in bank --------
    static void update(DataBank b, float threshold, float[][][] data) {
        for (int row=0; row<b.rows(); row++) {
            int sector = b.getByte(0,row)-1;
            int layer=b.getByte(1,row)-1;
            int wire=b.getShort(2,row)-1;
            int order = b.getByte(3,row);
            if (data[sector][layer][wire]>=threshold) {
                if(order==0 || order == 40) b.setByte(3,row,(byte)0);
                else if(order==50 || order == 60) b.setByte(3,row,(byte)50);
                else if(order==10 || order == 70) b.setByte(3,row,(byte)10);
                else if(order==80 || order == 90) b.setByte(3,row,(byte)80);
            }
            else{
                if(order==0 || order == 40) b.setByte(3,row,(byte)40);
                else if(order==50 || order == 60) b.setByte(3,row,(byte)60);
                else if(order==10 || order == 70) b.setByte(3,row,(byte)70);
                else if(order==80 || order == 90) b.setByte(3,row,(byte)90);
            }
        }
    }
}
