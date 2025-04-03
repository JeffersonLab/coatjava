package org.jlab.service.alert;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.tracking.kalmanfilter.Material;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;
import org.jlab.rec.ahdc.AI.*;
import org.jlab.rec.ahdc.Banks.RecoBankWriter;
import org.jlab.rec.ahdc.Cluster.Cluster;
import org.jlab.rec.ahdc.Cluster.ClusterFinder;
import org.jlab.rec.ahdc.Distance.Distance;
import org.jlab.rec.ahdc.HelixFit.HelixFitJava;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.Hit.HitReader;
import org.jlab.rec.ahdc.Hit.TrueHit;
import org.jlab.rec.ahdc.HoughTransform.HoughTransform;
import org.jlab.rec.ahdc.KalmanFilter.KalmanFilter;
import org.jlab.rec.ahdc.KalmanFilter.MaterialMap;
import org.jlab.rec.ahdc.PreCluster.PreCluster;
import org.jlab.rec.ahdc.PreCluster.PreClusterFinder;
import org.jlab.rec.ahdc.Track.Track;
import org.jlab.rec.ahdc.Mode;

import org.jlab.rec.atof.cluster.ATOFCluster;
import org.jlab.rec.atof.hit.ATOFHit;
import org.jlab.rec.atof.hit.BarHit;
import org.jlab.rec.atof.hit.HitFinder;
import org.jlab.rec.alert.projections.TrackProjector;

import java.io.File;
import java.util.*;

/** ALERTEngine reconstruction service.
 *
 *
 */
public class ALERTEngine extends ReconstructionEngine {


    private Mode mode = Mode.CV_Track_Finding;

    public ALERTEngine() {
        super("ALERT", "whit,ouillon,pilleux", "0.1");
    }

    @Override
    public boolean init() {

        if(this.getEngineConfigString("Mode")!=null) {
            //if (Objects.equals(this.getEngineConfigString("Mode"), Mode.AI_Track_Finding.name()))
            //    mode = Mode.AI_Track_Finding;
        }
        return true;
    }

    @Override
    public boolean processDataEvent(DataEvent event) {

        int    runNo          = 10;
        int    eventNo        = 777; // 

        if (event.hasBank("RUN::config")) {
            DataBank bank = event.getBank("RUN::config");
            runNo          = bank.getInt("run", 0);
            eventNo        = bank.getInt("event", 0);
            //magfieldfactor = bank.getFloat("solenoid", 0);
            if (runNo <= 0) {
                System.err.println("RTPCEngine:  got run <= 0 in RUN::config, skipping event.");
                return false;
            }
        }

        if (event.hasBank("AHDC::adc")) {
        }
        if (event.hasBank("ATOF::tdc")) {
        }

        return true;
    }

    public static void main(String[] args) {

        double starttime = System.nanoTime();

        int    nEvent     = 0;
        int    maxEvent   = 1000;
        int    myEvent    = 3;
        String inputFile  = "alert_out_update.hipo";
        String outputFile = "output.hipo";

        if (new File(outputFile).delete()) System.out.println("output.hipo is delete.");

        System.err.println(" \n[PROCESSING FILE] : " + inputFile);

        ALERTEngine en = new ALERTEngine();

        HipoDataSource reader = new HipoDataSource();
        HipoDataSync   writer = new HipoDataSync();

        en.init();

        reader.open(inputFile);
        writer.open(outputFile);

        while (reader.hasEvent() && nEvent < maxEvent) {
            nEvent++;
            // if (nEvent % 100 == 0) System.out.println("nEvent = " + nEvent);
            DataEvent event = reader.getNextEvent();

            // if (nEvent != myEvent) continue;
            // System.out.println("***********  NEXT EVENT ************");
            // event.show();

            en.processDataEvent(event);
            writer.writeEvent(event);

        }
        writer.close();

        System.out.println("finished " + (System.nanoTime() - starttime) * Math.pow(10, -9));
    }
}
