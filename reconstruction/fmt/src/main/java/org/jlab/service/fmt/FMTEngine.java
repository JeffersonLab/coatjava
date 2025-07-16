package org.jlab.service.fmt;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.swimtools.Swim;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.base.GeometryFactory;
import org.jlab.geom.prim.Point3D;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.io.base.*;
import org.jlab.rec.fmt.Constants;

import org.jlab.rec.fmt.banks.RecoBankWriter;
import org.jlab.rec.fmt.track.Track;
import org.jlab.rec.fmt.track.Trajectory;
import org.jlab.rec.fmt.track.fit.KFitter;
import org.jlab.rec.fmt.track.fit.StateVecs.StateVec;

import org.jlab.rec.urwell.reader.URWellCluster;
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
        //System.out.println(clusters.size());
        //for (int i = 0; i < clusters.size(); i++) System.out.println(clusters.get(i).toString());
        
        // === DC TRACKS ===========================================================================
        Track trk = new Track();
        List<Track> tracks = trk.getDCTracks(event, swimmer);
        if(tracks.isEmpty()) return true;

        // === SEEDS =============================================================================
        for(int i=0; i<tracks.size(); i++) {
            Track track = tracks.get(i);                           
            for(int j=0; j<clusters.size(); j++) {                    
                URWellCluster cluster = clusters.get(j);                                    
                // Match the layers from track
                if (cluster.sector() == track.getSector()) {
                    track.addCluster(cluster);
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
                Point3D momGlobal = track.transLocaltoGlobal(track.getSector(), sv.getPx(), sv.getPy(), sv.getPz());  
                
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
