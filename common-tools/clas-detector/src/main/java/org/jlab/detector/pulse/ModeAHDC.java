package org.jlab.detector.pulse;

import java.util.List;
import java.util.ArrayList;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.utils.groups.NamedEntry;

/**
 * A new extraction method dedicated to the AHDC signal waveform
 *
 * Some blocks of code are inspired by MVTFitter.java and Bonus12 (`createBonusBank()`)
 *
 * To do list:
 * - read pedestal from the DB when a baseline cannot be computed
 * - change the definition of ADC to be the max of the peak?
 * - fit waveforms to define arrival time (and charge)
 *
 * @author ftouchte, pilleux
 */
public class ModeAHDC extends HipoExtractor  {
    
    //Parameters, to be read from DB?
    
    //Saturation threshold should be 4095 (2^12-1)
    //But in practice waveforms saturate below it
    private final short ADC_LIMIT = 3600;
    //number of consecutive samples exceeding threshold to consider a saturation plateau
    private final int consecutiveSaturatingSamples = 3;
    //Sampling time in ns
    private final float samplingTime = 48.0f;
    //CF threshold
    private final float amplitudeFractionCFA = 0.5f;
    //For the CFD algo
    private final int binDelayCFD = 5;
    private final float fractionCFD = 0.5f;
    //Number of samples defining the baseline
    private final int nBaselineSamples = 4;
    //threshold for the slope to consider part of a wf flat
    private final int flateness = 200;
    //ADC offset to be considered as the default baseline
    private final float defaultBaseline = 300;
    // dream clock for fine timestamp correction
    private final float dream_clock = 8.0f;
    
    //Waveform types:
    //wfType 6 ⇒ too small (nsamples <= 10)
    //wfType 5 ⇒ decreasing baseline (or leadingEdgeTime fails)
    //wfType 4 ⇒ bad ToT (ToT < 300)
    //wfType 3 ⇒ pileUp // TO BE DONE
    //wfType 2 ⇒ bad trailingEdgeTime
    //wfType 1 ⇒ saturing
    //wfType 0 ⇒ OK
    
    private static class AHDCPulse extends Pulse {
        private long time_ZS;
        private int binMax;
        private int numberOfBins;
        private int effectiveNumberOfBins;
        private short[] samples;
    }
    
    /**
     *
     */
    public void preProcess(AHDCPulse p) {
        assignValidType(0, p);
        p.numberOfBins = p.samples.length;
        // check if the signal is too short
        int countNotZeros = 0;
        boolean FirstNonZeroFromTheTailReached = false;
        for (int i = p.samples.length-1; i >= 0; i--) {
            if (p.samples[i] > 0) {
                countNotZeros++;
                if (!FirstNonZeroFromTheTailReached) {
                    FirstNonZeroFromTheTailReached = true;
                    p.effectiveNumberOfBins = i+1; // this value can be identified as the effective number of bins
                }
            }
        }
        if (countNotZeros <= 10) assignInvalidType(p);
    }
    
    /**
     * This method computes the waveform baseline
     * from the average of the first few samples
     */
    public void baselineComputation(AHDCPulse p)
    {
        //Default baseline
        float adcOffset = -99;
        
        //Check if the wf has sufficient length, if not it is invalid
        if (p.numberOfBins >= this.nBaselineSamples+1){
            
            //The baseline is the average of the first few samples
            for(int i=0; i<this.nBaselineSamples;i++){
                adcOffset += p.samples[i];
            }
            adcOffset = adcOffset/this.nBaselineSamples;
        }
        else {this.assignInvalidType(p);}//invalid, too short wf
        p.pedestal = adcOffset;
    }
    
    /**
     * This method assigns a new type to the waveform
     * by checking if another more restrictive type had already
     * been assigned
     *
     * @param type an int that is the new wf type to be applied
     */
    public void assignValidType(int type, AHDCPulse p){
        // if a signal has already been classified as type 1 (saturated), do nothing
        if (p.wftype != 1) {
            //We only assign a new type if a more restrictive type was not defined before
            if(type>p.wftype) p.wftype = (short) type;
        }   
    }
    
    /**
     * This method assigns an invalid type (-1) to the waveform
     */
    public void assignInvalidType(AHDCPulse p){
        p.wftype = 6;
    }
    
    /**
     * This method subtracts the baseline,
     * computes the max ADC and corresponding time
     * and the integral of the wf
     *
     * @return an int for status
     *
     */
    public int waveformADCProcessing(AHDCPulse p)
    {
        //int numberOfBins = this.samples.length;
        
        //Initialize to dummy values
        p.adcMax = -99;
        p.binMax = -99;
        
        //For invalid wf, dummy values kept
        //if(this.pulse.wftype == 6) return 0;
        
        //Checking for saturation
        int nsaturating = 0;
        int maxNsaturating = 0;
        //beginning and end of the saturation plateau
        int startPlateau = -99;
        int endPlateau = -99;
        
        //Looping through samples
        for (int bin = 0; bin < p.numberOfBins; bin++){
            //Baseline subtraction
            p.samples[bin] = (short) Math.max(p.samples[bin] - p.pedestal, 0);
            //Look for maximum
            if ((p.adcMax < p.samples[bin]) && (bin >= nBaselineSamples)) { // the max should not be in the baseline
                p.adcMax = p.samples[bin];
                p.binMax = bin;
            }
            //Integration
            p.integral += p.samples[bin];
            
            //Check for saturation: if the sample exceeds the ths
            //and if it belongs to a group of saturating samples
            if(p.samples[bin]>(this.ADC_LIMIT-p.pedestal)){
                //if this is the first sample saturating, start a new plateau
                if(nsaturating==0) startPlateau = bin;
                //else continue the plateau
                else endPlateau = bin;
                nsaturating++;
                //we store the maximum length of consecutive saturating samples
                if(nsaturating>maxNsaturating) maxNsaturating = nsaturating;
            }
            else nsaturating = 0;//this sample is not in a row of saturating samples
        }
        
        //Saturating samples: if the number of consecutive saturating samples
        //is greater than the limit, we consider the wf saturated
        if(maxNsaturating>=this.consecutiveSaturatingSamples){
            this.assignValidType(1, p);
            //The time is taken as the middle of the plateau
            p.binMax = (startPlateau + endPlateau)/2;
        }
        
        //Define the pulse time as the peak time
        p.time = (p.binMax + p.time_ZS)*this.samplingTime;

        // Compute adcMax
        if (p.adcMax == 0) { 
            assignValidType(5, p);
            return 0;
        } // classification before the fit

        if (p.wftype == 1) { // special case of saturated signals
            float slope_max = 0;
            for (int bin = 0; bin < p.binMax; bin++) {
                float slope = (p.samples[bin+1] - p.samples[bin])/samplingTime;
                if (slope > slope_max) {
                    slope_max = slope;
                }
            }
            // empirical formula
            p.adcMax = -28.753520f + 191.791679f*slope_max;

        } else {
            //If there are 2*npts+1 points around the peak
            //(if the peak is not at an edge of the window)
            //Then the peak ADC value is revisited to be the average of these
            //TO DO: just use the adcmax???
            int npts = 1;
            if ((p.binMax - npts >= 0) && (p.binMax + npts <= p.numberOfBins - 1)){
                p.adcMax = 0;
                for (int bin = p.binMax - npts; bin <= p.binMax + npts; bin++) p.adcMax += p.samples[bin];
                p.adcMax = p.adcMax/(2*npts+1);
            }
        }
        
        
        return 0;
    }
    
    /**
     * This method computes the leading edge time
     * and time over threshold
     * over a constant fraction of the peak value
     *
     * @return an int for status
     *
     */
    public int waveformCFAprocessing(AHDCPulse p){
        
        //Initialization
        p.leadingEdgeTime = -9999;
        p.trailingEdgeTime = -9999;
        p.timeOverThreshold = -9999;

        if (p.binMax < 0) return 0;
        
        //Set the CFA threshold
        float threshold = Math.min(this.amplitudeFractionCFA*p.adcMax, p.samples[p.binMax]);
        
        //Crossing the threshold before the peak
        int binRise = -99;
        for (int bin = 0; bin < p.binMax - 1; bin++){
            if (p.samples[bin] < threshold && p.samples[bin+1] >= threshold)
                binRise = bin; //Here we keep only the last time the signal crosses the threshold
        }
        //If the waveform does not cross the ths before the peak
        //it was early or remant from a previous event and we cannot define a leading time
        //we set the time at zero
        if(binRise==-99) {
            this.assignValidType(5, p);
            p.leadingEdgeTime = (0 + p.time_ZS)*this.samplingTime;
        }
        else
        {
            float slopeRise = 0;
            //linear interpolation
            //threshold = leadingtime*(ADC1-ADC0)+ADC0
            if (binRise + 1 <= p.numberOfBins-1)
                slopeRise = p.samples[binRise+1] - p.samples[binRise];
            float fittedBinRise = (slopeRise == 0) ? binRise : binRise + (threshold - p.samples[binRise])/slopeRise;
            p.leadingEdgeTime = (fittedBinRise + p.time_ZS)*this.samplingTime;
        }
        
        //Crossing the threshold back down after the peak
        int binFall = -99;
        for (int bin = p.binMax; bin < p.numberOfBins-1; bin++){
            if (p.samples[bin] > threshold
                && p.samples[bin+1] <= threshold
                && p.samples[bin]>0
                && p.samples[bin+1]>0){
                binFall = bin+1;
                break; //We keep only the first time the signal crosses back the threshold
            }
        }
        //If the waveform does not cross the ths again
        //it was late and falling edge cannot be defined
        //the time correspond to the end of the signal
        if(binFall==-99) {
            this.assignValidType(2, p);
            p.trailingEdgeTime = (p.effectiveNumberOfBins -1 + p.time_ZS)*this.samplingTime;
        }
        else
        {
            float slopeFall = 0;
            if (binFall - 1 >= 0)
                slopeFall = p.samples[binFall] - p.samples[binFall-1];
            float fittedBinFall = (slopeFall == 0) ? binFall : binFall-1 + (threshold - p.samples[binFall-1])/slopeFall;
            p.trailingEdgeTime = (fittedBinFall + p.time_ZS)*this.samplingTime;
        }
        
        p.timeOverThreshold = p.trailingEdgeTime - p.leadingEdgeTime;
        //if ((this.pulse.timeOverThreshold < 300) || (this.pulse.timeOverThreshold > 750)) this.assignValidType(4); // bad tot
        if (p.timeOverThreshold < 300) this.assignValidType(4, p); // tot too small
        
        return 0;
    }
    
    /**
     * This methods extracts a time using the Constant Fraction Discriminator (CFD) algorithm
     * as described in https://commons.wikimedia.org/wiki/File:CFD_Diagram1.jpg for example
     *
     * @return an int for status
     *
     */
    public int computeTimeUsingConstantFractionDiscriminator(AHDCPulse p){
        //Dummy values for hits for which the leading edge is not defined
        p.constantFractionTime = -99;
        if(p.wftype>=4) return 0;
        
        //int numberOfBins = this.samples.length;
        float[] signal = new float[p.numberOfBins];
        
        // signal generation
        for (int bin = 0; bin < p.numberOfBins; bin++){
            signal[bin] = (1 - this.fractionCFD)*p.samples[bin]; // we fill it with a fraction of the original signal
            if (bin < p.numberOfBins - this.binDelayCFD)
                signal[bin] += -1*this.fractionCFD*p.samples[bin + this.binDelayCFD]; // we advance and invert a complementary fraction of the original signal and superimpose it to the previous signal
        }
        // determine the two humps
        int binHumpSup = 0;
        int binHumpInf = 0;
        for (int bin = 0; bin < p.numberOfBins; bin++){
            if (signal[bin] > signal[binHumpSup])
                binHumpSup = bin;
        }
        for (int bin = 0; bin < binHumpSup; bin++){ // this loop has been added to be sure : binHumpInf < binHumpSup
            if (signal[bin] < signal[binHumpInf])
                binHumpInf = bin;
        }
        // research for zero
        int binZero = 0;
        for (int bin = binHumpInf; bin <= binHumpSup; bin++){
            if (signal[bin] < 0)
                binZero = bin; // last pass below zero
        } // at this stage : binZero < constantFractionTime/samplingTime <= binZero + 1 // constantFractionTime is determined by assuming a linear fit between binZero and binZero + 1
        float slopeCFD = 0;
        if (binZero + 1 <= p.numberOfBins)
            slopeCFD = signal[binZero+1] - signal[binZero];
        float fittedBinZero = (slopeCFD == 0) ? binZero : binZero + (0 - signal[binZero])/slopeCFD;
        p.constantFractionTime = (fittedBinZero + p.time_ZS)*this.samplingTime;
        
        return 0;
    }
    
    /**
     * Apply fine timestamp correction
     *
     * adapted from decode/MVTFitter.java
     *
     * @param timestamp for fine time correction
     * @param fineTimeStampResolution correspond to the dream clock (usually equals to 8; but to be checked!)
     */
    
    private void fineTimeStampCorrection(long timestamp, float fineTimeStampResolution, AHDCPulse p) {
        long fineTimeStamp = timestamp & 0x00000007; // keep and convert last 3 bits of binary timestamp
        p.leadingEdgeTime += (double) ((fineTimeStamp+0.5) * fineTimeStampResolution); //fineTimeStampCorrection, only on the leadingEdgeTime for the moment (we don't use timeMax or constantFractionTime in the reconstruction yet)
    }
    
    /**
     * This method extracts relevant information from the waveform
     * and builds a pulse from it
     *
     * @param pars CCDB row
     * @param id link to row in source bank
     * @param timestamp ...
     * @param time_ZS time_ZS time bin of the first channel of the AHDC pulse (linked to zero suppression; if ZS=0, time_ZS == 0)
     * @param samples ADC samples
     */
    @Override
    public List<Pulse> extract(NamedEntry pars, int id, long timestamp, long time_ZS, short... samples){
        
        //Initialize everything each time a new wf is read
        //and new adc information is extracted.
        //This is messy but avoids feeding the same arguments to all methods
        AHDCPulse pulse = new AHDCPulse();
        pulse.id = id;
        pulse.timestamp = timestamp;
        pulse.time_ZS = time_ZS;
        pulse.samples = samples;
        pulse.binMax = -99;
        
        // Pre Process
        this.preProcess(pulse);

        //Baseline computation
        this.baselineComputation(pulse);
        
        //Get the ADC information from the pulse (peak ADC and time, integral)
        this.waveformADCProcessing(pulse);
        
        //Get the time overr threshold
        this.waveformCFAprocessing(pulse);
        
        //Get the CFD time
        this.computeTimeUsingConstantFractionDiscriminator(pulse);
        
        // Fine timestamp correction on leadingEdgeTime
        this.fineTimeStampCorrection(timestamp, dream_clock, pulse);
        
        List<Pulse> output = new ArrayList<>();
        output.add(pulse);
        return output;
    }
    
    @Override
    public void update(int n, IndexedTable it, DataEvent event, String wfBankName, String adcBankName) {
        DataBank wf = event.getBank(wfBankName);
        if (wf.rows() > 0) {
            event.removeBank(adcBankName);
            List<Pulse> pulses = getPulses(n, it, wf);
            if (pulses != null && !pulses.isEmpty()) {
                DataBank adc = event.createBank(adcBankName, pulses.size());
                for (int i=0; i<pulses.size(); ++i) {
                    copyIndices(wf, adc, i, i);
                    adc.setInt("ADC", i, (int)pulses.get(i).adcMax);
                    adc.setFloat("time", i, pulses.get(i).time);
                    adc.setFloat("leadingEdgeTime", i, pulses.get(i).leadingEdgeTime);
                    adc.setFloat("timeOverThreshold", i, pulses.get(i).timeOverThreshold);
                    adc.setFloat("constantFractionTime", i, pulses.get(i).constantFractionTime);
                    adc.setInt("integral", i, (int)pulses.get(i).integral);
                    adc.setFloat("ped", i, pulses.get(i).pedestal);
                    adc.setShort("wfType", i, pulses.get(i).wftype);
                }
                event.appendBank(adc);
            }
        }
    }
    
    @Override
    protected void update(int n, IndexedTable it, Bank wfBank, Bank adcBank) {
        if (wfBank.getRows() > 0) {
            List<Pulse> pulses = getPulses(n, it, wfBank);
            adcBank.reset();
            adcBank.setRows(pulses!=null ? pulses.size() : 0);
            if (pulses!=null && !pulses.isEmpty()) {
                for (int i=0; i<pulses.size(); ++i) {
                    copyIndices(wfBank, adcBank, pulses.get(i).id, i);
                    adcBank.putInt("ADC", i, (int)pulses.get(i).adcMax);
                    adcBank.putFloat("time", i, pulses.get(i).time);
                    adcBank.putFloat("leadingEdgeTime", i, pulses.get(i).leadingEdgeTime);
                    adcBank.putFloat("timeOverThreshold", i, pulses.get(i).timeOverThreshold);
                    adcBank.putFloat("constantFractionTime", i, pulses.get(i).constantFractionTime);
                    adcBank.putInt("integral", i, (int)pulses.get(i).integral);
                    adcBank.putFloat("ped", i, pulses.get(i).pedestal);
                    adcBank.putShort("wfType", i, pulses.get(i).wftype);
                }
            }
        }
    }
}
