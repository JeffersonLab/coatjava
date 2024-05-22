/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.mlanalysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.cluster.Cluster;
/**
 *
 * @author ziegler
 */
public class AIClusterReader {
    
    public Map<Integer, AIObject> fillAIClusterMap(DataEvent event) {
        Map<Integer, AIObject> map = new HashMap<>();
        DataBank aibank =null;
        if(event.hasBank("cvtml::clusters")) {
            aibank = event.getBank("cvtml::clusters");   
        } else {
            return map;
        }
        for(int l = 0; l<aibank.rows(); l++){
            if(aibank.getShort("status", l)!=1) continue;
            AIObject aio = new AIObject();
            aio.id=aibank.getShort("id", l);
            aio.status=aibank.getShort("status", l);
            aio.sector=aibank.getByte("sector", l);
            aio.layer=aibank.getShort("layer", l);
            aio.xo=aibank.getFloat("xo", l);
            aio.yo=aibank.getFloat("yo", l);
            aio.zo=aibank.getFloat("zo", l);
            aio.xe=aibank.getFloat("xe", l);
            aio.ye=aibank.getFloat("ye", l);
            aio.ze=aibank.getFloat("ze", l);
            aio.x=aibank.getFloat("x", l);
            aio.y=aibank.getFloat("y", l);
            aio.z=aibank.getFloat("z", l);
            aio.p=aibank.getFloat("p", l);
            aio.th=aibank.getFloat("th", l);
            aio.fi=aibank.getFloat("fi", l);
            map.put((int)aio.id, aio);
        }
        return map;
    }
    
    public List<Cluster> selectAIClusters(DataEvent event, List<Cluster> sVTClusters) {
        Map<Integer, AIObject> map = this.fillAIClusterMap(event);
       List<Cluster> sClus = new ArrayList<>();
        if(map.isEmpty()) return sClus;
        
        for(Cluster c : sVTClusters) {
            if(map.containsKey(c.getId())) {
                c.setAssociateAIObject(map.get(c.getId()));
                sClus.add(c);
            }
        }
        return sClus;
    }
    
}
