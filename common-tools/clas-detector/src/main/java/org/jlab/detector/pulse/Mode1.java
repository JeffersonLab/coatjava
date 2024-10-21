package org.jlab.detector.pulse;

import java.util.ArrayList;
import java.util.List;
import org.jlab.utils.groups.NamedEntry;

public class Mode1 extends HipoExtractor {

    @Override
    public List<Pulse> extract(NamedEntry entry, int id, short... samples) {

        // Retrive the extraction parameters from a CCDB table:
        double ped = entry.getValue("ped").doubleValue();
        double tet = entry.getValue("tet").doubleValue();
        int nsa = entry.getValue("nsa").intValue();
        int nsb = entry.getValue("nsb").intValue();

        // Perform the extraction algorithm:
        List<Pulse> pulses = new ArrayList<>();
        for (int i=0; i<samples.length; ++i) {
            if (samples[i] > ped+tet) {
                int n = 0;
                float integral = 0;
                for (int j=i-nsb; j<=i+nsa; ++j) {
                    if (j<0) continue;
                    if (j>=samples.length) continue;
                    integral += samples[j];
                    n++;
                }
                integral -= n * ped;
                pulses.add(new Pulse(integral, i, 0x0, id));
                i += nsa;
            }
        }
        return pulses;
    }

}
