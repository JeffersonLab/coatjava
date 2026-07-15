package org.jlab.rec.mucal;

import java.util.Arrays;
import java.util.List;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.evio.EvioDataBank;
import org.jlab.io.evio.EvioDataEvent;

public class MUCALEngine extends ReconstructionEngine {
    
    public MUCALEngine() {
        super("MUCAL", "devita", "3.0");
    }
    
    MUCALReconstruction reco;
    
    @Override
    public boolean init() {
        reco = new MUCALReconstruction();
        reco.debugMode=0;
        
        String[]  tables = new String[]{
            "/calibration/ft/ftcal/charge_to_energy",
            "/calibration/ft/ftcal/time_offsets",
            "/calibration/ft/ftcal/time_walk",
            "/calibration/ft/ftcal/status",
            "/calibration/ft/ftcal/thresholds",
            "/calibration/ft/ftcal/cluster",
            "/calibration/ft/ftcal/energycorr"
        };
        requireConstants(Arrays.asList(tables));
        this.getConstantsManager().setVariation("default");
        
        this.registerOutputBank("MUCAL::hits","MUCAL::clusters");
        
        return true;
    }
    
    @Override
    public void detectorChanged(int run) {}
    
    @Override
    public boolean processDataEventUser(DataEvent event) {
        
        // update calibration constants based on run number if changed
        int run = setRunConditionsParameters(event);
        
        if(run>=0) {
            // get hits fron banks
            List<MUCALHit> allHits = reco.initMUCAL(event,this.getConstantsManager(), run);
            // select good hits and order them by energy
            List<MUCALHit> selectedHits = reco.selectHits(allHits,this.getConstantsManager(), run);
            // create clusters
            List<MUCALCluster> clusters = reco.findClusters(selectedHits, this.getConstantsManager(), run);
            // set cluster status
            reco.selectClusters(clusters, this.getConstantsManager(), run);
            // write output banks
            reco.writeBanks(event, selectedHits, clusters, this.getConstantsManager(), run);
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
