package org.jlab.detector.waveform;

import java.util.ArrayList;
import java.util.List;

public class Mode7Extractor extends Mode1Extractor {

    public Mode7Extractor(float threshold, float pedestal, int nsb, int nsa) {
        super(threshold, pedestal, nsb, nsa);
    }

    private static float calculateTime(int t0, float ped, short... samples) {
        // t0 is the threshold-crossing sample index
        for (int j=t0+1; j<samples.length; ++j) {
            if (samples[j] < samples[j-1]) {
                float slope = (samples[j-1]-ped) / (j-t0);
                float offset = samples[j-1] - (j-1)*slope;
                return (samples[j-1]/2 - offset) / slope;
            }
        }
        return t0;
    }

    @Override
    public List<Pulse> extract(int id, short... samples) {
        // With Pulse a (immutable) record, this isn't possible:
        // List<Pulse> pulses = super.extract(id, samples);
        // for (Pulse p : pulses) p.time = calculateTime(p.time, pedestal, samples);
        // Or, with streams:
        List<Pulse> pulses = new ArrayList<>();
        for (int i=0; i<samples.length; ++i) {
            if (samples[i] > pedestal + threshold) {
                int n = 0;
                float integral = 0;
                for (int j=i-nsb; j<=i+nsa; ++j) {
                    if (j<0) continue;
                    if (j>=samples.length) continue;
                    integral += samples[j];
                    n++;
                }
                integral -= n*pedestal;
                float time = calculateTime(i, pedestal, samples);
                pulses.add(new Pulse(integral, time, 0x0, id));
                i += nsa;
            }
        }
        return pulses;
    }

}
