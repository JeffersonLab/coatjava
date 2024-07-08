/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.ml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.geom.prim.Vector3D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.cross.Cross;
import org.jlab.rec.cvt.hit.Hit;
import static org.jlab.rec.cvt.ml.CVTClustering.useMC;
import org.jlab.rec.cvt.mlanalysis.AIObject;
import org.jlab.rec.cvt.services.CVTReconstruction;
import org.jlab.rec.cvt.services.TracksFromTargetRec;
import org.jlab.rec.cvt.track.Seed;
import org.jlab.rec.cvt.track.Track;
import org.jlab.rec.cvt.Constants;
/**
 *
 * @author ziegler
 */
public class ConvResolver extends CVTInitializer {
    private double[] aistatus = new double[]{0};
    
    @Override
    public boolean processDataEvent(DataEvent event) {
        this.initialize(event);
        if(useMC) {
            this.processMC(event);
        } else {
            this.processData(event);
        }
        return true;
    }

    private void processData(DataEvent event) {
        ClusterBankIO aicr = new ClusterBankIO();
        
        List<DataBank> banks = new ArrayList<>();
        List<Cluster> aicls = aicr.getMLClusters(event, swimmer, aistatus);
        List<ArrayList<Cluster>>    clusters = new ArrayList<>();
        clusters.add(aicr.getSVTClusters());
        clusters.add(aicr.getBMTClusters());
        
        List<ArrayList<Cross>>    crosses = reco.findCrosses(aicls);
        if(crosses != null) {
            TracksFromTargetRec  trackFinder = new TracksFromTargetRec(swimmer, xyBeam);
            trackFinder.totTruthHits = reco.getTotalNbTruHits();
            List<Seed>   seeds = trackFinder.getSeeds(clusters, crosses);

            List<Track> tracks = trackFinder.getTracks(event, this.isInitFromMc(), 
                                                              this.isKfFilterOn(), 
                                                              this.getKfIterations(), 
                                                              true, this.getPid());
            DataBank aibank = null;
            
            if(event.hasBank("cvtml::clusters")) {
                aibank=event.getBank("cvtml::clusters");
            }
            event.removeBank("cvtml::clusters");
            ClusterBankIO.fillBank(aibank, clusters.get(0), clusters.get(1));
            
            banks.add(aibank);
        }
        
        event.appendBanks(banks.toArray(new DataBank[0]));
    }
    final int NSIGMA = 5;
    final double SIGMA_P = 5; // 3;
    final double SIGMA_PHI = 0.3;//0.57; //0.3; //0.12;
    final double SIGMA_THETA = 0.5;//0.57; //0.3; //0.2;
    public double[] trackParsDiff(org.jlab.rec.cvt.track.Track t, 
                                 double mcP, double mcTheta, double mcPhi ) {
        double[] result = null;
        Vector3D p = t.getHelix().getPXYZ(Constants.getSolenoidMagnitude());
        double P = p.mag();
        double Theta = p.theta();
        double Phi = p.phi();
        double dp = (P-mcP)/mcP;
        double dth = Math.toDegrees(Theta-mcTheta);
        double dph = Math.toDegrees(Phi-mcPhi);
        if(Math.abs(dph)>Math.PI) dph -= Math.signum(dph)*2*Math.PI;
       
        if(Math.abs(dp)<NSIGMA*SIGMA_P && 
                Math.abs(dth)<NSIGMA*SIGMA_THETA && 
                Math.abs(dph)<NSIGMA*SIGMA_PHI) { 
              result = new double[] {Math.abs(dp), Math.abs(dth), Math.abs(dph)};                       
        }
        return result;
    }
    private void processMC(DataEvent event) {
        ClusterBankIO aicr = new ClusterBankIO();
        if(!event.hasBank("cvtml::hits")) return;
        DataBank aihbank = event.getBank("cvtml::hits");
        
        Map<Integer, int[]> ahitmap = new HashMap<>();
        for(int i = 0; i<aihbank.rows(); i++) {
            int id = aihbank.getShort("id", i);
            int mcstat = aihbank.getShort("status", i);
            int trid = aihbank.getShort("truetid", i);
            int tid = aihbank.getShort("tid", i);
            ahitmap.put(id, new int[] {mcstat, trid, tid});
        }
        Map<Integer, Hit>hitMap = new HashMap<>();   
        List<DataBank> banks = new ArrayList<>();
        List<Cluster> aicls = aicr.getMLClusters(event, swimmer, aistatus);
        for(Cluster c : aicls) {
            for(Hit h : c) { 
                hitMap.put(h.getId(), h);
                if(ahitmap.containsKey(h.getId())) {
                    int[] bvls = ahitmap.get(h.getId());
                    h.MCstatus= bvls[0];
                    h.setTrueAssociatedTrackID(bvls[1]);
                    h.setAssociatedTrackID(bvls[2]);
                }
            }
        }
       
        List<ArrayList<Cluster>>    useClustersSplit = new ArrayList<>();
        useClustersSplit.add(aicr.getSVTClusters());
        useClustersSplit.add(aicr.getBMTClusters());
        List<ArrayList<Cross>>    crosses = reco.findCrosses(aicls);
       
        
        if(crosses == null) return ;
        double[] xyBeam = CVTReconstruction.getBeamSpot(event, beamPos);
        TracksFromTargetRec  trackFinder = new TracksFromTargetRec(swimmer, xyBeam);
        //trackFinder.totTruthHits = reco.getTotalNbTruHits();
        trackFinder.use6LayerSVT=true;
        List<Seed>   seeds = trackFinder.getSeeds(useClustersSplit, crosses);
        
        List<org.jlab.rec.cvt.track.Track> tracks = trackFinder.getTracks(event, this.isInitFromMc(), 
                                                          this.isKfFilterOn(), 
                                                          this.getKfIterations(), 
                                                          true, this.getPid());
        
       if (!event.hasBank("MC::Particle")) return ;
        DataBank bank = event.getBank("MC::Particle");
        if(bank==null) return ;
        List<AIObject> aios = new ArrayList<>();
        double[] pars = MCMLTracking.mcTrackPars(event).get(0);
        double mcPx = pars[3];
        double mcPy = pars[4];
        double mcPz = pars[5];
        double mcVx = pars[0];
        double mcVy = pars[1];
        double mcVz = pars[2];
        
        double mcP = Math.sqrt(mcPx*mcPx+mcPy*mcPy+mcPz*mcPz);
        double mcTheta = Math.acos(mcPz/mcP);
        double mcPhi = Math.atan2(mcPy,mcPx);
        
        org.jlab.rec.cvt.track.Track matchedTrack=null;
        double delP = Double.POSITIVE_INFINITY;
        if(tracks!=null) {
            for(org.jlab.rec.cvt.track.Track t : tracks) { 
                double[] m=this.trackParsDiff(t, mcP, mcTheta, mcPhi);
                if(m!=null) {
                    if(m[0]<delP) {
                        delP=m[0];
                        matchedTrack=t; 
                    }
                }
            }
            if(matchedTrack!=null) { 
                matchedTrack.setId(-111);
            }
            int ta =2;
            for(org.jlab.rec.cvt.track.Track t : tracks) {
                if(t.getId()==-111) continue;
                t.setId(ta++);
            }
            if(matchedTrack!=null) {
                matchedTrack.setId(1);
            }
            for(org.jlab.rec.cvt.track.Track t : tracks) {
                t.resetIds(t.getId());
            }
        }
        
        for(Cluster c : aicls) { 
            //if(c.getAssociatedTrackID()==-1) { 
                double isnotBG = 0;
                double W =0;
                for(Hit h : c) { 
                    W+=h.getStrip().getEdep();
                    if(h.getTrueAssociatedTrackID()!=-1) {
                        isnotBG +=h.getStrip().getEdep();
                    }
                }
                double perTru = isnotBG/W;
                if(perTru>MCMLTracking.perTruHitOnCls) {
                    c.BG=1;
                    for(Hit h : c) {
                        if(h.getTrueAssociatedTrackID()!=-1) {
                            c.associatedTrueTrkId = h.getTrueAssociatedTrackID(); 
                        }
                    }
                } else {
                    c.BG=0;
                    c.associatedTrueTrkId = -1;
                }
                //if(c.getSector()==aihs.svtTrkSectors[c.getRegion()-1] 
                //        || c.getSector()-1==aihs.svtTrkSectors[c.getRegion()-1]
                //        || c.getSector()+1==aihs.svtTrkSectors[c.getRegion()-1]) {
                    
                    //System.out.println(c.toString());
                    AIObject aio =MCMLTracking.AIBankEntry(c,  mcP, mcTheta, mcPhi, mcVx, mcVy, mcVz);
                    aios.add(aio);
                //}
            //}
        }
       
       
        event.removeBank("cvtml::hits");
        DataBank aihbank2 = event.createBank("cvtml::hits", aihbank.rows());
        
        for(int i = 0; i<aihbank.rows(); i++) {
            int id = aihbank.getShort("id", i);
            int mcstat = aihbank.getShort("status", i);
            int trid = aihbank.getShort("truetid", i);
            int tid = aihbank.getShort("tid", i);
            if(hitMap.containsKey(id)) { 
                tid=hitMap.get(id).getAssociatedTrackID();
                if(trid>-1) {
                    mcstat = 1;
                } else {
                    mcstat=0;
                }
            }
            aihbank2.setShort("id", i, (short) id);
            aihbank2.setShort("status", i, (short) mcstat);
            aihbank2.setShort("truetid", i, (short) trid);
            aihbank2.setShort("tid", i, (short) tid);
        }
        banks.add(aihbank2);
        
        DataBank aibank = null;
        if(event.hasBank("cvtml::clusters")) {
            aibank=event.getBank("cvtml::clusters");
        }
        event.removeBank("cvtml::clusters");
        DataBank aibank2 = event.createBank("cvtml::clusters", aibank.rows());
        ClusterBankIO.fillBank(aibank2, aios);
        banks.add(aibank2);
        
        event.appendBanks(banks.toArray(new DataBank[0]));
       
    }
}
