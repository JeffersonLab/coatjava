package org.jlab.rec.service;

import java.util.ArrayList;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

import java.util.concurrent.atomic.AtomicInteger;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.base.Detector;
import org.jlab.geom.detector.alert.ATOF.AlertTOFFactory;
import org.jlab.rec.atof.banks.RecoBankWriter;
import org.jlab.rec.atof.cluster.AtofCluster;
import org.jlab.rec.atof.cluster.ClusterFinder;
import org.jlab.rec.atof.hit.AtofHit;
import org.jlab.rec.atof.hit.BarHit;
import org.jlab.rec.atof.hit.HitFinder;
import org.jlab.rec.atof.trackMatch.TrackProjector;

/**
 * Service to return reconstructed Atof hits and clusters
 *
 * @author npilleux
 *
 */
public class AtofEngine extends ReconstructionEngine {

    public AtofEngine() {
        super("ATOF", "pilleux", "1.0");
    }

    RecoBankWriter rbc;

    private final AtomicInteger run = new AtomicInteger(0);
    private Detector Atof;
    private double b; //Magnetic field
    
    public void setB(double B) {
        this.b = B;
    }
    public double getB() {
        return b;
    }
    public void setAtof(Detector ATOF) {
        this.Atof = ATOF;
    }
    public Detector getAtof() {
        return Atof;
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

        //Track Projector Initialisation with b field
        TrackProjector projector = new TrackProjector();
        projector.setB(this.b);
        projector.projectTracks(event);

        //Hit finder init
        HitFinder hitfinder = new HitFinder();
        hitfinder.findHits(event, Atof);

        ArrayList<AtofHit> WedgeHits = hitfinder.getWedgeHits();
        ArrayList<BarHit> BarHits = hitfinder.getBarHits();
        
        //Exit if hit lists are empty
        if (WedgeHits.isEmpty() && BarHits.isEmpty()) {
            //			System.out.println("No hits : ");
            //			event.show();
            return true;
        }
        
        ClusterFinder clusterFinder = new ClusterFinder();
        clusterFinder.makeClusters(event,hitfinder);
        ArrayList<AtofCluster> Clusters = clusterFinder.getClusters();

        if (WedgeHits.size() != 0 || BarHits.size() != 0) {
            rbc.appendAtofBanks(event, WedgeHits, BarHits, Clusters);
        }
        return true;
    }

    @Override
    public boolean init() {
        rbc = new RecoBankWriter();

        AlertTOFFactory factory = new AlertTOFFactory();
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");
        this.Atof = factory.createDetectorCLAS(cp);
        this.registerOutputBank("ATOF::hits", "ATOF::clusters");

        return true;
    }

    public static void main(String arg[]) {
    }
}
