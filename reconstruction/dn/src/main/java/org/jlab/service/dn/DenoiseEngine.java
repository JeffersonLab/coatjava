package org.jlab.service.dn;

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
import java.io.IOException;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.system.ClasUtilsFile;

public class DenoiseEngine extends ReconstructionEngine {
  
    final static int LAYERS=36;
    final static int WIRES=112;

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
        } catch (IOException ex) {
            System.getLogger(DenoiseEngine.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            return false;
        } catch (ModelNotFoundException ex) {
            System.getLogger(DenoiseEngine.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            return false;
        }
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        if (event.hasBank("DC::tot")) {
            try {
                ZooModel<float[][], float[][]> model = criteria.loadModel();
                Predictor<float[][], float[][]> predictor = model.newPredictor();
                float[][][] input = getSectors(event.getBank("DC::tot"));
                for (int sector=0; sector<6; ++sector) {
                    float[][] output = predictor.predict(input[sector]);
                    show(output);
                }
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }

    private static float[][][] getSectors(DataBank bank) {
        float[][] sector = getAlmostStraightSlightlyBendingTrack();
        float[][][] ret = new float[6][LAYERS][WIRES];
        for (int s=0; s<6; ++s)
            for (int l=0; l<LAYERS; ++l)
                for (int w=0; w<WIRES; ++w)
                    ret[s][l][w] = sector[l][w];
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
