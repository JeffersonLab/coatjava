package org.jlab.service.ai;

import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.system.ClasUtilsFile;
import org.jlab.rec.ai.dcCluster.DCCluster;
import org.jlab.rec.ai.dcCluster.DCClusterCombo;

public class DCClsComboEngine extends ReconstructionEngine {
    final String inputBank = "HitBasedTrkg::Clusters";
    final String outputBank = "ai::tracks";
    
    final static String CONF_THREADS = "threads";
    
    final static String LIMIT_CLSCOMBOS_PERSECTOR = "limitClsCombosPerSector";
    int limitClsCombosPerSector = (int)1e5;
    
    final static String CONF_MODEL_FILE_6CLS = "modelFile6Cls";
    final static String CONF_THRESHOLD_6CLS = "threshold6Cls";  
    String modelFile6Cls = "mlp_64h_4l_6cls.pt";
    float threshold6Cls = 0.95f;
    Criteria<float[][], float[]> criteria6Cls;
    ZooModel<float[][], float[]> model6Cls;
    PredictorPool predictors6Cls;
    
    final static String CONF_MODEL_FILE_5CLS = "modelFile5Cls";
    final static String CONF_THRESHOLD_5CLS = "threshold5Cls";  
    String modelFile5Cls = "mlp_64h_3l_5cls.pt";
    float threshold5Cls = 0.05f;
    Criteria<float[][], float[]> criteria5Cls;
    ZooModel<float[][], float[]> model5Cls;
    PredictorPool predictors5Cls;    
            
    final static int SUPERLAYERS = 6;    
    
    private static final Logger LOGGER = Logger.getLogger(DCClsComboEngine.class.getName());
    private boolean warned6ClsLimit = false;
    private boolean warned5ClsLimit = false;
    
    public DCClsComboEngine() {
        super("DCClsComboEngine","tongtong","1.0");
    }

    @Override
    public void detectorChanged(int run){}

    @Override
    public boolean init() {
        System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
        System.setProperty("ai.djl.pytorch.num_threads", "1");
        System.setProperty("ai.djl.pytorch.graph_optimizer", "false");
        
        int threads = Integer.parseInt(getEngineConfigString(CONF_THREADS,"64"));
        
        if (getEngineConfigString(LIMIT_CLSCOMBOS_PERSECTOR) != null)
            limitClsCombosPerSector = Integer.parseInt(getEngineConfigString(LIMIT_CLSCOMBOS_PERSECTOR));
        
        if (getEngineConfigString(CONF_THRESHOLD_6CLS) != null)
            threshold6Cls = Float.parseFloat(getEngineConfigString(CONF_THRESHOLD_6CLS));
        if (getEngineConfigString(CONF_MODEL_FILE_6CLS) != null)
            modelFile6Cls = getEngineConfigString(CONF_MODEL_FILE_6CLS);

        try {
            String modelPath = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/data/nnet/clsCombo/" + modelFile6Cls);

            criteria6Cls = Criteria.builder()
                .setTypes(float[][].class, float[].class)
                .optModelPath(Paths.get(modelPath))
                .optEngine("PyTorch")
                .optTranslator(DCClsComboEngine.getBatchTranslator6Cls())
                .optProgress(new ProgressBar())
                .build();

            model6Cls = criteria6Cls.loadModel();
            predictors6Cls = new PredictorPool(threads, model6Cls);
        } catch (NullPointerException | MalformedModelException | IOException | ModelNotFoundException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            return false;
        }
        
        if (getEngineConfigString(CONF_THRESHOLD_5CLS) != null)
            threshold5Cls = Float.parseFloat(getEngineConfigString(CONF_THRESHOLD_5CLS));
        if (getEngineConfigString(CONF_MODEL_FILE_5CLS) != null)
            modelFile5Cls = getEngineConfigString(CONF_MODEL_FILE_5CLS);

        try {
            String modelPath = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/data/nnet/clsCombo/" + modelFile5Cls);

            criteria5Cls = Criteria.builder()
                .setTypes(float[][].class, float[].class)
                .optModelPath(Paths.get(modelPath))
                .optEngine("PyTorch")
                .optTranslator(DCClsComboEngine.getBatchTranslator5Cls())
                .optProgress(new ProgressBar())
                .build();

            model5Cls = criteria5Cls.loadModel();
            predictors5Cls = new PredictorPool(threads, model5Cls);
        } catch (NullPointerException | MalformedModelException | IOException | ModelNotFoundException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            return false;
        }  
        
        return true;        
    }
    
    @Override
    public boolean processDataEventUser(DataEvent event) {
        
        //// 6-cluster combo prediction
        // load clusters from bank HitBasedTrkg::Clusters         
        if (!event.hasBank(inputBank)) return true;
        DataBank bank = event.getBank(inputBank);
        
        List<DCCluster> allClusterList = new ArrayList();
        Map<Integer, Map<Integer, List<DCCluster>>> map = new HashMap<>();
        int rows = bank.rows();
        for (int r = 0; r < rows; r++) {

            int id          = bank.getInt("id", r);
            int sector      = bank.getByte("sector", r);
            int superlayer  = bank.getByte("superlayer", r);
            float avgWire   = bank.getFloat("avgWire", r);
            float fitSlope  = bank.getFloat("fitSlope", r);

            DCCluster cls = new DCCluster(id, sector, superlayer, avgWire, fitSlope);
            allClusterList.add(cls);
            map.computeIfAbsent(sector, s -> new HashMap<>()).computeIfAbsent(superlayer, sl -> new ArrayList<>()).add(cls);
        }
                        
        // Make 6-cluster combos
        warned6ClsLimit = false;
        List<DCClusterCombo> all6ClsCombos = new ArrayList();
        for(Map<Integer, List<DCCluster>> map_sl_clsList : map.values()){
            if(map_sl_clsList.size() == 6){
                Map<Integer, List<DCCluster>> orderedMap = new TreeMap<>(map_sl_clsList); // Sorts entries by superlayer in ascending order
                
                List<DCClusterCombo> combos = new ArrayList();
                generate6ClsCombos(orderedMap, 1, new DCCluster[6], combos);
                
                all6ClsCombos.addAll(combos);
            }
        }
        
        // Batch prediction for 6-cluster combos
        float[] outputs6Cls = null; 
        List<DCClusterCombo> predicted6ClsCombos = new ArrayList();
        try {
            if(!all6ClsCombos.isEmpty()){
                List<float[]> batchInputs6Cls = new ArrayList<>();
                for (DCClusterCombo clsCombo: all6ClsCombos) {
                    float[] input = new float[12];
                    for (int i = 0; i < SUPERLAYERS; i++) {
                        input[i]   = clsCombo.get(i).getAvgWire();
                        input[i+6] = clsCombo.get(i).getFitSlope();
                    }
                    batchInputs6Cls.add(input);
                }            

                float[][] batchArray6Cls = new float[batchInputs6Cls.size()][];
                for (int i = 0; i < batchInputs6Cls.size(); i++) {
                    batchArray6Cls[i] = batchInputs6Cls.get(i);
                }

                Predictor<float[][], float[]> predictor6Cls = predictors6Cls.take();            
                try {
                    outputs6Cls = predictor6Cls.predict(batchArray6Cls);
                } finally {
                    predictors6Cls.put(predictor6Cls);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Batch prediction error in 6ClsComboClf", e);
        }
        
        // Filter 6-cluster combos based on threshold
        if(outputs6Cls!=null) {
            for(int i = 0; i < outputs6Cls.length; i++){
               if(outputs6Cls[i] > threshold6Cls) {
                   all6ClsCombos.get(i).setProbability(outputs6Cls[i]);
                   predicted6ClsCombos.add(all6ClsCombos.get(i));
               }
            }
        }
        
        // Separate predicted 6-cluster combos into sectors
        Map<Integer, List<DCClusterCombo>> map_sector_predicted6ClsComboList = new HashMap<>();
        for(DCClusterCombo clsCombo : predicted6ClsCombos){
            map_sector_predicted6ClsComboList.computeIfAbsent(clsCombo.get(0).getSector(), s -> new ArrayList<>()).add(clsCombo);
        }
        
        // Resolve overlapping cluster combos, and get final 6-cluster combos
        List<DCClusterCombo> final6ClsComboList = new ArrayList();
        for(List<DCClusterCombo> predicted6ClsComboList : map_sector_predicted6ClsComboList.values()){
            final6ClsComboList.addAll(resolveSharedClusterConflicts(predicted6ClsComboList));
        }
        
                
        //// 5-cluster combo prediction with remaining clusters
        // Remove clusters in final 6-cluster combos from orignal cluster list
        for (DCClusterCombo combo : final6ClsComboList) {
            for (DCCluster cls : combo) {
                allClusterList.removeIf(c -> c.getId() == cls.getId());
            }
        }
        
        // Separate clusters into sectors
        map.clear();
        for (DCCluster cls : allClusterList) {
            map.computeIfAbsent(cls.getSector(), s -> new HashMap<>()).computeIfAbsent(cls.getSuperlayer(), sl -> new ArrayList<>()).add(cls);
        }
        
        // Make 5-cluster combos
        warned5ClsLimit = false;
        List<DCClusterCombo> all5ClsCombos = new ArrayList<>();
        for (Map<Integer, List<DCCluster>> map_sl_clsList : map.values()) {
            if(map_sl_clsList.size() >= 5){
                Map<Integer, List<DCCluster>> orderedMap = new TreeMap<>(map_sl_clsList); // Sorts entries by superlayer in ascending order
                
                List<DCClusterCombo> combos = new ArrayList<>();
                generate5ClsCombos(orderedMap, combos);
                all5ClsCombos.addAll(combos);
            }
        }
        
        // Batch prediction for 5-cluster combos
        float[] outputs5Cls = null;
        List<DCClusterCombo> predicted5ClsCombos = new ArrayList<>();

        try {
            if (!all5ClsCombos.isEmpty()) {

                // Build input batch
                List<float[]> batchInputs5 = new ArrayList<>();
                for (DCClusterCombo combo : all5ClsCombos) {
                    float[] input = new float[11];

                    // 5 avgWires
                    for (int i = 0; i < 5; i++) {
                        input[i] = combo.get(i).getAvgWire();
                    }

                    // 5 fitSlopes
                    for (int i = 0; i < 5; i++) {
                        input[i + 5] = combo.get(i).getFitSlope();
                    }

                    // missing superlayer (1–6)
                    input[10] = combo.getMissingSL();

                    batchInputs5.add(input);
                }

                float[][] batchArray5 = batchInputs5.toArray(new float[0][]);

                Predictor<float[][], float[]> predictor5Cls = predictors5Cls.take();
                try {
                    outputs5Cls = predictor5Cls.predict(batchArray5);
                } finally {
                    predictors5Cls.put(predictor5Cls);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Batch prediction error in 5ClsComboClf", e);
        }

        // Filter by threshold
        if (outputs5Cls != null) {
            for (int i = 0; i < outputs5Cls.length; i++) {
                if (outputs5Cls[i] > threshold5Cls) {
                    all5ClsCombos.get(i).setProbability(outputs5Cls[i]);
                    predicted5ClsCombos.add(all5ClsCombos.get(i));
                }
            }
        }
        
        // Separate predicted 5-cluster combos into sectors
        Map<Integer, List<DCClusterCombo>> map_sector_predicted5ClsComboList = new HashMap<>();
        for (DCClusterCombo combo : predicted5ClsCombos) {
            int sector = combo.get(0).getSector();
            map_sector_predicted5ClsComboList.computeIfAbsent(sector, s -> new ArrayList<>()).add(combo);
        }
        
        // Resolve overlapping cluster combos, and get final 5-cluster combos
        List<DCClusterCombo> final5ClsComboList = new ArrayList<>();
        for (List<DCClusterCombo> lst : map_sector_predicted5ClsComboList.values()) {
            final5ClsComboList.addAll(resolveSharedClusterConflicts(lst));
        }
                      
        //// Write bank
        List<DCClusterCombo> finalClsComboList = new ArrayList<>();
        finalClsComboList.addAll(final6ClsComboList);
        finalClsComboList.addAll(final5ClsComboList);
        
        writeBank(event,finalClsComboList);  
        
        return true;        
    }
    
    public void writeBank(DataEvent event, List<DCClusterCombo> clsComboList){      
        DataBank bank = event.createBank(outputBank, clsComboList.size());
        for(int i = 0; i < clsComboList.size(); i++){
            bank.setByte("id", i, (byte) (i+1));
            bank.setByte("sector", i, (byte) clsComboList.get(i).get(0).getSector());
            bank.setFloat("prob", i, (float) clsComboList.get(i).getProbability());
            
            int[] ids = new int[6];
            for(DCCluster cls : clsComboList.get(i)){
                ids[cls.getSuperlayer()-1] = cls.getId();
            }
            
            for(int c = 0; c < 6; c++){
                int order = c+1;
                bank.setShort("c"+order, i, (short) ids[c]);
            }
        }
        event.removeBank(outputBank);
        event.appendBank(bank);
    }    
    
    /**
    * Recursively generate all possible 6-cluster combinations from a map of clusters per superlayer.
    *
    * @param map        a map from superlayer index (1-6) to a list of DCClusters in that superlayer
    * @param sl         the current superlayer being processed (1-based)
    * @param current    an array storing the current combination of clusters being built
    * @param comboList  the list to store all generated DCClusterCombo objects
    */
    public void generate6ClsCombos(Map<Integer, List<DCCluster>> map, int sl, DCCluster[] current, List<DCClusterCombo> comboList) {

        // Limit for combos        
        if (comboList.size() >= limitClsCombosPerSector) {
            if (!warned6ClsLimit) {                
                LOGGER.log(Level.FINE, String.format(
                    "6-cluster combo generation reached limit: %d combos per sector. "
                    + "Further combinations are discarded.",
                    limitClsCombosPerSector
                ));
                warned6ClsLimit = true;
            }
            return;
        }
        
        // Base case: if all superlayers have been processed (sl > 6)
        // then we have a complete 6-cluster combination
        if (sl > SUPERLAYERS) {
            // Clone the current array and convert to a List
            List<DCCluster> list = new ArrayList<>(Arrays.asList(current.clone()));
            // Wrap the list in a DCClusterCombo and add to output list
            comboList.add(new DCClusterCombo(list));
            return;
        }

        // Recursive case: iterate over all clusters in the current superlayer
        for (DCCluster cls : map.get(sl)) {
            // Set the current cluster for this superlayer in the combination array
            current[sl-1] = cls;
            // Recurse to the next superlayer
            generate6ClsCombos(map, sl+1, current, comboList);
        }
    }
    
    /**
     * Generate all possible 5-cluster combinations from clusters per superlayer.
     * Each combination has one missing superlayer.
     *
     * @param mapSL      a map from superlayer index (1-6) to a list of DCClusters
     * @param outputList the list to store all generated DCClusterCombo objects
     */
    public void generate5ClsCombos(Map<Integer, List<DCCluster>> mapSL,
                                   List<DCClusterCombo> outputList) {
        
        // Iterate over all possible missing superlayers (1 to 6)
        for (int missingSL = 1; missingSL <= SUPERLAYERS; missingSL++) {

            // Check if clusters exist in the other 5 superlayers
            boolean ok = true;
            for (int sl = 1; sl <= SUPERLAYERS; sl++) {
                if (sl == missingSL) continue;   // skip the missing superlayer
                if (!mapSL.containsKey(sl)) {   // if any required superlayer is missing, skip
                    ok = false;
                    break;
                }
            }
            if (!ok) continue;

            // Recursively generate all combinations for the remaining 5 superlayers
            generate5ClsRecursive(mapSL, missingSL, 1, new DCCluster[5], 0, outputList);
        }
    }

    /**
     * Recursive helper to generate 5-cluster combinations for a given missing superlayer.
     *
     * @param mapSL      map of superlayer to list of clusters
     * @param missingSL  the superlayer index that should be missing in the combination
     * @param sl         the current superlayer being processed (1-based)
     * @param current    array storing the currently selected clusters
     * @param idx        index in the 'current' array for the next cluster
     * @param outputList list to store generated DCClusterCombo objects
     */
    private void generate5ClsRecursive(Map<Integer, List<DCCluster>> mapSL,
                                       int missingSL,
                                       int sl,
                                       DCCluster[] current,
                                       int idx,
                                       List<DCClusterCombo> outputList) {
        
        // Limit for combos
        if (outputList.size() >= limitClsCombosPerSector) {
            if (!warned5ClsLimit) {
                LOGGER.log(Level.FINE, String.format(
                    "5-cluster combo generation reached limit: %d combos per sector. "
                    + "Further combinations are discarded.",
                    limitClsCombosPerSector
                ));
                warned5ClsLimit = true;
            }
            return;
        }

        // Base case: all superlayers processed
        if (sl > SUPERLAYERS) {
            // Convert current array to list and wrap in DCClusterCombo
            List<DCCluster> list = Arrays.asList(current.clone());
            DCClusterCombo combo = new DCClusterCombo(new ArrayList<>(list), missingSL);
            outputList.add(combo);
            return;
        }

        // If current superlayer is the missing one, skip it
        if (sl == missingSL) {
            generate5ClsRecursive(mapSL, missingSL, sl + 1, current, idx, outputList);
            return;
        }

        // Iterate over all clusters in the current superlayer
        for (DCCluster cls : mapSL.get(sl)) {
            current[idx] = cls;  // add cluster to current combination
            // Recurse to next superlayer, increment index in current array
            generate5ClsRecursive(mapSL, missingSL, sl + 1, current, idx + 1, outputList);
        }
    }

    
    /**
    * Resolve conflicts among a list of DCClusterCombos by removing overlapping combos.
    * Two combos are considered conflicting if they share any DCCluster (same ID).
    * The combos with higher probability are prioritized.
    *
    * @param comboList the list of DCClusterCombo objects to process
    * @return a list of DCClusterCombo objects with conflicts resolved
    */
    public List<DCClusterCombo> resolveSharedClusterConflicts(List<DCClusterCombo> comboList) {   
        // Sort the combos in descending order of probability
        comboList.sort((a, b) -> Float.compare(b.getProbability(), a.getProbability()));
        
        List<DCClusterCombo> selected = new ArrayList<>();
        for (DCClusterCombo combo : comboList) {
            boolean conflict = false;

            for (DCClusterCombo kept : selected) {
                if (shareCluster(combo, kept)) {
                    conflict = true;
                    break;
                }
            }

            if (!conflict) {
                selected.add(combo);
            }
        }

        return selected;
    }
    
    public boolean shareCluster(DCClusterCombo a, DCClusterCombo b) {
        if(a.size() == 6 && b.size() == 6){
            for (int sl = 0; sl < 6; sl++) {
                if (a.get(sl).getId() == b.get(sl).getId()) {
                    return true;
                }
            }
        }
        else{
            for (DCCluster ca : a) {
                for (DCCluster cb : b) {
                    if (ca.getId() == cb.getId()) return true;
                }
            }
        }
        return false;
    }    
        
    // -------- Translator for 6-cluster combo--------
    public static Translator<float[][], float[]> getBatchTranslator6Cls() {
        return new Translator<float[][], float[]>() {
            @Override
            public NDList processInput(TranslatorContext ctx, float[][] batchInput) {
                NDManager manager = ctx.getNDManager();
                int batch = batchInput.length;
                int dim = 12;

                float[][] normalized = new float[batch][dim];

                // Normalize for each sample
                for (int b = 0; b < batch; b++) {
                    for (int i = 0; i < 6; i++) normalized[b][i] = batchInput[b][i] / 112f;
                    for (int i = 6; i < 12; i++) normalized[b][i] = batchInput[b][i];
                }

                NDArray x = manager.create(normalized);   // shape: (batch, 12)
                return new NDList(x);
            }

            @Override
            public float[] processOutput(TranslatorContext ctx, NDList out) {
                return out.get(0).toFloatArray();
            }

            @Override
            public Batchifier getBatchifier() { return Batchifier.STACK; }
        };
    }
    
    // -------- Translator for 5-cluster combo--------
    public static Translator<float[][], float[]> getBatchTranslator5Cls() {
        return new Translator<float[][], float[]>() {
            @Override
            public NDList processInput(TranslatorContext ctx, float[][] batchInput) {
                NDManager manager = ctx.getNDManager();
                int batch = batchInput.length;
                int dim = 11;

                float[][] normalized = new float[batch][dim];

                // Normalize for each sample
                for (int b = 0; b < batch; b++) {
                    for (int i = 0; i < 5; i++) normalized[b][i] = batchInput[b][i] / 112f;
                    for (int i = 5; i < 10; i++) normalized[b][i] = batchInput[b][i];
                    normalized[b][10] = batchInput[b][10]/6.0f;
                }

                NDArray x = manager.create(normalized);   // shape: (batch, 11)
                return new NDList(x);
            }

            @Override
            public float[] processOutput(TranslatorContext ctx, NDList out) {
                return out.get(0).toFloatArray();
            }

            @Override
            public Batchifier getBatchifier() { return Batchifier.STACK; }
        };
    }        
}
