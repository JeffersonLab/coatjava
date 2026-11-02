package org.jlab.detector.decode;

import org.jlab.detector.decode.FADCData.Peak;

/**
 *
 * @author gavalian
 */
public class ExtendedFADCFitter extends Peak implements IFADCFitter {

    private final int pedistalMaxBin = 5;
    private final int p1 = 1;
    private final int p2 = 15;

    public ExtendedFADCFitter(){}

    @Override
    public Peak[] fit(int nsa, int nsb, int tet, int pedr, short[] pulse) {
        t0=0; adc=0; ped=0; baseline=0; rms=0; 
        thresholdCrossing=0; pulsePeakValue=0; pulsePeakPosition=0; pulseWidth=0;
        tcoarse=0; tfine=0;
        double noise  = 0;
        int    tstart = pedistalMaxBin+1;
        int    tcross = 0;
        int    pmax   = 0;
        int    ppos   = 0;
        // calculate pedestal means and noise
        if (pedr!=0) ped=pedr;        // use default mode 7 pedestal range (1-4)
        int pedsum = 0;
        if(pulse.length<p2+1 && pedr==0) {
            for (int bin = 0; bin < pulse.length; bin++) {
                pedsum += pulse[bin];
            }
            ped=pedsum/pulse.length;
            return new Peak[]{this};
        }
        if (pedr==0) {
            tstart = p2+1;
            for (int bin = p1+1; bin < p2+1; bin++) {
                pedsum += pulse[bin];
                noise  += pulse[bin] * pulse[bin];
            }
            baseline = ((double) pedsum)/ (p2 - p1);
            ped = pedsum=pedsum/(p2-p1);	//(int) baseline;
            rms = Math.sqrt(noise / (p2 - p1) - baseline * baseline);
        }
        // find threshold crossing
        for (int bin=tstart; bin<pulse.length; bin++) {
            if(pulse[bin]>ped+tet) {
                tcross=bin;
                thresholdCrossing=tcross;
                break;
            }
        }
        if(tcross>0) {
            // calculate integral and find maximum
            for (int bin=Math.max(0,tcross-nsb); bin<Math.min(pulse.length,tcross+nsa+1); bin++) { // sum should be up to tcross+nsa (without +1), this was added to match the old fit method
                adc+=pulse[bin]-ped;
                if(bin>=tcross && pulse[bin]>pmax) {
                    pmax=pulse[bin];
                    ppos=bin;
                }
            }
            pulsePeakPosition=ppos;
            pulsePeakValue=pmax;
            // calculating mode 7 pulse time
            double halfMax = (pmax+baseline)/2;
            int s0 = -1;
            int s1 = -1;
            for (int bin=tcross-1; bin<Math.min(pulse.length-1,ppos+1); bin++) {
                if (pulse[bin]<=halfMax && pulse[bin+1]>halfMax) {
                    s0 = bin;
                    break;
                }
            }
            for (int bin=ppos; bin<Math.min(pulse.length-1,tcross+nsa); bin++) {
                if (pulse[bin]>halfMax && pulse[bin+1]<=halfMax) {
                    s1 = bin;
                    break;
                }
            }
            if(s0>-1) {
                int a0 = pulse[s0];
                int a1 = pulse[s0+1];
                // set course time to be the sample before the 50% crossing
                tcoarse = s0;
                // set the fine time from interpolation between the two samples before and after the 50% crossing (6 bits resolution)
                tfine   = ((int) ((halfMax - a0)/(a1-a0) * 64));
                t0      = (tcoarse << 6) + tfine;
            }
            if(s1>-1 && s0>-1) {
                pulseWidth  = s1 - s0;
            }
        }
        return new Peak[]{this};
    }

}
