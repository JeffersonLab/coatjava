package org.jlab.detector.pulse;

import java.util.List;
import java.util.ArrayList;

import net.jcip.annotations.GuardedBy;
import org.jlab.utils.groups.NamedEntry;


/**
 * A new extraction method dedicated to the AHDC signal waveform
 * 
 * Some blocks of code are inspired by MVTFitter.java
 *
 * @author  ftouchte
 */
public class ModeAHDC extends HipoExtractor  {

	public static final short ADC_LIMIT = 4095; // 2^12-1
	/**
	 * This method extracts relevant informations from the digitized signal
	 * (the samples) and store them in a Pulse
	 *
	 * @param pars CCDB row
	 * @param id link to row in source bank
	 * @param samples ADC samples
	 */
	@Override
	public List<Pulse> extract(NamedEntry pars, int id, short... samples){
		// Settings parameters (they can be initialised by a CCDB)
		float samplingTime = 0;
		int sparseSample = 0;
		short adcOffset = 0;
		long timeStamp = 0;
		float fineTimeStampResolution = 0;

		float amplitudeFractionCFA = 0;
		int binDelayCFD = 0;
		float fractionCFD = 0;

		// Calculation intermediaries
		int binMax = 0; //Bin of the max ADC over the pulse
		int binOffset = 0; //Offset due to sparse sample
		float adcMax = 0; //Max value of ADC over the pulse (fitted)
		float timeMax =0; //Time of the max ADC over the pulse (fitted)
		float integral = 0; //Sum of ADCs over the pulse (not fitted)
		long timestamp = 0;

		short[] samplesCorr; //Waveform after offset (pedestal) correction
		int binNumber = 0; //Number of bins in one waveform

		float leadingEdgeTime = 0; // moment when the signal reaches a Constant Fraction of its Amplitude uphill (fitted)
		float trailingEdgeTime = 0; // moment when the signal reaches a Constant Fraction of its Amplitude downhill (fitted)
		float timeOverThreshold = 0; // is equal to (timeFallCFA - timeRiseCFA)
		float constantFractionTime ; // time extracted using the Constant Fraction Discriminator (CFD) algorithm (fitted)
		/// /////////////////////////
		// Begin waveform correction
		/// ////////////////////////
		//waveformCorrection(samples,adcOffset,samplingTime,sparseSample, binMax, adcMax, integral, samplesCorr[], binOffset, timeMax);
		/**
		 * This method subtracts the pedestal (noise) from samples and stores it in : samplesCorr
		 * It also computes a first value for : adcMax, binMax, timeMax and integral
		 * This code is inspired by the one of MVTFitter.java
		 * @param samples ADC samples
		 * @param adcOffset pedestal or noise level
		 * @param samplingTime time between two adc bins
		 * @param sparseSample used to define binOffset
		 */
		//private void waveformCorrection(short[] samples, short adcOffset, float samplingTime, int sparseSample, int binMax, int adcMax, int integral, short samplesCorr[], int binOffset, int timeMax){
			binNumber = samples.length;
			binMax = 0;
			adcMax = (short) (samples[0] - adcOffset);
			integral = 0;
			samplesCorr = new short[binNumber];
			for (int bin = 0; bin < binNumber; bin++){
				samplesCorr[bin] = (short) (samples[bin] - adcOffset);
				if (adcMax < samplesCorr[bin]){
					adcMax = samplesCorr[bin];
					binMax = bin;
				}
				integral += samplesCorr[bin];
			}
			/*
			 * If adcMax + adcOffset == ADC_LIMIT, that means there is saturation
			 * In that case, binMax is the middle of the first plateau
			 * This convention can be changed
			 */
			if ((short) adcMax + adcOffset == ADC_LIMIT) {
				int binMax2 = binMax;
				for (int bin = binMax; bin < binNumber; bin++){
					if (samplesCorr[bin] + adcOffset == ADC_LIMIT) {
						binMax2 = bin;
					}
					else {
						break;
					}
				}
				binMax = (binMax + binMax2)/2;
			}
			binOffset = sparseSample*binMax;
			timeMax = (binMax + binOffset)*samplingTime;
		//}

		/// /////////////////////////
		// Begin fit average
		/// ////////////////////////

		//fitAverage(samplingTime);
		/**
		 * This method gives a more precise value of the max of the waveform by computing the average of five points around the binMax
		 * It is an alternative to fitParabolic()
		 * The suitability of one of these fits can be the subject of a study
		 * Remark : This method updates adcMax but doesn't change timeMax
		 * @param samplingTime time between 2 ADC bins
		 */
		//private void fitAverage(float samplingTime){
			if ((binMax - 2 >= 0) && (binMax + 2 <= binNumber - 1)){
				adcMax = 0;
				for (int bin = binMax - 2; bin <= binMax + 2; bin++){
					adcMax += samplesCorr[bin];
				}
				adcMax = adcMax/5;
			}
		//}

		/// /////////////////////////
		// Begin computeTimeAtConstantFractionAmplitude
		/// ////////////////////////
		//computeTimeAtConstantFractionAmplitude(samplingTime,amplitudeFractionCFA);
		/**
		 * This method determines the moment when the signal reaches a Constant Fraction of its Amplitude (i.e fraction*adcMax)
		 * It fills the attributs : leadingEdgeTime trailingEdgeTime, timeOverThreshold
                 *
		 * @param samplingTime time between 2 ADC bins
		 * @param amplitudeFraction amplitude fraction between 0 and 1
		 */
		//private void computeTimeAtConstantFractionAmplitude(float samplingTime, float amplitudeFractionCFA){
			float threshold = amplitudeFractionCFA*adcMax;
			// leadingEdgeTime
			int binRise = 0;
			for (int bin = 0; bin < binMax; bin++){
				if (samplesCorr[bin] < threshold)
					binRise = bin;  // last pass below threshold and before adcMax
			} // at this stage : binRise < leadingEdgeTime/samplingTime <= binRise + 1 // leadingEdgeTime is determined by assuming a linear fit between binRise and binRise + 1
			float slopeRise = 0;
			if (binRise + 1 <= binNumber-1)
				slopeRise = samplesCorr[binRise+1] - samplesCorr[binRise];
			float fittedBinRise = (slopeRise == 0) ? binRise : binRise + (threshold - samplesCorr[binRise])/slopeRise;
			leadingEdgeTime = (fittedBinRise + binOffset)*samplingTime; // binOffset is determined in wavefromCorrection() // must be the same for all time ? // or must be defined using fittedBinRise*sparseSample

			// trailingEdgeTime
			int binFall = binMax;
			for (int bin = binMax; bin < binNumber; bin++){
				if (samplesCorr[bin] > threshold){
					binFall = bin;
				}
				else {
					binFall = bin;
					break; // first pass below the threshold
				}
			} // at this stage : binFall - 1 <= timeRiseCFA/samplingTime < binFall // trailingEdgeTime is determined by assuming a linear fit between binFall - 1 and binFall
			float slopeFall = 0;
			if (binFall - 1 >= 0)
				slopeFall = samplesCorr[binFall] - samplesCorr[binFall-1];
			float fittedBinFall = (slopeFall == 0) ? binFall : binFall-1 + (threshold - samplesCorr[binFall-1])/slopeFall;
			trailingEdgeTime = (fittedBinFall + binOffset)*samplingTime;

			// timeOverThreshold
			timeOverThreshold = trailingEdgeTime - leadingEdgeTime;
		//}
		/// /////////////////////////
		// Begin computeTimeUsingConstantFractionDiscriminator
		/// ////////////////////////
		//computeTimeUsingConstantFractionDiscriminator(samplingTime,fractionCFD,binDelayCFD);
		/**
		 * This methods extracts a time using the Constant Fraction Discriminator (CFD) algorithm
		 * It fills the attribut : constantFractionTime
		 * @param samplingTime time between 2 ADC bins
		 * @param fractionCFD CFD fraction parameter between 0 and 1
		 * @param binDelayCFD CFD delay parameter
		 */
		//private void computeTimeUsingConstantFractionDiscriminator(float samplingTime, float fractionCFD, int binDelayCFD){
			float[] signal = new float[binNumber];
			// signal generation
			for (int bin = 0; bin < binNumber; bin++){
				signal[bin] = (1 - fractionCFD)*samplesCorr[bin]; // we fill it with a fraction of the original signal
				if (bin < binNumber - binDelayCFD)
					signal[bin] += -1*fractionCFD*samplesCorr[bin + binDelayCFD]; // we advance and invert a complementary fraction of the original signal and superimpose it to the previous signal
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
			constantFractionTime = (fittedBinZero + binOffset)*samplingTime;

		//}

		/// /////////////////////////
		// Begin fineTimeStampCorrection
		/// ////////////////////////
		//fineTimeStampCorrection(timeStamp,fineTimeStampResolution);
		/**
		 * From MVTFitter.java
		 * Make fine timestamp correction (using dream (=electronic chip) clock)
		 * @param timeStamp timing informations (used to make fine corrections)
		 * @param fineTimeStampResolution precision of dream clock (usually 8)
		 */
		//private void fineTimeStampCorrection (long timeStamp, float fineTimeStampResolution) {
			//this.timestamp = timeStamp;
			String binaryTimeStamp = Long.toBinaryString(timeStamp); //get 64 bit timestamp in binary format
			if (binaryTimeStamp.length()>=3){
				byte fineTimeStamp = Byte.parseByte(binaryTimeStamp.substring(binaryTimeStamp.length()-3,binaryTimeStamp.length()),2); //fineTimeStamp : keep and convert last 3 bits of binary timestamp
				timeMax += (float) ((fineTimeStamp+0.5) * fineTimeStampResolution); //fineTimeStampCorrection
				// Question : I wonder if I have to do the same thing of all time quantities that the extract() methods compute.
			}
		//}
		// output
		Pulse pulse = new Pulse();
		pulse.adcMax = adcMax;
		pulse.time = timeMax;
		pulse.timestamp = timestamp;
		pulse.integral = integral;
		pulse.leadingEdgeTime  = leadingEdgeTime ;
		pulse.trailingEdgeTime = trailingEdgeTime;
		pulse.timeOverThreshold = timeOverThreshold;
		pulse.constantFractionTime = constantFractionTime;
		//pulse.binMax = binMax;
		//pulse.binOffset = binOffset;
		pulse.pedestal = adcOffset;
		List<Pulse> output = new ArrayList<>();
		output.add(pulse);
		return output;
	}
	/**
	 * Fit the max of the pulse using parabolic fit, this method updates the timeMax and adcMax values
	 * @param samplingTime time between 2 ADC bins
	 */
	private void fitParabolic(float samplingTime) {

	}
}
