package org.jlab.service.dc;

import java.util.HashMap;
import java.util.Map;
import org.jlab.detector.base.DetectorDescriptor;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.groot.data.H1F;
import org.jlab.groot.ui.TCanvas;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.utils.groups.IndexedTable;

/**
 *
 * @author devita
 */
public class DCRBFirmware {
    
    private static IndexedTable reverse = null;
    
    private static IndexedTable getReverseTT(int run) {
        ConstantsManager manager = new ConstantsManager();
        manager.init("/daq/tt/dc");
        IndexedTable tt = manager.getConstants(run, "/daq/tt/dc");
        IndexedTable reverse = new IndexedTable(4, "crate/I:slot/I:channel/I");
        for(int row=0; row<tt.getRowCount(); row++) {
            int crate   = Integer.valueOf((String)tt.getValueAt(row,0));
            int slot    = Integer.valueOf((String)tt.getValueAt(row,1));
            int channel = Integer.valueOf((String)tt.getValueAt(row,2));
            int sector  = tt.getIntValue("sector",    crate,slot,channel);
            int layer   = tt.getIntValue("layer",     crate,slot,channel);
            int comp    = tt.getIntValue("component", crate,slot,channel);
            int order   = tt.getIntValue("order",     crate,slot,channel);
            reverse.addEntry(sector, layer, comp, order);
            reverse.setIntValue(crate,   "crate",   sector, layer, comp, order);
            reverse.setIntValue(slot,    "slot",    sector, layer, comp, order);
            reverse.setIntValue(channel, "channel", sector, layer, comp, order);
        }
        return reverse;
    }

    public static void main(String[] args) {
        
        int[][] window = {{600, 1500, 1300}, {1200, 1200, 1200}, {200, 200, 200}};
        String[] title = {"TDC (ns)", "ToT (ns)", "Order Type"};
        
        H1F hMult = new H1F("hMult", "Hit multiplicity per group", 100, 0, 100);
        
        H1F[][] hAll = new H1F[3][3];
        H1F[][] hOn  = new H1F[3][3];
        H1F[][] hOff = new H1F[3][3];
        for(int r=0; r<3; r++) {
            for(int j=0; j<3; j++) {
               hAll[r][j] = new H1F("hOn"+r+j, "All hits - R"+(r+1), 100, 0, window[j][r]);
               hOn[r][j]  = new H1F("hOn"+r+j, "Hits on track - R"+(r+1), 100, 0, window[j][r]);
               hOff[r][j] = new H1F("hOff"+r+j, "Hits off track - R"+(r+1), 100, 0, window[j][r]);
               hOn[r][j].setTitleX(title[j]);
               hOff[r][j].setTitleX(title[j]);
            }
        }
        
        HipoDataSource reader = new HipoDataSource();
        reader.open("/Users/devita/dcrb/rec_alltdc.hipo");
        while(reader.hasEvent()) {
            DataEvent event = reader.getNextEvent();
            
            if(reverse==null && event.hasBank("RUN::config")) {
                int run = event.getBank("RUN::config").getInt("run", 0);
                if(run>0) reverse = getReverseTT(run);
            }
            
            Map<Integer,Integer> onHBtrack = new HashMap<>();
            if(event.hasBank("HitBasedTrkg::HBHitTrkId")) {
                DataBank hits = event.getBank("HitBasedTrkg::HBHitTrkId");
                for(int i=0; i<hits.rows(); i++) {
                    if(hits.getInt("tid", i)>-1)
                        onHBtrack.put((int) hits.getShort("id",i), i);
                }
            }
            
            Map<Integer,Integer> onTBtrack = new HashMap<>();
            if(event.hasBank("TimeBasedTrkg::TBHits")) {
                DataBank hits = event.getBank("TimeBasedTrkg::TBHits");
                for(int i=0; i<hits.rows(); i++) {
                    if(hits.getInt("trkID", i)>-1)
                        onTBtrack.put((int) hits.getShort("id",i), i);
                }
            }
            
            Map<Integer, Integer> multiplicity = new HashMap();
            if(event.hasBank("DC::tdc")) {
                DataBank tdc = event.getBank("DC::tdc");
                for(int i=0; i<tdc.rows(); i++) {
                    int sector = tdc.getByte("sector", i);
                    int layer  = tdc.getByte("layer", i);
                    int wire   = tdc.getShort("component", i);
                    int order  = tdc.getByte("order", i);
                    int time   = tdc.getInt("TDC", i);
                    int tot    = tdc.getInt("width", i);
                    if((sector==2 || sector==5) /*&& order==0*/) {
                        int r = (layer-1)/12;
                        int crate = reverse.getIntValue("crate",   sector, layer, wire, order);
                        int slot  = reverse.getIntValue("slot",    sector, layer, wire, order);
                        int chan  = reverse.getIntValue("channel", sector, layer, wire, order);
                        int group = chan/16;
                        DetectorDescriptor desc = new DetectorDescriptor("DC");
                        desc.setCrateSlotChannel(crate, slot, group);
                        desc.setSectorLayerComponent(sector, layer, group);
                        if(multiplicity.containsKey(desc.getHashCode()))
                            multiplicity.replace(desc.getHashCode(), multiplicity.get(desc.getHashCode())+1);
                        else
                            multiplicity.put(desc.getHashCode(), 1);
                        
                        hAll[r][0].fill(time);
                        hAll[r][1].fill(tot);
                        hAll[r][2].fill(order);
                        if(onHBtrack.containsKey(i+1)) {
                            hOn[r][0].fill(time);
                            hOn[r][1].fill(tot);
                            hOn[r][2].fill(order);
                        }
                        else {
                            hOff[r][0].fill(time);
                            hOff[r][1].fill(tot);
                            hOff[r][2].fill(order);
                        }
                    }
                }
                for(int m : multiplicity.values())
                    hMult.fill(m);
            }
        }
        
        TCanvas canvas = new TCanvas("ToT", 1200, 800);
        canvas.divide(9, 4);
        canvas.cd(0);
        canvas.draw(hMult);
        canvas.getCanvas().getPad().getAxisY().setLog(true);
        for(int r=0; r<3; r++) {
            for(int j=0; j<3; j++) {
                canvas.cd(9+r*9+j);
                canvas.draw(hAll[r][j]);
                canvas.cd(9+r*9+j+3);
                canvas.draw(hOn[r][j]);
                canvas.cd(9+r*9+j+6);
                canvas.draw(hOff[r][j]);
            }
        }
    }
}
