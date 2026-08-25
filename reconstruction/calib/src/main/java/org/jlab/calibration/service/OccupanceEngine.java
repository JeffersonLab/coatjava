package org.jlab.calibration.service;

import org.jlab.io.base.DataEvent;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.detector.calib.utils.OccupanceTable.OccupanceDriver;

public class OccupanceEngine extends ReconstructionEngine {

    OccupanceDriver occupancy;
    
    public OccupanceEngine() {
        super("Occupance", "baltzell","0.1");
    }

    @Override
    public boolean processDataEventUser(DataEvent event) {
        occupancy.process(event);
        return true;
    }

    @Override
    public boolean init() {
        occupancy = new OccupanceDriver(getSchemaFactory(),
            Integer.parseInt(getEngineConfigString("occupancyPrescale","100")));
        return true;
    }

    @Override
    public void detectorChanged(int runNumber) {
        occupancy.reset();
    }

}