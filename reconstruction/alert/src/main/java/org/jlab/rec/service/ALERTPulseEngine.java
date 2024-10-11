package org.jlab.rec.service;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.detector.waveform.AHDCPulseExtractor;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author baltzell
 */
public class ALERTPulseEngine extends ReconstructionEngine {

    AHDCPulseExtractor ahdc = new AHDCPulseExtractor(10,100);
    
	public ALERTPulseEngine() {
		super("ALERTPulse", "baltzell", "0.1");
	}

    @Override
    public boolean processDataEvent(DataEvent de) {
        DataBank wf = de.getBank("AHDC::wf:12");
        DataBank adc = de.getBank("AHDC::adc");
        ahdc.extract(wf, adc);
        de.removeBank("AHDC::adc");
        de.appendBank(adc);
        return true;
    }

    @Override
    public boolean init() {
        registerOutputBank("AHDC::adc");
        this.requireConstants("/daq/config/ahdc");
        return true;
    }
    
}
