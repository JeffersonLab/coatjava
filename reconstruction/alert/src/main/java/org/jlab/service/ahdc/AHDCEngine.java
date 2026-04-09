package org.jlab.service.ahdc;

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
import org.jlab.rec.ahdc.DocaCluster.DocaClusterRefiner;
import org.jlab.rec.ahdc.DocaCluster.DocaCluster;
import org.jlab.rec.ahdc.Distance.Distance;
import org.jlab.rec.ahdc.HelixFit.HelixFitJava;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.Hit.HitReader;
import org.jlab.rec.ahdc.HoughTransform.HoughTransform;
import org.jlab.rec.ahdc.KalmanFilter.MaterialMap;
import org.jlab.rec.ahdc.PreCluster.PreCluster;
import org.jlab.rec.ahdc.PreCluster.PreClusterFinder;
import org.jlab.rec.ahdc.Track.Track;
import org.jlab.rec.ahdc.ModeTrackFinding;
import java.io.File;
import java.util.*;
import java.util.logging.Logger;

import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.detector.alert.AHDC.AlertDCDetector;
import org.jlab.geom.detector.alert.AHDC.AlertDCFactory;
import org.jlab.rec.alert.constants.CalibrationConstantsLoader;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.detector.pulse.ModeAHDC;

/** AHDCEngine reconstruction service.
 *
 *  AHDC Reconstruction using only AHDC information.
 *
 * Reconstruction utilizing other detectors (i.e. ATOF) are 
 * implemented in ALERTEngine.
 *
 */
public class AHDCEngine extends ReconstructionEngine {
    static final Logger LOGGER = Logger.getLogger(AHDCEngine.class.getName());

    private boolean simulation;

    /// Material Map used by Kalman filter
    private HashMap<String, Material> materialMap;

    private ModelTrackFinding modelTrackFinding;
    private ModeTrackFinding modeTrackFinding = ModeTrackFinding.AI_Track_Finding;
    static final double TRACK_FINDING_AI_THRESHOLD = 0.2;
    static final int MAX_HITS_FOR_AI = 300;

    private AlertDCDetector factory = null;
    private ModeAHDC ahdcExtractor = new ModeAHDC();

    // AHDC calibration maps (instance-level, rebuilt on run change)
    private Map<Integer, double[]> ahdcTimeOffsets;
    private Map<Integer, double[]> ahdcTimeToDistance;
    private Map<Integer, double[]> ahdcRawHitCuts;
    private Map<Integer, double[]> ahdcAdcGains;
    private Map<Integer, double[]> ahdcTimeOverThreshold;

    int Run = -1;

    public AHDCEngine() { super("ALERT", "ouillon", "1.0.1"); }

    public boolean init(ModeTrackFinding m) {
        modeTrackFinding = m;
        return init();
    }

    @Override
    public boolean init() {

        factory = (new AlertDCFactory()).createDetectorCLAS(new DatabaseConstantProvider());
        simulation = false;

        if (materialMap == null) materialMap = MaterialMap.generateMaterials();

        String modeConfig = this.getEngineConfigString("Mode");
        if (modeConfig != null) modeTrackFinding = ModeTrackFinding.valueOf(modeConfig);
        if (modeTrackFinding == ModeTrackFinding.AI_Track_Finding) modelTrackFinding = new ModelTrackFinding();

        Map<String, Integer> tableMap = new HashMap<>();
        tableMap.put("/calibration/alert/ahdc/time_offsets", 3);
        tableMap.put("/calibration/alert/ahdc/time_to_distance_wire", 3);
        tableMap.put("/calibration/alert/ahdc/raw_hit_cuts", 3);
        tableMap.put("/calibration/alert/ahdc/gains", 3);
        tableMap.put("/calibration/alert/ahdc/time_over_threshold", 3);

        requireConstants(tableMap);
        
        this.getConstantsManager().setVariation("default");
        
        this.registerOutputBank("AHDC::hits","AHDC::preclusters","AHDC::clusters","AHDC::track","AHDC::mc","AHDC::ai:prediction");

        return true;
    }

    
    private void loadAHDCConstants(int run) {
        ConstantsManager manager = this.getConstantsManager();

        IndexedTable tblTimeOffsets       = manager.getConstants(run, "/calibration/alert/ahdc/time_offsets");
        IndexedTable tblTime2Dist         = manager.getConstants(run, "/calibration/alert/ahdc/time_to_distance");
        IndexedTable tblRawHitCuts        = manager.getConstants(run, "/calibration/alert/ahdc/raw_hit_cuts");
        IndexedTable tblAdcGains          = manager.getConstants(run, "/calibration/alert/ahdc/gains");
        IndexedTable tblTimeOverThreshold = manager.getConstants(run, "/calibration/alert/ahdc/time_over_threshold");

        ahdcTimeOffsets       = new HashMap<>();
        ahdcTimeToDistance    = new HashMap<>();
        ahdcRawHitCuts        = new HashMap<>();
        ahdcAdcGains          = new HashMap<>();
        ahdcTimeOverThreshold = new HashMap<>();

        // Time offsets
        for (int i = 0; i < tblTimeOffsets.getRowCount(); i++) {
            int sector    = Integer.parseInt((String) tblTimeOffsets.getValueAt(i, 0));
            int layer     = Integer.parseInt((String) tblTimeOffsets.getValueAt(i, 1));
            int component = Integer.parseInt((String) tblTimeOffsets.getValueAt(i, 2));
            int key = sector * 10000 + layer * 100 + component;
            ahdcTimeOffsets.put(key, new double[]{
                tblTimeOffsets.getDoubleValue("t0",      sector, layer, component),
                tblTimeOffsets.getDoubleValue("dt0",     sector, layer, component),
                tblTimeOffsets.getDoubleValue("extra1",  sector, layer, component),
                tblTimeOffsets.getDoubleValue("extra2",  sector, layer, component),
                tblTimeOffsets.getDoubleValue("chi2ndf", sector, layer, component)
            });
        }

        // Time to distance
        for (int i = 0; i < tblTime2Dist.getRowCount(); i++) {
            int sector    = Integer.parseInt((String) tblTime2Dist.getValueAt(i, 0));
            int layer     = Integer.parseInt((String) tblTime2Dist.getValueAt(i, 1));
            int component = Integer.parseInt((String) tblTime2Dist.getValueAt(i, 2));
            int key = sector * 10000 + layer * 100 + component;
            ahdcTimeToDistance.put(key, new double[]{
                tblTime2Dist.getDoubleValue("p0",      sector, layer, component),
                tblTime2Dist.getDoubleValue("p1",      sector, layer, component),
                tblTime2Dist.getDoubleValue("p2",      sector, layer, component),
                tblTime2Dist.getDoubleValue("p3",      sector, layer, component),
                tblTime2Dist.getDoubleValue("p4",      sector, layer, component),
                tblTime2Dist.getDoubleValue("p5",      sector, layer, component),
                tblTime2Dist.getDoubleValue("dp0",     sector, layer, component),
                tblTime2Dist.getDoubleValue("dp1",     sector, layer, component),
                tblTime2Dist.getDoubleValue("dp2",     sector, layer, component),
                tblTime2Dist.getDoubleValue("dp3",     sector, layer, component),
                tblTime2Dist.getDoubleValue("dp4",     sector, layer, component),
                tblTime2Dist.getDoubleValue("dp5",     sector, layer, component),
                tblTime2Dist.getDoubleValue("chi2ndf", sector, layer, component)
            });
        }

        // Raw hit cuts
        for (int i = 0; i < tblRawHitCuts.getRowCount(); i++) {
            int sector    = Integer.parseInt((String) tblRawHitCuts.getValueAt(i, 0));
            int layer     = Integer.parseInt((String) tblRawHitCuts.getValueAt(i, 1));
            int component = Integer.parseInt((String) tblRawHitCuts.getValueAt(i, 2));
            int key = sector * 10000 + layer * 100 + component;
            ahdcRawHitCuts.put(key, new double[]{
                tblRawHitCuts.getDoubleValue("t_min",   sector, layer, component),
                tblRawHitCuts.getDoubleValue("t_max",   sector, layer, component),
                tblRawHitCuts.getDoubleValue("tot_min", sector, layer, component),
                tblRawHitCuts.getDoubleValue("tot_max", sector, layer, component),
                tblRawHitCuts.getDoubleValue("adc_min", sector, layer, component),
                tblRawHitCuts.getDoubleValue("adc_max", sector, layer, component),
                tblRawHitCuts.getDoubleValue("ped_min", sector, layer, component),
                tblRawHitCuts.getDoubleValue("ped_max", sector, layer, component)
            });
        }

        // ADC gains
        for (int i = 0; i < tblAdcGains.getRowCount(); i++) {
            int sector    = Integer.parseInt((String) tblAdcGains.getValueAt(i, 0));
            int layer     = Integer.parseInt((String) tblAdcGains.getValueAt(i, 1));
            int component = Integer.parseInt((String) tblAdcGains.getValueAt(i, 2));
            int key = sector * 10000 + layer * 100 + component;

            // TODO: Try and catch here that is weird no? 
            double extra1 = 0.0, extra2 = 0.0, extra3 = 0.0;
            try { extra1 = tblAdcGains.getDoubleValue("extra1", sector, layer, component); } catch (Exception e) {}
            try { extra2 = tblAdcGains.getDoubleValue("extra2", sector, layer, component); } catch (Exception e) {}
            try { extra3 = tblAdcGains.getDoubleValue("extra3", sector, layer, component); } catch (Exception e) {}
            ahdcAdcGains.put(key, new double[]{
                tblAdcGains.getDoubleValue("gainCorr",  sector, layer, component),
                tblAdcGains.getDoubleValue("dgainCorr", sector, layer, component),
                extra1, extra2, extra3
            });
        }

        // Time over threshold
        for (int i = 0; i < tblTimeOverThreshold.getRowCount(); i++) {
            int sector    = Integer.parseInt((String) tblTimeOverThreshold.getValueAt(i, 0));
            int layer     = Integer.parseInt((String) tblTimeOverThreshold.getValueAt(i, 1));
            int component = Integer.parseInt((String) tblTimeOverThreshold.getValueAt(i, 2));
            int key = sector * 10000 + layer * 100 + component;
            double extra1 = 0.0, extra2 = 0.0, extra3 = 0.0;
            try { extra1 = tblTimeOverThreshold.getDoubleValue("extra1", sector, layer, component); } catch (Exception e) {}
            try { extra2 = tblTimeOverThreshold.getDoubleValue("extra2", sector, layer, component); } catch (Exception e) {}
            try { extra3 = tblTimeOverThreshold.getDoubleValue("extra3", sector, layer, component); } catch (Exception e) {}
            ahdcTimeOverThreshold.put(key, new double[]{
                tblTimeOverThreshold.getDoubleValue("totCorr",  sector, layer, component),
                tblTimeOverThreshold.getDoubleValue("dtotCorr", sector, layer, component),
                extra1, extra2, extra3
            });
        }
    }

    @Override
    public boolean processDataEvent(DataEvent event) {

        if(event.hasBank("MC::Particle")) simulation = true;

        ahdcExtractor.update(30, null, event, "AHDC::wf", "AHDC::adc");

        if (event.hasBank("RUN::config")) {
            DataBank bank = event.getBank("RUN::config");
            int newRun = bank.getInt("run", 0);
            if (newRun <= 0) {
                LOGGER.warning("AHDCEngine:  got run <= 0 in RUN::config, skipping event.");
                return false;
            }
            // Load the constants
            //-------------------
            if(Run != newRun) {
                loadAHDCConstants(newRun);
                Run = newRun;
            }
        }

        if (event.hasBank("AHDC::adc")) {
            // I) Read raw hits
            HitReader hitReader = new HitReader(event, factory, simulation, ahdcRawHitCuts, ahdcTimeOffsets, ahdcTimeToDistance, ahdcTimeOverThreshold, ahdcAdcGains);
            ArrayList<Hit> AHDC_Hits = hitReader.get_AHDCHits();

            // II) Create PreClusters
            PreClusterFinder preclusterfinder = new PreClusterFinder();
            preclusterfinder.findPreclusters(AHDC_Hits);
            ArrayList<PreCluster> AHDC_PreClusters = preclusterfinder.get_AHDCPreClusters();


            // III) Track Finding: Input = PreClusters, Output = Tracks
            // During track finding we build Clusters and InterClusters. Each of these objects must be assigned a Track ID so we can:
            //   - identify which track they belong to,
            //   - write them properly into the output banks later,
            //   - and reuse them downstream in the ALERT Engine.
            //
            // If using AI-based track finding, tracks are identified using inter-clusters.
            // Otherwise, the conventional methods (Hough Transform or distance) use clusters.

            // Safety check: if too many hits, rely on conventional track finding
            if (AHDC_Hits.size() > MAX_HITS_FOR_AI) {
                LOGGER.info("Too many AHDC_Hits in AHDC::adc, rely on conventional track finding for this event");
                modeTrackFinding = ModeTrackFinding.CV_Distance;
            }

            ArrayList<Track> AHDC_Tracks = new ArrayList<>();

            if (modeTrackFinding == ModeTrackFinding.AI_Track_Finding) {
                // 1) Create inter-clusters from pre-clusters
                PreClustering preClustering = new PreClustering();
                ArrayList<InterCluster> inter_clusters = preClustering.mergePreclusters(AHDC_PreClusters);
                
                // 2) Create track candidates from inter-clusters
                ArrayList<ArrayList<InterCluster>> tracks_candidates = new ArrayList<>();
                TrackCandidatesGenerator trackCandidatesGenerator = new TrackCandidatesGenerator();
                boolean success = trackCandidatesGenerator.getAllPossibleTrack(inter_clusters, tracks_candidates);

                if (!success) {
                    LOGGER.severe("Too many track candidates find by the AI, exiting...");
                    return false;
                }

                // 3) Use AI model to evaluate track candidates
                ArrayList<TrackPrediction> predictions = new ArrayList<>();
                try {
                    AIPrediction aiPrediction = new AIPrediction();
                    predictions = aiPrediction.prediction(tracks_candidates, modelTrackFinding);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                // 4) Use the output for the AI model to select the good tracks among the candidates
                for (TrackPrediction t : predictions) {
                    if (t.getPrediction() > TRACK_FINDING_AI_THRESHOLD) AHDC_Tracks.add(new Track(t.getClusters()));
                }
                // The assignment of Track ID to all objects is done in the Kalman filter step below 
                // I don't know if it is a good idea.
            }
            else {
                // Conventional Track Finding: Hough Transform or Distance: use cluster informations to find tracks
                // 1) Create clusters from pre-clusters
                ClusterFinder clusterfinder = new ClusterFinder();
                clusterfinder.findCluster(AHDC_PreClusters);
                ArrayList<Cluster> AHDC_Clusters = clusterfinder.get_AHDCClusters();
                
                // 2) Find tracks using the selected conventional method
                if (modeTrackFinding == ModeTrackFinding.CV_Distance) {
                    Distance distance = new Distance();
                    distance.find_track(AHDC_Clusters);
                    AHDC_Tracks = distance.get_AHDCTracks();
                }
                else if (modeTrackFinding == ModeTrackFinding.CV_Hough) {
                    HoughTransform houghtransform = new HoughTransform();
                    houghtransform.find_tracks(AHDC_Clusters);
                    AHDC_Tracks = houghtransform.get_AHDCTracks();
                }
            }


            //Temporary track method ONLY for MC with no background;
            //AHDC_Tracks.add(new Track(AHDC_Hits));

            // V) Global fit
            int trackid = 0;
            ArrayList<DocaCluster> all_docaClusters = new ArrayList<>();
            AHDC_Tracks.removeIf(track -> track.get_Clusters().size() < 3);
            for (Track track : AHDC_Tracks) {
              trackid++;
              track.set_trackId(trackid);
              List<Cluster> originalClusters = track.get_Clusters();
              ArrayList<DocaCluster> docaClusters = DocaClusterRefiner.buildRefinedClusters(originalClusters);
              all_docaClusters.addAll(docaClusters);
              if (docaClusters == null || docaClusters.size() < 3 || originalClusters == null || originalClusters.size() < 3) {
                // not enough points, skip helix fit
                continue;
              }
              HelixFitJava h = new HelixFitJava();
              track.setPositionAndMomentum(h.helix_fit_with_doca_selection(docaClusters, 1));
            }

            // VII) Write bank
            RecoBankWriter writer = new RecoBankWriter();

            DataBank recoHitsBank       = writer.fillAHDCHitsBank(event, AHDC_Hits);
            DataBank recoPreClusterBank = writer.fillPreClustersBank(event, AHDC_PreClusters);
            ArrayList<Cluster> AHDC_Clusters = new ArrayList<>();
            for (Track track : AHDC_Tracks) {
                AHDC_Clusters.addAll(track.get_Clusters());
            }
            DataBank recoClusterBank    = writer.fillClustersBank(event, AHDC_Clusters);
            DataBank recoTracksBank     = writer.fillAHDCTrackBank(event, AHDC_Tracks);
            DataBank clustersDocaBank   = writer.fillAHDCDocaClustersBank(event, all_docaClusters);

            ArrayList<InterCluster> all_interclusters = new ArrayList<>();
            for (Track track : AHDC_Tracks) {
                all_interclusters.addAll(track.getInterclusters());
            }
            DataBank recoInterClusterBank = writer.fillInterClusterBank(event, all_interclusters);

            //event.removeBanks("AHDC::hits","AHDC::preclusters","AHDC::clusters","AHDC::track","AHDC::kftrack","AHDC::mc","AHDC::ai:prediction");
            event.appendBank(recoHitsBank);
            event.appendBank(recoPreClusterBank);
            event.appendBank(recoClusterBank);
            event.appendBank(recoTracksBank);
            event.appendBank(recoInterClusterBank);
            event.appendBank(clustersDocaBank);

            if (simulation) {
                DataBank recoMCBank = writer.fillAHDCMCTrackBank(event);
                event.appendBank(recoMCBank);
            }

        }
        return true;
    }

    public static void main(String[] args) {

        double starttime = System.nanoTime();

        int    nEvent     = 0;
        int    maxEvent   = 10;
        int    myEvent    = 3;
        String inputFile  = "output1.hipo";
        String outputFile = "output.hipo";

        if (new File(outputFile).delete()) System.out.println("output.hipo is delete.");

        System.err.println(" \n[PROCESSING FILE] : " + inputFile);

        AHDCEngine en = new AHDCEngine();

        HipoDataSource reader = new HipoDataSource();

        // en.init();
        en.init(ModeTrackFinding.AI_Track_Finding);

        reader.open(inputFile);
        // SchemaFactory factory = reader.getReader().getSchemaFactory();
        HipoDataSync   writer = new HipoDataSync();
        writer.open(outputFile);

        while (reader.hasEvent() && nEvent < maxEvent) {
            nEvent++;
            // if (nEvent % 100 == 0) System.out.println("nEvent = " + nEvent);
            DataEvent event = reader.getNextEvent();
            System.out.println("Event: " + nEvent);

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