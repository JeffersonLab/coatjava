package org.jlab.detector.pulse;

import org.jlab.detector.base.DetectorType;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.utils.groups.IndexedTable;

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

    public ExtractorPars(DetectorType type, ConstantsManager conman, int run) {
        String tableName = "/daq/config/"+type.getName();
        IndexedTable table = conman.getConstants(run, tableName);
        threshold = (float)table.getDoubleValue("tet", run);
        pedestal = (float)table.getDoubleValue("ped", run);
        nsa = table.getIntValue("nsa", run);
        nsb = table.getIntValue("nsb", run);
        adcBankName = type.getName()+"::adc";
        wfBankName = type.getName()+"::wf";
    }
}
