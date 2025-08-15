package org.jlab.service.fmt;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.swimtools.Swim;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.base.GeometryFactory;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.io.base.*;
import org.jlab.rec.fmt.Constants;

import org.jlab.rec.fmt.banks.RecoBankWriter;
import org.jlab.rec.fmt.track.Track;
import org.jlab.rec.fmt.track.Trajectory;
import org.jlab.rec.fmt.track.fit.KFitter;
import org.jlab.rec.fmt.track.fit.StateVecs.StateVec;

import org.jlab.rec.urwell.reader.URWellCluster;
import org.jlab.rec.urwell.reader.URWellCross;
import org.jlab.rec.urwell.reader.URWellReader;

/**
 * Service to return reconstructed track candidates - the output is in hipo format
 *
 * @author ziegler, benkel, devita
 */
public class FMTEngine extends ReconstructionEngine {

    boolean debug = false;

    public FMTEngine() {
        super("FMT", "ziegler", "5.0");
    }

    @Override
    public boolean init() {
        
        // Get the constants for the correct variation
        String variation = this.getEngineConfigString("variation");
        if (variation!=null) {
            System.out.println("["+this.getName()+"] " +
                    "run with FMT geometry variation based on yaml = " + variation);
        }
        else {
            variation = "default";
            System.out.println("["+this.getName()+"] run with FMT default geometry");
        }
        
        String[] tables = new String[]{
            "/geometry/beam/position",
            "/calibration/mvt/fmt_time",
            "/calibration/mvt/fmt_status"
        };
        requireConstants(Arrays.asList(tables));
        this.getConstantsManager().setVariation(variation);

        // Load the geometry
        int run = 10;
        Constants.setDetector(GeometryFactory.getDetector(DetectorType.FMT,run, variation));        
        
        // Register output banks
        super.registerOutputBank("FMT::Tracks");
        super.registerOutputBank("FMT::Trajectory");
        
        return true;
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        // Initial setup.
        if(debug) System.out.println("\nNew event");
        
        // Set run number.
        DataBank runConfig = event.getBank("RUN::config");
        if (runConfig == null || runConfig.rows() == 0) return true;
        int run         = runConfig.getInt("run", 0);
                        
        // Set swimmer.
        Swim swimmer = new Swim();

        // Set beam shift. NOTE: Set to zero for the time being, when beam alignment is done
        //                       uncomment this code.
        IndexedTable beamOffset = this.getConstantsManager().getConstants(run, "/geometry/beam/position");
        double xB = beamOffset.getDoubleValue("x_offset", 0,0,0);
        double yB = beamOffset.getDoubleValue("y_offset", 0,0,0);       
        
                
        // === CLUSTERS ============================================================================
        URWellReader uRWellReader = new URWellReader(event);
        List<URWellCluster> clusters = uRWellReader.getUrwellClusters();
        List<URWellCross> crosses = uRWellReader.getUrwellCrosses();
        //System.out.println(clusters.size());
        //for (int i = 0; i < clusters.size(); i++) System.out.println(clusters.get(i).toString());
                
        // === DC TRACKS ===========================================================================
        Track trk = new Track();
        List<Track> tracks = trk.getDCTracks(event, swimmer);
        if(tracks.isEmpty()) return true;

        // === SEEDS =============================================================================
        Point3D target = new Point3D(0,0,0);
        for(int i=0; i<tracks.size(); i++) {
            Track track = tracks.get(i);                           
           
            List<URWellCross>[] trackCrosses = new ArrayList[Constants.NLAYERS/2];
            for(int j=0; j<trackCrosses.length; j++) trackCrosses[j] = new ArrayList<>(); 
            for(int j=0; j<crosses.size(); j++) {                    
                    
                URWellCross cross = crosses.get(j);                    
                
                Trajectory trj = track.getDCTraj(cross.getCluster1().layer());
                if (trj==null || track.getSector()!=cross.sector()) continue; 

                // Match the layers from traj.
                double d = cross.position().distance(trj.getPosition());
                double e = cross.getCluster1().energy()-cross.getCluster2().energy();
                if (d < Constants.CIRCLECONFUSION && Math.abs(e)<Constants.CROSSDELTAE) {
                    trackCrosses[cross.region()-1].add(cross);
                }
            }
            System.out.println();
            for(int j=0; j<trackCrosses.length; j++) System.out.print(trackCrosses[j].size() + " "); 
                    
            List<List<URWellCross>> segments = new ArrayList<>();
            List<URWellCross> end = new ArrayList<>();
            for(int r=6; r>3; r--)
                end.addAll(trackCrosses[r-1]);
            for(URWellCross ce : end) {
                Line3D ve = new Line3D(ce.position(),target);
                for(int ro=1; ro<ce.region()-3; ro++) {
                    for(URWellCross co : trackCrosses[ro-1]) {
                        double dei = ce.position().distance(co.position());
                        double dti = ve.distance(co.position()).length();
                        if(dti<0.1*dei) {
                            Line3D vei = new Line3D(ce.position(),co.position());
                            List<URWellCross> segment = new ArrayList();
                            segment.add(ce);
                            for(int r=co.region()+1; r<ce.region(); r++) {
                                for(URWellCross cr : trackCrosses[r]) {
                                    if(vei.distance(cr.position()).length()<0.1) {
                                        segment.add(cr);
                                        break;
                                    }
                                }
                            }
                            segment.add(co);
                            if(segment.size()>=4)
                                segments.add(segment);
                        }
                    }
                }
            }
            System.out.print(segments.size());
            if(!segments.isEmpty()) {
                segments.sort(Comparator.comparingInt(List<URWellCross>::size).reversed());
                for(URWellCross cross : segments.get(0)) {
                    track.addCluster(cross.getCluster1());
                    track.addCluster(cross.getCluster2());
                }
            }
        }
        
        List<Track> filtedTracks = new ArrayList();
        for(Track track : tracks){
            if (track.getClusters().size() > 4) filtedTracks.add(track);
        }
        
      
        // === TRACKS ==============================================================================
        KFitter kf = null;
        
        // Iterate on list to run the fit.
        for(int i=0; i<filtedTracks.size(); i++) {
            Track track = tracks.get(i);                
                            
            // Set status and stop if there are not at least two measurements to fit against.
            List<URWellCluster> trackClusters = track.getClusters();           

            kf = new KFitter(track, swimmer, 0);            
            
            kf.runFitter(track.getSector());


            // Do one last KF pass with filtering off to get the final Chi^2.
            kf.totNumIter = 1;
            kf.filterOn   = false;
            kf.runFitter(track.getSector());

            if (kf.finalStateVec != null) {
                StateVec sv = kf.finalStateVec;
                
                // swim to beamline to get vertex parameters
                int charge = (int)Math.signum(sv.Q);
                
                Point3D posGlobal = track.transLocaltoGlobal(track.getSector(), sv.x, sv.y, sv.z);                
                Point3D momGlobal = track.transLocaltoGlobal(track.getSector(), sv.getPx()/sv.getP()*track.getP(), sv.getPy()/sv.getP()*track.getP(), sv.getPz()/sv.getP()*track.getP());  
                
                swimmer.SetSwimParameters(posGlobal.x(),posGlobal.y(),posGlobal.z(), -momGlobal.x(),-momGlobal.y(),-momGlobal.z(),-charge);
                double[] Vt = swimmer.SwimToBeamLine(xB, yB);
                
                // if successful, save track parameters
                if(Vt == null) continue;
                track.setStatus(0);
                track.setNDF(trackClusters.size());
                track.setQ(charge);
                track.setChi2(kf.chi2);
                track.setX(Vt[0]);
                track.setY(Vt[1]);
                track.setZ(Vt[2]);
                track.setPx(-Vt[3]);
                track.setPy(-Vt[4]);
                track.setPz(-Vt[5]);
            }
        }
                
        RecoBankWriter.appendFMTBanks(event, filtedTracks);
        return true;
   }

}
