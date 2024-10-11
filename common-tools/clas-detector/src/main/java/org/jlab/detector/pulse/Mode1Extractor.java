package org.jlab.detector.pulse;

import java.util.ArrayList;
import java.util.List;

public class Mode1Extractor extends APulseExtractor {

    float threshold;
    float pedestal;
    int nsa;
    int nsb;

    public Mode1Extractor(float threshold, float pedestal, int nsb, int nsa) {
        this.threshold = threshold;
        this.pedestal = pedestal;
        this.nsb = nsb;
        this.nsa = nsa;
    }

    @Override
    public List<Pulse> extract(int id, short... samples) {
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
                pulses.add(new Pulse(integral, i, 0x0, id));
                i += nsa;
            }
        }
        return pulses;
    }

}
