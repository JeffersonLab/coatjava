package org.jlab.service.alert;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.File;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.base.Detector;
import org.jlab.geom.detector.alert.ATOF.AlertTOFFactory;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.swimtools.Swim;

import org.jlab.rec.alert.TrackMatchingAI.ModelTrackMatching;

import org.jlab.rec.alert.banks.RecoBankWriter;
import org.jlab.rec.alert.projections.TrackProjector;
import org.jlab.rec.atof.hit.ATOFHit;

import ai.djl.util.Pair;


/** 
 * <h1>ALERTEngine reconstruction service.</h1>
 *
 * @author  Whit Armstrong
 * @author  Noemie Pilleux
 * @since   2025-04-03
 */
public class ALERTEngine extends ReconstructionEngine {

    /**
     * ALERT Engine output bank writer.
     * 
     * @see RecoBankWriter
     *
     * <h3>Output banks</h3>
     * <ul>
     * <li> Track Projection @see TrackProjector</li>
     * </ul>
     *
     */
    private RecoBankWriter rbc;

    Detector ATOF; // ALERT ATOF detector

    /**
     *  Current run number being processed.
     *  TODO: why atomic here and nowhere else? 
     */
    private final AtomicInteger run = new AtomicInteger(0);

    private double b; //Magnetic field

    private ModelTrackMatching modelTrackMatching;

    public void setB(double B) {
        this.b = B;
    }
    public double getB() {
        return b;
    }

    /**
     * ALERTEngine service c'tor. 
     */
    public ALERTEngine() {
        super("ALERT", "whit,ouillon,pilleux", "0.1");
    }

    /** 
     * ALERTEngine initialization.
     * Creates the RecoBankWriter and checks for various yaml flags.
     * TODO: document flags
     */
    @Override
    public boolean init() {

        rbc = new RecoBankWriter();

        modelTrackMatching = new ModelTrackMatching();

        AlertTOFFactory factory = new AlertTOFFactory();
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");
        ATOF = factory.createDetectorCLAS(cp);

        if(this.getEngineConfigString("Mode")!=null) {
            //if (Objects.equals(this.getEngineConfigString("Mode"), Mode.AI_Track_Finding.name()))
            //    mode = Mode.AI_Track_Finding;
        }
        return true;
    }

    /**
     * Process Event.
     * Main method called to process event data.
     *
     * <ul>
     * <li> Check for AHDC and ATOF banks </li>
     * <li> Project track to ATOF</li>
     * </ul>
     */
    @Override
    public boolean processDataEvent(DataEvent event) {

        if (!event.hasBank("AHDC::adc")) 
            return false;
        if (!event.hasBank("ATOF::tdc")) 
            return false;

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
        //Track Projector Initialisation with b field
        Swim swim = new Swim();
        float magField[] = new float[3];
        swim.BfieldLab(eventVx, eventVy, eventVz, magField); 
        this.b = Math.sqrt(Math.pow(magField[0],2) + Math.pow(magField[1],2) + Math.pow(magField[2],2));

        TrackProjector projector = new TrackProjector();
        projector.setB(this.b);
        projector.projectTracks(event);
        rbc.appendMatchBanks(event, projector.getProjections());

        /// ---------------------------------------------------------------------------------------
        /// Track matching using AI ---------------------------------------------------------------

        if (event == null)  return false; // TODO: is it useful?
        if (!event.hasBank("AHDC::track")) return false;

        DataBank bank_AHDCtracks = event.getBank("AHDC::track");
        DataBank bank_AHDCInterclusters = event.getBank("AHDC::interclusters");
        DataBank bank_ATOFHits = event.getBank("ATOF::hits");

        ArrayList<Pair<Integer, Integer>> matched_ATOF_hit_id = new ArrayList<>();

        for (int i = 0; i < bank_AHDCtracks.rows(); i++) {
            int track_id = bank_AHDCtracks.getInt("trackid", i);

            // Get all interclusters for this track
            ArrayList<Pair<Float, Float>> interClusters = new ArrayList<>();
            for (int j = 0; j < bank_AHDCInterclusters.rows(); j++) {
                int intercluster_track_id = bank_AHDCInterclusters.getInt("trackid", j);
                if (intercluster_track_id == track_id) {
                    float x = bank_AHDCInterclusters.getFloat("x", j);
                    float y = bank_AHDCInterclusters.getFloat("y", j);
                    interClusters.add(new Pair<>(x, y));
                }
            }
            if (interClusters.size() != 5) continue;

            try {

                float[] pred = modelTrackMatching.prediction(interClusters);
                int sector_pred = (int) pred[0];
                int layer_pred = (int) pred[1];
                int wedge_pred = (int) pred[2];

                ATOFHit hit_pred = new ATOFHit(sector_pred, layer_pred, wedge_pred, 0, 0, 0, 0, ATOF);
                double pred_x = hit_pred.getX();
                double pred_y = hit_pred.getY();
                double pred_z = hit_pred.getZ();

                double threshold = 20.0;
                double minDistanceSquared = threshold * threshold;

                ATOFHit matchAtofHit = null; // Could be used later
                int matchHitId = -1;

                for (int k = 0; k < bank_ATOFHits.rows(); k++) {
                    int component = bank.getInt("component", k);
                    if (component == 10) continue;

                    int sector = bank.getInt("sector", k);
                    int layer = bank.getInt("layer", k);

                    ATOFHit hit = new ATOFHit(sector, layer, component, 0, 0, 0, 0, ATOF);

                    double dx = pred_x - hit.getX();
                    double dy = pred_y - hit.getY();
                    double dz = pred_z - hit.getZ();

                    double distanceSquared = dx * dx + dy * dy + dz * dz;

                    if (distanceSquared < minDistanceSquared) {
                        minDistanceSquared = distanceSquared;
                        matchAtofHit = hit;
                        matchHitId = bank_ATOFHits.getInt("id", k);
                    }
                }
                matched_ATOF_hit_id.add(new Pair<>(track_id, matchHitId));

            } catch (Exception ex) {
                System.out.println("Exception in ALERTEngine processDataEvent: " + ex); // TODO: proper logging
            }


            
        }
        return true;
    }

    /**
     * ALERTEngine main.
     * TODO: needs good test.
     */
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
