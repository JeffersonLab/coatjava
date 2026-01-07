package org.jlab.rec.ai.dcCluster;


public class DCCluster{
    int id;
    int sector;
    int superlayer;
    float avgWire;
    float fitSlope;
    
    public DCCluster(int id, int sector, int superlayer, float avgWire, float fitSlope){
    this.id = id;
    this.sector = sector;
    this.superlayer = superlayer;
    this.avgWire = avgWire;
    this.fitSlope = fitSlope;
    }
    
    public int getId(){
        return id;
    }
    
    public int getSector(){
        return sector;
    }
    
    public int getSuperlayer(){
        return superlayer;
    }
    
    public float getAvgWire(){
        return avgWire;
    }
    
    public float getFitSlope(){
        return fitSlope;
    }
        
}