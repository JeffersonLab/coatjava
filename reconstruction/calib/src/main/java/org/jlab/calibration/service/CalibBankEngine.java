package org.jlab.calibration.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.jlab.calibration.detectors.CTOFCalibrator;
import org.jlab.calibration.detectors.DCCalibrator;
import org.jlab.calibration.detectors.DetectorCalibrator;
import org.jlab.calibration.detectors.FTOFCalibrator;
import org.jlab.calibration.detectors.RICHCalibrator;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author devita
 */
public class CalibBankEngine extends ReconstructionEngine {

    private final Map<DetectorType,DetectorCalibrator> calibrators = new HashMap<>();

    public static final String CONF_DETECTORS = "detectors";
    private final List<DetectorType> detectors = new ArrayList<>();
    
    static final Logger logger = Logger.getLogger(CalibBankEngine.class.getName());

    public CalibBankEngine() {
        super("CALIB", "devita", "1.0");
        calibrators.put(DetectorType.DC  , new DCCalibrator());
        calibrators.put(DetectorType.FTOF, new FTOFCalibrator());
        calibrators.put(DetectorType.CTOF, new CTOFCalibrator());
        calibrators.put(DetectorType.RICH, new RICHCalibrator());
    }

    @Override
    public boolean init() {
        String[] dets = getEngineConfigString(CONF_DETECTORS,"DC,FTOF,RICH").split(",");
        for(String d : dets) {
            DetectorType type = DetectorType.getType(d.trim());
            if(type != DetectorType.UNDEFINED)
                detectors.add(type);
        }
        if(detectors.isEmpty())
            return false;
        
        String[] outputBanks = new String[detectors.size()];
        for(int i=0; i<detectors.size(); i++) 
            outputBanks[i] = calibrators.get(detectors.get(i)).getOutputBankName();
        this.registerOutputBank(outputBanks);
        
        return true;
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        
        List<DataBank> banks = new ArrayList<>();
        
        for(DetectorType d : detectors) {
        
            DetectorCalibrator calibrator = calibrators.get(d);
            
            if(calibrator.isGoodEvent(event)) {
                DataBank calib = calibrator.getCalibBank(event);
                if(calib!=null) banks.add(calib);
            }
        }
        
        if(!banks.isEmpty())
            event.appendBanks(banks.toArray(new DataBank[banks.size()]));
        return true;
    }

}
