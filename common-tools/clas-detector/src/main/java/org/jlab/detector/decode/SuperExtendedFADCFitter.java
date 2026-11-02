package org.jlab.detector.decode;

import java.util.ArrayList;
import java.util.List;
import org.jlab.detector.decode.IFADCFitter.Peak;

public class SuperExtendedFADCFitter extends Peak implements IFADCFitter {
    
    static final int PED_BIN1=1;
    static final int PED_BIN2=15;

    public SuperExtendedFADCFitter(){}
    
    @Override
    public Peak[] fit(int nsa, int nsb, int tet, int pedr, short[] pulse) {

        List<Peak> hits = new ArrayList<>(3);

        t0=0; adc=0; ped=0; baseline=0; rms=0; tcoarse=0; tfine=0;
        thresholdCrossing=0; pulsePeakValue=0; pulsePeakPosition=0; pulseWidth=0;

        if (pedr != 0) {
            // user-defined pedestal:
            baseline = ped = pedr;
        }
        else {
            // not enough samples, just set pedestal and abort:
            int pedsum = 0;
            if (pulse.length<PED_BIN2+1) {
                for (int bin = 0; bin < pulse.length; bin++)
                    pedsum += pulse[bin];
                ped = pedsum/pulse.length;
                return hits.stream().toArray(Peak[]::new);
            }
            // calculate pedestal:
            for (int bin = PED_BIN1+1; bin < PED_BIN2+1; bin++) {
                pedsum += pulse[bin];
                rms  += pulse[bin] * pulse[bin];
            }
            baseline = ((double) pedsum) / (PED_BIN2 - PED_BIN1);
            ped = (int)baseline;
            rms = Math.sqrt(rms / (PED_BIN2 - PED_BIN1) - baseline * baseline);
        }
        
        boolean belowThreshold = false;

        for (int crossBin = PED_BIN2+1; crossBin<pulse.length; crossBin++) {
            
            // mark that we went below theshold:
            if (pulse[crossBin] <= ped+tet) belowThreshold = true;
            
            // we went below and above theshold again:
            else if (belowThreshold) {
                
                belowThreshold = false;
                
                // find the maximum:
                for (int maxBin = crossBin; maxBin<pulse.length; maxBin++) {
                    if (pulse[maxBin] < pulse[maxBin-1]) {
                        
                        // found maximum, now make a new hit:
                        Peak f = new Peak();
                        f.ped = ped;
                        f.thresholdCrossing = crossBin;
                        f.pulsePeakPosition = maxBin-1;
                        f.pulsePeakValue = pulse[maxBin-1];
                        
                        // define its energy:
                        for (int eBin = Math.max(crossBin-nsb,0); eBin<pulse.length && eBin<crossBin+nsa; eBin++)
                            f.adc += pulse[eBin] - f.ped;
                        
                        final double halfMax = (pulse[maxBin-1] + baseline)/2;
                        
                        // find the half-max crossing upwards:
                        for (int halfBinUp = crossBin; halfBinUp < maxBin; halfBinUp++) {
                            if (pulse[halfBinUp] <= halfMax && pulse[halfBinUp+1] > halfMax) {
                                
                                // found half-height, now define timing:
                                int a0 = pulse[halfBinUp];
                                int a1 = pulse[halfBinUp+1];
                                f.tcoarse = halfBinUp;
                                f.tfine   = ((int) ((halfMax - a0)/(a1-a0) * 64));
                                f.t0      = (tcoarse << 6) + tfine;
                                
                                // find the half-max crossing downwards:
                                for (int halfBinDown = halfBinUp; halfBinDown<pulse.length-1; halfBinDown++) {
                                    if (pulse[halfBinDown] > halfMax && pulse[halfBinDown+1] <= halfMax) {
                                        f.pulseWidth = halfBinDown - halfBinUp;
                                        break;
                                    }
                                }
                                break;
                            }
                        }
                        
                        // add the new hit we just made:
                        hits.add(f);
                        
                        // move search bin forward:
                        crossBin = f.pulsePeakPosition+1;
                        break;
                    }
                }
            }
        }
        return hits.stream().toArray(Peak[]::new);
    }

}
