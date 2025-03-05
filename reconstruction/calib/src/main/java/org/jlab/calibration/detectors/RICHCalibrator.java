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
public class RICHCalibrator extends DetectorCalibrator {
        
    public RICHCalibrator() {
        super(DetectorType.RICH);
        super.init("RICH::Hit", "RICH::Photon", "REC::Particle");
    }
    @Override
    public boolean isGoodEvent(DataEvent event) {
        
        DataBank part = event.getBank("REC::Particle");                
        if(part.rows()<1 ||
           part.getInt("pid", 0)!=11 || 
           ((int) (Math.abs(part.getShort("status", 0))/1000))!=2)
                return false;
        
        return true;
    }

    @Override
    public DataBank buildCalibBank(DataEvent event) {
        DataBank part = event.getBank("REC::Particle"); ;
        DataBank hits = event.getBank("RICH::Hit");
        DataBank phos = event.getBank("RICH::Photon");
            
        List<Integer> goodPhotons = new ArrayList<>();
        for(int i=0; i<phos.rows(); i++) {
            int pid    = phos.getInt("hypo_pid", i);
            int pindex = phos.getByte("pindex", i);
            if(part.getInt("pid", pindex)==pid) goodPhotons.add(i);
        }

        List<Integer> goodClusters = new ArrayList<>();
        for(int i=0; i<hits.rows(); i++) {
            int cluster = hits.getShort("cluster", i);
            if(cluster>0) goodClusters.add(i);
        }

        if(!goodPhotons.isEmpty() || !goodClusters.isEmpty()) {
            DataBank calib = event.createBank("RICH::calib", goodPhotons.size()+goodClusters.size());

            int row =0;
            for(int i : goodPhotons) {
                int hindex = phos.getShort("hindex", i);
                calib.setByte( "pindex",       row, phos.getByte("pindex", i));
                calib.setShort("hindex",       row, (short) hindex);
                calib.setByte( "sector",       row, (byte) hits.getShort("sector", hindex));
                calib.setShort("pmt",          row, hits.getShort("pmt", hindex));
                calib.setShort("anode",        row, hits.getShort("anode", hindex));
                calib.setShort("status",       row, hits.getShort("status", hindex));
                calib.setByte( "used",         row, phos.getByte("used", i));
                calib.setFloat("x",            row, hits.getFloat("x", hindex));
                calib.setFloat("y",            row, hits.getFloat("y", hindex));
                calib.setFloat("z",            row, hits.getFloat("z", hindex));
                calib.setFloat("time",         row, hits.getFloat("time", hindex));
                calib.setFloat("rawtime",      row, hits.getFloat("rawtime", hindex));
                calib.setShort("duration",     row, hits.getShort("duration", hindex));
                calib.setFloat("traced_the",   row, phos.getFloat("traced_the", i));
                calib.setFloat("traced_phi",   row, phos.getFloat("traced_phi", i));
                calib.setFloat("traced_hitx",  row, phos.getFloat("traced_hitx", i));
                calib.setFloat("traced_hity",  row, phos.getFloat("traced_hity", i));
                calib.setFloat("traced_hitz",  row, phos.getFloat("traced_hitz", i));
                calib.setFloat("traced_path",  row, phos.getFloat("traced_path", i));
                calib.setFloat("traced_time",  row, phos.getFloat("traced_time", i));
                calib.setFloat("traced_stime", row, phos.getFloat("traced_stime", i));
                calib.setShort("traced_nrfl",  row, phos.getShort("traced_nrfl", i));
                calib.setShort("traced_nrfr",  row, phos.getShort("traced_nrfr", i));
                calib.setShort("traced_1rfl",  row, phos.getShort("traced_1rfl", i));
                calib.setInt("traced_layers",  row, phos.getInt("traced_layers", i));
                calib.setInt("traced_compos",  row, phos.getInt("traced_compos", i));
                calib.setFloat("traced_etaC",  row, phos.getFloat("traced_etaC", i));
                calib.setFloat("etac_ref",     row, phos.getFloat("etac_ref", i));
                calib.setFloat("etac_rms",     row, phos.getFloat("etac_rms", i));
                calib.setFloat("prob",         row, phos.getFloat("prob", i));
                row++;
            }
            for(int i : goodClusters) {
                calib.setShort("hindex",       row, (short) i);
                calib.setByte( "sector",       row, (byte) hits.getShort("sector", i));
                calib.setShort("pmt",          row, hits.getShort("pmt", i));
                calib.setShort("anode",        row, hits.getShort("anode", i));
                calib.setShort("status",       row, hits.getShort("status", i));
                calib.setByte( "used",         row, (byte) 2);
                calib.setFloat("y",            row, hits.getFloat("y", i));
                calib.setFloat("z",            row, hits.getFloat("z", i));
                calib.setFloat("time",         row, hits.getFloat("time", i));
                calib.setFloat("rawtime",      row, hits.getFloat("rawtime", i));
                calib.setShort("duration",     row, hits.getShort("duration", i));
                row++;
            }
            return calib;
        }
        return null;
    }
}
