package org.jlab.rec.ai.dcCluster;

import java.util.List;
import java.util.ArrayList;

public class DCURCombo extends DCClusterCombo {
    private List<URCross> urCrsList;

    public DCURCombo(DCClusterCombo dcClsCombo, List<URCross> urCrsList) {
        super(dcClsCombo);
        this.urCrsList = urCrsList;
    } 
    
    public DCURCombo(DCClusterCombo dcClsCombo, int missingSL, List<URCross> urCrsList) {
        super(dcClsCombo, missingSL);
        this.urCrsList = urCrsList;
    } 

    public DCURCombo(DCClusterCombo dcClsCombo, int missingSL1, int missingSL2, List<URCross> urCrsList) {
        super(dcClsCombo, missingSL1, missingSL2);
        this.urCrsList = urCrsList;
    } 

    public DCURCombo(DCClusterCombo dcClsCombo, int missingSL, int missingSL1, int missingSL2, List<URCross> urCrsList) {
        super(dcClsCombo, missingSL, missingSL1, missingSL2);
        this.urCrsList = urCrsList;
    }    
    
    public List<URCross> getURCrsList(){
        return urCrsList;
    }      
}