package org.jlab.detector.waveform;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class AHDCPulseExtractor extends APulseExtractor {

    float threshold;
    float pedestal;
    
    public AHDCPulseExtractor(float threshold, float pedestal) {
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

    public static void main(String args[]) {
        AHDCPulseExtractor e = new AHDCPulseExtractor(5,10);
        short[] samples = {10,10,1000,100,10,10};
        System.out.print(e.extract((short)7,samples));
    }

}
