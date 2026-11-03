package org.jlab.detector.decode;

import java.util.ArrayList;
import java.util.List;
import org.jlab.detector.decode.FADCData.Peak;

public class ExtendedFADCMultiFitter extends Peak implements IFADCFitter {
    
    static final int PED_BIN1=1;
    static final int PED_BIN2=15;

    public ExtendedFADCMultiFitter(){}
    
    @Override
    public Peak[] fit(int nsa, int nsb, int tet, int pedr, short[] pulse) {

        zero();

        if (pulse.length < PED_BIN2+1) return new Peak[]{this};

        if (pedr != 0) {
            // user-defined pedestal:
            baseline = ped = pedr;
        }
        else {
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
        List<Peak> hits = new ArrayList<>(3);

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
                        hits.add(f);
                        f.ped = ped;
                        f.t0 = f.thresholdCrossing = f.tcoarse = crossBin;
                        f.pulsePeakPosition = maxBin-1;
                        f.pulsePeakValue = pulse[maxBin-1];
                        
                        // define its energy:
                        for (int eBin = Math.max(crossBin-nsb,0); eBin<pulse.length && eBin<crossBin+nsa; eBin++)
                            f.adc += pulse[eBin] - f.ped;
                        
                        final double halfMax = (f.pulsePeakValue + baseline)/2;
                        
                        // find the half-max crossing upwards:
                        for (int halfBinUp = crossBin; halfBinUp < maxBin; halfBinUp++) {
                            if (pulse[halfBinUp] <= halfMax && pulse[halfBinUp+1] > halfMax) {
                                
                                // found half-height, now define timing:
                                int a0 = pulse[halfBinUp];
                                int a1 = pulse[halfBinUp+1];
                                f.tcoarse = halfBinUp;
                                f.tfine   = ((int) ((halfMax - a0)/(a1-a0) * 64));
                                f.t0      = (f.tcoarse << 6) + f.tfine;
                                
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
                        
                        // move search bin forward:
                        crossBin = f.pulsePeakPosition + 1;
                        break;
                    }
                }
            }
        }
        return hits.stream().toArray(Peak[]::new);
    }

}
