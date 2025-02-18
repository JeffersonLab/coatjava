package org.jlab.clas.service;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.detector.pulse.Mode3;
import org.jlab.detector.pulse.Mode7;
import org.jlab.io.base.DataEvent;

/**
 * An example of using a {@link org.jlab.detector.pulse.HipoExtractor} from a
 * {@link org.jlab.clas.reco.ReconstructionEngine}.
 *
 * @author baltzell
 */
public class PulseExtractorEngine extends ReconstructionEngine {

    Mode3 mode3 = new Mode3();
    Mode3 mode7 = new Mode7();
    
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
        mode3.update(6, null, event, "BMT::wf", "BMT::adc");
        //mode7.update(80, null, event, "AHDC::wf", "AHDC::adc");

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
