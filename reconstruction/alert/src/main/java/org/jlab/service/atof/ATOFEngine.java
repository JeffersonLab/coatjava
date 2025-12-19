package org.jlab.service.atof;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.base.Detector;
import org.jlab.geom.detector.alert.ATOF.AlertTOFFactory;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.rec.atof.banks.RecoBankWriter;
import org.jlab.rec.atof.cluster.ATOFCluster;
import org.jlab.rec.atof.cluster.ClusterFinder;
import org.jlab.rec.atof.hit.ATOFHit;
import org.jlab.rec.atof.hit.BarHit;
import org.jlab.rec.atof.hit.HitFinder;
import org.jlab.rec.alert.constants.CalibrationConstantsLoader;

/**
 * Service to return reconstructed ATOF hits and clusters
 *
 * @author npilleux
 *
 */
public class ATOFEngine extends ReconstructionEngine {

    public ATOFEngine() {
        super("ATOF", "pilleux", "1.0");
    }

    RecoBankWriter rbc;

    private Detector ATOF;
    private double b; //Magnetic field
    private boolean useStartTime = true;
    static final Logger LOGGER = Logger.getLogger(ATOFEngine.class.getName());
    
    public void setB(double B) {
        this.b = B;
    }
    public double getB() {
        return b;
    }
    public void setATOF(Detector ATOF) {
        this.ATOF = ATOF;
    }
    public Detector getATOF() {
        return ATOF;
    }

    int Run = -1;
    
    @Override
    public boolean processDataEvent(DataEvent event) {
        if (!event.hasBank("RUN::config")) {
            return true;
        }
        float startTime = 0;
        if(useStartTime)
        {
            //This assumes the FD reconstruction produced an event with good startTime
            //All start time handling could be moved as an EB-type step later
            if (!event.hasBank("REC::Event")) {
                LOGGER.finer("REC::Event bank could not be read in ATOF engine while requesting starttime");
                return true;
            }
            //Deal with FT TODO : if(event.getBank("REC::Event").getFloat("startTime", 0)==-1000)
            else startTime = event.getBank("REC::Event").getFloat("startTime", 0);
        }

        DataBank bank = event.getBank("RUN::config");

        int runNo = bank.getInt("run", 0);
        if (runNo <= 0) {
            System.err.println("ATOFEngine:  got run <= 0 in RUN::config, skipping event.");
            return false;
        }
        int newRun = runNo; 
            // Load the constants
        if(Run!=newRun) {
            CalibrationConstantsLoader.Load(newRun, this.getConstantsManager());
            }
        
        ////Do we need to read the event vx,vy,vz?
        ////If not, this part can be moved in the initialization of the engine.
        //double eventVx=0,eventVy=0,eventVz=0; //They should be in CM
        ////Track Projector Initialisation with b field
        //Swim swim = new Swim();
        //float magField[] = new float[3];
        //swim.BfieldLab(eventVx, eventVy, eventVz, magField); 
        //this.b = Math.sqrt(Math.pow(magField[0],2) + Math.pow(magField[1],2) + Math.pow(magField[2],2));

        ///// \todo move this to ALERTEngine
        //TrackProjector projector = new TrackProjector();
        //projector.setB(this.b);
        //projector.projectTracks(event);
        //rbc.appendMatchBanks(event, projector.getProjections());

        // Why do we have to "find" hits? 
        //Hit finder init
        HitFinder hitfinder = new HitFinder();
        hitfinder.findHits(event, ATOF, startTime);

        ArrayList<ATOFHit> WedgeHits = hitfinder.getWedgeHits();
        ArrayList<BarHit> BarHits = hitfinder.getBarHits();
        
        //Exit if hit lists are empty
        if (WedgeHits.isEmpty() && BarHits.isEmpty()) {
            //			System.out.println("No hits : ");
            //			event.show();
            return true;
        }
        
        ClusterFinder clusterFinder = new ClusterFinder();
        clusterFinder.makeClusters(event,hitfinder);
        ArrayList<ATOFCluster> Clusters = clusterFinder.getClusters();

        if (WedgeHits.size() != 0 || BarHits.size() != 0) {
            rbc.appendATOFBanks(event, WedgeHits, BarHits, Clusters);
        }
        return true;
    }

    @Override
    public boolean init() {
        rbc = new RecoBankWriter();

        AlertTOFFactory factory = new AlertTOFFactory();
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");
        this.ATOF = factory.createDetectorCLAS(cp);
        
        String[] alertTables = new String[] {
            	"/calibration/alert/ahdc/time_offsets",
                "/calibration/alert/ahdc/time_to_distance",
                "/calibration/alert/ahdc/raw_hit_cuts",
                "/calibration/alert/atof/effective_velocity",
                "/calibration/alert/atof/time_walk",
                "/calibration/alert/atof/attenuation",
                "/calibration/alert/atof/time_offsets"
        };
        
        Map<String, Integer> tableMap = new HashMap<>();
        for (String table : alertTables) {
            if (table.equals("/calibration/alert/atof/time_offsets") ||
                table.equals("/calibration/alert/atof/time_walk")) {
                tableMap.put(table, 4);
            } else {
                tableMap.put(table, 3);
            }
        }

        requireConstants(tableMap);
        this.getConstantsManager().setVariation("default");
        this.registerOutputBank("ATOF::hits", "ATOF::clusters");
        String useStartTimeString = "UseStartTime";
        if(this.getEngineConfigString(useStartTimeString)!=null) {
            if ("true".equals(this.getEngineConfigString(useStartTimeString)))
                this.useStartTime = true;
            else if ("false".equals(this.getEngineConfigString(useStartTimeString)))
                this.useStartTime = false;
            else {LOGGER.severe("Invalid option parsed for ATOF UseStartTime"); return false;}
        }
        return true;
    }

    public static void main(String arg[]) {
    }
}
