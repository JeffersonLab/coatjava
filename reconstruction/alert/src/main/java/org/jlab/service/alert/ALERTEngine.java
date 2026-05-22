package org.jlab.service.alert;

import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.HashMap;
import java.util.Map;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.swimtools.Swim;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.geom.base.Detector;
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
    Detector ATOF; // ALERT ATOF detector
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
    public void detectorChanged(int run) {}

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
        
        // One CCDB session for both ATOF and AHDC geometry.
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");
        ATOF = factory.createDetectorCLAS(cp);
        AHDC = (new AlertDCFactory()).createDetectorCLAS(cp);

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

            ATOFHit hit_pred = new ATOFHit(sector_pred, layer_pred, wedge_pred, 0, 0, 0, 0f, ATOF, null);
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

                ATOFHit hit = new ATOFHit(sector, layer, component, 0, 0, 0, 0f, ATOF, null);

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
        
        // read the list of tracks/hits from the banks AHDC::track and AHDC::hits
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
        // intialise the Kalman Filter
        double magfieldfactor = runBank.getFloat("solenoid", 0);
        double magfield = 50*magfieldfactor;
        boolean IsMC = event.hasBank("MC::Particle");
        PDGParticle proton = PDGDatabase.getParticleById(2212);
        int Niter = 40;
        KalmanFilter KF = new KalmanFilter(proton, Niter);
        ///////////////////////////////////////////////////////
        // first propagation : each AHDC_tracks will be fitted
        ///////////////////////////////////////////////////////
        KF.propagation(AHDC_tracks, event, magfield, IsMC);
        /////////////////////////////////////////////
        // write the AHDC::kftrack bank in the event
        /////////////////////////////////////////////
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
