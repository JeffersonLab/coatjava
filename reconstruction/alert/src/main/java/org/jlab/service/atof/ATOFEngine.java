package org.jlab.service.atof;

import java.util.ArrayList;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

import java.util.concurrent.atomic.AtomicInteger;
import org.jlab.clas.swimtools.Swim;
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
//import org.jlab.rec.alert.projections.TrackProjector;

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

    private final AtomicInteger run = new AtomicInteger(0);
    private Detector ATOF;
    private double b; //Magnetic field
    
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

    @Override
    public boolean processDataEvent(DataEvent event) {

        if (!event.hasBank("RUN::config")) {
            return true;
        }

        DataBank bank = event.getBank("RUN::config");

        int newRun = bank.getInt("run", 0);
        if (newRun == 0) {
            return true;
        }

        if (run.get() == 0 || (run.get() != 0 && run.get() != newRun)) {
            run.set(newRun);
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
        hitfinder.findHits(event, ATOF);

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
        this.registerOutputBank("ATOF::hits", "ATOF::clusters");

        return true;
    }

    public static void main(String arg[]) {
    }
}
