package org.jlab.detector.decode;

public interface IFADCFitter {

    public Peak fit(int nsa, int nsb, int tet, int pedr, short[] pulse);

    public Peak[] multiFit(int nsa, int nsb, int tet, int pedr, short[] pulse);

    public static class Peak { 
        public int     t0, adc, ped, tcourse, tfine;
        public int     thresholdCrossing, pulsePeakValue, pulsePeakPosition, pulseWidth;
        public double  baseline, rms;
    }

}
