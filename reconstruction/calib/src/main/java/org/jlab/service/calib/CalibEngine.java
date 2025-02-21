package org.jlab.service.calib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author devita
 */
public class CalibEngine extends ReconstructionEngine {

    public static final String CONF_DETECTORS = "detectors";
    private List<DetectorType> detectors = new ArrayList<>();
    
    static final Logger logger = Logger.getLogger(CalibEngine.class.getName());

    public CalibEngine() {
        super("BG", "baltzell", "1.0");
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
        this.createFTOFbank(event);
        return true;
    }

    private void createFTOFbank(DataEvent event) {
        DataBank part = event.getBank("REC::Particle");
        DataBank scin = event.getBank("REC::Scintillator");
        DataBank trac = event.getBank("REC::Track");
        DataBank hits = event.getBank("FTOF::hits");
        DataBank adcs = event.getBank("FTOF::adc");
        DataBank tdcs = event.getBank("FTOF::tdc");
                        
        if(part!=null && scin!=null && hits!=null && adcs!=null && tdcs!=null) {        
            
            if(part.rows()<2 ||
               part.getInt("pid", 0)!=11 || 
               ((int) (Math.abs(part.getShort("status", 0))/1000))!=2)
                return;
            
            Map<Integer,Map<Integer,Double>> paths = new HashMap<>();
            for(int is=0; is<scin.rows(); is++) {
                int detector = scin.getByte("detector", is);
                if(DetectorType.getType(detector)==DetectorType.FTOF) {
                    int pindex = scin.getShort("pindex", is);                
                    int layer  = scin.getByte("layer", is);
                    double path = scin.getFloat("path", is);
                    if(!paths.containsKey(pindex))
                        paths.put(pindex, new HashMap<>());
                    paths.get(pindex).put(layer, path);
                }
            }

                        
            Map<Integer,Integer> pinds = new HashMap<>();
            for(int it=0; it<trac.rows(); it++) {
                int detector = trac.getByte("detector", it);
                if(DetectorType.getType(detector)==DetectorType.DC) {
                    int index  = trac.getShort("index", it);                
                    int pindex = trac.getShort("pindex", it);                
                    pinds.put(index, pindex);
                }
            }
            
            int ngood = 0;
            for(int i=0; i<hits.rows(); i++) {
                if(hits.getShort("trackid", i)>0) ngood++;
            }
            
            if(ngood>0) {
                DataBank calib = event.createBank("FTOF::calib", ngood);
                
                int row =0;
                for(int i=0; i<hits.rows(); i++) {
                    int tid = hits.getShort("trackid", i);
                    if(tid>0) {
                        int pindex = pinds.get(tid-1);
                        int layer  = hits.getByte("layer", i);
                        double path = paths.get(pindex).containsKey(layer) ? paths.get(pindex).get(layer) : 0;
                        double px  = part.getFloat("px", pindex);
                        double py  = part.getFloat("py", pindex);
                        double pz  = part.getFloat("pz", pindex);
                        calib.setShort("id", row, hits.getShort("id", i));
                        calib.setShort("status", row, hits.getShort("status", i));
                        calib.setShort("trackid", row, hits.getShort("trackid", i));
                        calib.setByte("sector", row, hits.getByte("sector", i));
                        calib.setByte("layer", row, hits.getByte("layer", i));
                        calib.setShort("component", row, hits.getShort("component", i));
                        calib.setFloat("energy", row, hits.getFloat("energy", i));
                        calib.setFloat("time", row, hits.getFloat("time", i));
                        calib.setFloat("x", row, hits.getFloat("x", i));
                        calib.setFloat("y", row, hits.getFloat("y", i));
                        calib.setFloat("z", row, hits.getFloat("z", i));
                        calib.setFloat("tx", row, hits.getFloat("tx", i));
                        calib.setFloat("ty", row, hits.getFloat("ty", i));
                        calib.setFloat("tz", row, hits.getFloat("tz", i));
                        calib.setFloat("p", row, (float) Math.sqrt(px*px+py*py+pz*pz));
                        calib.setFloat("pathLength", row, (float) path);
                        calib.setFloat("pathLengthThruBar", row, hits.getFloat("pathLengthThruBar", i));
                        calib.setInt("adc1", row, adcs.getInt("ADC", hits.getShort("adc_idx1", i)));
                        calib.setInt("adc2", row, adcs.getInt("ADC", hits.getShort("adc_idx2", i)));
                        calib.setInt("tdc1", row, tdcs.getInt("TDC", hits.getShort("tdc_idx1", i)));
                        calib.setInt("tdc2", row, tdcs.getInt("TDC", hits.getShort("tdc_idx2", i)));
                        row++;
                    }
                }
                event.appendBank(calib);
            }
        }
    }
}
