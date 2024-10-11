package org.jlab.detector.waveform;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author baltzell
 */
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
    public List<Pulse> extract(short... samples) {
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
                pulses.add(new Pulse(integral, i, 0x0));
                i += nsa;
            }
        }
        return pulses;
    }
    
    public static void main(String args[]) {
        Mode1Extractor e = new Mode1Extractor(5f,10f,2,2);
        short[] samples = {9,10,11,8,1000,100,10,10,10,10,10,2000,200,10,10};
        System.out.print(e.extract(samples));
    }

}
