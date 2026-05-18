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
import org.jlab.geom.prim.Point3D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.system.ClasUtilsFile;
import org.jlab.rec.ai.dcCluster.DCCluster;
import org.jlab.rec.ai.dcCluster.DCClusterCombo;
import org.jlab.rec.ai.dcCluster.URCross;
import org.jlab.rec.ai.dcCluster.DCURCombo;

public class DCURComboEngine extends ReconstructionEngine {
    final String inputDCBank = "HitBasedTrkg::Clusters";
    final String inputURBank = "URWT::crosses";
    final String outputBank = "ai::tracks";
       
    // Totally, 8 models for DC-cluster & uRWell-cross combos
    final static int nDCClses[] = {6,6,6,5,5,5,4,4}; // number of DC clusters for 8 models, separately
    final static int nURCrses[] = {2,1,0,2,1,0,2,1}; // number of uRWell crosses for 8 models, separately
         
    final static String CONF_MODEL_FILES[] = {"modelFile6DCCls2URCrs", "modelFile6DCCls1URCrs", "modelFile6DCCls", 
        "modelFile5DCCls2URCrs", "modelFile5DCCls1URCrs", "modelFile5DC", 
        "modelFile4DCCls2URCrs", "modelFile4DCCls1URCrs"};
    final static String CONF_THRESHOLDS[] = {"threshold6DCCls2URCrs", "threshold6DCCls1URCrs", "threshold6DCCls", 
        "threshold5DCCls2URCrs", "threshold5DCCls1URCrs", "threshold5DCCls", 
        "threshold4DCCls2URCrs", "threshold4DCCls1URCrs"};  
    String modelFiles[] ={"dcURCombo/mlp_6dccls_2urcrs.pt", "dcURCombo/mlp_6dccls_1urcrs.pt", "dcURCombo/mlp_6dccls.pt", 
        "dcURCombo/mlp_5dccls_2urcrs.pt", "dcURCombo/mlp_5dccls_1urcrs.pt", "dcURCombo/mlp_5dccls.pt", 
        "dcURCombo/mlp_4dccls_2urcrs.pt", "dcURCombo/mlp_4dccls_1urcrs.pt"};
    float thresholds[] ={0.95f, 0.95f, 0.95f, 
        0.95f, 0.95f, 0.85f, 
        0.85f, 0.05f};    
    
    PredictorPool predictors[] = new PredictorPool[8];
    
    final static String CONF_THREADS = "threads";
    
    final static int SUPERLAYERS = 6; 
    
    private static final float MAXDCWIRE = 112f;
    private static final float URXRANGE = 140f;
    private static final float URYRANGE = 160f;
            
    private static final float URCROSSPAIRXCUT = 1.05f;
    private static final float URCROSSPAIRYCUT = 0.65f;
    private static final float URCROSSPAIRTIMECUT = 28.0f;
    
    public DCURComboEngine() {
        super("DCURComboEngine","tongtong","1.0");
    }

    @Override
    public void detectorChanged(int run){}

    @Override
    public boolean init() {
        System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
        System.setProperty("ai.djl.pytorch.num_threads", "1");
        System.setProperty("ai.djl.pytorch.graph_optimizer", "false");
        
        int threads = Integer.parseInt(getEngineConfigString(CONF_THREADS,"64"));
        
        for(int i = 0; i < 8; i++){
            if (getEngineConfigString(CONF_THRESHOLDS[i]) != null)
                thresholds[i] = Float.parseFloat(getEngineConfigString(CONF_THRESHOLDS[i]));
            if (getEngineConfigString(CONF_MODEL_FILES[i]) != null)
                modelFiles[i] = getEngineConfigString(CONF_MODEL_FILES[i]);

            try {
                String modelPath = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/data/nnet/" + modelFiles[i]);

                Criteria<float[][], float[]> criteria = Criteria.builder()
                    .setTypes(float[][].class, float[].class)
                    .optModelPath(Paths.get(modelPath))
                    .optEngine("PyTorch")
                    .optTranslator(DCURComboEngine.getBatchTranslator(nDCClses[i], nURCrses[i]))
                    .optProgress(new ProgressBar())
                    .build();

                ZooModel<float[][], float[]> model= criteria.loadModel();
                predictors[i] = new PredictorPool(threads, model);
            } catch (NullPointerException | MalformedModelException | IOException | ModelNotFoundException ex) {
                Logger.getLogger(DCURComboEngine.class.getName()).log(Level.SEVERE, null, ex);
                return false;
            }
        }                
        
        return true;        
    }
    
    @Override
    public boolean processDataEventUser(DataEvent event) {
        
        // load DC clusters from bank HitBasedTrkg::Clusters, and uRWell crosses from bank URWT::crosses     
        if (!event.hasBank(inputDCBank) || !event.hasBank(inputURBank)) return true;
        DataBank dcBank = event.getBank(inputDCBank);
        DataBank urBank = event.getBank(inputURBank);
        
        List<DCCluster> allDCClusterList = new ArrayList();
        for (int r = 0; r < dcBank.rows(); r++) {

            int id          = dcBank.getInt("id", r);
            int sector      = dcBank.getByte("sector", r);
            int superlayer  = dcBank.getByte("superlayer", r);
            float avgWire   = dcBank.getFloat("avgWire", r);
            float fitSlope  = dcBank.getFloat("fitSlope", r);

            DCCluster cls = new DCCluster(id, sector, superlayer, avgWire, fitSlope);
            allDCClusterList.add(cls);
        }
        
        List<URCross> allURCrossList = new ArrayList();
        for (int r = 0; r < urBank.rows(); r++) {

            int id          = urBank.getInt("id", r);
            int sector      = urBank.getByte("sector", r);
            int region  = urBank.getByte("region", r);
            float x   = urBank.getFloat("x", r);
            float y  = urBank.getFloat("y", r);
            float z  = urBank.getFloat("z", r);
            float time  = urBank.getFloat("time", r);
            int status = urBank.getShort("status", r);
            
            if(status == 0){
                Point3D pointLocal = new Point3D(x, y, z);
                pointLocal.rotateZ(Math.toRadians(-60 * (sector - 1)));
                pointLocal.rotateY(Math.toRadians(-25)); 

                URCross crs = new URCross(id, sector, region, (float)pointLocal.x(), (float)pointLocal.y(), time);
                allURCrossList.add(crs);
            }
        }   
        
        // Predict combos for 8 categories in order
        List<DCURCombo> allFinalDCURComboList = new ArrayList<>();        
        for(int i = 0; i < 8; i++){
            List<DCURCombo> finalComboList = predictFinalDCURComboList(nDCClses[i], nURCrses[i], allDCClusterList, allURCrossList, predictors[i], thresholds[i]);
            allFinalDCURComboList.addAll(finalComboList);
            
            for (DCURCombo combo : finalComboList) {
                allDCClusterList.removeAll(combo);
                allURCrossList.removeAll(combo.getURCrsList());
            }
        }
                
        writeBank(event, allFinalDCURComboList); 
        
        return true;        
    }
    
    public void writeBank(DataEvent event, List<DCURCombo> dcURComboList){      
        DataBank bank = event.createBank(outputBank, dcURComboList.size());
        
        for(int i = 0; i < dcURComboList.size(); i++){
            bank.setByte("id", i, (byte) (i+1));
            bank.setByte("sector", i, (byte) dcURComboList.get(i).get(0).getSector());
            bank.setFloat("prob", i, (float) dcURComboList.get(i).getProbability());
            
            int[] dcIds = new int[6];
            for(DCCluster cls : dcURComboList.get(i)){
                dcIds[cls.getSuperlayer()-1] = cls.getId();
            }
            
            for(int c = 0; c < 6; c++){
                int order = c+1;
                bank.setShort("c"+order, i, (short) dcIds[c]);
            }
            
            int[] urIds = new int[2];
            for(URCross crs : dcURComboList.get(i).getURCrsList()){
                urIds[crs.getRegion()-1] = crs.getId();
            }
            
            for(int r = 0; r < 2; r++){
                int order = r+1;
                bank.setShort("ur"+order, i, (short) urIds[r]);
            }
            
        }        
        
        event.removeBank(outputBank);
        event.appendBank(bank);
    } 
    
    
    public List<DCURCombo> predictFinalDCURComboList(int nDCCls, int nURCrs, List<DCCluster> allDCClusterList, List<URCross> allURCrossList, PredictorPool predictors, float threshold){
        // Make dc map from sector to map from superlayer to cluster list
        Map<Integer, Map<Integer, List<DCCluster>>> dcMap = new HashMap<>();
        for (DCCluster cls : allDCClusterList) {
            dcMap.computeIfAbsent(cls.getSector(), s -> new HashMap<>()).computeIfAbsent(cls.getSuperlayer(), sl -> new ArrayList<>()).add(cls);
        }
                               
        // Make map from sector to DC-cluster combos        
        Map<Integer, List<DCClusterCombo>> map_sector_dcClsCombos = new HashMap();
        if(nDCCls == 6){
            for(int sector : dcMap.keySet()){
                if(dcMap.get(sector).size() == 6){
                    Map<Integer, List<DCCluster>> orderedMap = new TreeMap<>(dcMap.get(sector)); // Sorts entries by superlayer in ascending order
                    List<DCClusterCombo> combos = new ArrayList();
                    generate6ClsCombos(orderedMap, 1, new DCCluster[6], combos);
                    map_sector_dcClsCombos.put(sector, combos);
                }
            }
        }        
        else if(nDCCls == 5){
            for(int sector : dcMap.keySet()){
                if(dcMap.get(sector).size() >= 5){
                    Map<Integer, List<DCCluster>> orderedMap = new TreeMap<>(dcMap.get(sector)); // Sorts entries by superlayer in ascending order 
                    List<DCClusterCombo> combos = new ArrayList();
                    generate5ClsCombos(orderedMap, combos);  
                    map_sector_dcClsCombos.put(sector, combos);
                }
            }
        }        
        else if(nDCCls == 4){
            for(int sector : dcMap.keySet()){
                if(dcMap.get(sector).size() >= 4){
                    Map<Integer, List<DCCluster>> orderedMap = new TreeMap<>(dcMap.get(sector)); // Sorts entries by superlayer in ascending order 
                    List<DCClusterCombo> combos = new ArrayList();
                    generate4ClsCombos(orderedMap, combos);  
                    map_sector_dcClsCombos.put(sector, combos);
                }
            }
        }
        
        
        // Make map from sector to uRWell-cross combos 
        Map<Integer, List<List<URCross>>> map_sector_urCrsCombos = new HashMap();        
        if(nURCrs == 2){
            Map<Integer, Map<Integer, List<URCross>>> urMap = new HashMap<>();
            for (URCross crs : allURCrossList) {
                urMap.computeIfAbsent(crs.getSector(), s -> new HashMap<>()).computeIfAbsent(crs.getRegion(), reg -> new ArrayList<>()).add(crs);
            }
            
            for(int sector : urMap.keySet()){
                if(urMap.get(sector).size() == 2){                
                    List<List<URCross>> comboList = new ArrayList();
                    generate2URCrsCombos(urMap.get(sector), comboList);
                    map_sector_urCrsCombos.put(sector, comboList);
                }        
            }
        }
        else if(nURCrs == 1){
            for (URCross crs : allURCrossList) {
                List<URCross> crsList = new ArrayList();
                crsList.add(crs);
                map_sector_urCrsCombos.computeIfAbsent(crs.getSector(), s -> new ArrayList<>()).add(crsList);
            }
        }
                
        // Make DC-cluster and UR-cross combos
        List<DCURCombo> allDCURCombos = new ArrayList();
        if(nURCrs > 0){ 
            for(int sector : map_sector_dcClsCombos.keySet()){
                if(map_sector_urCrsCombos.containsKey(sector)){
                    List<DCURCombo> dcURCombos = new ArrayList();
                    generateDCURCombos(map_sector_dcClsCombos.get(sector), map_sector_urCrsCombos.get(sector), dcURCombos);
                    allDCURCombos.addAll(dcURCombos);
                }
            }
        }
        else if(nURCrs == 0){
           for(int sector : map_sector_dcClsCombos.keySet()){
               for(DCClusterCombo dcCombo : map_sector_dcClsCombos.get(sector)){
                   DCURCombo dcURCombo = new DCURCombo(dcCombo, dcCombo.getMissingSL(), dcCombo.getMissingSL1(), dcCombo.getMissingSL2(), new ArrayList());
                   allDCURCombos.add(dcURCombo);
               }
           } 
        }

        // Batch prediction for DC-cluster and UR-cross combos
        float[] outputsDCClsURCrs = null; 
        List<DCURCombo> predictedDCClsURCrsCombos = new ArrayList();
        try {
            if(!allDCURCombos.isEmpty()){
                List<float[]> batchInputsDCClsURCrs = new ArrayList<>();
                for (DCURCombo combo: allDCURCombos) {
                    int missingDCSLs = SUPERLAYERS - nDCCls;
                    float[] input = new float[(nDCCls + nURCrs) * 2 + missingDCSLs];
                    for (int i = 0; i < nDCCls; i++) {
                        input[i]   = combo.get(i).getAvgWire();
                        input[i+nDCCls] = combo.get(i).getFitSlope();
                    }
                    for (int i = 0; i < nURCrs; i++) {
                        input[2*nDCCls + 2*i] = combo.getURCrsList().get(i).getX();
                        input[2*nDCCls + 1 + 2*i] = combo.getURCrsList().get(i).getY();
                    }
                    if(missingDCSLs == 1) {
                        input[2 * (nDCCls + nURCrs)] = combo.getMissingSL();
                    }
                    else if(missingDCSLs == 2) {
                        input[2 * (nDCCls + nURCrs)] = combo.getMissingSL1();
                        input[2 * (nDCCls + nURCrs) + 1] = combo.getMissingSL2();
                    }   
                    batchInputsDCClsURCrs.add(input);
                }            

                float[][] batchArrayDCClsURCrs = new float[batchInputsDCClsURCrs.size()][];
                for (int i = 0; i < batchInputsDCClsURCrs.size(); i++) {
                    batchArrayDCClsURCrs[i] = batchInputsDCClsURCrs.get(i);
                }

                Predictor<float[][], float[]> predictor = predictors.take();            
                try {
                    outputsDCClsURCrs = predictor.predict(batchArrayDCClsURCrs);
                } finally {
                    predictors.put(predictor);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Batch prediction error in DCURComboClf", e);
        }
        
        // Filter DC-cluster and UR-cross combos based on threshold
        if(outputsDCClsURCrs!=null) {
            for(int i = 0; i < outputsDCClsURCrs.length; i++){
               if(outputsDCClsURCrs[i] > threshold) {
                   allDCURCombos.get(i).setProbability(outputsDCClsURCrs[i]);
                   predictedDCClsURCrsCombos.add(allDCURCombos.get(i));
               }
            }
        }     
        
        // Separate predicted DC-cluster and uRWell-cross combos into sectors
        Map<Integer, List<DCURCombo>> map_sector_predictedDCClsURCrsComboList = new HashMap<>();
        for(DCURCombo clsCombo : predictedDCClsURCrsCombos){
            map_sector_predictedDCClsURCrsComboList.computeIfAbsent(clsCombo.get(0).getSector(), s -> new ArrayList<>()).add(clsCombo);
        }
        
        // Resolve overlapping cluster combos, and get final DC-cluster and uRWell-cross combos
        List<DCURCombo> finalDCClsURCrsComboList = new ArrayList();
        for(List<DCURCombo> predictedDCClsURCrsComboList : map_sector_predictedDCClsURCrsComboList.values()){
            finalDCClsURCrsComboList.addAll(resolveSharedDCURComboConflicts(predictedDCClsURCrsComboList));
        }
        
        return finalDCClsURCrsComboList;
    }
    
    
    /**
    * Generate all possible DC-UR combinations
    * Inputs for dcClsCombos and urCrsCombos must from the same sector
    * @param dcClsCombos  a list for DCClusterCombo
    * @param urCrsCombos  a list for uRWell cross combo from R1R2
    * @param dcURCombos  the list to store all DC-UR combinations
    */    
    public void generateDCURCombos(List<DCClusterCombo> dcClsCombos, List<List<URCross>> urCrsCombos, List<DCURCombo> dcURCombos){
        for(DCClusterCombo dcClsCombo : dcClsCombos){
            for(List<URCross> crsList : urCrsCombos){                
                DCURCombo dcURCombo = new DCURCombo(dcClsCombo, dcClsCombo.getMissingSL(),dcClsCombo.getMissingSL1(), dcClsCombo.getMissingSL2(), crsList);
                dcURCombos.add(dcURCombo);
            }
        }
    }    
    
    /**
    * Generate all possible 2-uRWell-cross combinations from a map of crosses per region.
    *
    * @param map        a map from region index (1-2) to a list of URCross in that region
    * @param comboList  the list to store all uRWell cross combinations
    */
    public void generate2URCrsCombos(Map<Integer, List<URCross>> map, List<List<URCross>> comboList) {
        for(URCross crs1 : map.get(1)){
            for(URCross crs2 : map.get(2)){
                List<URCross> crsCombo = new ArrayList();
                crsCombo.add(crs1);
                crsCombo.add(crs2);
                if(Math.abs(crs2.getX() - crs1.getX()) < URCROSSPAIRXCUT && Math.abs(crs2.getY() - crs1.getY()) < URCROSSPAIRYCUT && Math.abs(crs2.getTime() - crs1.getTime()) < URCROSSPAIRTIMECUT) {
                    comboList.add(crsCombo);
                }
            }
        }      
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
     * Generate all possible 4-cluster combinations from clusters per superlayer.
     * Each combination has one missing superlayer.
     *
     * @param mapSL      a map from superlayer index (1-6) to a list of DCClusters
     * @param outputList the list to store all generated DCClusterCombo objects
     */
    public void generate4ClsCombos(Map<Integer, List<DCCluster>> mapSL,
                                   List<DCClusterCombo> outputList) {

        // Iterate over all possible missing superlayers (1 to 6)
        for (int missingSL1 = 1; missingSL1 <= SUPERLAYERS - 1; missingSL1++) {
            for (int missingSL2 = missingSL1+1; missingSL2 <= SUPERLAYERS; missingSL2++) {

                // Check if clusters exist in the other 4 superlayers
                boolean ok = true;
                for (int sl = 1; sl <= SUPERLAYERS; sl++) {
                    if (sl == missingSL1 || sl == missingSL2) continue;   // skip the missing superlayers
                    if (!mapSL.containsKey(sl)) {   // if any required superlayer is missing, skip
                        ok = false;
                        break;
                    }
                }
                if (!ok) continue;

                // Recursively generate all combinations for the remaining 5 superlayers
                generate4ClsRecursive(mapSL, missingSL1, missingSL2, 1, new DCCluster[4], 0, outputList);
            }
        }
    }

    /**
     * Recursive helper to generate 4-cluster combinations for a given missing superlayer.
     *
     * @param mapSL      map of superlayer to list of clusters
     * @param missingSL1  the first superlayer index that should be missing in the combination
     * @param missingSL2  the second superlayer index that should be missing in the combination
     * @param sl         the current superlayer being processed (1-based)
     * @param current    array storing the currently selected clusters
     * @param idx        index in the 'current' array for the next cluster
     * @param outputList list to store generated DCClusterCombo objects
     */
    private void generate4ClsRecursive(Map<Integer, List<DCCluster>> mapSL,
                                       int missingSL1,                                                                             
                                       int missingSL2,
                                       int sl,
                                       DCCluster[] current,
                                       int idx,
                                       List<DCClusterCombo> outputList) {

        // Base case: all superlayers processed
        if (sl > SUPERLAYERS) {
            // Convert current array to list and wrap in DCClusterCombo
            List<DCCluster> list = Arrays.asList(current.clone());
            DCClusterCombo combo = new DCClusterCombo(new ArrayList<>(list), missingSL1, missingSL2);
            outputList.add(combo);
            return;
        }

        // If current superlayer is the missing one, skip it
        if (sl == missingSL1 || sl == missingSL2) {
            generate4ClsRecursive(mapSL, missingSL1, missingSL2, sl + 1, current, idx, outputList);
            return;
        }

        // Iterate over all clusters in the current superlayer
        for (DCCluster cls : mapSL.get(sl)) {
            current[idx] = cls;  // add cluster to current combination
            // Recurse to next superlayer, increment index in current array
            generate4ClsRecursive(mapSL, missingSL1, missingSL2, sl + 1, current, idx + 1, outputList);
        }
    } 
    
    /**
    * Resolve conflicts among a list of DCURCombos by removing overlapping combos.
    * Two combos are considered conflicting if they share any DCCluster (same ID) or URWellCross (same ID).
    * The combos with higher probability are prioritized.
    *
    * @param comboList the list of DCURCombo objects to process
    * @return a list of DCURCombo objects with conflicts resolved
    */
    public List<DCURCombo> resolveSharedDCURComboConflicts(List<DCURCombo> comboList) {   
        // Sort the combos in descending order of probability
        comboList.sort((a, b) -> Float.compare(b.getProbability(), a.getProbability()));
        
        List<DCURCombo> selected = new ArrayList<>();
        for (DCURCombo combo : comboList) {
            boolean conflict = false;

            for (DCURCombo kept : selected) {
                if (shareDCUR(combo, kept)) {
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
    
    
    public boolean shareDCUR(DCURCombo a, DCURCombo b) {
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
        
        if(!a.getURCrsList().isEmpty() && !b.getURCrsList().isEmpty()){
            if(a.getURCrsList().size() == b.getURCrsList().size()){
                for(int i = 0; i < a.getURCrsList().size(); i++){
                    if(a.getURCrsList().get(i).getId() == b.getURCrsList().get(i).getId()) return true;
                }
            }
            else{
                for(URCross crsa : a.getURCrsList()){
                    for(URCross crsb : b.getURCrsList()){
                        if(crsb.getId() == crsa.getId()) return true;
                    }
                }
            }
        }
        
        return false;
    }
       
    // -------- Translator for DC-cluster and uRWell-cross combo--------
    public static Translator<float[][], float[]> getBatchTranslator(int nDCCls, int nURCrs) {
        return new Translator<float[][], float[]>() {
            int missingSLs = SUPERLAYERS - nDCCls;
            
            @Override
            public NDList processInput(TranslatorContext ctx, float[][] batchInput) {                            
                NDManager manager = ctx.getNDManager();
                int batch = batchInput.length;
                int dim = 2* (nDCCls + nURCrs) + missingSLs;

                float[][] normalized = new float[batch][dim];

                // Normalize for each sample
                for (int b = 0; b < batch; b++) {
                    for (int i = 0; i < nDCCls; i++) normalized[b][i] = batchInput[b][i] / MAXDCWIRE;
                    for (int i = nDCCls; i < 2*nDCCls; i++) normalized[b][i] = batchInput[b][i];
                    for(int i = 0; i < nURCrs; i++){
                        normalized[b][2*nDCCls + 2*i] = batchInput[b][2*nDCCls + 2*i] / URXRANGE;
                        normalized[b][2*nDCCls + 1 + 2*i] = batchInput[b][2*nDCCls + 1 + 2*i] / URYRANGE; 
                    }
                    if(missingSLs == 1){
                        normalized[b][2*(nDCCls+nURCrs)] = batchInput[b][2*(nDCCls+nURCrs)]/(float)SUPERLAYERS;
                    }
                    else if(missingSLs == 2){
                        normalized[b][2*(nDCCls+nURCrs)] = batchInput[b][2*(nDCCls+nURCrs)]/(float)SUPERLAYERS;
                        normalized[b][2*(nDCCls+nURCrs)+1] = batchInput[b][2*(nDCCls+nURCrs)+1]/(float)SUPERLAYERS;
                    }
                }

                NDArray x = manager.create(normalized);   // shape: (batch, dim)
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
