package org.jlab.clas.service;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.detector.pulse.ExampleExtractor;
import org.jlab.io.base.DataEvent;

public class PulseExtractorEngine extends ReconstructionEngine {

    ExampleExtractor basic = new ExampleExtractor();
    
	public PulseExtractorEngine() {
		super("PULSE", "baltzell", "0.0");
	}

	@Override
	public boolean init() {
        // If using a CCDB table, must register it here:
        //requireConstants("/daq/config/ahdc");
		return true;
	}

    @Override
    public boolean processDataEvent(DataEvent event) {

        // No CCDB table, hardcoded parameters in the extractor:
        basic.update(6, null, event, "BMT::wf", "BMT::adc");

        /*
        // Requiring a CCDB table:
        DataBank runConfig = event.getBank("RUN::config");
        if (runConfig.rows()>0) {
            IndexedTable it = getConstantsManager().getConstants(
                runConfig.getInt("run", 0), "/daq/config/ahdc");
            basic.update(136, it, event, "AHDC::wf", "AHDC::adc");
        }
        */

        return true;
    }

}
