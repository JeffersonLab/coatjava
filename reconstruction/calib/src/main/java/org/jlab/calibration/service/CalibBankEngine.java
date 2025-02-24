package org.jlab.calibration.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.jlab.calibration.detectors.DCCalibrator;
import org.jlab.calibration.detectors.DetectorCalibrator;
import org.jlab.calibration.detectors.FTOFCalibrator;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author devita
 */
public class CalibBankEngine extends ReconstructionEngine {

    private Map<DetectorType,DetectorCalibrator> calibrators = new HashMap<>();

    public static final String CONF_DETECTORS = "detectors";
    private List<DetectorType> detectors = new ArrayList<>();
    
    static final Logger logger = Logger.getLogger(CalibBankEngine.class.getName());

    public CalibBankEngine() {
        super("BG", "baltzell", "1.0");
        calibrators.put(DetectorType.DC  , new DCCalibrator());
        calibrators.put(DetectorType.FTOF, new FTOFCalibrator());
    }

    @Override
    public boolean init() {
        String[] dets = getEngineConfigString(CONF_DETECTORS,"DC,FTOF").split(",");
        for(String d : dets) {
            DetectorType type = DetectorType.getType(d.trim());
            if(type != DetectorType.UNDEFINED)
                detectors.add(type);
        }
        return !detectors.isEmpty();
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
