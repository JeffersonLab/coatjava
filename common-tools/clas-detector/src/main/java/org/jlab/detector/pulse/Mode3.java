package org.jlab.detector.pulse;

import java.util.ArrayList;
import java.util.List;
import org.jlab.utils.groups.NamedEntry;

/**
 * Similar to a FADC250 Mode-3 pulse extraction.
 * 
 * @author baltzell
 */
public class Mode3 extends HipoExtractor {

    // Fixed extraction parameters:
    final double ped = 2000;
    final double tet = 2000;
    final int nsa = 30;
    final int nsb = 5;

    /**
     * @param pars CCDB row
     * @param id link to row in source bank
     * @param samples ADC samples
     * @return extracted pulses 
     */
    @Override
    public List<Pulse> extract(NamedEntry pars, int id, short... samples) {

        List<Pulse> pulses = null;

        /*
        // Retrive extraction parameters from a CCDB table:
        double ped = pars.getValue("ped").doubleValue();
        double tet = pars.getValue("tet").doubleValue();
        int nsa = pars.getValue("nsa").intValue();
        int nsb = pars.getValue("nsb").intValue();
        */

        // Perform the extraction:
        for (int i=0; i<samples.length-1; ++i) {
            // Check for threshold crossing:
            if (samples[i] > ped+tet && samples[i+1] > samples[i]) {
                int n = 0;
                float integral = 0;
                // Integrate the pulse:
                for (int j=i-nsb; j<=i+nsa; ++j) {
                    if (j<0) continue;
                    if (j>=samples.length) break;
                    integral += samples[j];
                    n++;
                }
                integral -= n * ped;
                Pulse p = new Pulse(integral, i, 0x0, id);
                p.pedestal = (float)(ped);
                // Add the new pulse to the list:
                if (pulses == null) pulses = new ArrayList<>();
                pulses.add(p);
                // Add a holdoff time before next possible pulse:
                i += nsa;
            }
        }
        return pulses;
    }

}
