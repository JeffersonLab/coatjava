package org.jlab.service.urwt;

import java.util.ArrayList;
import java.util.List;
import org.jlab.detector.base.DetectorDescriptor;
import org.jlab.detector.base.DetectorType;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;

/**
 * URWT in-layer cluster
 * 
 * @author bondi, devita
 */
public class URWTCluster extends ArrayList<URWRStrip> {
   
    
    private DetectorDescriptor  desc          = new DetectorDescriptor(DetectorType.URWT);
    private int                 id;  
    private Line3D              clusterLine   = new Line3D();
    public int                  indexMaxStrip = -1;
    private byte                clusterStatus =  1;
    
    public URWTCluster(URWRStrip strip){
        this.desc.setSectorLayerComponent(strip.getDescriptor().getSector(), 
                                          strip.getDescriptor().getLayer(), 0);
        this.add(strip);
        this.clusterLine.copy(strip.getLine());
        this.indexMaxStrip = 0;
    }
    
    public int getId() {
        return id;
    }
    
    public DetectorDescriptor getDescriptor(){
        return this.desc;
    }
    
    public int getSector() {
        return this.desc.getSector();
    }
    
    public int getLayer() {
        return this.desc.getLayer();
    }
    
    public int getChamber() {
        return this.get(0).getChamber();
    }
    
    public Line3D  getLine() {return this.clusterLine;}    
    
    public double getEnergy(){
        double energy = 0.0;
        for(URWRStrip strip : this){
            energy += strip.getEnergy();
        }
        return energy;
    }
    
    public double getTime(){
        double time = 0.0;
        for(URWRStrip strip : this){
            time += strip.getTime()*strip.getEnergy();
        }
        time /= this.getEnergy();
        return time;
    }
    
    public double getSeedTime(){
        if(this.indexMaxStrip >= 0 && this.indexMaxStrip < this.size()){
            return this.get(indexMaxStrip).getTime();
        }
            return 0.0;
    }

    public URWRStrip getSeedStrip() {
    	    return this.get(this.indexMaxStrip);
    }
    
    public int      getMaxStrip(){
        return this.get(this.indexMaxStrip).getDescriptor().getComponent();
    }
    
    public boolean  addStrip(URWRStrip strip){
        for(URWRStrip s : this){
            if(s.isNeighbour(strip)){
                this.add(strip);
                if(strip.getEnergy()>this.get(indexMaxStrip).getEnergy()){
                    this.indexMaxStrip = this.size()-1;
                    this.clusterLine.copy(strip.getLine());
                }
                return true;
            }
        }
        return false;
    }
    
    public int getADC(){
        int adc = 0;
        for(URWRStrip s : this){
            adc+= s.getADC();
        }
        return adc;
    }
    
    public void setStatus(int val) {this.clusterStatus+=val;}
    
    public byte getStatus()  {return clusterStatus;}   
    
    public void setClusterId(int id){
        this.id = id;
        for(URWRStrip strip : this){
            strip.setClusterId(id);
        }
    }

    public void redoClusterLine(){
        
        Point3D pointOrigin = new Point3D(0.0,0.0,0.0);
        Point3D pointEnd    = new Point3D(0.0,0.0,0.0);
        
        double summE   = 0.0;
        
        for(int i = 0; i < this.size(); i++){
            Line3D line = this.get(i).getLine();
            
            double energy    = this.get(i).getEnergy();
            
            pointOrigin.setX(pointOrigin.x()+line.origin().x()*energy);
            pointOrigin.setY(pointOrigin.y()+line.origin().y()*energy);
            pointOrigin.setZ(pointOrigin.z()+line.origin().z()*energy);
            
            pointEnd.setX(pointEnd.x()+line.end().x()*energy);
            pointEnd.setY(pointEnd.y()+line.end().y()*energy);
            pointEnd.setZ(pointEnd.z()+line.end().z()*energy);
            
            summE   += energy;
        }
                
        this.clusterLine.set(
                pointOrigin.x()/summE,
                pointOrigin.y()/summE,
                pointOrigin.z()/summE,
                pointEnd.x()/summE,
                pointEnd.y()/summE,
                pointEnd.z()/summE
        );
    }
    
    
    public static List<URWTCluster> createClusters(List<URWRStrip> stripList){
    	
        List<URWTCluster>  clusterList = new ArrayList<>();
        
        if(!stripList.isEmpty()){
            for(int loop = 0; loop < stripList.size(); loop++){ //Loop over all strips 
                boolean stripAdded = false;                
                for(URWTCluster  cluster : clusterList) {
                    if(cluster.addStrip(stripList.get(loop))){ //Add adjacent strip to newly seeded peak
                        stripAdded = true;
                    }
                }
                if(!stripAdded){
                    URWTCluster  newPeak = new URWTCluster(stripList.get(loop)); //Non-adjacent strip seeds new peak
                    clusterList.add(newPeak);
                }
            }
        }
        for(int loop = 0; loop < clusterList.size(); loop++){
            clusterList.get(loop).setClusterId(loop+1);
            clusterList.get(loop).redoClusterLine();
        }
        return clusterList;
    }   
    
    public static List<URWTCluster> getClusters(List<URWTCluster> clusters, int sector, int layer) {
        List<URWTCluster> selectedClusters = new ArrayList<>();
        for(URWTCluster cluster : clusters) {
            if(cluster.getSector()==sector && cluster.getLayer()==layer)
                selectedClusters.add(cluster);
        }
        return selectedClusters;
    }
    
    @Override
    public String toString(){
        StringBuilder str = new StringBuilder();
        str.append(String.format("----> cluster  ( %3d %3d )  ENERGY = %12.5f\n", 
                this.desc.getSector(),this.desc.getLayer(), this.getEnergy()));
        str.append(this.clusterLine.toString());
        str.append("\n");
        for(URWRStrip strip : this){
            str.append("\t\t");
            str.append(strip.toString());
            str.append("\n");
        }
        
        return str.toString();
    }

    
    


}
