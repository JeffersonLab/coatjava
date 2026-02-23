package org.jlab.service.rtof;

import java.util.ArrayList;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

import java.util.concurrent.atomic.AtomicInteger;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.rec.rtof.banks.RecoBankWriter;
import org.jlab.rec.rtof.cluster.RTOFCluster;
import org.jlab.rec.rtof.cluster.ClusterFinder;
import org.jlab.rec.rtof.hit.RTOFRawHit;
import org.jlab.rec.rtof.hit.RTOFHit;
import org.jlab.rec.rtof.hit.HitFinder;

/**
 * Service to return reconstructed RTOF hits and clusters
 *
 * @author npilleux, Nilanga Wickramaarachchi
 *
 */
public class RTOFEngine extends ReconstructionEngine {

    public RTOFEngine() {
        super("RTOF", "Nilanga Wickramaarachchi", "1.0");
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
        

        //Hit finder init
        HitFinder hitfinder = new HitFinder();
        hitfinder.findHits(event);

        ArrayList<RTOFHit> RTOFHits = hitfinder.getRTOFHits();
        
        //Exit if hit list is empty
        if (RTOFHits.isEmpty()) {
            //			System.out.println("No hits : ");
            //			event.show();
            return true;
        }
        
        ClusterFinder clusterFinder = new ClusterFinder();
        clusterFinder.makeClusters(event,hitfinder);
        ArrayList<RTOFCluster> Clusters = clusterFinder.getClusters();

        if (RTOFHits.size() != 0) {
            rbc.appendRTOFBanks(event, RTOFHits, Clusters);
        }
        return true;
    }

    @Override
    public boolean init() {
        rbc = new RecoBankWriter();

        this.registerOutputBank("RTOF::hits", "RTOF::clusters");

        return true;
    }

    public static void main(String arg[]) {
    }
}
