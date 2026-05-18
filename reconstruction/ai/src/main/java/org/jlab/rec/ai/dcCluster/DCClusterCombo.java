package org.jlab.rec.ai.dcCluster;

import java.util.List;
import java.util.ArrayList;

public class DCClusterCombo extends ArrayList<DCCluster> {
    
    private int id = -1;
    private int missingSL = -1;
    private float probability = -1;
   
    private int missingSL1 = -1;
    private int missingSL2 = -1;
    
    public DCClusterCombo(List<DCCluster> clsList) {
        super(clsList); 
    }
    
    public DCClusterCombo(List<DCCluster> clsList, int missingSL) {
        super(clsList); 
        this.missingSL = missingSL;
    } 
    
    public DCClusterCombo(List<DCCluster> clsList, int missingSL1, int missingSL2) {
        super(clsList); 
        this.missingSL1 = missingSL1;
        this.missingSL2 = missingSL2;
    } 

    public DCClusterCombo(List<DCCluster> clsList, int missingSL, int missingSL1, int missingSL2) {
        super(clsList);
        this.missingSL = missingSL;
        this.missingSL1 = missingSL1;
        this.missingSL2 = missingSL2;
    }    

    public int getMissingSL() {
        return missingSL;
    }
    
    public int getMissingSL1() {
        return missingSL1;
    }    

    public int getMissingSL2() {
        return missingSL2;
    }    
    
    public void setProbability(float probability){
        this.probability = probability;
    }
    
    public float getProbability(){
        return probability;
    }
    
    public void setId(int id){
        this.id = id;
    }
    
    public int getId(){
        return id;
    }    
}