package org.jlab.rec.ft.trk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author devita
 * @author filippi
 */
public class FTTRKEngine extends ReconstructionEngine {

	public FTTRKEngine() {
		super("FTTRK", "devita", "1.0");
	}
        
	FTTRKReconstruction reco;
	
	@Override
	public boolean init() {

            String[]  tables = new String[]{ 
                "/calibration/ft/fthodo/charge_to_energy",
                "/calibration/ft/fthodo/time_offsets",
                "/calibration/ft/fthodo/status",
                "/geometry/ft/fthodo",
                "/geometry/ft/fttrk"
            };
            requireConstants(Arrays.asList(tables));
            this.getConstantsManager().setVariation("default");

            reco = new FTTRKReconstruction();
	        reco.debugMode=0;
            
            if(this.getEngineConfigString("fall18TT")!=null) {
                FTTRKConstantsLoader.ADJUSTTT = Boolean.parseBoolean(this.getEngineConfigString("fall18TT"));
            }

            return true;
	}

    @Override
    public void detectorChanged(int runNumber) {
        FTTRKConstantsLoader.Load(runNumber, this.getConstantsManager().getVariation());
    }

	@Override
	public boolean processDataEventUser(DataEvent event) {
        // update calibration constants based on run number if changed
        int run = setRunConditionsParameters(event);
        if(run>=0) {
            // get hits fron banks
            List<FTTRKHit> allHits = reco.initFTTRK(event,this.getConstantsManager(), run); 
            // create clusters
            List<FTTRKCluster> clusters = reco.findClusters(allHits);
            // create crosses
            List<FTTRKCross> crosses = reco.findCrosses(clusters);
            // update hit banks with associated clusters/crosses information
            reco.updateAllHitsWithAssociatedIDs(allHits, clusters);
            // write output banks
            reco.writeBanks(event, allHits, clusters, crosses);
        }
        return true;
	}
        
    public ArrayList<FTTRKCluster> processDataEventAndGetClusters(DataEvent event) {
        List<FTTRKHit> allHits      = new ArrayList();
        ArrayList<FTTRKCluster> clusters = new ArrayList();   // era ArrayList
        ArrayList<FTTRKCross>   crosses  = new ArrayList();
            
        // update calibration constants based on run number if changed
        int run = setRunConditionsParameters(event);
            
        if(run>=0) {
            // get hits from banks
            allHits = reco.initFTTRK(event,this.getConstantsManager(), run);
            if(allHits.size()>0){
                // create clusters
                clusters = reco.findClusters(allHits);
                // create crosses
                crosses = reco.findCrosses(clusters);
                // update hit banks with associated clusters/crosses information
                reco.updateAllHitsWithAssociatedIDs(allHits, clusters);
                // write output banks
                reco.writeBanks(event, allHits, clusters, crosses);
            }
        }
        return clusters;
	}      

    public int setRunConditionsParameters(DataEvent event) {
        int run = -1;
        if(event.hasBank("RUN::config")==false) {
                System.out.println("RUN CONDITIONS NOT READ!");
        }       
        DataBank bank = event.getBank("RUN::config");
        run = bank.getInt("run")[0];
	    return run;	
    }
}

        
    
