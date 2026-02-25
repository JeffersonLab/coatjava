package org.jlab.service.alert;

import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.swimtools.Swim;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.base.Detector;
import org.jlab.geom.detector.alert.ATOF.AlertTOFFactory;
import org.jlab.geom.detector.alert.ATOF.AlertTOFDetector;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;
import org.jlab.rec.alert.TrackMatchingAI.ModelTrackMatching;
import org.jlab.rec.alert.AIPID.ModelPrePID;
import org.jlab.rec.alert.banks.RecoBankWriter;
import org.jlab.rec.alert.projections.TrackProjector;
import org.jlab.rec.atof.hit.ATOFHit;
import org.jlab.rec.ahdc.KalmanFilter.RadialKFHit;
import org.jlab.rec.ahdc.KalmanFilter.KalmanFilter;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.geom.detector.alert.AHDC.AlertDCDetector;
import org.jlab.geom.detector.alert.AHDC.AlertDCFactory;
import org.jlab.rec.ahdc.Track.Track;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.jlab.clas.pdg.PDGDatabase;
import org.jlab.clas.pdg.PDGParticle;
import java.util.logging.Logger;



import ai.djl.util.Pair;
import org.jlab.rec.alert.AIPID.PrePIDResult;


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
    static final Logger LOGGER = Logger.getLogger(ModelPrePID.class.getName());
    AlertTOFDetector ATOF; // ALERT ATOF detector
    private AlertDCDetector AHDC; // ALERT AHDC detector

    /**
     *  Current run number being processed.
     *  TODO: why atomic here and nowhere else? 
     */
    private final AtomicInteger run = new AtomicInteger(0);

    private double b; //Magnetic field

    private ModelTrackMatching modelTrackMatching;
    private ModelPrePID modelPrePID;

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
        modelPrePID = new ModelPrePID();

        AlertTOFFactory factory = new AlertTOFFactory();
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");
        ATOF = factory.createDetectorCLAS(cp);
        AHDC = (new AlertDCFactory()).createDetectorCLAS(new DatabaseConstantProvider());

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

        DataBank runBank = event.getBank("RUN::config");

        int newRun = runBank.getInt("run", 0);
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

                ATOFHit hit_pred = new ATOFHit(sector_pred, layer_pred, wedge_pred, 0, 0, 0, 0f, ATOF);
                double pred_x = hit_pred.getX();
                double pred_y = hit_pred.getY();
                double pred_z = hit_pred.getZ();

                double threshold = 20.0;
                double minDistanceSquared = threshold * threshold;

                ATOFHit matchAtofHit = null; // Could be used later
                int matchHitId = -1;

                for (int k = 0; k < bank_ATOFHits.rows(); k++) {
                    int component = bank_ATOFHits.getInt("component", k);
                    if (component == 10) continue;

                    int sector = bank_ATOFHits.getInt("sector", k);
                    int layer = bank_ATOFHits.getInt("layer", k);

                    ATOFHit hit = new ATOFHit(sector, layer, component, 0, 0, 0, 0f, ATOF);

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
        rbc.appendTrackMatchingAIBank(event, matched_ATOF_hit_id);
        
        // ---------------------------------------------------------------------------------------
        // PrePID using AI (AHDC::track + ATOF::clusters matched via ALERT::ai:projections)
        // ---------------------------------------------------------------------------------------
        if (event.hasBank("ALERT::ai:projections") && event.hasBank("AHDC::track") && event.hasBank("ATOF::hits")) {

            DataBank bankProj = event.getBank("ALERT::ai:projections");
            DataBank bankTrk  = event.getBank("AHDC::track");
            DataBank bankHit  = event.getBank("ATOF::hits");

            ArrayList<PrePIDResult> prepid_results = new ArrayList<>();

            for (int i = 0; i < bankProj.rows(); i++) {

                int trackid = bankProj.getInt("trackid", i);
                int hitid = bankProj.getInt("matched_atof_hit_id", i); // TODO: Fix to hit_id instead of clusterid
                
                // TODO: refactor this to replace this with single line
                int trkRow = -1;
                for (int r = 0; r < bankTrk.rows(); r++) {
                    if (bankTrk.getInt("trackid", r) == trackid) { trkRow = r; break; }
                }
                if (trkRow < 0) continue;

                int hitRow = -1;
                for (int r = 0; r < bankHit.rows(); r++) {
                    if (bankHit.getInt("id", r) == hitid) { hitRow = r; break; }
                }
                if (hitRow < 0) continue;

                // Build feature vector float[23] in the exact training order
                float[] x = new float[23];

                // AHDC::track (13)
                x[0]  = bankTrk.getFloat("x", trkRow);
                x[1]  = bankTrk.getFloat("y", trkRow);
                x[2]  = bankTrk.getFloat("z", trkRow);
                x[3]  = bankTrk.getFloat("px", trkRow);
                x[4]  = bankTrk.getFloat("py", trkRow);
                x[5]  = bankTrk.getFloat("pz", trkRow);
                x[6]  = bankTrk.getInt("n_hits", trkRow);
                x[7]  = bankTrk.getInt("sum_adc", trkRow);
                x[8]  = bankTrk.getFloat("path", trkRow);
                x[9]  = bankTrk.getFloat("dEdx", trkRow);
                x[10] = bankTrk.getFloat("p_drift", trkRow);
                x[11] = bankTrk.getFloat("chi2", trkRow);
                x[12] = bankTrk.getFloat("sum_residuals", trkRow);

                /*// ATOF::clusters (10)
                x[13] = bankClu.getInt("n_bar", cluRow);
                x[14] = bankClu.getInt("n_wedge", cluRow);
                x[15] = bankClu.getFloat("time", cluRow);
                x[16] = bankClu.getFloat("x", cluRow);
                x[17] = bankClu.getFloat("y", cluRow);
                x[18] = bankClu.getFloat("z", cluRow);
                x[19] = bankClu.getFloat("energy", cluRow);
                x[20] = bankClu.getFloat("pathlength", cluRow);
                x[21] = bankClu.getFloat("inpathlength", cluRow);
                x[22] = bankClu.getInt("projID", cluRow);*/
                
                // ATOF::Hits (Temporarily updating to the same 10 slots as ATOF Clusters would have if it worked)
                x[13] = 0f;
                x[14] = 0f;
                x[15] = bankHit.getFloat("time", hitRow);
                x[16] = bankHit.getFloat("x", hitRow);
                x[17] = bankHit.getFloat("y", hitRow);
                x[18] = bankHit.getFloat("z", hitRow);
                x[19] = bankHit.getFloat("energy", hitRow);
                x[20] = 0f;
                x[21] = 0f;
                x[22] = 0f;

                try {
                    float[] pred = modelPrePID.prediction(x);
                    int prepid = (int) pred[0];
                    prepid_results.add(new PrePIDResult(trackid, hitid, prepid, pred[1], pred[2], pred[3], pred[4], pred[5]));
                } catch (TranslateException ex) {
                    LOGGER.warning(() -> "Exception in ALERTEngine PrePID: " + ex);
                }
            }

            rbc.appendPrePIDBank(event, prepid_results);
        }


        
        ///////////////////////////////////////////
        /// Kalmam Filter
        /// ///////////////////////////////////////
        
        /// Read the list of tracks/hits from the banks AHDC::track and AHDC::hits
        if (!event.hasBank("AHDC::track")) {return false;}
        DataBank trackBank = event.getBank("AHDC::track");
        DataBank hitBank = event.getBank("AHDC::hits");
        ArrayList<Track> AHDC_tracks = new ArrayList<>();
        for (int row = 0; row < trackBank.rows(); row++) {
            int trackid = trackBank.getInt("trackid", row);
            ArrayList<Hit> AHDC_hits = new ArrayList<>();
            for (int hit_row = 0; hit_row < hitBank.rows(); hit_row++) {
                if(trackid == hitBank.getInt("trackid", hit_row)) {
                    int id = hitBank.getShort("id", hit_row);
                    int superlayer = hitBank.getByte("superlayer", hit_row);
                    int layer = hitBank.getByte("layer", hit_row);
                    int wire = hitBank.getInt("wire", hit_row);
                    int adc = hitBank.getInt("adc", hit_row);
                    double doca = hitBank.getDouble("doca", hit_row);
                    double time = hitBank.getDouble("time", hit_row);
                    double tot = hitBank.getDouble("timeOverThreshold", hit_row);
                    // warning : adc is the calibrated one, we need the adc for the Kalman filter
                    Hit hit = new Hit(id, superlayer, layer, wire, doca, adc, time);
                    hit.setWirePosition(AHDC);
                    hit.setTrackId(trackid);
                    hit.setADC(adc);
                    hit.setToT(tot);
                    AHDC_hits.add(hit);
                }
            }
            Track track = new Track(AHDC_hits);
            // Initialise the position and the momentum using the information of the AHDC::track
            // position : mm
            // momentum : MeV
            double x = trackBank.getFloat("x", row);
            double y = trackBank.getFloat("y", row);
            double z = trackBank.getFloat("z", row);
            double px = trackBank.getFloat("px", row);
            double py = trackBank.getFloat("py", row);
            double pz = trackBank.getFloat("pz", row);
            double[] vec = {x, y, z, px, py, pz};
            track.setPositionAndMomentumVec(vec);
            track.set_trackId(trackid);
            AHDC_tracks.add(track);
        }
        /// Intialise the Kalman Filter
        double magfieldfactor = runBank.getFloat("solenoid", 0);
        double magfield = 50*magfieldfactor;
        boolean IsMC = event.hasBank("MC::Particle");
        PDGParticle proton = PDGDatabase.getParticleById(2212);
        int Niter = 40;
        KalmanFilter KF = new KalmanFilter(proton, Niter);

        /// Add ATOF wedge hits
        HashMap<Integer, RadialKFHit> ATOF_hits = new HashMap<>();
        for (Pair<Integer, Integer> pair : matched_ATOF_hit_id) {
            int trackid = pair.getKey();
            int atofid = pair.getValue();
            if (trackid > 0 && atofid > 0) {
                // recover the wedge
                for (int row = 0; row < bank_ATOFHits.rows(); row++) {
                    if (bank_ATOFHits.getShort("id", row) == atofid) {
                        double x = bank_ATOFHits.getFloat("x", row);
                        double y = bank_ATOFHits.getFloat("y", row);
                        double z = bank_ATOFHits.getFloat("z", row);
                        z -= 32.3; // there is a shift between AHDC and ATOF (still don't know why) !
                        RadialKFHit hit = new RadialKFHit(x, y, z);
                            // error on r
                        double wedge_width = 20; //mm
                        double dr2 = Math.pow(wedge_width, 2)/12; // mm^2
                            // error on phi
                        double open_angle = Math.toRadians(6); // deg
                        double dphi2 = Math.pow(open_angle, 2)/12;
                            // error on z
                        double wedge_length = 27.7; //mm
                        double dz2 = Math.pow(wedge_length, 2)/12;
                        
                        RealMatrix measurementNoise = new Array2DRowRealMatrix(
                                                        new double[][]{
                                                            {dr2, 0.0000, 0.0000},
                                                            {0.00, dphi2, 0.0000},
                                                            {0.00, 0.0000, dz2}
                                                        });//3x3;
                        hit.setMeasurementNoise(measurementNoise);
                        ATOF_hits.put(trackid, hit);
                    }
                }
            }
        }
        KF.set_ATOF_hits(ATOF_hits);
        KF.set_ATOF_detector(ATOF);

        /// First propagation : each AHDC_tracks will be fitted
        KF.propagation(AHDC_tracks, event, magfield, IsMC);

        // // Get ATOF wedges predicted
        // HashMap<Integer, ArrayList<int[]>> ATOF_hits_predicted = KF.get_ATOF_hits_predicted();
        // for (Track track : AHDC_tracks) {
        //     int trackid = track.get_trackId();
        //     // if the AI has no match for this track, look for the predicted ones
        //     if (ATOF_hits.get(trackid) == null) {
        //         ArrayList<int[]> predicted_wedges = ATOF_hits_predicted.get(trackid);
        //         boolean IsHitSelected = false;
        //         int i = 0;
        //         while (i < predicted_wedges.size() && !IsHitSelected) {
        //             int sector = predicted_wedges.get(i)[0];
        //             int layer = predicted_wedges.get(i)[1];
        //             int wedge = predicted_wedges.get(i)[2];
        //             for (int row = 0; row < bank_ATOFHits.rows(); row++) {
        //                 if (bank_ATOFHits.getInt("sector", row) == sector && bank_ATOFHits.getInt("layer", row) == layer && bank_ATOFHits.getInt("component", row) == wedge) {
        //                     // create a RadialKFHit
        //                     double x = bank_ATOFHits.getFloat("x", row);
        //                     double y = bank_ATOFHits.getFloat("y", row);
        //                     double z = bank_ATOFHits.getFloat("z", row);
        //                     z -= 32.3; // there is a shift between AHDC and ATOF (still don't know why) !
        //                     RadialKFHit hit = new RadialKFHit(x, y, z);
        //                         // error on r
        //                     double wedge_width = 20; //mm
        //                     double dr2 = Math.pow(wedge_width, 2)/12; // mm^2
        //                         // error on phi
        //                     double open_angle = Math.toRadians(6); // deg
        //                     double dphi2 = Math.pow(open_angle, 2)/12;
        //                         // error on z
        //                     double wedge_length = 27.7; //mm
        //                     double dz2 = Math.pow(wedge_length, 2)/12;
                            
        //                     RealMatrix measurementNoise = new Array2DRowRealMatrix(
        //                                                     new double[][]{
        //                                                         {dr2, 0.0000, 0.0000},
        //                                                         {0.00, dphi2, 0.0000},
        //                                                         {0.00, 0.0000, dz2}
        //                                                     });//3x3;
        //                     hit.setMeasurementNoise(measurementNoise);
        //                     ATOF_hits.put(trackid, hit);

        //                     IsHitSelected = true;
        //                     break;
        //                 }
        //             } // end loop over ATOF bank rows
        //         } //end loop over predicted ATOF hits
        //     } // end if 
        // } // end loop over tracks

        /// Clean AHDC bad hits
        double sigma = 0.5; // mm
        for (Track track : AHDC_tracks) {
            ArrayList<Hit> AHDC_hits = track.getHits();
            Iterator<Hit> it = AHDC_hits.iterator();
            while (it.hasNext()) {
                Hit hit = it.next();
                if (Math.abs(hit.getResidual()) > 3*sigma) {
                    it.remove();
                }
            }
        }

        /// Second propagation : each AHDC_tracks will be fitted
        KF.set_Niter(15);
        KF.propagation(AHDC_tracks, event, magfield, IsMC);

        /// write the AHDC::kftrack bank in the event
        org.jlab.rec.ahdc.Banks.RecoBankWriter ahdc_writer = new org.jlab.rec.ahdc.Banks.RecoBankWriter();
        DataBank recoKFTracksBank   = ahdc_writer.fillAHDCKFTrackBank(event, AHDC_tracks);
        event.appendBank(recoKFTracksBank);
        // update the AHDC::hits bank : fill the residuals
        event.removeBank("AHDC::hits");
        ArrayList<Hit> AHDC_hits = new ArrayList<>();
        for (Track track : AHDC_tracks) {
            AHDC_hits.addAll(track.getHits());
        }     
        DataBank recoKFHitsBank = ahdc_writer.fillAHDCHitsBank(event, AHDC_hits);
        event.appendBank(recoKFHitsBank); // remark: only  hits associated to a track are saved
 

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
