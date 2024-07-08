/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.ml;

import java.util.ArrayList;
import java.util.List;
import org.jlab.detector.base.DetectorType;
import org.jlab.geom.prim.Point3D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.bmt.BMTType;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.rec.cvt.mlanalysis.AIObject;

/**
 *
 * @author ziegler
 */
public class MCMLTracking {
    
    public static double perTruHitOnCls=0.75;
    public static List<AIObject> AIBankEntry(org.jlab.rec.cvt.track.Track t, 
            double mcPtk, double mcThetatk, double mcPhitk, double mcVxtk, double mcVytk, double mcVztk) {
        double mcP=999;
        double mcTheta=999;
        double mcPhi=999;
        double mcVx=999;
        double mcVy=999;
        double mcVz=999;
        List<AIObject> aios = new ArrayList<>();
        for(Cluster c: t.getSeed().getClusters()) {
            double isnotBG = 0;
            double W =0;
            for(Hit h : c) {
                W+=h.getStrip().getEdep();
                if(h.getTrueAssociatedTrackID()!=-1) {
                    isnotBG +=h.getStrip().getEdep();
                }
            }
            double perTru = isnotBG/W;
            if(perTru>perTruHitOnCls) {
                c.BG=1;
                for(Hit h : c) { 
                    if(h.getTrueAssociatedTrackID()!=-1) {
                        c.associatedTrueTrkId = h.getTrueAssociatedTrackID();
                    }
                }
            } 
        }
        for(Cluster c: t.getSeed().getClusters()) {
            int layr = c.getLayer();
            if(c.getDetector()==DetectorType.BMT)
                    layr+=6;
            Point3D ep1=null;
            Point3D ep2=null;
            if(c.getType()==BMTType.C) {
                ep1 = c.getArc().origin();
                ep2 = c.getArc().end();
            } else {
                ep1 = c.getLine().origin();
                ep2 = c.getLine().end();
            }
           
            int onTrk = c.associatedTrueTrkId;

            if(onTrk==-1) onTrk=0;
            if(onTrk==1) {
                 mcP=mcPtk;
                 mcTheta=mcThetatk;
                 mcPhi=mcPhitk;
                 mcVx = mcVxtk;
                 mcVy = mcVytk;
                 mcVz = mcVztk;
                
                if(c.BG==1 || c.BG==0) {
                    AIObject aio = new AIObject();
                    aio.id=(short) c.getId();
                    aio.status=(short) c.BG;
                    aio.sector=(byte)c.getSector();
                    aio.layer=(byte)layr;
                    aio.xo=(float)ep1.x();
                    aio.yo=(float)ep1.y();
                    aio.zo=(float)ep1.z();
                    aio.xe=(float)ep2.x();
                    aio.ye=(float)ep2.y();
                    aio.ze=(float)ep2.z();
                    aio.x=(float)mcVx;
                    aio.y=(float)mcVy;
                    aio.z=(float)mcVz;
                    aio.p=(float)mcP;
                    aio.th=(float)mcTheta;
                    aio.fi=(float)mcPhi;
                    aios.add(aio);
                }
            }
        }
        
        return aios;
    }
    
    public static AIObject AIBankEntry(Cluster c, 
            double mcPtk, double mcThetatk, double mcPhitk, double mcVxtk, double mcVytk, double mcVztk) {

        double mcP=999;
        double mcTheta=999;
        double mcPhi=999;
        double mcVx=999;
        double mcVy=999;
        double mcVz=999;
        int layr = c.getLayer();
        if(c.getDetector()==DetectorType.BMT)
                layr+=6;
        Point3D ep1=null;
        Point3D ep2=null;
        if(c.getType()==BMTType.C) {
            ep1 = c.getArc().origin();
            ep2 = c.getArc().end();
        } else {
            ep1 = c.getLine().origin();
            ep2 = c.getLine().end();
        }
        int onTrk = c.associatedTrueTrkId; 
        if(onTrk==-1) onTrk=0;
        if(onTrk!=-1) {
            mcP=mcPtk;
            mcTheta=mcThetatk;
            mcPhi=mcPhitk;
            mcVx=mcVxtk;
            mcVy=mcVytk;
            mcVz=mcVztk;
       }
        if(c.BG==0){
            mcP=999;
            mcTheta=999;
            mcPhi=999;
            mcVx=999;
            mcVy=999;
            mcVz=999;
        }
        AIObject aio = new AIObject();
        aio.id=(short) c.getId();
        aio.status=(short) c.BG;
        aio.sector=(byte)c.getSector();
        aio.layer=(byte)layr;
        aio.xo=(float)ep1.x();
        aio.yo=(float)ep1.y();
        aio.zo=(float)ep1.z();
        aio.xe=(float)ep2.x();
        aio.ye=(float)ep2.y();
        aio.ze=(float)ep2.z();
        aio.x=(float)mcVx;
        aio.y=(float)mcVy;
        aio.z=(float)mcVz;
        aio.p=(float)mcP;
        aio.th=(float)mcTheta;
        aio.fi=(float)mcPhi;
        
        return aio;
        
        
    }
    
    private static int PidToCharge(int pid) {
        
        if(Math.abs(pid)>44) {
            return (int) Math.signum(pid);
        } else {
            return (int) -Math.signum(pid);
        }
    }
    
    public static List<double[]> mcTrackPars(DataEvent event) {
        List<double[]> result = new ArrayList<>();
        if (event.hasBank("MC::Particle") == false) {
            return result;
        }
        DataBank bank = event.getBank("MC::Particle");
        
        // fills the arrays corresponding to the variables
        if(bank!=null) {
            for(int i = 0; i < bank.rows(); i++) {
                double[] value = new double[7];
                value[0] = (double) bank.getFloat("vx", i);
                value[1] = (double) bank.getFloat("vy", i);
                value[2] = (double) bank.getFloat("vz", i);
                value[3] = (double) bank.getFloat("px", i);
                value[4] = (double) bank.getFloat("py", i);
                value[5] = (double) bank.getFloat("pz", i);
                value[6] = PidToCharge(bank.getInt("pid", i));
                
                result.add(value);
            }
        }
        return result;
    }
   
}
