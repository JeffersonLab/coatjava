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
import org.jlab.rec.alert.constants.CalibrationConstantsLoader;

import java.io.File;
import java.util.*;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.detector.alert.AHDC.AlertDCDetector;
import org.jlab.geom.detector.alert.AHDC.AlertDCFactory;

/** AHDCEngine reconstruction service.
 *
 *  AHDC Reconstruction using only AHDC information.
 *
 * Reconstruction utilizing other detectors (i.e. ATOF) are 
 * implemented in ALERTEngine.
 *
 */
public class AHDCEngine extends ReconstructionEngine {

    private boolean                   simulation;

    /** 
     * String name for track seedsfinding method.
     *  Options are:
     *  - "distance"
     *  - "hough"
     *
     *  \todo remove bool use_AI_for_trackfinding and use option string above.
     */
    private String                    findingMethod;

    /// Material Map used by Kalman filter
    private HashMap<String, Material> materialMap;

    /// \todo better name... Model of what?
    private Model model;

    /// \todo better name... mode for what?
    private Mode mode = Mode.CV_Track_Finding;

    private AlertDCDetector factory = null;
    
    public AHDCEngine() {
        super("ALERT", "ouillon", "1.0.1");
    }

    @Override
    public boolean init() {

        factory = (new AlertDCFactory()).createDetectorCLAS(new DatabaseConstantProvider());
        
        simulation    = false;
        findingMethod = "distance";

        if (materialMap == null) {
            materialMap = MaterialMap.generateMaterials();
        }

        if(this.getEngineConfigString("Mode")!=null) {
            if (Objects.equals(this.getEngineConfigString("Mode"), Mode.AI_Track_Finding.name()))
                mode = Mode.AI_Track_Finding;

            if (Objects.equals(this.getEngineConfigString("Mode"), Mode.CV_Track_Finding.name()))
                mode = Mode.CV_Track_Finding;
        }

        if (mode == Mode.AI_Track_Finding) {
            model = new Model();
        }

        // Requires calibration constants
        String[] alertTables = new String[] {
            "/calibration/alert/ahdc/time_offsets",
                "/calibration/alert/ahdc/time_to_distance",
                "/calibration/alert/atof/effective_velocity",
                "/calibration/alert/atof/time_walk",
                "/calibration/alert/atof/attenuation",
                "/calibration/alert/atof/time_offsets"
        };
        requireConstants(Arrays.asList(alertTables));

        this.registerOutputBank("AHDC::hits","AHDC::preclusters","AHDC::clusters","AHDC::track","AHDC::kftrack","AHDC::mc","AHDC::ai:prediction");

        return true;
    }

    int Run = -1;

    @Override
    public boolean processDataEvent(DataEvent event) {

        int    runNo          = 10; // needed here?
        int    eventNo        = 777; // same

        double magfield       = 50.0;  // what is this?
        double magfieldfactor = 1;     // why is this here?

	if(event.hasBank("MC::Particle")){
	    simulation = true;
	}

        if (event.hasBank("RUN::config")) {
            DataBank bank = event.getBank("RUN::config");
            runNo          = bank.getInt("run", 0);
            eventNo        = bank.getInt("event", 0);
            magfieldfactor = bank.getFloat("solenoid", 0);
            if (runNo <= 0) {
                System.err.println("AHDCEngine:  got run <= 0 in RUN::config, skipping event.");
                return false;
            }
            int newRun = Run;        
            newRun = runNo; 
            // Load the constants
            //-------------------
            if(Run!=newRun) {
                CalibrationConstantsLoader.Load(newRun,"default",this.getConstantsManager()); 
                Run = newRun;
            }
        }

        /// What is this? 
        magfield = 50 * magfieldfactor;

        if (event.hasBank("AHDC::adc")) {
            // I) Read raw hit
            HitReader hitRead = new HitReader(event, factory, simulation);

            ArrayList<Hit>     AHDC_Hits     = hitRead.get_AHDCHits();
            if(simulation){
                ArrayList<TrueHit> TrueAHDC_Hits = hitRead.get_TrueAHDCHits();
            }
            //System.out.println("AHDC_Hits size " + AHDC_Hits.size());

            // II) Create PreCluster
            ArrayList<PreCluster> AHDC_PreClusters = new ArrayList<>();
            PreClusterFinder preclusterfinder = new PreClusterFinder();
            preclusterfinder.findPreCluster(AHDC_Hits);
            AHDC_PreClusters = preclusterfinder.get_AHDCPreClusters();
            //System.out.println("AHDC_PreClusters size " + AHDC_PreClusters.size());

            // III) Create Cluster
            ClusterFinder clusterfinder = new ClusterFinder();
            clusterfinder.findCluster(AHDC_PreClusters);
            ArrayList<Cluster> AHDC_Clusters = clusterfinder.get_AHDCClusters();
            //System.out.println("AHDC_Clusters size " + AHDC_Clusters.size());

            // IV) Track Finder
            ArrayList<Track> AHDC_Tracks = new ArrayList<>();
            ArrayList<TrackPrediction> predictions = new ArrayList<>();

            // If there is too much hits, we rely on to the conventional track finding
            if (AHDC_Hits.size() > 300) mode = Mode.CV_Track_Finding;

            if (mode == Mode.CV_Track_Finding) {
                if (findingMethod.equals("distance")) {
                    // IV) a) Distance method
                    //System.out.println("using distance");
                    Distance distance = new Distance();
                    distance.find_track(AHDC_Clusters);
                    AHDC_Tracks = distance.get_AHDCTracks();
                } else if (findingMethod.equals("hough")) {
                    // IV) b) Hough Transform method
                    //System.out.println("using hough");
                    HoughTransform houghtransform = new HoughTransform();
                    houghtransform.find_tracks(AHDC_Clusters);
                    AHDC_Tracks = houghtransform.get_AHDCTracks();
                }
            }
            if (mode == Mode.AI_Track_Finding) {
                // AI ---------------------------------------------------------------------------------
                AHDC_Hits.sort(new Comparator<Hit>() {
                    @Override
                    public int compare(Hit a1, Hit a2) {
                        return Double.compare(a1.getRadius(), a2.getRadius());
                    }
                });
                PreClustering preClustering = new PreClustering();
                ArrayList<PreCluster> preClustersAI = preClustering.find_preclusters_for_AI(AHDC_Hits);
                ArrayList<PreclusterSuperlayer> preclusterSuperlayers = preClustering.merge_preclusters(preClustersAI);
                TrackConstruction trackConstruction = new TrackConstruction();
                ArrayList<ArrayList<PreclusterSuperlayer>> tracks = new ArrayList<>();
                boolean sucess = trackConstruction.get_all_possible_track(preclusterSuperlayers, tracks);

                if (!sucess) {
                    System.err.println("Too much tracks candidates, exit");
                    return false;
                }

                try {
                    AIPrediction aiPrediction = new AIPrediction();
                    predictions = aiPrediction.prediction(tracks, model.getModel());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                for (TrackPrediction t : predictions) {
                    if (t.getPrediction() > 0.2)
                        AHDC_Tracks.add(new Track(t.getClusters()));
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
            KalmanFilter kalmanFitter = new KalmanFilter(AHDC_Tracks, event, simulation);
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
        String inputFile  = "merged_10.hipo";
        String outputFile = "output.hipo";

        if (new File(outputFile).delete()) System.out.println("output.hipo is delete.");

        System.err.println(" \n[PROCESSING FILE] : " + inputFile);

        AHDCEngine en = new AHDCEngine();

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
