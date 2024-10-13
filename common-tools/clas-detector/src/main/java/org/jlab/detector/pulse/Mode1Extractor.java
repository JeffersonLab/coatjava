package org.jlab.detector.pulse;

import java.util.ArrayList;
import java.util.List;

public class Mode1Extractor extends HipoExtractor {

    @Override
    public List<Pulse> extract(ExtractorPars pars, int id, short... samples) {
        List<Pulse> pulses = new ArrayList<>();
        for (int i=0; i<samples.length; ++i) {
            if (samples[i] > pars.pedestal + pars.threshold) {
                int n = 0;
                float integral = 0;
                for (int j=i-pars.nsb; j<=i+pars.nsa; ++j) {
                    if (j<0) continue;
                    if (j>=samples.length) continue;
                    integral += samples[j];
                    n++;
                }
                integral -= n*pars.pedestal;
                pulses.add(new Pulse(integral, i, 0x0, id));
                i += pars.nsa;
            }
        }
        return pulses;
    }

}
