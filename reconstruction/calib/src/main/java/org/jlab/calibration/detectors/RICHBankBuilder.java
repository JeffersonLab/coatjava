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
public class RICHBankBuilder extends CalibBankBuilder {
        
    public RICHBankBuilder() {
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
        DataBank part = event.getBank("REC::Particle");
        DataBank rich = event.getBank("RICH::Particle");        
        DataBank hits = event.getBank("RICH::Hit");
        DataBank clus = event.getBank("RICH::Cluster");
        DataBank phos = event.getBank("RICH::Photon");
            
        List<Integer> goodPhotons = new ArrayList<>();
        for(int i=0; i<phos.rows(); i++) {
            int pid    = phos.getInt("hypo_pid", i);
            int pindex = phos.getByte("pindex", i);
            if(part.getInt("pid", pindex)==pid) goodPhotons.add(i);
        }

        Map<Integer,Integer> part2Rich = new HashMap<>();
        Map<Integer,Integer> clus2Rich = new HashMap<>();
        for(int i=0; i<rich.rows(); i++) {
            int pindex = rich.getByte("pindex", i);
            int hindex = rich.getShort("hindex", i);
            part2Rich.put(pindex, i);
            clus2Rich.put(hindex, i);
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
                int pindex = phos.getByte("pindex", i);
                calib.setByte( "pindex",       row, (byte) pindex);
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
                calib.setByte("emilay",        row, rich.getByte("emilay", part2Rich.get(pindex)));
                calib.setByte("emico",         row, rich.getByte("emico", part2Rich.get(pindex)));
                calib.setShort("emqua",        row, rich.getShort("emqua", part2Rich.get(pindex)));
                calib.setFloat("start_time",   row, phos.getFloat("start_time", i));
                calib.setFloat("traced_the",   row, phos.getFloat("traced_the", i));
                calib.setFloat("traced_phi",   row, phos.getFloat("traced_phi", i));
                calib.setFloat("traced_hitx",  row, phos.getFloat("traced_hitx", i));
                calib.setFloat("traced_hity",  row, phos.getFloat("traced_hity", i));
                calib.setFloat("traced_hitz",  row, phos.getFloat("traced_hitz", i));
                calib.setFloat("traced_path",  row, phos.getFloat("traced_path", i));
                calib.setFloat("traced_time",  row, phos.getFloat("traced_time", i));
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
                int cluster = hits.getShort("cluster", i);
                calib.setShort("hindex",       row, (short) i);
                calib.setByte( "sector",       row, (byte) hits.getShort("sector", i));
                calib.setShort("pmt",          row, hits.getShort("pmt", i));
                calib.setShort("anode",        row, hits.getShort("anode", i));
                calib.setShort("status",       row, hits.getShort("status", i));
                calib.setByte( "used",         row, (byte) 2);
                calib.setFloat("x",            row, hits.getFloat("x", i));
                calib.setFloat("y",            row, hits.getFloat("y", i));
                calib.setFloat("z",            row, hits.getFloat("z", i));
                calib.setFloat("time",         row, hits.getFloat("time", i));
                calib.setFloat("rawtime",      row, hits.getFloat("rawtime", i));
                calib.setShort("duration",     row, hits.getShort("duration", i));
                if(clus2Rich.containsKey(cluster-1)) {
                    int rindex  = clus2Rich.get(cluster-1);
                    calib.setByte("pindex",    row, rich.getByte("pindex", rindex));
                    calib.setFloat("mchi2",    row, rich.getFloat("mchi2", rindex));
                    calib.setShort("msize",    row, clus.getShort("size", cluster-1));
                }
                else
                    calib.setByte("pindex",    row, (byte) -1);
                row++;
            }
            return calib;
        }
        return null;
    }
}
