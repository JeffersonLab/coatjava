package org.jlab.rec.urwell.reader;

import org.jlab.detector.base.DetectorDescriptor;
import org.jlab.detector.base.DetectorType;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Line3D;
import org.jlab.service.urwt.URWTConstants;

/**
 *
 * @author Tongtong Cao
 */

public class URWellCluster {
    
    private int id = -1;
    private DetectorDescriptor desc = new DetectorDescriptor(DetectorType.URWT);
    private int size = 0;
    private double energy = 0;
    private double time = 0;
    private int crossIndex = -1;
    private double stereo = 10.;
    private Line3D lineLocal = new Line3D();
    private boolean isHBTimeCoinc = true;

    public URWellCluster(int id, int sector, int layer, int component, int size, double energy, double time, Point3D pointOrigin, Point3D pointEnd) {
        this.id = id;
        this.desc.setSectorLayerComponent(sector, layer, component);
        this.size = size;
        this.energy = energy;
        this.time = time;
        stereo = Math.pow(-1, layer) * URWTConstants.STEREO[(layer-1)/2]; // stereo angle is negative for odd layers, while positive for even layers
        
        Point3D pointOriginLocal = new Point3D();
        pointOriginLocal.copy(pointOrigin);
        pointOriginLocal.rotateZ(Math.toRadians(-60 * (sector - 1)));
        pointOriginLocal.rotateY(Math.toRadians(-25));                    
        Point3D pointEndLocal = new Point3D();
        pointEndLocal.copy(pointEnd);
        pointEndLocal.rotateZ(Math.toRadians(-60 * (sector - 1)));
        pointEndLocal.rotateY(Math.toRadians(-25));   
        lineLocal.set(pointOriginLocal, pointEndLocal);                     
    }
    
    public int id() {
        return this.id;
    }
    
    public int sector() {
        return this.desc.getSector();
    }

    public int layer() {
        return this.desc.getLayer();
    }

    public int strip() {
        return this.desc.getComponent();
    }

    public int size() {
        return size;
    }

    public double energy() {
        return energy;
    }

    public double time() {
        return time;
    }

    public int getCrossIndex() {
        return crossIndex;
    }

    public void setCrossIndex(int crossIndex) {
        this.crossIndex = crossIndex;
    }    
    
    public double getStereo(){
        return stereo;
    }
    
    public Line3D getLineLocal(){
        return lineLocal;
    } 
    
    public void setIsHBTimeCoinc(boolean isHBTimeCoinc){
        this.isHBTimeCoinc = isHBTimeCoinc;
    }
    
    public boolean getIsHBTimeCoinc(){
        return isHBTimeCoinc;
    }
}
