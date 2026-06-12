package org.jlab.service.recoil.tof;

import java.util.ArrayList;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

import java.util.concurrent.atomic.AtomicInteger;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.service.recoil.tof.RecoBankWriter;
import org.jlab.service.recoil.tof.RTOFCluster;
import org.jlab.service.recoil.tof.ClusterFinder;
import org.jlab.service.recoil.tof.RTOFRawHit;
import org.jlab.service.recoil.tof.RTOFHit;
import org.jlab.service.recoil.tof.HitFinder;

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
    public boolean processDataEventUser(DataEvent event) {

        if (!event.hasBank("RUN::config")) {
            return true;
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

    @Override
    public void detectorChanged(int run) {}
   
}

