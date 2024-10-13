package org.jlab.detector.pulse;

import java.util.List;

public class Mode7Extractor extends Mode1Extractor {

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
    public List<Pulse> extract(ExtractorPars pars, int id, short... samples) {
        List<Pulse> pulses = super.extract(pars, id, samples);
        for (Pulse p : pulses) p.time = calculateTime((int)p.time, pars.pedestal, samples);
        return pulses;
    }

}
