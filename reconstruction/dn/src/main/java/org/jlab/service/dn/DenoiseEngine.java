package org.jlab.service.dn;

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

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.system.ClasUtilsFile;

public class DenoiseEngine extends ReconstructionEngine {

    final float threshold = 0.1f;
    
    final static String BANK_NAME = "DC::tot";
    final static boolean SIMULATION_MODE = true;
    final static int LAYERS = 36;
    final static int WIRES = 112;

    Criteria<float[][],float[][]> criteria;

    public DenoiseEngine() {
        super("DenoiseEngine","lleztlab","1.0");
    }

    @Override
    public boolean init() {
        try {
            criteria = Criteria.builder()
                .setTypes(float[][].class, float[][].class)
                .optModelPath(Paths.get(ClasUtilsFile.getResourceDir("etc/nnet/dn/cnn_autoenc_0f_112.pt")))
                .optEngine("PyTorch")
                .optTranslator(DenoiseEngine.getTranslator())
                .optProgress(new ProgressBar())
                .build();
            return criteria.isDownloaded();
        } catch (IOException | ModelNotFoundException ex) {
            System.getLogger(DenoiseEngine.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            return false;
        }
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        if (event.hasBank(BANK_NAME)) {
            DataBank bank = event.getBank(BANK_NAME);
            event.removeBank(BANK_NAME);
            try {
                ZooModel<float[][], float[][]> model = criteria.loadModel();
                Predictor<float[][], float[][]> predictor = model.newPredictor();
                for (int sector=0; sector<6; ++sector) {
                    float[][] input = DenoiseEngine.getSector(bank, sector+1);
                    float[][] output = predictor.predict(input);
                    show(input);
                    show(output);
                    update(bank, threshold, output, sector);
                }
                event.appendBank(bank);
            }
            catch (MalformedModelException | ModelNotFoundException | TranslateException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    private static void update(DataBank b, float threshold, float[][] data, int sector) {
        for (int row=0; row<b.rows(); row++) {
            byte s = b.getByte("sector",row);
            byte l = b.getByte("layer", row);
            short c = b.getShort("component", row);
            byte o = b.getByte("order", row);
            if (s == sector && data[l][c] < threshold)
                b.setByte("order", row, (byte)(o+10));
        }
    }

    private static float[][] getSector(DataBank bank, int sector) {
        if (SIMULATION_MODE) return getAlmostStraightSlightlyBendingTrack();
        float[][] ret = new float[LAYERS][WIRES];
        for (int i=0; i<bank.rows(); ++i) {
            if (bank.getByte("sector",i) == sector) {
                byte l = bank.getByte("layer",i);
                short c = bank.getShort("component",i);
                byte o = bank.getByte("order",i);
                if (0==o || 10==o)
                    ret[l][c] = 1.0f;
            }
        }
        return ret;
    }

    public static void show(float[][] data) {
        System.out.println("Output shape: [" + data.length + "," + data[0].length + "]");
        System.out.println("Output values:");
        for (int i = 0; i < LAYERS; i++) {
            for (int j = 0; j < WIRES; j++)
                System.out.printf("%.3f ", data[i][j]);
            System.out.println();
        }
    }

    public static float[][] getAlmostStraightSlightlyBendingTrack() {
        float[][] ret = new float[LAYERS][WIRES];
        for (int y = 0; y < LAYERS; y++) {
            int x = 50 + (y / 10);
            ret[y][x] = 1.0f;
        }
        return ret;
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
