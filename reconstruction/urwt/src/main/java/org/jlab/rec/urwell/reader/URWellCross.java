package org.jlab.rec.urwell.reader;

import java.util.List;
import java.util.ArrayList;
import org.jlab.detector.base.DetectorDescriptor;
import org.jlab.detector.base.DetectorType;
import org.jlab.geom.prim.Point3D;
import org.jlab.service.urwt.URWTConstants;
/**
 *
 * @author Tongtong Cao
 */

public class URWellCross {

    private DetectorDescriptor desc = new DetectorDescriptor(DetectorType.URWT);
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
    private double _lxRelativeDCSL1 = -999; // x of uRWell cross relative to DC SL1 in LC 
    private double _lyRelativeDCSL1 = -999; // y of uRWell cross relative to DC SL1 in LC 
    private double _xRelativeDCSL1AtPlaneY0TSC = -999; // x of R1 cross relative to DC SL1 at the plan y = 0 in TSC 
    private double _xRelativeDCSL2AtPlaneY0TSC = -999; // x of R1 cross relative to DC SL2 at the plan y = 0 in TSC
    private double _xErrRelativeDCAtPlaneY0TSCHB = -999; // x error of R1 cross relative to DC SL1 or SL2 at the plan y = 0 in TSC 
    
    List<URWellStateVec> stateVecs = new ArrayList(); // States on clusters after tracking
    
    private double residual = -1;
    
    private int NNTrkId = -1;

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
        this._lxRelativeDCSL1 = getLxRelativeToDCSL1LC();
        this._lyRelativeDCSL1 = getLyRelativeToDCSL1LC();
        this._xRelativeDCSL1AtPlaneY0TSC = getXRelativeToDCSL1AtPlaneY0TSC();
        this._xRelativeDCSL2AtPlaneY0TSC = getXRelativeToDCSL2AtPlaneY0TSC();  
        this._xErrRelativeDCAtPlaneY0TSCHB = getXErrRelativeToDCAtPlaneY0TSCHB(); 
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
        this._lxRelativeDCSL1 = getLxRelativeToDCSL1LC();
        this._lyRelativeDCSL1 = getLyRelativeToDCSL1LC();
        this._xRelativeDCSL1AtPlaneY0TSC = getXRelativeToDCSL1AtPlaneY0TSC();
        this._xRelativeDCSL2AtPlaneY0TSC = getXRelativeToDCSL2AtPlaneY0TSC(); 
        this._xErrRelativeDCAtPlaneY0TSCHB = getXErrRelativeToDCAtPlaneY0TSCHB(); 
    }   
    
    public void setURWellStateVecs(List<URWellStateVec> svcs){
        this.stateVecs.clear();
        this.stateVecs.addAll(svcs);
    }
    
    public List<URWellStateVec> getURWellStateVecs(){
        return stateVecs;
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
    
    private double getLxRelativeToDCSL1LC(){     
        if(local != null) {
            return 1 - (URWTConstants.DCSL1L1ZTSC - local.z())/URWTConstants.INTERVALDCSL1L1L2TSC;
        }
        else return -999;
    }
    
    public double getLxRelativeDCSL1LC(){        
        return _lxRelativeDCSL1;
    }
        
    private double getLyRelativeToDCSL1LC(){
        if(local != null) {
            double xAlongDCSL1PlaneY0TSC = local.x()  - local.y() * Math.tan(Math.toRadians(6));
            double lyRelativeDCSL1 = (xAlongDCSL1PlaneY0TSC - URWTConstants.DCSL1L1W1XTSC) * Math.cos(Math.toRadians(6))/URWTConstants.INTERVALDCSL1L1L2TSC + URWTConstants.YDCSL1L1W1LC;
            return lyRelativeDCSL1;
        }
        else return -999;
    }
    
    public double getLyRelativeDCSL1LC(){
        return _lyRelativeDCSL1;
    }
    
    private double getXRelativeToDCSL1AtPlaneY0TSC(){
        return local.x() - local.y() * Math.tan(Math.toRadians(6));
    }
    
    private double getXRelativeToDCSL2AtPlaneY0TSC(){
        return local.x() + local.y() * Math.tan(Math.toRadians(6));
    }
    
    private double getXErrRelativeToDCAtPlaneY0TSCHB(){
        return Math.sqrt(URWTConstants.URWELLXRESOLUTIONHB * URWTConstants.URWELLXRESOLUTIONHB + 
                URWTConstants.URWELLYRESOLUTIONHB * URWTConstants.URWELLYRESOLUTIONHB * Math.tan(Math.toRadians(6)) * Math.tan(Math.toRadians(6)));
    }
    
    public double getXRelativeDCSL1AtPlaneY0TSC(){
        return _xRelativeDCSL1AtPlaneY0TSC;
    }
    
    public double getXRelativeDCSL2AtPlaneY0TSC(){
        return _xRelativeDCSL2AtPlaneY0TSC;
    }
    
    public double getXErrRelativeDCAtPlaneY0TSCHB(){
       return _xErrRelativeDCAtPlaneY0TSCHB; 
    }
    
    public void setResidule(double residual){
        this.residual = residual;
    }
    
    public double getResidule(){
        return residual;
    }
    
    public void setNNTrkId(int NNTrkId){
        this.NNTrkId = NNTrkId;
    }
    
    public int getNNTrkId(){
        return NNTrkId;
    }
    
    public URWellCross clone(){
        URWellCross crsClone = new URWellCross(this.id, this.sector(), this.region, this.position().x(), this.position().y(), this.position().z(), this.energy, this.time, this.cluster1, this.cluster2, this.status);
        crsClone.set_tid(this.tid);
        crsClone.setCluster1(this.getCluster1());
        crsClone.setCluster2(this.getCluster2());
        crsClone.status =  this.status;
        crsClone._lxRelativeDCSL1 = this.getLxRelativeToDCSL1LC();
        crsClone._lyRelativeDCSL1 = this.getLyRelativeToDCSL1LC();
        crsClone._xRelativeDCSL1AtPlaneY0TSC = this.getXRelativeToDCSL1AtPlaneY0TSC();
        crsClone._xRelativeDCSL2AtPlaneY0TSC = this.getXRelativeToDCSL2AtPlaneY0TSC(); 
        crsClone._xErrRelativeDCAtPlaneY0TSCHB = this.getXErrRelativeToDCAtPlaneY0TSCHB(); 
        crsClone.getURWellStateVecs().clear();
        crsClone.getURWellStateVecs().addAll(this.getURWellStateVecs());
                    
        return crsClone;
    }
}
