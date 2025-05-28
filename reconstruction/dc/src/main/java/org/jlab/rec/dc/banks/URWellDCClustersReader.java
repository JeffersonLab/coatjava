package org.jlab.rec.dc.banks;

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
    
    public Map<Integer, Integer> getMapClsIdURWellCrossId(DataEvent event){
        DataBank bank = event.getBank("HitBasedTrkg::URWellDCClusters");
        
        Map<Integer, Integer> map_clsId_uRWellCrossId  = new HashMap();
        if(bank != null){
            for (int i = 0; i < bank.rows(); i++) {
                int clsId       = bank.getShort("id", i);
                int uRWellCrossId       = bank.getShort("URWell_Cross_ID", i);
                map_clsId_uRWellCrossId.put(clsId, uRWellCrossId);                
            }
        }
        
        return map_clsId_uRWellCrossId;
    }              
}