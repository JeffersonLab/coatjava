package org.jlab.service.cnd;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cnd.constants.CalibrationConstantsLoader;
import org.jlab.rec.cnd.banks.HitReader;
import org.jlab.rec.cnd.banks.RecoBankWriter;
import org.jlab.rec.cnd.hit.CndHit;
import org.jlab.rec.cnd.hit.CvtGetHTrack;
import org.jlab.rec.cnd.hit.HalfHit;
import org.jlab.rec.cnd.hit.CndHitFinder;
import org.jlab.rec.cnd.cluster.CNDCluster;
import org.jlab.rec.cnd.cluster.CNDClusterFinder;

/**
 * Service to return reconstructed CND Hits - the output is in Hipo format
 * doing clustering job at the end, provide the cluster infos for PID ("rwangcn8@gmail.com")
 *
 *
 */
public class CNDCalibrationEngine extends ReconstructionEngine {

	public CNDCalibrationEngine() {
		super("CND", "chatagnon & WANG", "1.0");
	
	}

	RecoBankWriter rbc;
        
    private AtomicInteger Run = new AtomicInteger(0);

	@Override
	public boolean processDataEventUser(DataEvent event) {

        if (!event.hasBank("RUN::config")) {
            return true;
        }

        DataBank bank = event.getBank("RUN::config");

        // Load the constants
        int newRun = bank.getInt("run", 0);
        if (newRun == 0)
           return true;
        if (Run.get() == 0 || (Run.get() != 0 && Run.get() != newRun)) {
            Run.set(newRun);
        }
        CalibrationConstantsLoader constantsLoader = new CalibrationConstantsLoader(newRun, this.getConstantsManager());
            
		ArrayList<HalfHit> halfhits = HitReader.getCndHalfHits(event, constantsLoader);		
		//1) exit if halfhit list is empty
		if(halfhits.isEmpty() ){
			return true;
		}

		//2) find the CND hits from these half-hits
		CndHitFinder hitFinder = new CndHitFinder();
		ArrayList<CndHit> hits = hitFinder.findHits(halfhits,0, constantsLoader);

		CvtGetHTrack cvttry = new CvtGetHTrack();
		cvttry.getCvtHTrack(event,constantsLoader); // get the list of helix associated with the event

		//int flag=0;
		for (CndHit hit : hits){ // findlength for charged particles
			double length =hitFinder.findLength(hit, cvttry.getHelices(),0,constantsLoader);
			if (length!=0){
				hit.set_tLength(length); // the path length is non zero only when there is a match with cvt track
			}
		}

        //// clustering of the CND hits
        CNDClusterFinder cndclusterFinder = new CNDClusterFinder();
        ArrayList<CNDCluster> cndclusters = cndclusterFinder.findClusters(hits,constantsLoader);

		if(!hits.isEmpty()){
			rbc.appendCNDBanks(event,hits,cndclusters);
		}

		return true;
	}

	@Override
	public boolean init() {
        rbc = new RecoBankWriter();
        requireConstants(Arrays.asList(CalibrationConstantsLoader.getCndTables()));
        this.getConstantsManager().setVariation("default");
        this.registerOutputBank("CND::hits","CND::clusters");
        return true;
	}
    
    @Override
    public void detectorChanged(int runNumber) {}

}

