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
 * @author ftouchte (main algo), pilleux (refactoring and minor corrections)
 */
public class ModeAHDC extends HipoExtractor  {

    //Parameters, to be read from DB?
    private final short ADC_LIMIT = 4095; // 2^12-1
    private final float samplingTime = 50.0f;
    private final float amplitudeFractionCFA = 0.5f;
    private final int binDelayCFD = 5;
    private final float fractionCFD = 0.3f;
    
    //Waveform and corresponding pulse
    private short[] samples;
    private Pulse pulse;
    private long time_ZS;
    private int binMax;

    /**
    * This method computes the waveform baseline 
    * from the average of the first five samples
    */    
    public void baselineComputation()
    {
        short adcOffset = 0;//default baseline
        //The baseline is the average of the five first samples
        if (this.samples.length >= 5) adcOffset = (short) ((this.samples[0] + this.samples[1] + this.samples[2] + this.samples[3] + this.samples[4])/5);
        //else //Here we should add a condition to skip these events
        this.pulse.pedestal = adcOffset;
    }
    
    /**
    * This method removes the baseline, 
    * computes the max ADC and corresponding time
    * and the integral of the wf
    */    
    public void waveformADCProcessing()
    {
        int binNumber = this.samples.length;   
        pulse.adcMax = -9999;
        this.binMax = -9999;
        //Looping through samples
        for (int bin = 0; bin < binNumber; bin++){
            this.samples[bin] = (short) Math.max(this.samples[bin] - this.pulse.pedestal, 0);
            if (this.pulse.adcMax < this.samples[bin]){
                this.pulse.adcMax = this.samples[bin];
                this.binMax = bin;
            }
            this.pulse.integral += this.samples[bin];
        }
        //Dealing with saturating waveforms. Do we really want to keep these?
        //If the peak crosses the adc saturation limit
        if ((short) this.pulse.adcMax + this.pulse.pedestal == ADC_LIMIT) {
            //Starting from the sample corresponding to the peak
            //Which is the beginning of the saturation plateau
            int binMax2 = this.binMax;
            //Explore the following bins and check if they belong to the plateau
            for (int bin = this.binMax; bin < binNumber; bin++){
                //If they do, add them to the plateau
                if (this.samples[bin] + this.pulse.pedestal == ADC_LIMIT) binMax2 = bin;
                else break;
            }
            //Now we have the bin range for the plateau
            //The max time is defined as the middle of the plateau
            this.binMax = (this.binMax + binMax2)/2;
        }
        //Define the pulse time as the peak time 
        this.pulse.time = (this.binMax + this.time_ZS)*this.samplingTime;
        //If there are five points around the peak
        //(if the peak is not at an edge of the window)
        //Then the peak ADC value is revisited to be the average of these
        //TO DO: adapt the time information consequently
        if ((this.binMax - 2 >= 0) && (this.binMax + 2 <= binNumber - 1)){
            this.pulse.adcMax = 0;
            for (int bin = this.binMax - 2; bin <= this.binMax + 2; bin++) this.pulse.adcMax += this.samples[bin];
            this.pulse.adcMax = this.pulse.adcMax/5;
        }
       }
    
    /**
    * This method computes the TOT 
    * as the time spent over a constant fraction 
    * of the peak value
    */    
    public void waveformCFAprocessing(){
        int binNumber = this.samples.length;
        //Set the CFA threshold
        float threshold = this.amplitudeFractionCFA*this.pulse.adcMax;
        
        //TO DO BEFORE HERE: Tests for peak detection, we don't want to add 
        //remnants from the previous wfs in the TOT computation
        
        //Crossing the threshold before the peak
        int binRise = 0;
        for (int bin = 0; bin < binMax; bin++){
            if (this.samples[bin] < threshold)
                binRise = bin; //Here we keep only the last time the signal crosses the threshold
        }
        float slopeRise = 0;
        //linear interpolation
        //threshold = leadingtime*(ADC1-ADC0)+ADC0
        if (binRise + 1 <= binNumber-1)
            slopeRise = this.samples[binRise+1] - this.samples[binRise];
        float fittedBinRise = (slopeRise == 0) ? binRise : binRise + (threshold - this.samples[binRise])/slopeRise;
        this.pulse.leadingEdgeTime = (fittedBinRise + this.time_ZS)*this.samplingTime;

        //Going under the threshold
        int binFall = binMax;
        for (int bin = binMax; bin < binNumber; bin++){
            if (this.samples[bin] > threshold){
                binFall = bin;
            }
            else {
                binFall = bin;
                break; //We keep only the first time the signal crosses back the threshold
            }
        }
        float slopeFall = 0;
        if (binFall - 1 >= 0)
            slopeFall = this.samples[binFall] - this.samples[binFall-1];
        float fittedBinFall = (slopeFall == 0) ? binFall : binFall-1 + (threshold - this.samples[binFall-1])/slopeFall;
        this.pulse.trailingEdgeTime = (fittedBinFall + this.time_ZS)*this.samplingTime;

        this.pulse.timeOverThreshold = this.pulse.trailingEdgeTime - this.pulse.leadingEdgeTime;
        }
    
         /**
         * This methods extracts a time using the Constant Fraction Discriminator (CFD) algorithm
         * as described in https://commons.wikimedia.org/wiki/File:CFD_Diagram1.jpg for example
         */
       public void computeTimeUsingConstantFractionDiscriminator(){
        int binNumber = this.samples.length;
        float[] signal = new float[binNumber];
        // signal generation
        for (int bin = 0; bin < binNumber; bin++){
            signal[bin] = (1 - this.fractionCFD)*this.samples[bin]; // we fill it with a fraction of the original signal
            if (bin < binNumber - this.binDelayCFD)
                signal[bin] += -1*this.fractionCFD*this.samples[bin + this.binDelayCFD]; // we advance and invert a complementary fraction of the original signal and superimpose it to the previous signal
        }
        // determine the two humps
        int binHumpSup = 0;
        int binHumpInf = 0;
        for (int bin = 0; bin < binNumber; bin++){
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
        if (binZero + 1 <= binNumber)
            slopeCFD = signal[binZero+1] - signal[binZero];
        float fittedBinZero = (slopeCFD == 0) ? binZero : binZero + (0 - signal[binZero])/slopeCFD;
        this.pulse.constantFractionTime = (fittedBinZero + this.time_ZS)*this.samplingTime;
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
        
        this.pulse = new Pulse();
        this.pulse.id = id;
        this.pulse.timestamp = timestamp;
        this.time_ZS = time_ZS;
        this.samples = samples;
        this.baselineComputation();
        
        //Get the ADC information from the pulse (peak ADC and time, integral)
        this.waveformADCProcessing();
        //Get the time overr threshold
        this.waveformCFAprocessing();
        //Get the CFD time
        this.computeTimeUsingConstantFractionDiscriminator();

        List<Pulse> output = new ArrayList<>();
        output.add(this.pulse);
        return output;
    }

    @Override
    public void update(int n, IndexedTable it, DataEvent event, String wfBankName, String adcBankName) {
        DataBank wf = event.getBank(wfBankName);
        if (wf.rows() > 0) {
            event.removeBank(adcBankName);
            event.getBank(adcBankName).show();
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
                    adc.setShort("ped", i, (short)pulses.get(i).pedestal);
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
                    adcBank.putShort("ped", i, (short)pulses.get(i).pedestal); 
                } 
            }
        } 
    }
}
