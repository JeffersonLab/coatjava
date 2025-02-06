package org.jlab.rec.service;

import cnuphys.magfield.MagneticFields;
import java.util.ArrayList;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;

import java.util.concurrent.atomic.AtomicInteger;
import org.jlab.clas.swimtools.Swim;
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
import org.jlab.utils.CLASResources;

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

    private final AtomicInteger Run = new AtomicInteger(0);
    private Detector ATOF;
    private double B; //Magnetic field
    
    //Setters and getters
    public void setB(double B) {
        this.B = B;
    }
    public double getB() {
        return B;
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

        // Load the constants
        //-------------------
        int newRun = bank.getInt("run", 0);
        if (newRun == 0) {
            return true;
        }

        if (Run.get() == 0 || (Run.get() != 0 && Run.get() != newRun)) {
            Run.set(newRun);
        }

        //CalibrationConstantsLoader constantsLoader = new CalibrationConstantsLoader(newRun, this.getConstantsManager());

        //Track Projector Initialisation with B field
        TrackProjector projector = new TrackProjector();
        projector.setB(this.B);
        projector.ProjectTracks(event);

        //Hit finder init
        HitFinder hitfinder = new HitFinder();
        hitfinder.FindHits(event, ATOF);

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

        //requireConstants(Arrays.asList(CalibrationConstantsLoader.getAtofTables()));
        //this.getConstantsManager().setVariation("default");

        this.registerOutputBank("ATOF::hits", "ATOF::clusters");

        return true;
    }

    public static void main(String arg[]) {
        ATOFEngine en = new ATOFEngine();
        
         //READING MAG FIELD MAP
        System.setProperty("CLAS12DIR", "../../");
        String mapDir = CLASResources.getResourcePath("etc") + "/data/magfield";
        try {
            MagneticFields.getInstance().initializeMagneticFields(mapDir,
                    "Symm_torus_r2501_phi16_z251_24Apr2018.dat", "Symm_solenoid_r601_phi1_z1201_13June2018.dat");
        } catch (Exception e) {
            e.printStackTrace();
        }
        float[] b = new float[3];
        Swim swimmer = new Swim();
        swimmer.BfieldLab(0, 0, 0, b);
        en.setB(Math.abs(b[2]));

        en.init();
        String input = "/Users/npilleux/Desktop/alert/atof-reconstruction/coatjava/reconstruction/alert/src/main/java/org/jlab/rec/atof/hit/mixed_ions.hipo";
        HipoDataSource reader = new HipoDataSource();
        reader.open(input);
        
        while (reader.hasEvent()) {
            DataEvent event = (DataEvent) reader.getNextEvent();
            en.processDataEvent(event);
            event.getBank("ATOF::clusters").show();
            }
    }
}
