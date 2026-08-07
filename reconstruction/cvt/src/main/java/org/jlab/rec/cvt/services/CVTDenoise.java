package org.jlab.rec.cvt.services;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.system.ClasUtilsFile;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Arc3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.rec.cvt.Geometry;
import org.jlab.clas.swimtools.Swim;
import org.jlab.rec.cvt.Constants;
import org.jlab.service.ai.PredictorPool;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.rec.cvt.bmt.BMTType;

/* -----------------------------------------------
   Input class
   ----------------------------------------------- */
class CVTInput {

    float[][] data;  // [450][9]
    float[] mask;    // [450]

    public CVTInput(float[][] data, float[] mask) {
        this.data = data;
        this.mask = mask;
    }
}

/* -----------------------------------------------
   DJL Translator
   ----------------------------------------------- */
class CVTTranslator implements Translator<CVTInput, float[][]> {

    @Override
    public NDList processInput(TranslatorContext ctx, CVTInput input) {

        NDManager manager = ctx.getNDManager();

        NDArray x = manager.create(input.data).reshape(1, 450, 9);
        NDArray mask = manager.create(input.mask).reshape(1, 450);

        return new NDList(x, mask);
    }

    @Override
    public float[][] processOutput(TranslatorContext ctx, NDList list) {

        NDArray output = list.singletonOrThrow(); // shape: (1, 450, C) or (1, 450)
        output = output.squeeze(0); // remove batch dim -> (450, C)

        float[] flat = output.toFloatArray();
        long[] shape = output.getShape().getShape();

        int dim0 = (int) shape[0];
        int dim1 = shape.length > 1 ? (int) shape[1] : 1;

        float[][] result = new float[dim0][dim1];

        for(int i=0;i<dim0;i++){
            for(int j = 0; j < dim1; j++){
                result[i][j] = flat[i * dim1 + j];
            }
        }

        return result;
    }

    @Override
    public Batchifier getBatchifier() {
        return null;
    }
}

/* -----------------------------------------------
   Hit class
   ----------------------------------------------- */
class LocalHit {
    int layer; // 1 to 6 for SVT, and 7 to 12 for BMT
    int sector;
    int strip;
    int index; // Index of hits in a strip, in case that there are multiple hits in a strip
    
    public LocalHit(int layer, int sector, int strip, int index) {
        this.layer = layer;
        this.sector = sector;
        this.strip = strip;
        this.index = index;
    }
    
    public int getLayer(){
        return layer;
    }
    
    public int getSector(){
        return sector;
    }

    public int getStrip(){
        return strip;
    }

    public int getIndex(){
        return index;
    }
}

/* -----------------------------------------------
   Denoise engine
   ----------------------------------------------- */
public class CVTDenoise {
    private static final int NSECTIONS = 3; // Detectors are separted into 3 sections, and a seperate model for each section
    private static final int NLAYERS = 12; // Layers from 1 to 12
    private static final int MAX_HITS = 450; // Maximum of hits for each section
    private static final int NFEATURES = 9; // Number of features for input of models
        
    // Model files and thresholds for the three models; They could be set in yaml files     
    String[] modelFiles = {"classifier_torchscript_sector1_noCSWeight_weightInTraining.pt", "classifier_torchscript_sector2_noCSWeight_weightInTraining.pt", "classifier_torchscript_sector3_noCSWeight_weightInTraining.pt"};
    float[] thresholds = {0.1f, 0.1f, 0.1f}; // To be determined
        
    // For each feature of each layer, minimum and maximum for scaling features
    private float[][] minVals = new float[NFEATURES][NLAYERS];
    private float[][] maxVals = new float[NFEATURES][NLAYERS];  
    
    private Criteria<CVTInput, float[][]>[] criterias;
    private ZooModel<CVTInput, float[][]>[] model;
    private PredictorPool[] predictors;       
    
    public CVTDenoise(String[] modelFiles, float[] thresholds) {
        this.modelFiles = modelFiles;
        this.thresholds = thresholds;
        init();
    }
    
    private boolean init() {        
        criterias = new Criteria[NSECTIONS];
        model = new ZooModel[NSECTIONS];
        predictors = new PredictorPool[NSECTIONS];             
                
        initScaling(); // Initial minVals and maxVals
        
        System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
        System.setProperty("ai.djl.pytorch.num_threads", "1");
        System.setProperty("ai.djl.pytorch.graph_optimizer", "false");
        
        
        // Load models and set predictor pools
        for(int i = 0; i < NSECTIONS; i++){
            
            try {
                String modelPath = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/data/nnet/cvtdn/" + modelFiles[i]);
                
                CVTTranslator translator = new CVTTranslator();                
                criterias[i] = Criteria.builder()
                    .setTypes(CVTInput.class, float[][].class)
                    .optModelPath(Paths.get(modelPath))
                    .optEngine("PyTorch")
                    .optTranslator(translator)
                    .optProgress(new ProgressBar())
                    .build();

                model[i] = criterias[i].loadModel();
                predictors[i] = new PredictorPool(16, model[i]);
            } catch (NullPointerException | MalformedModelException | IOException | ModelNotFoundException ex) {
                Logger.getLogger(CVTEngine.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
        } 
        
        return true;
    }
    
    public void predict(List<ArrayList<Hit>> cvtHits) {
        List<Hit> bst_hits = cvtHits.get(0);
        List<Hit> bmt_hits = cvtHits.get(1);
        
        if (bst_hits.isEmpty() && bmt_hits.isEmpty()) return;
        
        float[][][] x = new float[NSECTIONS][MAX_HITS][NFEATURES];
        float[][] mask = new float[NSECTIONS][MAX_HITS];        

        int[] nHits = {0, 0, 0};
        
        Map<Integer, LocalHit>[] maps = new HashMap[NSECTIONS];
        for (int i = 0; i < NSECTIONS; i++) {
           maps[i] = new HashMap<>();
        }
        
        // Read BST bank and set input for models
        int[][][] nHitsLayerSectorStrip_BST = new int[6][18][256]; // Number of hits in a strip
        for(int i = 0; i < bst_hits.size(); i++){
            Hit hit = bst_hits.get(i);
            int sector = hit.getSector();
            int layer = hit.getLayer();
            int strip = hit.getStrip().getStrip();
            
            LocalHit localHit = new LocalHit(layer, sector, strip, nHitsLayerSectorStrip_BST[layer-1][sector-1][strip-1]++);
            
            Line3D line = Geometry.getInstance().getSVT().getStrip(layer, sector, strip);                             
            float[] features = {strip, (float)(line.origin().x()/10.), (float)(line.end().x()/10.), (float)(line.origin().y()/10.), 
                (float)(line.end().y()/10.), (float)(line.origin().z()/10.), (float)(line.end().z()/10.), sector, layer}; // unit conversion from mm to cm for end points               

            for(int f = 0; f < NFEATURES - 1; f++){
                float min = minVals[f][layer-1];
                float max = maxVals[f][layer-1];
                features[f] = (features[f] - min) / (max-min);
            }                
            features[NFEATURES-1] = (features[NFEATURES-1] - 1) / (NLAYERS - 1);                

            List<Integer> sectionList = getSectionList(layer, sector);
            for(int section : sectionList){
                for(int f = 0; f < NFEATURES; f++){
                    x[section - 1][nHits[section - 1]][f] = features[f];
                }

                mask[section - 1][nHits[section - 1]] = 1.0f;

                maps[section - 1].put(nHits[section - 1], localHit);

                nHits[section - 1]++;
                if(nHits[section - 1] == MAX_HITS) {
                    Logger.getLogger(CVTEngine.class.getName()).log(Level.SEVERE, "Number of hits is over maximum limit!");
                    return;
                }
            } 
        }
        
        // Read BMT bank and set input for models
        int[][][] nHitsLayerSectorStrip_BMT = new int[6][3][1152]; // Number of hits in a strip
        for(int i = 0; i < bmt_hits.size(); i++){
            Hit hit = bmt_hits.get(i);
            int sector = hit.getSector();
            int layer = hit.getLayer();
            int strip = hit.getStrip().getStrip();
            
            LocalHit localHit = new LocalHit(layer+6, sector, strip, nHitsLayerSectorStrip_BMT[layer-1][sector-1][strip-1]++);
            
            Point3D originPoint, endPoint;                            
            if(hit.getType() == BMTType.Z) {
                Line3D line = hit.getStrip().getLine();
                originPoint = line.origin();
                endPoint = line.origin();
            }
            else {
                Arc3D arcLine = hit.getStrip().getArc();
                originPoint = arcLine.origin();
                endPoint = arcLine.end();
            }

            layer += 6;
            float[] features = {strip, (float)(originPoint.x()/10.), (float)(endPoint.x()/10.), (float)(originPoint.y()/10.), 
                (float)(endPoint.y()/10.), (float)(originPoint.z()/10.), (float)(endPoint.z()/10.), sector, layer}; // unit conversion from mm to cm for end points  

            for(int f = 0; f < NFEATURES - 1; f++){
                float min = minVals[f][layer-1];
                float max = maxVals[f][layer-1];
                features[f] = (features[f] - min) / (max-min);
            }                
            features[NFEATURES-1] = (features[NFEATURES-1] - 1) / (NLAYERS - 1);

            List<Integer> sectionList = getSectionList(layer, sector);
            for(int section : sectionList){
                for(int f = 0; f < NFEATURES; f++){
                    x[section - 1][nHits[section - 1]][f] = features[f];
                }                    

                mask[section - 1][nHits[section - 1]] = 1.0f;

                maps[section - 1].put(nHits[section - 1], localHit);

                nHits[section - 1]++;
                if(nHits[section - 1] == MAX_HITS) {
                    Logger.getLogger(CVTEngine.class.getName()).log(Level.SEVERE, "Number of hits is over maximum limit!");
                    return;
                }
            }             
        }        
                        
        int maxIndex_BST = Integer.MIN_VALUE;
        for (int i = 0; i < nHitsLayerSectorStrip_BST.length; i++) {
            for (int j = 0; j < nHitsLayerSectorStrip_BST[i].length; j++) {
                for (int k = 0; k < nHitsLayerSectorStrip_BST[i][j].length; k++) {
                    if (nHitsLayerSectorStrip_BST[i][j][k] > maxIndex_BST) {
                        maxIndex_BST = nHitsLayerSectorStrip_BST[i][j][k];
                    }
                }
            }
        }        
        int maxIndex_BMT = Integer.MIN_VALUE;
        for (int i = 0; i < nHitsLayerSectorStrip_BMT.length; i++) {
            for (int j = 0; j < nHitsLayerSectorStrip_BMT[i].length; j++) {
                for (int k = 0; k < nHitsLayerSectorStrip_BMT[i][j].length; k++) {
                    if (nHitsLayerSectorStrip_BMT[i][j][k] > maxIndex_BMT) {
                        maxIndex_BMT = nHitsLayerSectorStrip_BMT[i][j][k];
                    }
                }
            }
        }        
        int maxIndex = Math.max(maxIndex_BST, maxIndex_BMT);
        
        // AI-based hit classification.
        // Hit status is encoded in a single byte:
        // bits [0–2] : noise flags for sections 1–3 (1 = noise, 0 = signal)
        // bits [3–5] : hit presence for sections 1–3 (1 = hit present, 0 = no hit)
        byte[][][][] statuses = new byte[NLAYERS][18][1152][maxIndex+1];        
        for(int s = 0; s < NSECTIONS; s++){
            CVTInput input = new CVTInput(x[s],mask[s]);
            try{
                Predictor<CVTInput, float[][]> predictor = predictors[s].take();
                
                try{
                    float[][] preds = predictor.predict(input);
                    for(int i = 0; i < nHits[s]; i++){                                                
                        LocalHit hit = maps[s].get(i);
                        statuses[hit.getLayer()-1][hit.getSector()-1][hit.getStrip() - 1][hit.getIndex()] |= (byte) (1 << (s+3));  
                        if(preds[i][0] < thresholds[s]) statuses[hit.getLayer()-1][hit.getSector()-1][hit.getStrip() - 1][hit.getIndex()] |= (byte) (1 << s);                      
                    }
                } finally {
                    predictors[s].put(predictor);
                }
                                                
            } catch (TranslateException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        
        // Update BST and BMT hits to record predictions
        updateHits(bst_hits, bmt_hits, statuses);      
    }

    //  Scaling per layer
    private void initScaling(){

        for(int l=0;l<12;l++) minVals[0][l]=1f;

        int[] stripMax={256,256,256,256,256,256,896,640,640,1024,768,1152};
        for(int l=0;l<12;l++) maxVals[0][l]=stripMax[l];

        float[] xyMin={-7,-8,-10,-10,-15,-15,-15,-18,-18,-23,-23,-23};
        float[] xyMax={7,8,10,10,15,15,15,18,18,23,23,23};

        for(int f=1;f<=4;f++){
            for(int l=0;l<12;l++){
                minVals[f][l]=xyMin[l];
                maxVals[f][l]=xyMax[l];
            }
        }

        float[] zMin={-25,-25,-22,-22,-18,-18,-18,-21,-21,-21,-21,-21};
        float[] zMax={25,25,22,22,18,18,21,21,21,25,25,25};

        for(int f=5;f<=6;f++){
            for(int l=0;l<12;l++){
                minVals[f][l]=zMin[l];
                maxVals[f][l]=zMax[l];
            }
        }

        float[] sectorMax={11,11,15,15,19,19,3,3,3,3,3,3};

        for(int l=0;l<12;l++){
            minVals[7][l]=1f;
            maxVals[7][l]=sectorMax[l];
        }

        /*
        float[] timeMin={0,0,0,0,0,0,4,4,4,4,4,4};
        float[] timeMax={511,511,511,511,511,511,436,436,436,436,436,436};

        for(int l=0;l<12;l++){
            minVals[8][l]=timeMin[l];
            maxVals[8][l]=timeMax[l];
        }
        */
    }

    // Set sections based on layer and sector of hits
    // Some SVT hits are shared by sections1&2 or sections2&3
    private List<Integer> getSectionList(int layer, int sector) {
        List<Integer> sectionList = new ArrayList<>();

        if (layer >= 1 && layer <= 2) {
            if (sector >= 1 && sector <= 4) sectionList.add(1);
            if (sector >= 4 && sector <= 8) sectionList.add(2);
            if (sector >= 8 && sector <= 10) sectionList.add(3);
        }
        else if (layer >= 3 && layer <= 4) {
            if (sector >= 1 && sector <= 6) sectionList.add(1);
            if (sector >= 6 && sector <= 10) sectionList.add(2);
            if (sector >= 10 && sector <= 14) sectionList.add(3);
        }
        else if (layer >= 5 && layer <= 6) {
            if (sector >= 1 && sector <= 7) sectionList.add(1);
            if (sector >= 7 && sector <= 13) sectionList.add(2);
            if (sector >= 14 && sector <= 18) sectionList.add(3);
        }
        else {
            if (sector == 1) sectionList.add(1);
            if (sector == 2) sectionList.add(2);
            if (sector == 3) sectionList.add(3);
        }

        return sectionList;
    }
    
    // -------- Update BST & BMT hits --------
    private void updateHits(List<Hit> bst_hits, List<Hit> bmt_hits, byte[][][][] statuses) {
        int[][][] nHitsLayerSectorStrip_BST = new int[6][18][256]; // Number of hits in a strip
        for (int i=0; i<bst_hits.size(); i++) {
            Hit hit = bst_hits.get(i);
            int sector = hit.getSector();
            int layer = hit.getLayer();
            int strip = hit.getStrip().getStrip();
            
            byte status = statuses[layer-1][sector-1][strip-1][nHitsLayerSectorStrip_BST[layer-1][sector-1][strip-1]];            
            hit.setDenoiseStatus(status);
            nHitsLayerSectorStrip_BST[layer-1][sector-1][strip-1]++;                        
        }
        
        int[][][] nHitsLayerSectorStrip_BMT = new int[6][3][1152]; // Number of hits in a strip
        for (int i=0; i<bmt_hits.size(); i++) {
            Hit hit = bmt_hits.get(i);
            int sector = hit.getSector();
            int layer = hit.getLayer();
            int strip = hit.getStrip().getStrip();
            
            byte status = statuses[layer+5][sector-1][strip-1][nHitsLayerSectorStrip_BMT[layer-1][sector-1][strip-1]]; // For BMT, Layer from 1 to 6 in bank, while layer from 7 to 12 in model
            hit.setDenoiseStatus(status);
            nHitsLayerSectorStrip_BMT[layer-1][sector-1][strip-1]++;
        }
    }           
}
