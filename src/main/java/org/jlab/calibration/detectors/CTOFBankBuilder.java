package org.jlab.calibration.detectors;

import java.util.HashMap;
import java.util.Map;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author devita
 */
public class CTOFBankBuilder extends CalibBankBuilder {

    public CTOFBankBuilder() {
        super(DetectorType.CTOF);
        super.init("CTOF::adc", "CTOF::tdc", "CTOF::hits", "CVTRec::Tracks", 
                   "REC::Track", "REC::Scintillator", "REC::Particle");
    }
    @Override
    public boolean isGoodEvent(DataEvent event) {
        
        DataBank part = event.getBank("REC::Particle");                
        if(part.rows()<2 ||
           part.getInt("pid", 0)!=11 || 
           ((int) (Math.abs(part.getShort("status", 0))/1000))!=2)
                return false;
        else {
            for(int i=1; i<part.rows(); i++) {
                if(part.getByte("charge", i)<0 &&
                   part.getShort("status", i)>4000)
                    return true;
            } 
        }
        return false;
    }

    @Override
    public DataBank buildCalibBank(DataEvent event) {
        DataBank part = event.getBank("REC::Particle");                
        DataBank scin = event.getBank("REC::Scintillator");
        DataBank trac = event.getBank("REC::Track");
        DataBank cvts = event.getBank("CVTRec::Tracks");
        DataBank hits = event.getBank("CTOF::hits");
        DataBank adcs = event.getBank("CTOF::adc");
        DataBank tdcs = event.getBank("CTOF::tdc");
            
        Map<Integer,Integer> sinds = new HashMap<>();
        for(int is=0; is<scin.rows(); is++) {
            int detector = scin.getByte("detector", is);
            if(DetectorType.getType(detector)==DetectorType.CTOF) {
                int pindex = scin.getShort("pindex", is);                
                sinds.put(pindex, is);
            }
        }


        Map<Integer,Integer> tinds = new HashMap<>();
        for(int it=0; it<trac.rows(); it++) {
            int detector = trac.getByte("detector", it);
            if(DetectorType.getType(detector)==DetectorType.CVT) {
                int index  = trac.getShort("index", it);                
                tinds.put((int) cvts.getShort("ID", index), it);
            }
        }

        int ngood = 0;
        for(int i=0; i<hits.rows(); i++) {
            int tid = hits.getShort("trkID", i);
            if(tid>0 && tinds.containsKey(tid)) {
                int tindex = tinds.get(tid);
                int pindex = trac.getShort("pindex", tindex);                
                if(sinds.containsKey(pindex))
                    ngood++;
            }
        }

        if(ngood>0) {
            DataBank calib = event.createBank("CTOF::calib", ngood);

            int row =0;
            for(int i=0; i<hits.rows(); i++) {
                int tid = hits.getShort("trkID", i);
                if(tid>0 && tinds.containsKey(tid)) {
                    int tindex = tinds.get(tid);
                    int pindex = trac.getShort("pindex", tindex);                
                    if(sinds.containsKey(pindex)) {
                        int sindex = sinds.get(pindex);
                        calib.setShort("id", row, hits.getShort("id", i));
                        calib.setShort("status", row, hits.getShort("status", i));
                        calib.setShort("trackid", row, hits.getShort("trkID", i));
                        calib.setShort("pindex", row, (short) pindex);
                        calib.setShort("component", row, hits.getShort("component", i));
                        calib.setFloat("energy", row, hits.getFloat("energy", i));
                        calib.setFloat("time", row, hits.getFloat("time", i));
                        calib.setFloat("x", row, hits.getFloat("x", i));
                        calib.setFloat("y", row, hits.getFloat("y", i));
                        calib.setFloat("z", row, hits.getFloat("z", i));
                        calib.setFloat("tx", row, scin.getFloat("hx", sindex));
                        calib.setFloat("ty", row, scin.getFloat("hy", sindex));
                        calib.setFloat("tz", row, scin.getFloat("hz", sindex));
                        calib.setInt("pid", row, part.getInt("pid", pindex));
                        calib.setByte("charge", row, part.getByte("charge", pindex));
                        calib.setFloat("px", row, part.getFloat("px", pindex));
                        calib.setFloat("py", row, part.getFloat("py", pindex));
                        calib.setFloat("pz", row, part.getFloat("pz", pindex));
                        calib.setFloat("vx", row, part.getFloat("vx", pindex));
                        calib.setFloat("vy", row, part.getFloat("vy", pindex));
                        calib.setFloat("vz", row, part.getFloat("vz", pindex));
                        calib.setFloat("vt", row, part.getFloat("vt", pindex));
                        calib.setFloat("pathLength", row, scin.getFloat("path", sindex));
                        calib.setFloat("pathLengthThruBar", row, hits.getFloat("pathLengthThruBar", i));
                        calib.setFloat("chi2", row, trac.getFloat("chi2", tindex));
                        calib.setShort("NDF", row, trac.getShort("NDF", tindex));
                        calib.setInt("adc1", row, adcs.getInt("ADC", hits.getShort("adc_idx1", i)));
                        calib.setInt("adc2", row, adcs.getInt("ADC", hits.getShort("adc_idx2", i)));
                        calib.setInt("tdc1", row, tdcs.getInt("TDC", hits.getShort("tdc_idx1", i)));
                        calib.setInt("tdc2", row, tdcs.getInt("TDC", hits.getShort("tdc_idx2", i)));
                        row++;
                    }
                }
            }
            return calib;
        }
        return null;
    }
}
