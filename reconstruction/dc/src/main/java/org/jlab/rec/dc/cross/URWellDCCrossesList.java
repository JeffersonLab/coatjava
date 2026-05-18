package org.jlab.rec.dc.cross;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.jlab.rec.dc.cross.CrossList;
import org.jlab.rec.urwell.reader.URWellCross;
import org.jlab.rec.dc.cross.Cross;

/**
 * A class for cross combos with DC crosses and with/without uRWell cross
 *
 * @author Tongtong Cao
 */

public class URWellDCCrossesList{
    private List<URWellDCCrosses> urDCCrossesList = new ArrayList();
    
    public URWellDCCrossesList(){}
    
    public List<URWellDCCrosses> get_URWellDCCrossesList(){
        return urDCCrossesList;
    }
    
    public void set_URWellDCCrossesList(CrossList crosslists){
        for(List<Cross> crossList : crosslists){
            for(Cross crs: crossList){
                if(crs.get_Segment1().get_Superlayer() == 1){
                    if(!crs.get_Segment1().getMatchedURWellCrosses().isEmpty())
                        this.add_URWellDCCrosses(crs.get_Segment1().getMatchedURWellCrosses(), crossList);
                    else
                        this.add_URWellDCCrosses(new ArrayList(), crossList);
                    break;                    
                }
            }
        }
    }
    
    public void set_URWellDCCrossesList(CrossList crosslists, Map<Integer, List<URWellCross>> map_nnTrkId_urCrses){
        for(List<Cross> crossList : crosslists){
            int nnTrkId = crossList.get(0).get_Segment1().get(0).NNTrkId;
            if(map_nnTrkId_urCrses.containsKey(nnTrkId)){
                this.add_URWellDCCrosses(map_nnTrkId_urCrses.get(nnTrkId), crossList);
            }
            else this.add_URWellDCCrosses(new ArrayList(), crossList);
        }
    }    
    
    public void set_URWellDCCrossesList(List<URWellDCCrosses> urDCCrossesList){
        this.urDCCrossesList = urDCCrossesList;
    }
    
    public void add_URWellDCCrossesList(List<URWellDCCrosses> urDCCrossesList){
        this.urDCCrossesList.addAll(urDCCrossesList);
    }
    
    public void add_URWellDCCrosses(List<URWellCross> urCrosses, List<Cross> dcCrosses){
        urDCCrossesList.add(new URWellDCCrosses(urCrosses, dcCrosses));
    }
    
    public void add(URWellDCCrosses urDCCrosses){
        urDCCrossesList.add(urDCCrosses);
    }

    public class URWellDCCrosses {

        private List<URWellCross> urCrosses = new ArrayList();
        private List<Cross> dcCrosses = new ArrayList();
        
        public URWellDCCrosses(List<URWellCross> urCrosses, List<Cross> dcCrosses) {            
            this.urCrosses.addAll(urCrosses);
            this.dcCrosses.addAll(dcCrosses);
        }
                
        public List<URWellCross> get_URWellCrosses(){
            return urCrosses;
        }
        
        public List<Cross> get_DCCrosses(){
            return dcCrosses;
        }

    }
}
