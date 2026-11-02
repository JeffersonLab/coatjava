package org.jlab.detector.decode;

import java.util.ArrayList;
import java.util.List;

public class ExtendedFADCFitter1 implements IFADCFitter {
    
    static final int PED_BIN1=1;
    static final int PED_BIN2=15;

    public int     t0, adc, ped, pedsum, tcourse, tfine;
    public int     thresholdCrossing, pulsePeakValue, pulsePeakPosition, pulseWidth;
    public double  baseline, rms;
    
    public ExtendedFADCFitter1(){}
    
    public ExtendedFADCFitter1[] multiFit(int nsa, int nsb, int tet, int pedr, short[] pulse) {

        t0=0; adc=0; ped=0; pedsum=0; baseline=0; rms=0; tcourse=0; tfine=0;
        thresholdCrossing=0; pulsePeakValue=0; pulsePeakPosition=0; pulseWidth=0;

        if (pedr != 0) {
            // user-defined pedestal:
            ped = pedr;
        }
        else {
            // not enough samples, just set pedestal and abort:
            if (pulse.length<PED_BIN2+1) {
                for (int bin = 0; bin < pulse.length; bin++)
                    pedsum += pulse[bin];
                ped=pedsum/pulse.length;
                return null;
            }
            // calculate pedestal:
            for (int bin = PED_BIN1+1; bin < PED_BIN2+1; bin++) {
                pedsum += pulse[bin];
                rms  += pulse[bin] * pulse[bin];
            }
            baseline = ((double) pedsum)/ (PED_BIN2 - PED_BIN1);
            ped = pedsum=pedsum/(PED_BIN2-PED_BIN1);	//(int) baseline;
            rms = Math.sqrt(rms / (PED_BIN2 - PED_BIN1) - baseline * baseline);
        }
        
        List<ExtendedFADCFitter1> hits = null;
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
                        
                        // make a new hit:
                        ExtendedFADCFitter1 f = new ExtendedFADCFitter1();
                        f.ped = ped;
                        f.thresholdCrossing = crossBin;
                        f.pulsePeakPosition = maxBin-1;
                        f.pulsePeakValue = pulse[maxBin-1];
                        
                        // define its energy:
                        final int eLimit = Math.min(pulse.length, crossBin + nsa);
                        for (int eBin = Math.max(crossBin-nsb,0); eBin < eLimit; eBin++)
                            f.adc += pulse[eBin] - f.ped;
                        
                        // find the half-max crossing upwards:
                        final double halfMax = (pulse[maxBin-1] + baseline)/2;
                        for (int halfBinUp = crossBin+1; halfBinUp < maxBin; halfBinUp++) {
                            if (pulse[halfBinUp] <= halfMax && pulse[halfBinUp+1] > halfMax) {
                                
                                // define timing:
                                int a0 = pulse[halfBinUp];
                                int a1 = pulse[halfBinUp+1];
                                f.tcourse = halfBinUp;
                                f.tfine   = ((int) ((halfMax - a0)/(a1-a0) * 64));
                                f.t0      = (tcourse << 6) + tfine;
                                
                                // find the half-max crossing downwards:
                                for (int halfBinDown = halfBinUp; halfBinDown<pulse.length; halfBinDown++) {
                                    if (pulse[halfBinDown] > halfMax && pulse[halfBinDown+1] <= halfMax) {
                                        f.pulseWidth = halfBinDown - halfBinUp;
                                        break;
                                    }
                                }
                                break;
                            }
                        }
                        
                        // add the new hit we just made:
                        if (hits == null ) hits = new ArrayList<>();
                        hits.add(f);
                        
                        // move search bin forward:
                        crossBin = f.pulsePeakPosition;
                        break;
                    }
                }
                // if we didn't generate a new hit, stop:
                if (hits == null) break;
            }
        }
        return hits == null ? null : hits.stream().toArray(ExtendedFADCFitter1[]::new);
    }
    
    @Override
    public void fit(DetectorDataDgtz.ADCData data) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
