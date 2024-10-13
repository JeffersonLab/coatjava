package org.jlab.rec.service;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.detector.pulse.AHDCExtractor;
import org.jlab.detector.pulse.ExtractorPars;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

public class ALERTPulseEngine extends ReconstructionEngine {

    AHDCExtractor ahdc = new AHDCExtractor();
    
	public ALERTPulseEngine() {
		super("ALERTPulse", "baltzell", "0.1");
	}

    @Override
    public boolean processDataEvent(DataEvent de) {
        ExtractorPars pars = new ExtractorPars("AHDC::wf","AHDC::adc");
        ahdc.update(pars, de);
        return true;
    }

    @Override
    public boolean init() {
        registerOutputBank("AHDC::adc");
        this.requireConstants("/daq/config/ahdc");
        return true;
    }
    
}
