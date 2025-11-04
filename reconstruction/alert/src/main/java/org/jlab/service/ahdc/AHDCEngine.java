package org.jlab.service.ahdc;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.tracking.kalmanfilter.Material;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;
import org.jlab.jnp.hipo4.data.SchemaFactory;
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
import org.jlab.rec.ahdc.ModeTrackFinding;
import java.io.File;
import java.util.*;
import java.util.logging.Logger;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.detector.alert.AHDC.AlertDCDetector;
import org.jlab.geom.detector.alert.AHDC.AlertDCFactory;
import org.jlab.rec.alert.constants.CalibrationConstantsLoader;
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

    /// TODO: Need to be in the ALERT Engine
    private Model_TM model_tm;

    private AlertDCDetector factory = null;
    private ModeAHDC ahdcExtractor = new ModeAHDC();

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

        if(this.getEngineConfigString("Mode")!=null) {
            if (Objects.equals(this.getEngineConfigString("Mode"), ModeTrackFinding.AI_Track_Finding.name()))
                modeTrackFinding = ModeTrackFinding.AI_Track_Finding;
            else if (Objects.equals(this.getEngineConfigString("Mode"), ModeTrackFinding.CV_Distance.name()))
                modeTrackFinding = ModeTrackFinding.CV_Distance;
            else if (Objects.equals(this.getEngineConfigString("Mode"), ModeTrackFinding.CV_Hough.name()))
                modeTrackFinding = ModeTrackFinding.CV_Hough;
        }

        if (modeTrackFinding == ModeTrackFinding.AI_Track_Finding) {
            modelTrackFinding = new ModelTrackFinding();
        }

        // Requires calibration constants
        String[] alertTables = new String[] {
            	"/calibration/alert/ahdc/time_offsets",
                "/calibration/alert/ahdc/time_to_distance",
                "/calibration/alert/ahdc/raw_hit_cuts",
                "/calibration/alert/atof/effective_velocity",
                "/calibration/alert/atof/time_walk",
                "/calibration/alert/atof/attenuation",
                "/calibration/alert/atof/time_offsets"
        };
        requireConstants(Arrays.asList(alertTables));
        
        this.getConstantsManager().setVariation("default");
        
        this.registerOutputBank("AHDC::hits","AHDC::preclusters","AHDC::clusters","AHDC::track","AHDC::kftrack","AHDC::mc","AHDC::ai:prediction");

        return true;
    }

    int Run = -1;

    @Override
    public boolean processDataEvent(DataEvent event) {

        double magfield = 50.0; // what is this? The full magnetic field strength in kGauss (factor * 50kGauss)

        if(event.hasBank("MC::Particle")) simulation = true;

        ahdcExtractor.update(30, null, event, "AHDC::wf", "AHDC::adc");

        if (event.hasBank("RUN::config")) {
            DataBank bank = event.getBank("RUN::config");
            int newRun = bank.getInt("run", 0);
            float magfieldfactor = bank.getFloat("solenoid", 0);
            if (newRun <= 0) {
                System.err.println("AHDCEngine:  got run <= 0 in RUN::config, skipping event.");
                return false;
            }
            // Load the constants
            //-------------------
            if(Run != newRun) {
                CalibrationConstantsLoader.Load(newRun, this.getConstantsManager());
                Run = newRun;
            }

            /// What is this? The field value in the RUN::config bank is a scaling factor (between -1 and 1) of the full field
            /// The kalman filter use the field in kG not Tesla
            magfield = 50 * magfieldfactor;
        }



        if (event.hasBank("AHDC::adc")) {
            // I) Read raw hits
            HitReader hitReader = new HitReader(event, factory, simulation);
            ArrayList<Hit> AHDC_Hits = hitReader.get_AHDCHits();
            if(simulation) { ArrayList<TrueHit> TrueAHDC_Hits = hitReader.get_TrueAHDCHits(); }

            // II) Create PreCluster
            PreClusterFinder preclusterfinder = new PreClusterFinder();
            preclusterfinder.findPreCluster(AHDC_Hits);
            ArrayList<PreCluster> AHDC_PreClusters = preclusterfinder.get_AHDCPreClusters();

            // III) Create Cluster
            ClusterFinder clusterfinder = new ClusterFinder();
            clusterfinder.findCluster(AHDC_PreClusters);
            ArrayList<Cluster> AHDC_Clusters = clusterfinder.get_AHDCClusters();

            // IV) Track Finder
            ArrayList<Track> AHDC_Tracks = new ArrayList<>();
            ArrayList<TrackPrediction> predictions = new ArrayList<>();

            // If there is too much hits, we rely on to the conventional track finding
            if (AHDC_Hits.size() > 300) {
                LOGGER.info("Too many AHDC_Hits in AHDC::adc, rely on conventional track finding for this event");
                modeTrackFinding = ModeTrackFinding.CV_Distance;
            }

            if (modeTrackFinding == ModeTrackFinding.CV_Distance || modeTrackFinding == ModeTrackFinding.CV_Hough) {
                if (modeTrackFinding == ModeTrackFinding.CV_Distance) {
                    Distance distance = new Distance();
                    distance.find_track(AHDC_Clusters);
                    AHDC_Tracks = distance.get_AHDCTracks();
                } else if (modeTrackFinding == ModeTrackFinding.CV_Hough) {
                    HoughTransform houghtransform = new HoughTransform();
                    houghtransform.find_tracks(AHDC_Clusters);
                    AHDC_Tracks = houghtransform.get_AHDCTracks();
                }
            }
            if (modeTrackFinding == ModeTrackFinding.AI_Track_Finding) {
                // AI ---------------------------------------------------------------------------------
                AHDC_Hits.sort(Comparator.comparingDouble(Hit::getRadius));
                PreClustering preClustering = new PreClustering();
                ArrayList<PreCluster> preClustersAI = preClustering.find_preclusters_for_AI(AHDC_Hits);
                ArrayList<PreclusterSuperlayer> preclusterSuperlayers = preClustering.merge_preclusters(preClustersAI);
                TrackConstruction trackConstruction = new TrackConstruction();
                ArrayList<ArrayList<PreclusterSuperlayer>> tracks = new ArrayList<>();
                boolean success = trackConstruction.get_all_possible_track(preclusterSuperlayers, tracks);

                if (!success) {
                    System.err.println("Too much tracks candidates, exit");
                    return false;
                }

                try {
                    AIPrediction aiPrediction = new AIPrediction();
                    predictions = aiPrediction.prediction(tracks, modelTrackFinding);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                // Track matching with AI: predict which sector, layer and wedge should be hit
                for (TrackPrediction t : predictions) {
                    if (t.getPrediction() > 0.2) {
                        try {
                            float[] pred = model_tm.prediction(t.getSuperpreclusters());
                            Track track = new Track(t.getClusters());
                            track.set_predicted_ATOF_sector((int)pred[0]);
                            track.set_predicted_ATOF_layer((int)pred[1]);
                            track.set_predicted_ATOF_wedge((int)pred[2]);
                            AHDC_Tracks.add(track);

                         } catch (Exception e) {throw new RuntimeException(e);}

                    }

                }
            }
            // ------------------------------------------------------------------------------------


            //Temporary track method ONLY for MC with no background;
            //AHDC_Tracks.add(new Track(AHDC_Hits));

            // V) Global fit
            for (Track track : AHDC_Tracks) {
              int nbOfPoints = track.get_Clusters().size();

              double[][] szPos = new double[nbOfPoints][3];

              int j = 0;
              for (Cluster cluster : track.get_Clusters()) {
                szPos[j][0] = cluster.get_X();
                szPos[j][1] = cluster.get_Y();
                szPos[j][2] = cluster.get_Z();
                j++;
              }

              HelixFitJava h = new HelixFitJava();
              track.setPositionAndMomentum(h.HelixFit(nbOfPoints, szPos, 1));
              // double p = 150.0;//MeV/c
              // double phi          = Math.atan2(szPos[0][1], szPos[0][0]);
              // double x_0[] = {0.0, 0.0, 0.0, p*Math.sin(phi),
              // p*Math.cos(phi), 0.0}; track.setPositionAndMomentumVec(x_0);
            }

            // VI) Kalman Filter
            // System.out.println("AHDC_Tracks = " + AHDC_Tracks);
            KalmanFilter kalmanFitter = new KalmanFilter(AHDC_Tracks, event, magfield, simulation);
            // VII) Write bank
            RecoBankWriter writer = new RecoBankWriter();

            DataBank recoHitsBank       = writer.fillAHDCHitsBank(event, AHDC_Hits);
            DataBank recoPreClusterBank = writer.fillPreClustersBank(event, AHDC_PreClusters);
            DataBank recoClusterBank    = writer.fillClustersBank(event, AHDC_Clusters);
            DataBank recoTracksBank     = writer.fillAHDCTrackBank(event, AHDC_Tracks);
            DataBank recoKFTracksBank   = writer.fillAHDCKFTrackBank(event, AHDC_Tracks);
            DataBank AIPredictionBanks = writer.fillAIPrediction(event, predictions);

            event.appendBank(recoHitsBank);
            event.appendBank(recoPreClusterBank);
            event.appendBank(recoClusterBank);
            event.appendBank(recoTracksBank);
            event.appendBank(recoKFTracksBank);
            event.appendBank(AIPredictionBanks);

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
        SchemaFactory factory = reader.getReader().getSchemaFactory();
        HipoDataSync   writer = new HipoDataSync(factory);
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
