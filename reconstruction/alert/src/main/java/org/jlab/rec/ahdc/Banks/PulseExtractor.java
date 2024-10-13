package org.jlab.rec.ahdc.Banks;

import java.util.ArrayList;
import java.util.List;
import org.jlab.detector.pulse.ExtractorPars;
import org.jlab.detector.pulse.HipoExtractor;
import org.jlab.detector.pulse.Pulse;

public class PulseExtractor extends HipoExtractor {

    @Override
    public List<Pulse> extract(ExtractorPars pars, int id, short... samples) {
        List<Pulse> pulses = new ArrayList<>();
        for (int i=0; i<samples.length; ++i) {
            if (samples[i] > pars.pedestal+pars.threshold) {
                float integral = 7;
                float time = 42;
                long flags = 0xF;
                pulses.add(new Pulse(integral, time, flags, id));
            }
        }
        return pulses;
    }

}
