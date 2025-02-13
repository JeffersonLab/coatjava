package org.jlab.rec.service;

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

import java.io.File;
import java.util.*;

public class AHDCEngine extends ReconstructionEngine {

	private boolean                   simulation;
	private boolean                   use_AI_for_trackfinding;
	private String                    findingMethod;
	private HashMap<String, Material> materialMap;
	private Model model;

	public AHDCEngine() {
		super("ALERT", "ouillon", "1.0.1");
	}

	@Override
	public boolean init() {
		simulation    = false;
		findingMethod = "distance";
		use_AI_for_trackfinding = true;

		if (materialMap == null) {
			materialMap = MaterialMap.generateMaterials();
		}

		model = new Model();

		return true;
	}


	@Override
	public boolean processDataEvent(DataEvent event) {

		int    runNo          = 10;
		int    eventNo        = 777;
		double magfield       = 50.0;
		double magfieldfactor = 1;

		if (event.hasBank("RUN::config")) {
			DataBank bank = event.getBank("RUN::config");
			runNo          = bank.getInt("run", 0);
			eventNo        = bank.getInt("event", 0);
			magfieldfactor = bank.getFloat("solenoid", 0);
			if (runNo <= 0) {
				System.err.println("RTPCEngine:  got run <= 0 in RUN::config, skipping event.");
				return false;
			}
		}

		magfield = 50 * magfieldfactor;

		if (event.hasBank("AHDC::adc")) {
			// I) Read raw hit
			HitReader hitRead = new HitReader(event, simulation);

			ArrayList<Hit>     AHDC_Hits     = hitRead.get_AHDCHits();
			ArrayList<TrueHit> TrueAHDC_Hits = hitRead.get_TrueAHDCHits();
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

			if (use_AI_for_trackfinding == false) {
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
			else {
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
				ArrayList<ArrayList<PreclusterSuperlayer>> tracks = trackConstruction.get_all_possible_track(preclusterSuperlayers);


				try {
					AIPrediction aiPrediction = new AIPrediction();
					predictions = aiPrediction.prediction(tracks, model.getModel());
				} catch (Exception e) {
					throw new RuntimeException(e);
				}

				for (TrackPrediction t : predictions) {
					if (t.getPrediction() > 0.5)
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
			}

			// VI) Kalman Filter
			// System.out.println("AHDC_Tracks = " + AHDC_Tracks);
			KalmanFilter kalmanFitter = new KalmanFilter(AHDC_Tracks, event);

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
		int    maxEvent   = 1000;
		int    myEvent    = 3;
		String inputFile  = "alert_out_update.hipo";
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
