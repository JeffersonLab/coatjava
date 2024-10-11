package org.jlab.detector.pulse;

import java.util.ArrayList;
import java.util.List;

public class AHDCExtractor extends APulseExtractor {

    float threshold;
    float pedestal;
    
    public AHDCExtractor(float threshold, float pedestal) {
        this.threshold = threshold;
        this.pedestal = pedestal;
    }

    @Override
    public List<Pulse> extract(int id, short... samples) {
        List<Pulse> pulses = new ArrayList<>();
        for (int i=0; i<samples.length; ++i) {
            if (samples[i] > pedestal+threshold) {
                float integral = 7;
                float time = 42;
                long flags = 0xF;
                pulses.add(new Pulse(integral, time, flags, id));
            }
        }
        return pulses;
    }

}
