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
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.translate.Batchifier;
import ai.djl.translate.TranslateException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.system.ClasUtilsFile;

public class DCDenoiseEngine extends ReconstructionEngine {

    final static String[] BANK_NAMES = {"DC::tot","DC::tdc"};
    final static String CONF_THRESHOLD = "threshold";
    final static String CONF_THREADS = "threads";
    final static int LAYERS = 36;
    final static int WIRES = 112;

    float threshold = 0.025f;
    Criteria<float[][],float[][]> criteria;
    ZooModel<float[][], float[][]> model;
    PredictorPool predictors;
    
    public static class PredictorPool {
        final BlockingQueue<Predictor> pool;
        public PredictorPool(int size, ZooModel model) {
            pool = new ArrayBlockingQueue<>(size);
            for (int i=0; i<size; i++) pool.add(model.newPredictor());
        }
        public Predictor get() throws InterruptedException {
            return pool.poll();
        }
        public void put(Predictor p) {
            if (p != null) pool.offer(p);
        }
    }

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
        try {
            criteria = Criteria.builder()
                .setTypes(float[][].class, float[][].class)
                .optModelPath(Paths.get(ClasUtilsFile.getResourceDir("CLAS12DIR","etc/data/nnet/dn/cnn_autoenc_sector1_nBlocks2.pt")))
                .optEngine("PyTorch")
                .optTranslator(DCDenoiseEngine.getTranslator())
                .optProgress(new ProgressBar())
                .build();
            model = criteria.loadModel();
            int threads = Integer.parseInt(getEngineConfigString(CONF_THREADS,"64"));
            predictors = new PredictorPool(threads, model);
            return true;
        } catch (NullPointerException | MalformedModelException | IOException | ModelNotFoundException ex) {
            System.getLogger(DCDenoiseEngine.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            return false;
        }
    }

    public static void main(String args[]){
        DCDenoiseEngine dn = new DCDenoiseEngine();
        dn.init();
        for (int i=0; i<10000; i++) {
            dn.processFakeEvent();
        }
    }
    
    @Override
    public boolean processDataEvent(DataEvent event) {

        //if (true) return processFakeEvent();
       
        for (int i=0; i<BANK_NAMES.length; i++){
            if (event.hasBank(BANK_NAMES[i])) {
                DataBank bank = event.getBank(BANK_NAMES[i]);
                try {
                    // WARNING:  Predictor is *not* thread safe.
                    Predictor<float[][], float[][]> predictor = predictors.get();
                    for (int sector=0; sector<6; sector++) {
                        float[][] input = DCDenoiseEngine.read(bank, sector+1);
                        float[][] output = predictor.predict(input);
                        //System.out.println("IN:");show(input);
                        //System.out.println("OUT:");show(output);
                        update(bank, threshold, output, sector);
                    }
                    predictors.put(predictor);
                    event.removeBank(BANK_NAMES[i]);
                    event.appendBank(bank);
                }
                catch (TranslateException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
        }
        return true;
    }

    boolean processFakeEvent() {
        try {
            Predictor<float[][], float[][]> predictor = model.newPredictor();
            float[][] input = getAlmostStraightSlightlyBendingTrack();
            float[][] output = predictor.predict(input);
            //System.out.println("IN:");show(input);
            //System.out.println("OUT:");show(output);
        }
        catch (TranslateException e) {
            throw new RuntimeException(e);
        }
        return true;
    }
    
    /**
     * Reject sub-threshold hits by modifying the bank's order variable.
     * WARNING:  This is not a full implementation of OrderType enum and
     * all its names, but for now a copy of the subset in C++ DC denoising, see:
     * https://code.jlab.org/hallb/clas12/coatjava/denoising/-/blob/main/denoising/code/drift.cc?ref_type=heads#L162-198 
     */
    static void update(DataBank b, float threshold, float[][] data, int sector) {
        //System.out.println("IN:");b.show();
        for (int row=0; row<b.rows(); row++) {
            if (b.getByte(0,row)-1 != sector) continue;
            if (data[b.getByte(1,row)-1][b.getShort(2,row)-1] < threshold) {                
                if(b.getByte(3,row) == 0) b.setByte(3, row, (byte)(60));
                if(b.getByte(3,row) == 10) b.setByte(3, row, (byte)(90));
            }
        }
        //System.out.println("OUT:");b.show();
    }

    /**
     * Get one-sector data with weights set to 0/1.
     */
    static float[][] read(DataBank bank, int sector) {
        float[][] data = new float[LAYERS][WIRES];
        for (int i=0; i<bank.rows(); ++i) {
            if (bank.getByte(0,i) == sector) {
                byte o = bank.getByte(3,i);
                if (0==o || 10==o)
                    // got a hit, set weight to one:
                    data[bank.getByte(1,i)-1][bank.getShort(2,i)-1] = 1.0f;
            }
        }
        return data;
    }

    /**
     * Print all hits for one sector.
     */
    static void show(float[][] data) {
        System.out.println("Shape: [" + data.length + "," + data[0].length + "]");
        for (int i = 0; i < LAYERS; i++) {
            for (int j = 0; j < WIRES; j++)
                System.out.printf("%.3f ", data[i][j]);
            System.out.println();
        }
    }

    /**
     * @return a dummy sector/track 
     */
    static float[][] getAlmostStraightSlightlyBendingTrack() {
        float[][] data = new float[LAYERS][WIRES];
        for (int y = 0; y < LAYERS; y++) {
            int x = 50 + (y / 10);
            data[y][x] = 1.0f;
        }
        return data;
    }

    public static Translator<float[][],float[][]> getTranslator() {
        return new Translator<float[][],float[][]>() {
            @Override
            public NDList processInput(TranslatorContext ctx, float[][] input) throws Exception {
                NDManager manager = ctx.getNDManager();
                int height = input.length;
                int width = input[0].length;
                float[] flat = new float[height * width];
                for (int i = 0; i < height; i++) {
                    System.arraycopy(input[i], 0, flat, i * width, width);
                }
                NDArray x = manager.create(flat, new Shape(height, width));
                // Add batch and channel dims -> [1,1,36,112]
                x = x.expandDims(0).expandDims(0);
                return new NDList(x);
            }
            @Override
            public float[][] processOutput(TranslatorContext ctx, NDList list) throws Exception {
                NDArray result = list.get(0);
                // Remove batch and channel dims -> [36,112]
                result = result.squeeze();
                // Convert to 1D float array
                float[] flat = result.toFloatArray();
                // Reshape manually into 2D array
                long[] shape = result.getShape().getShape();
                int height = (int) shape[0];
                int width = (int) shape[1];
                float[][] output2d = new float[height][width];
                for (int i = 0; i < height; i++) {
                    System.arraycopy(flat, i * width, output2d[i], 0, width);
                }
                return output2d;
            }
            @Override
            public Batchifier getBatchifier() {
                return null; // no batching
            }
        };
    }

}
