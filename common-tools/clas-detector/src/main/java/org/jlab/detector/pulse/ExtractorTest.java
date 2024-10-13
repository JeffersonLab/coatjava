package org.jlab.detector.pulse;

public class ExtractorTest {

    public static void main(String[] args) {
        ExtractorPars pars = new ExtractorPars("NAB::wf","NAB::adc");
        pars.threshold=5;
        pars.pedestal=10;
        pars.nsb=2;
        pars.nsa=2;
        short[] samples = {9,10,11,8,1000,100,10,10,10,10,10,2000,200,10,10};
        Mode1Extractor e = new Mode1Extractor();
        System.out.println(e.extract(pars,2,samples));
        Mode7Extractor s = new Mode7Extractor();
        System.out.println(s.extract(pars,2,samples));
    }
    
}
