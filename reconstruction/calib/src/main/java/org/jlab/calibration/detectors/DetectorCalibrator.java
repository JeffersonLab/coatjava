package org.jlab.calibration.detectors;

import java.util.Arrays;
import java.util.List;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author devita
 */
public abstract class DetectorCalibrator {
    
    private DetectorType  type;
    private List<String> bankNames;
    private String outputBankName;
    
    public DetectorCalibrator(DetectorType type) {
        this.type = type;
        this.outputBankName = type.getName()+"::calib";
    }
    
    public void init(String... banks) {
        bankNames = Arrays.asList(banks);
    }
    
    public String getOutputBankName() {
        return this.outputBankName;
    }
    
    public DataBank getCalibBank(DataEvent event) {
        for(String b : bankNames)
            if(!event.hasBank(b))
                return null;
        
        if(this.isGoodEvent(event)) 
            return this.buildCalibBank(event);
        else
            return null;
    }
    
    public abstract boolean isGoodEvent(DataEvent event);
    
    public abstract DataBank buildCalibBank(DataEvent event);
}
