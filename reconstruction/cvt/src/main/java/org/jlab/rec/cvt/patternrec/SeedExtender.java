/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.patternrec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.clas.pdg.PDGDatabase;
import org.jlab.clas.swimtools.Swim;
import org.jlab.clas.tracking.kalmanfilter.AKFitter;
import org.jlab.clas.tracking.kalmanfilter.Surface;
import org.jlab.clas.tracking.kalmanfilter.Units;
import org.jlab.clas.tracking.kalmanfilter.helical.KFitter;
import org.jlab.clas.tracking.trackrep.Helix;
import org.jlab.detector.base.DetectorType;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.rec.cvt.Constants;
import org.jlab.rec.cvt.bmt.BMTType;
import org.jlab.rec.cvt.cross.Cross;
import org.jlab.rec.cvt.measurement.MLayer;
import org.jlab.rec.cvt.measurement.Measurements;
import org.jlab.rec.cvt.track.Seed;

/**
 *
 * @author veronique
 */
public class SeedExtender {
    
    private final double xbeam;
    private final double ybeam;
    private Swim swimmer;
    
    public SeedExtender(Swim swimmer, double xb, double yb) {
        this.swimmer = swimmer;
        xbeam = xb;
        ybeam = yb;
    }
    
    public List<Cross> match2BMTCrosses(Seed s, Map<Integer, Surface> surfaceMap, List<Cross> bmtCrosses) {
        List<Cross> matches = new ArrayList<>(); 
        int penalty=0;
        this.refineSeed(s);
        Map<Long, Point3D> ktrj = s.getTrajectory();
        if(ktrj==null) return matches;
        
        Map<Long, List<Cross>> crs = new HashMap<>();
        for(Cross c : bmtCrosses) {
            long seclayr = key(c.getSector(), c.getCluster1().getLayer()+6);
            crs
                .computeIfAbsent(seclayr, k -> new ArrayList<>())
                .add(c);
        }
        
        for(long sl : ktrj.keySet()) {
            if(crs.containsKey(sl)) {  
                List<Cross> crssl = crs.get(sl);
                BMTType type = crssl.getFirst().getType();
                Point3D p3 = ktrj.get(sl);
                Cross c = this.findClosestCross(crssl, type, p3);
                if(c!=null) {
                    matches.add(c);
                } else {
                    penalty++;
                }
            }
        }
        
        s.penalty = penalty;
        return matches;        
    }
    
    private Cross findClosestCross(List<Cross> crssl, BMTType type, Point3D p3 ) {
        Cross closest = null;
        double minDelta = Double.POSITIVE_INFINITY;
        double delta = 0;
        
        for(Cross c : crssl) { 
            if(type==BMTType.C) {
                delta = Math.abs(c.getPoint().z()-p3.z());
            } else {
                delta = Math.sqrt((c.getPoint().x()-p3.x())*(c.getPoint().x()-p3.x()) +
                                  (c.getPoint().y()-p3.y())*(c.getPoint().y()-p3.y()));
            }
            
            if(delta<minDelta) {
                minDelta = delta;
                closest = c;
            }
        }
        if(passClosenessCutOff(closest, minDelta)) {
            return closest;
        }
        
        return null;
    }
    
    private boolean passClosenessCutOff(Cross closest, double minDelta) {
        boolean pass = false;
        if(closest==null)
            return pass;
        double metric = 10; 
        int layer = closest.getCluster1().getLayer();
        if(layer==1 || layer==4 || layer==6) {
            metric = 10;
        } else {
            metric = 1;
        }
        
        if(minDelta < metric)
            pass = true;
        
        return pass;
    }
    
    static long key(int sector, int layer) {
        return (((long) sector) << 32) | (layer & 0xffffffffL);
    }
    
    /**
     * Using KFitter to refine the seed
     */
    public void refineSeed(Seed seed) {
        double solenoidScale = Constants.getInstance().getSolenoidScale();
        double solenoidValue = Constants.getInstance().getSolenoidMagnitude(); // already the absolute value
        Map<Integer, Point3D> trj = new HashMap<>();
        
        KFitter kf = new KFitter(true, 5, Constants.KFDIR, swimmer, Constants.getInstance().KFMatrixLibrary);
        kf.polarity = (int) Math.signum(Constants.getSolenoidScale()); 
        
        Measurements measure = new Measurements(xbeam, ybeam, Constants.getInstance().kfBeamSpotConstraint());
        
        int pid = 211;
        List<Surface> surfaces = measure.getMeasurements(seed);

        Point3D  v = seed.getHelix().getVertex(); 
        Vector3D p = seed.getHelix().getPXYZ(solenoidValue);
        if(Constants.getInstance().seedingDebugMode)
            System.out.println("Seed vtx = "+v.toString()+" Seed p = "+p.toString());

        if(Constants.getInstance().preElossCorrection && pid!=Constants.DEFAULTPID) {
            double pcorr = measure.getELoss(p.mag(), PDGDatabase.getParticleMass(pid));
            p.scale(pcorr/p.mag());
        }

        int charge = (int) (Math.signum(solenoidScale)*seed.getHelix().getCharge());
        if(solenoidValue<0.001)
            charge = 1;

        Helix hlx = new Helix(v.x(),v.y(),v.z(),p.x(),p.y(),p.z(), charge,
                        solenoidValue, xbeam , ybeam, Units.MM);
        double[][] cov = Constants.COVHELIX;
        if(solenoidValue>0.001 && seed.getHelix().radius() <Constants.getInstance().getRCUT())    
            return ;
        if(Constants.getInstance().seedingDebugMode)
            System.out.println("Refine seed initializing fitter...for "+seed.toString());
        kf.init(hlx, cov, xbeam, ybeam, 0, surfaces, PDGDatabase.getParticleMass(pid));
        kf.runFitter();

        if(Constants.getInstance().seedingDebugMode)
            System.out.println("KF status ... failed "+kf.setFitFailed+" ndf "+kf.NDF+" helix "+kf.getHelix());
        if (kf.setFitFailed == false && kf.NDF>0 && kf.getHelix()!=null) { 
            seed.getHelix().setDCA(kf.getHelix().getD0());
            seed.getHelix().setCurvature(kf.getHelix().getOmega());
            seed.getHelix().setTanDip(kf.getHelix().getTanL());
            seed.getHelix().setPhiAtDCA(kf.getHelix().getPhi0());
            seed.getHelix().setZ0(kf.getHelix().getZ0());
            Point3D  trackPos = new Point3D();
            Vector3D trackDir = new Vector3D();
            Map<Long, Point3D> ktrj = new HashMap<>();
            if(kf.trajPoints!=null ) {
                for(int l = 1; l<=6; l++) {
                    int index = MLayer.getType(DetectorType.BST, l).getIndex();
                    AKFitter.HitOnTrack trajPoint = kf.trajPoints.get(index);
                    long key = (((long) trajPoint.sector) << 32) | (l & 0xffffffffL);
                    ktrj.put(key, new Point3D(trajPoint.x, trajPoint.y, trajPoint.z));
                    int index2 = MLayer.getType(DetectorType.BMT, l).getIndex();
                    AKFitter.HitOnTrack trajPoint2 = kf.trajPoints.get(index2);
                    int l2 = l+6;
                    long key2 = (((long) trajPoint2.sector) << 32) | (l2 & 0xffffffffL);
                    
                    ktrj.put(key2, new Point3D(trajPoint2.x, trajPoint2.y, trajPoint2.z));
                }
                seed.setTrajectory(ktrj);
            }
            for(Cross c : seed.getCrosses()) {
                if(kf.trajPoints!=null ) {
                    int layer = c.getCluster1().getLayer();
                    int index = MLayer.getType(c.getDetector(), layer).getIndex();
                    AKFitter.HitOnTrack traj = kf.trajPoints.get(index);
                    if(traj==null) return; 
                    trackPos.set(traj.x, traj.y, traj.z);
                    trackDir.setXYZ(traj.px, traj.py, traj.pz);
                    c.update(trackPos, trackDir.asUnit());
                }
            }
        } else {
            seed.failed=true;
        }
    }
    
    public void extendSeedToBMT(Seed seed, List<Cross> bMTCrosses) {
        Map<Integer, Surface> surfaceMap = new HashMap<>();
        Helix helix = seed.getHelix().getKFHelix();
        RoadSurfaces.mapRoadSurfaces(helix, surfaceMap);
        if(surfaceMap==null) return;
        List<Cross> matchedBMTCrosses = match2BMTCrosses(seed, surfaceMap,bMTCrosses);
        if(matchedBMTCrosses.isEmpty()) return;
        Seed sclone = seed.clone();
        for(Cross c : matchedBMTCrosses) {
            sclone.add_Cluster(c.getCluster1());
        }
        sclone.add_Crosses(matchedBMTCrosses);
        sclone.fit(3, xbeam, ybeam, Constants.getSolenoidMagnitude());
        sclone.update_Crosses(xbeam, ybeam);
        refineSeed(sclone);
        List<Cross> matchedBMTCrosses2 = match2BMTCrosses(seed, surfaceMap,bMTCrosses);
        for(Cross c : matchedBMTCrosses2) {
            seed.add_Cluster(c.getCluster1());
        }
        seed.add_Crosses(matchedBMTCrosses2);
        seed.fit(3, xbeam, ybeam, Constants.getSolenoidMagnitude());
        seed.update_Crosses(xbeam, ybeam);
    }
}
