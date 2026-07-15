package org.jlab.rec.ft.hodo;

import java.util.Arrays;
import java.util.List;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.evio.EvioDataBank;
import org.jlab.io.evio.EvioDataEvent;

public class FTHODOEngine extends ReconstructionEngine {
    
    public FTHODOEngine() {
        super("FTHODO", "devita", "3.0");
    }
    
    FTHODOReconstruction reco;
    
    @Override
    public boolean init() {
        reco = new FTHODOReconstruction();
        reco.debugMode=0;
        
        String[]  tables = new String[]{
            "/calibration/ft/fthodo/charge_to_energy",
            "/calibration/ft/fthodo/time_offsets",
            "/calibration/ft/fthodo/status",
            "/geometry/ft/fthodo"
        };
        requireConstants(Arrays.asList(tables));
        this.getConstantsManager().setVariation("default");
        this.registerOutputBank("FTHODO::hits","FTHODO::clusters");
        return true;
    }
    
    @Override
    public void detectorChanged(int runNumber) {}
    
    @Override
    public boolean processDataEventUser(DataEvent event) {
        
        // update calibration constants based on run number if changed
        int run = setRunConditionsParameters(event);
        
        if(run>=0) {
            // get hits fron banks
            List<FTHODOHit> allHits = reco.initFTHODO(event,this.getConstantsManager(), run);
            // select good hits and order them by energy
            List<FTHODOHit> selectedHits = reco.selectHits(allHits);
            // create clusters
            List<FTHODOCluster> clusters = reco.findClusters(selectedHits);
            // write output banks
            reco.writeBanks(event, selectedHits, clusters);
        }
        return true;
    }
    
    public int setRunConditionsParameters(DataEvent event) {
        int run = -1;
        if(event.hasBank("RUN::config")==false) {
            System.out.println("RUN CONDITIONS NOT READ!");
        }
        
        if(event instanceof EvioDataEvent) {
            EvioDataBank bank = (EvioDataBank) event.getBank("RUN::config");
            run = bank.getInt("Run",0);
        }
        else {
            DataBank bank = event.getBank("RUN::config");
            run = bank.getInt("run",0);
        }
        return run;
    }
}
