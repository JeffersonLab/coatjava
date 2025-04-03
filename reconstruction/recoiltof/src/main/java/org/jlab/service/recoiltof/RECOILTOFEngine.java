package org.jlab.service.recoiltof;

import java.util.ArrayList;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

import java.util.concurrent.atomic.AtomicInteger;
import org.jlab.clas.swimtools.Swim;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.rec.recoiltof.banks.RecoBankWriter;
import org.jlab.rec.recoiltof.cluster.RECOILTOFCluster;
import org.jlab.rec.recoiltof.cluster.ClusterFinder;
import org.jlab.rec.recoiltof.hit.RECOILTOFHit;
import org.jlab.rec.recoiltof.hit.BarHit;
import org.jlab.rec.recoiltof.hit.HitFinder;

/**
 * Service to return reconstructed RECOILTOF hits and clusters
 *
 * @author npilleux, Nilanga Wickramaarachchi
 *
 */
public class RECOILTOFEngine extends ReconstructionEngine {

    public RECOILTOFEngine() {
        super("RECOILTOF", "Nilanga Wickramaarachchi", "1.0");
    }

    RecoBankWriter rbc;

    private final AtomicInteger run = new AtomicInteger(0);

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
        
        //Do we need to read the event vx,vy,vz?
        //If not, this part can be moved in the initialization of the engine.
        double eventVx=0,eventVy=0,eventVz=0; //They should be in CM

        //Hit finder init
        HitFinder hitfinder = new HitFinder();
        hitfinder.findHits(event);

        ArrayList<BarHit> BarHits = hitfinder.getBarHits();
        
        //Exit if hit list is empty
        if (BarHits.isEmpty()) {
            //			System.out.println("No hits : ");
            //			event.show();
            return true;
        }
        
        ClusterFinder clusterFinder = new ClusterFinder();
        clusterFinder.makeClusters(event,hitfinder);
        ArrayList<RECOILTOFCluster> Clusters = clusterFinder.getClusters();

        if (BarHits.size() != 0) {
            rbc.appendRECOILTOFBanks(event, BarHits, Clusters);
        }
        return true;
    }

    @Override
    public boolean init() {
        rbc = new RecoBankWriter();

        this.registerOutputBank("RECOILTOF::hits", "RECOILTOF::clusters");

        return true;
    }

    public static void main(String arg[]) {
    }
}
