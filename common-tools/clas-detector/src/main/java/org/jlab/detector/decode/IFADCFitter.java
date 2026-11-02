package org.jlab.detector.decode;

public interface IFADCFitter {

    public static class Peak { 
        public int     t0, adc, ped, tcoarse, tfine;
        public int     thresholdCrossing, pulsePeakValue, pulsePeakPosition, pulseWidth;
        public double  baseline, rms;
    }

    public Peak[] fit(int nsa, int nsb, int tet, int pedr, short[] pulse);

}
