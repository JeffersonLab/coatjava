package org.jlab.rec.dc.banks;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;



/**
 * A class to read bank HitBasedTrkg::URWellDCClusters 
 * and build map between cluster id and uRWell cross id for clusters at SL1
 *
 * @author Tongtong Cao
 */
public class URWellDCClustersReader {

    
    public URWellDCClustersReader() {}
    
    public Map<Integer, List<Integer>> getMapClsIdURWellCrossIds(DataEvent event){
        DataBank bank = event.getBank("HitBasedTrkg::URWellDCClusters");
        
        Map<Integer, List<Integer>> map_clsId_uRWellCrossIds  = new HashMap();
        if(bank != null){
            for (int i = 0; i < bank.rows(); i++) {
                int clsId       = bank.getShort("id", i);
                int uRWellCross1Id       = bank.getShort("URWell_Cross1_ID", i);
                int uRWellCross2Id       = bank.getShort("URWell_Cross2_ID", i);
                
                List<Integer> uRWellCrossIds = new ArrayList();
                if(uRWellCross1Id > 0 ) uRWellCrossIds.add(uRWellCross1Id);
                if(uRWellCross2Id > 0 ) uRWellCrossIds.add(uRWellCross2Id);
                map_clsId_uRWellCrossIds.put(clsId, uRWellCrossIds);                
            }
        }
        
        return map_clsId_uRWellCrossIds;
    }              
}