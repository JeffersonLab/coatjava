package org.jlab.calibration.service;

import org.jlab.io.base.DataEvent;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.detector.calib.utils.OccupanceTable;

public class OccupanceEngine extends ReconstructionEngine {

    int events;
    int prescale;
    OccupanceTable[] tables;
        
    public OccupanceEngine() {
        super("Occupance", "baltzell","0.1");
    }

    @Override
    public boolean processDataEventUser(DataEvent event) {
        for (OccupanceTable t : tables) t.fill(event);
            if (++events % prescale == 0) {
                for (OccupanceTable t : tables) {
                    if (t.getTable().getRowCount() > 0)
                        event.appendBank(t.create(events, event));
                    t.reset();
                }
                events = 0;
            }
        return true;
    }

    @Override
    public boolean init() {
        prescale = Integer.parseInt(getEngineConfigString("occupancyPrescale","100"));
        tables = new OccupanceTable[] {
            new OccupanceTable("DC::tot"),
            new OccupanceTable("DC::tdc"),
            new OccupanceTable("ECAL::adc"),
            new OccupanceTable("ECAL::tdc"),
            new OccupanceTable("FTOF::adc"),
            new OccupanceTable("FTOF::tdc"),
            new OccupanceTable("CTOF::adc"),
            new OccupanceTable("CTOF::tdc"),
            new OccupanceTable("HTCC::adc"),
            new OccupanceTable("HTCC::tdc"),
            new OccupanceTable("LTCC::adc"),
            new OccupanceTable("LTCC::tdc"),
            new OccupanceTable("BST::adc"),
            new OccupanceTable("BMT::adc"),
            new OccupanceTable("FTCAL::adc"),
            new OccupanceTable("FTHODO::adc"),
            new OccupanceTable("FTTRK::adc"),
            new OccupanceTable("RICH::tdc"),
            new OccupanceTable("BAND::adc"),
            new OccupanceTable("BAND::tdc"),
        };
        return true;
    }

    @Override
    public void detectorChanged(int runNumber) {
        for (int i=0; i<tables.length; i++) tables[i].reset();
        events = 0;
    }

}