package org.jlab.rec.service;

import java.util.ArrayList;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.base.Detector;
import org.jlab.geom.detector.alert.ATOF.AlertTOFFactory;
import org.jlab.rec.atof.banks.TestBankWriter;
import org.jlab.rec.atof.cluster.ATOFCluster;
import org.jlab.rec.atof.cluster.ClusterFinder;
import org.jlab.rec.atof.constants.Parameters;
import org.jlab.rec.atof.hit.ATOFHit;
import org.jlab.rec.atof.hit.BarHit;
import org.jlab.rec.atof.hit.HitFinder;
import org.jlab.rec.atof.veff.VeffCalibrator;

/**
 * Service to return ATOF test hits and clusters
 *
 * @author npilleux
 *
 */
public class ATOFTestEngine extends ReconstructionEngine {

    public ATOFTestEngine() {
        super("ATOFTest", "pilleux", "1.0");
    }

    TestBankWriter rbc;

    private Detector ATOF;

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
        int sigma_module = 5;
        int sigma_component = 11;
        double sigma_z = 5 * Parameters.LENGTH_ATOF;
        double sigma_t = 100;

        clusterFinder.makeClusters(hitfinder, sigma_module, sigma_component, sigma_z, sigma_t, event);
        ArrayList<ATOFCluster> Clusters = clusterFinder.getClusters();

        VeffCalibrator calibrator = new VeffCalibrator();
        calibrator.computeCalib(Clusters);

        if (WedgeHits.size() != 0 || BarHits.size() != 0) {
            rbc.appendATOFBanks(event, WedgeHits, BarHits, Clusters);
            rbc.appendVeffBanks(event, calibrator.getCalibs());
        }
        return true;
    }

    @Override
    public boolean init() {
        rbc = new TestBankWriter();

        AlertTOFFactory factory = new AlertTOFFactory();
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");
        this.ATOF = factory.createDetectorCLAS(cp);
        this.registerOutputBank("ATOF::testhits", "ATOF::testclusters");

        return true;
    }

    public static void main(String arg[]) {
    }
}
