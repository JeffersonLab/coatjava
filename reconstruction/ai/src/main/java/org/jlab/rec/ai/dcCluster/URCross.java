package org.jlab.rec.ai.dcCluster;


public class URCross{
    private int id;
    private int sector;
    private int region;
    private float x;
    private float y;
    private float time;
    
    public URCross(int id, int sector, int region, float x, float y, float time){
    this.id = id;
    this.sector = sector;
    this.region = region;
    this.x = x;
    this.y = y;
    this.time = time;
    }
    
    public int getId(){
        return id;
    }
    
    public int getSector(){
        return sector;
    }
    
    public int getRegion(){
        return region;
    }
    
    public float getX(){
        return x;
    }
    
    public float getY(){
        return y;
    }
    
    public float getTime(){
        return time;
    }
        
}