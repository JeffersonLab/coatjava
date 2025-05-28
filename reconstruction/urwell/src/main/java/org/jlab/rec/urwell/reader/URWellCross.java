package org.jlab.rec.urwell.reader;

import java.util.List;
import org.jlab.detector.base.DetectorDescriptor;
import org.jlab.detector.base.DetectorType;
import org.jlab.geom.prim.Point3D;
import org.jlab.service.urwell.URWellConstants;
/**
 *
 * @author Tongtong Cao
 */

public class URWellCross {

    private DetectorDescriptor desc = new DetectorDescriptor(DetectorType.URWELL);
    private Point3D global;
    private Point3D local; // Points in local coordinates
    private int region;
    private double energy = 0;
    private double time = 0;
    private int id = -1;
    private int cluster1 = -1;
    private int cluster2 = -1;
    private int status = -1;
    private int tid = -1; // Track id;
    private URWellCluster cls1 = null;
    private URWellCluster cls2 = null;
    private static double _lxR1RelativeDCSL1 = 1 - (URWellConstants.DCSL1L1ZTSC - URWellConstants.URWELLLOCALZR1)/URWellConstants.INTERVALDCSL1L1L2TSC; // x of R1 relative to DC SL1 in LC    
    private double _lyR1RelativeDCSL1 = -999; // y of R1 relative to DC SL1 in LC 
    private double _xRelativeDCSL1AtPlaneY0TSC = -999; // x of R1 cross relative to DC SL1 at the plan y = 0 in TSC 
    private double _xRelativeDCSL2AtPlaneY0TSC = -999; // x of R1 cross relative to DC SL2 at the plan y = 0 in TSC
    private double _xErrRelativeDCAtPlaneY0TSC = -999; // x error of R1 cross relative to DC SL1 or SL2 at the plan y = 0 in TSC 

    public URWellCross(int id, int sector, int region, double x, double y, double z, double energy, double time, int cluster1, int cluster2, int status) {
        this.id = id;
        this.desc.setSectorLayerComponent(sector, 0, 0);
        this.region = region;
        this.global = new Point3D(x, y, z);
        this.local = new Point3D(x, y, z);
        local.rotateZ(Math.toRadians(-60 * (sector - 1)));
        local.rotateY(Math.toRadians(-25)); 
        this.energy = energy;
        this.time = time;
        this.cluster1 = cluster1;
        this.cluster2 = cluster2;
        this.status =  status;
        this._lyR1RelativeDCSL1 = getLyRelativeToDCSL1LC();
        this._xRelativeDCSL1AtPlaneY0TSC = getXRelativeToDCSL1AtPlaneY0TSC();
        this._xRelativeDCSL2AtPlaneY0TSC = getXRelativeToDCSL2AtPlaneY0TSC();  
        this._xErrRelativeDCAtPlaneY0TSC = getXErrRelativeToDCAtPlaneY0TSC(); 
    }
    
    public URWellCross(int id, int tid, int sector, int region, double x, double y, double z, double x_local, double y_local, double z_local, double energy, double time, int cluster1, int cluster2, int status) {
        this.id = id;
        this.tid = tid;
        this.desc.setSectorLayerComponent(sector, 0, 0);
        this.region = region;
        this.global = new Point3D(x, y, z);
        this.local = new Point3D(x_local, y_local, z_local);
        this.energy = energy;
        this.time = time;
        this.cluster1 = cluster1;
        this.cluster2 = cluster2;
        this.status =  status;
        this._lyR1RelativeDCSL1 = getLyRelativeToDCSL1LC();
        this._xRelativeDCSL1AtPlaneY0TSC = getXRelativeToDCSL1AtPlaneY0TSC();
        this._xRelativeDCSL2AtPlaneY0TSC = getXRelativeToDCSL2AtPlaneY0TSC(); 
        this._xErrRelativeDCAtPlaneY0TSC = getXErrRelativeToDCAtPlaneY0TSC(); 
    }
    
    URWellStateVec stateVec = null;
    
    public void setURWellStateVec(URWellStateVec svc){
        stateVec = svc;
    }
    
    public URWellStateVec getURWellStateVec(){
        return stateVec;
    }

    /**
    * return track id
    */
    
    public int get_tid(){
        return tid;
    }
    
    /**
    * @param tid track id
    */
    
    public void set_tid(int tid){
        this.tid = tid;
    }

    public int id() {
        return this.id;
    }
    
    public int sector() {
        return this.desc.getSector();
    }
    
    public int region() {
        return this.region;
    }

    public Point3D position() {
        return this.global;
    }

    public Point3D local() {
        return this.local;
    }

    public double energy() {
        return energy;
    }

    public double time() {
        return time;
    }

    public void setClusterIndex1(int cluster) {
        this.cluster1 = cluster;
    }

    public void setClusterIndex2(int cluster) {
        this.cluster2 = cluster;
    }
    
    public int cluster1() {
        return this.cluster1;
    }
    
    public int cluster2() {
        return this.cluster2;
    }
    
    public int status() {
        return this.status;
    }
    
    public void setCluster1(URWellCluster cluster) {        
        cls1 = cluster;
    }

    public void setCluster2(URWellCluster cluster) {
        cls2 = cluster;
    }
    
    public URWellCluster getCluster1() {
        return cls1;
    }

    public URWellCluster getCluster2() {
        return cls2;
    }
    
    public void setCluster1(List<URWellCluster> urClusters) {
        URWellCluster cluster = null;
        
        for (URWellCluster cl : urClusters) {
            if (cl.id() == cluster1) {
                cluster = cl;
                break;
            }
        }
        
        cls1 = cluster;
    }

    public void setCluster2(List<URWellCluster> urClusters) {
        URWellCluster cluster = null;
        
        for (URWellCluster cl : urClusters) {
            if (cl.id() == cluster2) {
                cluster = cl;
                break;
            }
        }
        
        cls2 = cluster;
    }    

    public URWellCluster getCluster1(List<URWellCluster> urClusters) {
        URWellCluster cluster = null;
        
        for (URWellCluster cl : urClusters) {
            if (cl.id() == cluster1) {
                cluster = cl;
                break;
            }
        }
        
        return cluster;
    }

    public URWellCluster getCluster2(List<URWellCluster> urClusters) {
        URWellCluster cluster = null;
        
        for (URWellCluster cl : urClusters) {
            if (cl.id() == cluster2) {
                cluster = cl;
                break;
            }
        }
        
        return cluster;
    }
    
    public static double getLxRelativeDCSL1LC(){
        return _lxR1RelativeDCSL1;
    }
        
    private double getLyRelativeToDCSL1LC(){
        if(local != null) {
            double xAlongDCSL1PlaneY0TSC = local.x()  - local.y() * Math.tan(Math.toRadians(6));
            double lyR1RelativeDCSL1 = (xAlongDCSL1PlaneY0TSC - URWellConstants.DCSL1L1W1XTSC) * Math.cos(Math.toRadians(6))/URWellConstants.INTERVALDCSL1L1L2TSC + URWellConstants.YDCSL1L1W1LC;
            return lyR1RelativeDCSL1;
        }
        else return -999;
    }
    
    public double getLyRelativeDCSL1LC(){
        return _lyR1RelativeDCSL1;
    }
    
    private double getXRelativeToDCSL1AtPlaneY0TSC(){
        return local.x() - local.y() * Math.tan(Math.toRadians(6));
    }
    
    private double getXRelativeToDCSL2AtPlaneY0TSC(){
        return local.x() + local.y() * Math.tan(Math.toRadians(6));
    }
    
    private double getXErrRelativeToDCAtPlaneY0TSC(){
        return Math.sqrt(URWellConstants.URWELLXRESOLUTION * URWellConstants.URWELLXRESOLUTION + 
                URWellConstants.URWELLYRESOLUTION * URWellConstants.URWELLYRESOLUTION * Math.tan(Math.toRadians(6)) * Math.tan(Math.toRadians(6)));
    }
    
    public double getXRelativeDCSL1AtPlaneY0TSC(){
        return _xRelativeDCSL1AtPlaneY0TSC;
    }
    
    public double getXRelativeDCSL2AtPlaneY0TSC(){
        return _xRelativeDCSL2AtPlaneY0TSC;
    }
    
    public double getXErrRelativeDCAtPlaneY0TSC(){
       return _xErrRelativeDCAtPlaneY0TSC; 
    }
}
