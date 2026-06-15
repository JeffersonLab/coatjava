package org.jlab.service.alert;

import ai.djl.translate.TranslateException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Iterator;

import java.util.HashMap;
import java.util.Map;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.swimtools.Swim;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.geom.base.Detector;
import org.jlab.geom.detector.alert.ATOF.AlertTOFDetector;
import org.jlab.geom.detector.alert.ATOF.AlertTOFFactory;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;
import org.jlab.rec.alert.TrackMatchingAI.ModelTrackMatching;
import org.jlab.rec.alert.AIPID.ModelPrePID;
import org.jlab.rec.alert.banks.RecoBankWriter;
import org.jlab.rec.alert.projections.TrackProjector;
import org.jlab.rec.atof.hit.ATOFHit;
import org.jlab.rec.alert.AI.InterCluster;
import org.jlab.rec.ahdc.AHDCCluster.AHDCCluster;
import org.jlab.rec.ahdc.DocaCluster.DocaCluster;
import org.jlab.rec.ahdc.DocaCluster.DocaClusterRefiner;
import org.jlab.rec.ahdc.HelixFit.HelixFitJava;
import org.jlab.rec.ahdc.KalmanFilter.KalmanFilter;
import org.jlab.rec.ahdc.KalmanFilter.RadialKFHit;
import org.jlab.rec.ahdc.TrackFindingMode;
import org.jlab.rec.ahdc.PreCluster.PreCluster;
import org.jlab.rec.ahdc.PreCluster.PreClusterFinder;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.alert.TrackFinding.AITrackFinder;
import org.jlab.rec.alert.TrackFinding.DistanceTrackFinder;
import org.jlab.rec.alert.TrackFinding.GNNTrackFinder;
import org.jlab.rec.alert.TrackFinding.HoughTrackFinder;
import org.jlab.rec.alert.TrackFinding.TrackFinder;
import org.jlab.rec.alert.TrackFinding.TrackFinderResult;
import org.jlab.geom.detector.alert.AHDC.AlertDCDetector;
import org.jlab.geom.detector.alert.AHDC.AlertDCFactory;
import org.jlab.rec.alert.Track.AtofHitStub;
import org.jlab.rec.alert.Track.Track;
import org.jlab.rec.alert.Track.TrackCandidate;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.jlab.clas.pdg.PDGDatabase;
import org.jlab.clas.pdg.PDGParticle;
import java.util.List;
import java.util.logging.Logger;



import ai.djl.util.Pair;
import org.jlab.rec.alert.AIPID.PrePIDResult;


/**
 * <h1>ALERTEngine reconstruction service.</h1>
 *
 * <h2>YAML configuration</h2>
 *
 * All settings the engine reads from the reconstruction YAML, shown with their
 * default values:
 *
 * <pre>{@code
 * services:
 *   - class: org.jlab.service.alert.ALERTEngine
 *     name: ALERT
 *
 * configuration:
 *   services:
 *     ALERT:
 *       Mode: "AI_GNN"   # track-finding strategy; see TrackFindingMode
 *                        # (AI_MLP | CV_Distance | CV_Hough | AI_GNN)
 * }</pre>
 *
 * @author  Whit Armstrong
 * @author  Noemie Pilleux
 * @author  Mathieu Ouillon
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
    static final Logger LOGGER = Logger.getLogger(ALERTEngine.class.getName());
    private AlertTOFDetector ATOF; // ALERT ATOF detector
    private AlertDCDetector AHDC; // ALERT AHDC detector

    /**
     *  Current run number being processed.
     *  TODO: why atomic here and nowhere else? 
     */
    private final AtomicInteger run = new AtomicInteger(0);

    private double b; //Magnetic field

    private ModelTrackMatching modelTrackMatching;
    private ModelPrePID modelPrePID;

    // AHDC track-finding strategy (driven by ALERT.Mode YAML key)
    private TrackFinder trackFinder;
    private final org.jlab.rec.ahdc.Banks.RecoBankWriter ahdcWriter = new org.jlab.rec.ahdc.Banks.RecoBankWriter();

    // AHDC calibration table (refreshed on run change)
    private IndexedTable ahdcAdcGainsTable;

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

    @Override
    public void detectorChanged(int run) {
        DatabaseConstantProvider cp = new DatabaseConstantProvider(run, "default");
        cp.loadTable("/geometry/alert/ahdc/layer_alignment");
        cp.loadTable("/geometry/alert/ahdc/wire_alignment");
        ATOF = (new AlertTOFFactory()).createDetectorCLAS(cp);
        AHDC = (new AlertDCFactory()).createDetectorCLAS(cp);
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

        Map<String, Integer> tableMap = new HashMap<>();
        tableMap.put("/calibration/alert/ahdc/gains", 3);
        requireConstants(tableMap);
        this.getConstantsManager().setVariation("default");

        TrackFindingMode mode = TrackFindingMode.AI_GNN;
        String modeConfig = this.getEngineConfigString("Mode");
        if (modeConfig != null) mode = TrackFindingMode.valueOf(modeConfig);
        switch (mode) {
            case AI_MLP:            trackFinder = new AITrackFinder();       break;
            case CV_Distance:       trackFinder = new DistanceTrackFinder(); break;
            case CV_Hough:          trackFinder = new HoughTrackFinder();    break;
            case AI_GNN:            trackFinder = new GNNTrackFinder();      break;
        }

        this.registerOutputBank(
                "AHDC::preclusters", "AHDC::clusters", "AHDC::track",
                "AHDC::interclusters", "AHDC::docaclusters", "AHDC::ai:prediction",
                "AHDC::mc", "AHDC::kftrack",
                "ALERT::projections", "ALERT::ai:projections", "ALERT::prePID");

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
    public boolean processDataEventUser(DataEvent event) {

        if (!event.hasBank("AHDC::adc"))
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
            ahdcAdcGainsTable = this.getConstantsManager().getConstants(newRun, "/calibration/alert/ahdc/gains");
        }

        // ===========================================================================
        // AHDC track-finding pipeline (preclustering, track finder, DOCA, helix fit)
        // Originally lived in AHDCEngine; runs here so AHDCEngine is hits-only.
        // Reads AHDC::hits produced by AHDCEngine, mutates Hit.trackId during finding,
        // then rewrites AHDC::hits and writes the cluster/track/intercluster banks.
        // ===========================================================================
        boolean simulation = event.hasBank("MC::Particle");

        if (event.hasBank("AHDC::hits")) {

            // I) Reconstruct Hit list from AHDC::hits bank
            DataBank ahdcHitBank = event.getBank("AHDC::hits");
            ArrayList<Hit> AHDC_Hits = new ArrayList<>();
            for (int row = 0; row < ahdcHitBank.rows(); row++) {
                int id = ahdcHitBank.getShort("id", row);
                int superlayer = ahdcHitBank.getByte("superlayer", row);
                int layer = ahdcHitBank.getByte("layer", row);
                int wire = ahdcHitBank.getInt("wire", row);
                int adc = ahdcHitBank.getInt("adc", row);
                double doca = ahdcHitBank.getDouble("doca", row);
                double time = ahdcHitBank.getDouble("time", row);
                double tot = ahdcHitBank.getDouble("timeOverThreshold", row);
                Hit hit = new Hit(id, superlayer, layer, wire, doca, adc, time);
                hit.setWirePosition(AHDC);
                hit.setADC(adc);
                hit.setToT(tot);
                AHDC_Hits.add(hit);
            }

            // II) Track Finding via the strategy selected in init() (ALERT.Mode YAML key).
            // The implementation owns its own preclustering, cluster building, and any
            // mode-specific safety fallbacks (e.g. AITrackFinder delegates to Distance
            // when the hit count exceeds its MAX_HITS_FOR_AI threshold). The ATOF hits
            // are passed for finders that build joint AHDC+ATOF graphs (GNN); the
            // AHDC-only finders ignore them.
            List<AtofHitStub> atofHitsForGNN = extractAtofHits(event);
            TrackFinderResult trackResult = trackFinder.findTracks(AHDC_Hits, atofHitsForGNN);
            if (!trackResult.isValid()) {
                return false;
            }
            ArrayList<TrackCandidate> AHDC_Candidates = new ArrayList<>(trackResult.getTracks());

            // Preclusters are also written to AHDC::preclusters as a diagnostic bank;
            // PreClusterFinder is idempotent on Hit.use, so re-running it here is safe.
            PreClusterFinder preclusterfinder = new PreClusterFinder();
            preclusterfinder.findPreclusters(AHDC_Hits);
            ArrayList<PreCluster> AHDC_PreClusters = preclusterfinder.get_AHDCPreClusters();

            // IV) Global fit: DOCA refinement + helix fit.
            // Each surviving TrackCandidate is fitted into a Track; the fit is
            // dispatched on the candidate's CandidateType (see the switch below).
            int trackid = 0;
            ArrayList<DocaCluster> all_docaClusters = new ArrayList<>();
            AHDC_Candidates.removeIf(cand -> cand.get_Clusters().size() < 3);
            ArrayList<Track> AHDC_Tracks = new ArrayList<>();
            for (TrackCandidate cand : AHDC_Candidates) {
                trackid++;
                cand.set_trackId(trackid);
                // Every surviving candidate yields an AHDC::track row, even if the
                // helix fit below is skipped (its Track keeps zero parameters).
                Track track = new Track(cand);
                AHDC_Tracks.add(track);
                List<AHDCCluster> originalClusters = cand.get_Clusters();
                ArrayList<DocaCluster> docaClusters = DocaClusterRefiner.buildRefinedClusters(originalClusters);
                all_docaClusters.addAll(docaClusters);
                if (docaClusters == null || docaClusters.size() < 3 || originalClusters == null || originalClusters.size() < 3) {
                    // not enough points, skip helix fit
                    continue;
                }
                HelixFitJava h = new HelixFitJava();
                switch (cand.getType()) {
                    case AHDC_ATOF:
                        // EXTENSION HOOK: a future commit may incorporate
                        // cand.getAtofHits() as an additional fit constraint here.
                        // For now AHDC_ATOF fits exactly like AHDC_ONLY — the ATOF
                        // hits are carried on the candidate, not yet fitted.
                    case AHDC_ONLY:
                    case AHDC_VERTEX:
                    default:
                        track.setPositionAndMomentum(h.helix_fit_with_doca_selection(docaClusters, 1));
                        break;
                }
            }

            // V) Replace AHDC::hits (now with trackId) and write track-finding output banks
            DataBank recoHitsBank        = ahdcWriter.fillAHDCHitsBank(event, AHDC_Hits);
            DataBank recoPreClusterBank  = ahdcWriter.fillPreClustersBank(event, AHDC_PreClusters);
            ArrayList<AHDCCluster> AHDC_Clusters = new ArrayList<>();
            for (Track track : AHDC_Tracks) {
                AHDC_Clusters.addAll(track.get_Clusters());
            }
            DataBank recoClusterBank     = ahdcWriter.fillClustersBank(event, AHDC_Clusters);
            DataBank recoTracksBank      = ahdcWriter.fillAHDCTrackBank(event, AHDC_Tracks);
            DataBank clustersDocaBank    = ahdcWriter.fillAHDCDocaClustersBank(event, all_docaClusters);

            ArrayList<InterCluster> all_interclusters = new ArrayList<>();
            for (Track track : AHDC_Tracks) {
                all_interclusters.addAll(track.getInterclusters());
            }
            DataBank recoInterClusterBank = ahdcWriter.fillInterClusterBank(event, all_interclusters);

            event.removeBank("AHDC::hits");
            event.appendBank(recoHitsBank);
            event.appendBank(recoPreClusterBank);
            event.appendBank(recoClusterBank);
            event.appendBank(recoTracksBank);
            event.appendBank(recoInterClusterBank);
            event.appendBank(clustersDocaBank);

            if (simulation) {
                DataBank recoMCBank = ahdcWriter.fillAHDCMCTrackBank(event);
                event.appendBank(recoMCBank);
            }
        }
        // ===========================================================================

        // ATOF-dependent processing follows. Bail out for events without ATOF::tdc
        // so the AHDC track-finding output above stands on its own (matches the
        // pre-refactor flow where AHDCEngine ran independently of ATOF presence).
        if (!event.hasBank("ATOF::tdc"))
            return false;

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

            float[] pred;
            try {
                pred = modelTrackMatching.prediction(interClusters);
            } catch (TranslateException ex) {
                LOGGER.warning(() -> "Exception in ALERTEngine track matching: " + ex);
                continue;
            }
            int sector_pred = (int) pred[0];
            int layer_pred = (int) pred[1];
            int wedge_pred = (int) pred[2];

            // The matching model's three argmax heads can land outside the ATOF
            // ranges (sectors 0-14, layers 0-3, wedges 0-9) when the input
            // interclusters fall outside its training distribution; the ATOFHit
            // geometry lookup chain returns null on a miss and would NPE.
            if (sector_pred < 0 || sector_pred >= 15
                    || layer_pred < 0 || layer_pred >= 4
                    || wedge_pred < 0 || wedge_pred >= 10) {
                continue;
            }

            ATOFHit hit_pred = new ATOFHit(sector_pred, layer_pred, wedge_pred, 0, 0, 0, 0f, ATOF, null, 0);
            double pred_x = hit_pred.getX();
            double pred_y = hit_pred.getY();
            double pred_z = hit_pred.getZ();

            double threshold = 20.0;
            double minDistanceSquared = threshold * threshold;

            int matchHitId = -1;

            for (int k = 0; k < bank_ATOFHits.rows(); k++) {
                int component = bank_ATOFHits.getInt("component", k);
                if (component == 10) continue;

                double hitX = bank_ATOFHits.getFloat("x", k);
                double hitY = bank_ATOFHits.getFloat("y", k);
                double hitZ = bank_ATOFHits.getFloat("z", k);

                double dx = pred_x - hitX;
                double dy = pred_y - hitY;
                double dz = pred_z - hitZ;

                double distanceSquared = dx * dx + dy * dy + dz * dz;

                if (distanceSquared < minDistanceSquared) {
                    minDistanceSquared = distanceSquared;
                    //matchAtofHit = hit;
                    matchHitId = bank_ATOFHits.getInt("id", k);
                }
            }
            matched_ATOF_hit_id.add(new Pair<>(track_id, matchHitId));
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
        
        /// Pre conditions
        if (!event.hasBank("AHDC::track")) {return false;}
        if (!event.hasBank("AHDC::hits")) {return false;}

        /// tmp: misalignement with respect to the center of the AHDC (mm)
        double clas_alignement = +75;
        double atof_alignement = 0;

        /// Read the electron vertex
        double vz_electron = 0;
        double[] vz_error2 = {0.09, 1e10, 1e10}; // mm^2, radians^2, mm^2, error on r, phi, z
        boolean IsVertexDefined = false;
        if (event.hasBank("REC::Particle")) {
            DataBank recBank = event.getBank("REC::Particle");
            for (int row = 0; row < recBank.rows(); row++) {
                if (recBank.getInt("pid", row) == 11) {
                    vz_electron = 10*recBank.getFloat("vz",row); // conversion in mm
                    IsVertexDefined = true;
                    

                    //double px = recBank.getFloat("px",row);
                    //double py = recBank.getFloat("py",row);
                    //double pz = recBank.getFloat("pz",row);
                    //double p = Math.sqrt(px*px+py*py+pz*pz);
                    //double theta = Math.acos(pz/p);
                    
                    // set the resolutions on r and z! to be done
                    vz_error2[0] = 0.09; // should depend on p and theta
                    vz_error2[2] = 64; // should depend on p and theta

                    break; // only look at the first electron
                }
            }
        }

        /// Read the list of tracks/hits from the banks AHDC::track and AHDC::hits
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
                    // use raw adc in the Kalman Filter
                    double gainCorr = ahdcAdcGainsTable.getDoubleValue("gainCorr", 1, 10*superlayer+layer, wire);
                    Hit hit = new Hit(id, superlayer, layer, wire, doca, adc/gainCorr, time);
                    hit.setWirePosition(AHDC);
                    hit.setTrackId(trackid);
                    hit.setADC(adc);
                    hit.setToT(tot);
                    AHDC_hits.add(hit);
                }
            }
            // Rebuild a hits-only TrackCandidate and wrap it in a Track seeded
            // with the banked helix-fit position/momentum (mm / MeV).
            // Invariant: AHDC_hits is non-empty. The AI_MLP path uses greedy
            // non-overlap selection so each PreCluster (and thus each Hit) belongs to at most one
            // surviving track, so the set_trackId stamping is unambiguous and every AHDC::track
            // row has matching AHDC::hits rows.
            TrackCandidate newCandidate = new TrackCandidate(AHDC_hits);
            Track newTrack = new Track(newCandidate);
            double[] vec = {
                trackBank.getFloat("x",  row),
                trackBank.getFloat("y",  row),
                trackBank.getFloat("z",  row),
                trackBank.getFloat("px", row),
                trackBank.getFloat("py", row),
                trackBank.getFloat("pz", row)
            };
            newTrack.setPositionAndMomentumVec(vec);
            newTrack.set_trackId(trackid);
            AHDC_tracks.add(newTrack);
        }

        /// Associate the electron vertex (the beamline hit) to each track
        boolean IsMC = event.hasBank("MC::Particle");
        double vz_constraint = vz_electron + (IsMC ? 0 : clas_alignement); // we don't have the misalignment in simulation
        for (Track track : AHDC_tracks) {
            RadialKFHit hit_beam = new RadialKFHit(0, 0, vz_constraint);
            RealMatrix measurementNoise = new Array2DRowRealMatrix(
											new double[][]{
												{vz_error2[0], 0.0000      , 0.0000},
												{0.0000      , vz_error2[1], 0.0000},
												{0.0000      , 0.0000      , vz_error2[2]}
											});//3x3;
			hit_beam.setMeasurementNoise(measurementNoise);
            track.setBeamlineHit(hit_beam);
        }

        /// Look for ATOF wedge hits predicted by the AI
        HashMap<Integer, RadialKFHit> map_ATOF_hits = new HashMap<>();
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
                        z += atof_alignement; // there is a shift between AHDC and ATOF (still don't know why) !
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
                        map_ATOF_hits.put(trackid, hit);
                    }
                }
            }
        }

        /// Associate the ATOF hits to each track
        for (Track track : AHDC_tracks) {
            RadialKFHit hit = map_ATOF_hits.get(track.get_trackId());
            ArrayList<RadialKFHit> list = new ArrayList<>();
            if (hit != null) list.add(hit); // for now, we only consider one hit in the ATOF
            track.setATOFHits(list);
        }

        /// Intialise the Kalman Filter
        double magfieldfactor = runBank.getFloat("solenoid", 0);
        double magfield = 50*magfieldfactor;
        PDGParticle proton = PDGDatabase.getParticleById(2212);
        int Niter = 25;
        KalmanFilter KF = new KalmanFilter(proton, Niter);
        //KF.set_ATOF_detector(null);
        KF.set_ATOF_detector(ATOF); // Reference the ATOF geometry in the Kalman Filter
        KF.set_atof_alignement(atof_alignement);
        KF.set_vz_constraint(vz_constraint);
        KF.set_vertex_flag(IsVertexDefined);

        /// Do a first propagation
        KF.propagation(AHDC_tracks, magfield, IsMC);

        /// Look at the new ATOF hits predicted after projection of the track on the lower surface of the ATOF wedges
        HashMap<Integer, ArrayList<int[]>> ATOF_hits_predicted = KF.get_ATOF_hits_predicted();
        for (Track track : AHDC_tracks) {
            int trackid = track.get_trackId();
            ArrayList<int[]> possible_wedges = ATOF_hits_predicted.get(trackid);
            boolean IsHitSelected = false;
            if (possible_wedges != null) {
                for (int[] id : possible_wedges) {
                    int sector = id[0];
                    int layer  = id[1];
                    int wedge  = id[2];
                    // check if this hit exist in ATOF::hits
                    for (int row = 0; row < bank_ATOFHits.rows(); row++) {
                        if (bank_ATOFHits.getInt("sector", row) == sector && bank_ATOFHits.getInt("layer", row) == layer && bank_ATOFHits.getInt("component", row) == wedge) {
                            // create a RadialKFHit
                            double x = bank_ATOFHits.getFloat("x", row);
                            double y = bank_ATOFHits.getFloat("y", row);
                            double z = bank_ATOFHits.getFloat("z", row);
                            z += atof_alignement; // there is a shift between AHDC and ATOF (still don't know why) !
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

                            ArrayList<RadialKFHit> list = new ArrayList<>();
                            list.add(hit); // for now, we only consider one hit in the ATOF
                            track.setATOFHits(list); // update the list of the ATOF hit (i.e override AI ATOF hit if it exist)

                            IsHitSelected = true;
                            break;
                        } // end id matching
                    } // end loop over atof hit
                    if (IsHitSelected) break;
                }
            }
        }

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
        KF.propagation(AHDC_tracks, magfield, IsMC);

        /// Write the AHDC::kftrack bank in the event
        org.jlab.rec.ahdc.Banks.RecoBankWriter ahdc_writer = new org.jlab.rec.ahdc.Banks.RecoBankWriter();
        DataBank recoKFTracksBank   = ahdc_writer.fillAHDCKFTrackBank(event, AHDC_tracks);
        event.appendBank(recoKFTracksBank);
        /// Update the AHDC::hits bank : fill the residuals
        event.removeBank("AHDC::hits");
        ArrayList<Hit> AHDC_hits = new ArrayList<>();
        for (Track track : AHDC_tracks) {
            AHDC_hits.addAll(track.getHits());
        }     
        DataBank recoKFHitsBank = ahdc_writer.fillAHDCHitsBank(event, AHDC_hits);
        event.appendBank(recoKFHitsBank); // remark: only  hits assocuated to a track are saved
 

        return true;
    }

    /** Extract a deduplicated list of ATOF hits from {@code ATOF::hits} for the
     *  GNN graph builder. Dedup key is {@code (sector, layer, component)} —
     *  inference-time variant of the Python dedup which also keys on track id
     *  (only needed at training time). Returns an empty list when the bank is
     *  absent. */
    private static List<AtofHitStub> extractAtofHits(DataEvent event) {
        if (!event.hasBank("ATOF::hits")) return Collections.emptyList();
        DataBank bank = event.getBank("ATOF::hits");
        int rows = bank.rows();
        Set<Long> seen = new HashSet<>();
        ArrayList<AtofHitStub> hits = new ArrayList<>(rows);
        for (int r = 0; r < rows; r++) {
            int sector    = bank.getInt("sector", r);
            int layer     = bank.getInt("layer", r);
            int component = bank.getInt("component", r);
            long key = (((long) sector * 1000L) + layer) * 1000L + component;
            if (!seen.add(key)) continue;
            double x = bank.getFloat("x", r);
            double y = bank.getFloat("y", r);
            hits.add(new AtofHitStub(sector, layer, component, x, y));
        }
        return hits;
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
