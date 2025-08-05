package org.jlab.calibration.detectors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author devita
 */
public class DCBankBuilder extends CalibBankBuilder {

    private static final int MINCLUSTERSIZE = 5;
    private static final double MAXRESIDUAL = 0.1; // cm
    private static final double CHI2PIDCUT  = 5; // cm
    
    public DCBankBuilder() {
        super(DetectorType.DC);
        super.init("TimeBasedTrkg::TBHits","TimeBasedTrkg::TBHits","TimeBasedTrkg::TBTracks", 
                   "REC::Track", "REC::Particle");
    }
    @Override
    public boolean isGoodEvent(DataEvent event) {
        
        DataBank part = event.getBank("REC::Particle"); 
        
        if(part.rows()<2 ||
           part.getInt("pid", 0)!=11 || 
           (part.getFloat("chi2pid", 0))>CHI2PIDCUT || 
           ((int) (Math.abs(part.getShort("status", 0))/1000))!=2)
                return false;
        
        boolean hasHadron = false;
        for(int i=1; i<part.rows(); i++) {
            if(part.getByte("charge", i)!=0 &&
               Math.abs(part.getFloat("chi2pid", i))<CHI2PIDCUT && 
               ((int) part.getShort("status", i)/1000)==2) {
                hasHadron = true;
                break;
            }
        }
        return hasHadron;
    }

    @Override
    public DataBank buildCalibBank(DataEvent event) {
        DataBank part = event.getBank("REC::Particle");
        DataBank trac = event.getBank("REC::Track");
        DataBank tbts = event.getBank("TimeBasedTrkg::TBTracks");
        DataBank tbhs = event.getBank("TimeBasedTrkg::TBHits");


        Map<Integer,Integer> pinds = new HashMap<>();
        for(int it=0; it<trac.rows(); it++) {
            int detector = trac.getByte("detector", it);
            if(DetectorType.getType(detector)==DetectorType.DC) {
                int index   = trac.getShort("index", it);                
                int pindex  = trac.getShort("pindex", it);                
                int trackid = tbts.getShort("id", index);               
                pinds.put(trackid, pindex);
            }
        }

        Map<Integer,List<Integer>> clusters = new HashMap<>();
        for(int i=0; i<tbhs.rows(); i++) {
            int cluster = tbhs.getShort("clusterID", i);
            int trackid = tbhs.getByte("trkID", i);
            double tflight = tbhs.getFloat("TFlight", i);
            double fitresi = tbhs.getFloat("fitResidual", i);
            if(cluster>0 && 
               trackid>0 && 
               tflight>0 &&
               fitresi<MAXRESIDUAL) {
                if(!clusters.containsKey(cluster))
                    clusters.put(cluster, new ArrayList());
                clusters.get(cluster).add(i);
            }
        }
        
        int ngood = 0;
        for(Integer key : clusters.keySet()) {
            if(clusters.get(key).size()>=MINCLUSTERSIZE)
                ngood += clusters.get(key).size();
            else
                clusters.get(key).clear();
        }

        if(ngood>0) {
            DataBank calib = event.createBank("DC::calib", ngood);

            int row = 0;
            for(List<Integer> cluster : clusters.values()) {
                if(cluster.isEmpty()) continue;
                for(int i : cluster) {
                    calib.setShort("id", row, tbhs.getShort("id",i));
                    calib.setShort("status", row, tbhs.getShort("status",i));
                    calib.setByte("superlayer", row, tbhs.getByte("superlayer",i));
                    calib.setByte("layer", row, tbhs.getByte("layer",i));
                    calib.setByte("sector", row, tbhs.getByte("sector",i));
                    calib.setShort("wire", row, tbhs.getShort("wire",i));
                    calib.setInt("TDC", row, tbhs.getInt("TDC",i));
                    calib.setByte("jitter", row, tbhs.getByte("jitter",i));
                    calib.setFloat("time", row, tbhs.getFloat("time",i));
                    calib.setFloat("doca", row, tbhs.getFloat("doca",i));
                    calib.setFloat("docaError", row, tbhs.getFloat("docaError",i));
                    calib.setFloat("trkDoca", row, tbhs.getFloat("trkDoca",i));
                    calib.setFloat("timeResidual", row, tbhs.getFloat("timeResidual",i));
                    calib.setFloat("fitResidual", row, tbhs.getFloat("fitResidual",i));
                    calib.setFloat("DAFWeight", row, tbhs.getFloat("DAFWeight",i));
                    calib.setByte("LR", row, tbhs.getByte("LR",i));
                    calib.setFloat("X", row, tbhs.getFloat("X",i));
                    calib.setFloat("Z", row, tbhs.getFloat("Z",i));
                    calib.setFloat("B", row, tbhs.getFloat("B",i));
                    calib.setFloat("Alpha", row, tbhs.getFloat("Alpha",i));
                    calib.setFloat("TProp", row, tbhs.getFloat("TProp",i));
                    calib.setFloat("TFlight", row, tbhs.getFloat("TFlight",i));
                    calib.setFloat("T0", row, tbhs.getFloat("T0",i));
                    calib.setFloat("TStart", row, tbhs.getFloat("TStart",i));
                    calib.setFloat("beta", row, tbhs.getFloat("beta",i));
                    calib.setFloat("tBeta", row, tbhs.getFloat("tBeta",i));
                    calib.setFloat("dDoca", row, tbhs.getFloat("dDoca",i));
                    calib.setShort("clusterID", row, tbhs.getShort("clusterID",i));
                    calib.setByte("trkID", row, tbhs.getByte("trkID",i));
                    row++;
                }
            }
            return calib;
        }
        return null;
    }
}
