package org.jlab.detector.pulse;

import java.util.List;
import org.jlab.utils.groups.NamedEntry;

/**
 * Similar to a Mode-7 FADC250 pulse extraction.
 * 
 * @author baltzell
 */
public class Mode7 extends Mode3 {

    /**
     * @param t0 threshold-crossing sample index
     * @param ped pedestal (for calculating pulse half-height)
     * @param samples
     * @return pulse time
     */
    private static float calculateTime(int t0, float ped, short... samples) {
        for (int j=t0+1; j<samples.length; ++j) {
            if (samples[j] < samples[j-1]) {
                float slope = (samples[j-1]-ped) / (j-t0);
                float offset = samples[j-1] - (j-1)*slope;
                return (samples[j-1]/2 - offset) / slope;
            }
        }
        // Fall back to Mode-3 time:
        return t0;
    }

    @Override
    public List<Pulse> extract(NamedEntry pars, int id, short... samples) {
        List<Pulse> pulses = super.extract(pars, id, samples);
        for (Pulse p : pulses)
            p.time = calculateTime((int)p.time, p.pedestal, samples);
        return pulses;
    }

}
