package org.jlab.rec.ft.cal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JFrame;
import org.jlab.clas.detector.DetectorData;
import org.jlab.clas.detector.DetectorEvent;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.geom.prim.Vector3D;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.evio.EvioDataBank;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.hipo.HipoDataSource;


public class FTCALEngine extends ReconstructionEngine {

    public FTCALEngine() {
        super("FTCAL", "devita", "3.0");
    }

    FTCALReconstruction reco;
    
    @Override
    public boolean init() {
        reco = new FTCALReconstruction();
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

        this.registerOutputBank("FTCAL::hits","FTCAL::clusters");

        return true;
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        List<FTCALHit>     allHits           = new ArrayList();
        List<FTCALHit>     selectedHits      = new ArrayList();
        List<FTCALCluster> clusters          = new ArrayList();
            
        // update calibration constants based on run number if changed
        int run = setRunConditionsParameters(event);

        if(run>=0) {
            // get hits fron banks
            allHits = reco.initFTCAL(event,this.getConstantsManager(), run);
            // select good hits and order them by energy
            selectedHits = reco.selectHits(allHits,this.getConstantsManager(), run);
            // create clusters
            clusters = reco.findClusters(selectedHits, this.getConstantsManager(), run);
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
