package org.jlab.detector.pulse;

public class ExtractorPars {

    public String wfBankName;
    public String adcBankName;

    public float threshold = Float.MAX_VALUE;
    public float pedestal = Float.MAX_VALUE;
    public int nsa = 0;
    public int nsb = 0;

    public ExtractorPars(String wfBankName, String adcBankName) {
        this.wfBankName = wfBankName;
        this.adcBankName = adcBankName;
    }
}
