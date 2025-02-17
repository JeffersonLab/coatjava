package org.jlab.service.dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.groot.base.GStyle;
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
    
    private static IndexedTable tt = null;
    private static IndexedTable reverse = null;
    
    private static void getReverseTT(int run) {
        ConstantsManager manager = new ConstantsManager();
        manager.init("/daq/tt/dc");
        tt = manager.getConstants(run, "/daq/tt/dc");
        reverse = new IndexedTable(4, "crate/I:slot/I:channel/I");
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
    }

    public static class Hit implements Comparable {
        
        private int index;
        private int sector;
        private int layer;
        private int wire;
        private int order;
        private int tdc;
        private int tot;

        public Hit(int index, int sector, int layer, int wire, int order, int tdc, int tot) {
            this.index = index;
            this.sector = sector;
            this.layer = layer;
            this.wire = wire;
            this.order = order;
            this.tdc = tdc;
            this.tot = tot;
        }

        public int index() {
            return index;
        }

        public int sector() {
            return sector;
        }

        public int layer() {
            return layer;
        }

        public int region() {
            return (layer-1)/12+1;
        }

        public int wire() {
            return wire;
        }

        public int order() {
            return order;
        }

        public int tdc() {
            return tdc;
        }

        public int tot() {
            return tot;
        }
        
        public int hashCode(){
            return  generateHashCode(sector, layer, wire);
        }
        
        public static int generateHashCode(int s, int l, int c){
            return  ((s<<24)&0xFF000000)|
                    ((l<<16)&0x00FF0000)|(c&0x0000FFFF);
        }
        
        public static int getSector(int hashCode) {
            return (hashCode&0xFF000000)>>24;
        }
        
        public static int getLayer(int hashCode) {
            return (hashCode&0x00FF0000)>>16;
        }
        
        public static int getComponent(int hashCode) {
            return (hashCode&0x0000FFFF);
        }
        
        @Override
        public int compareTo(Object o) {
            return this.tdc<((Hit)o).tdc() ? -1 :1;
        }
        
    }
    
    public static void main(String[] args) {
        
        GStyle.getH1FAttributes().setOptStat("10");
        int[][] hmin = {{-100, -100, -100}, {-100, -100, -100}, {-50, -50, -50}};
        int[][] hmax = {{600, 1500, 1300}, {1200, 1200, 1200}, {150, 150, 150}};
        String[] title = {"TDC (ns)", "ToT (ns)", "Order Type"};
        
        H1F[]   hMult = new H1F[3];
        H1F[]   hTrks = new H1F[3];
        H1F[][] hAll = new H1F[3][3];
        H1F[][] hFst = new H1F[3][3];
        H1F[][] hSnd = new H1F[3][3];
        H1F[][] hOn  = new H1F[3][3];
        H1F[][] hOff = new H1F[3][3];
        for(int r=0; r<3; r++) {
            hMult[r] = new H1F("hMult"+r, "Hit multiplicity per group - R"+(r+1), 64, 0, 64);        
            hTrks[r] = new H1F("hTrks"+r, "Track multiplicity", 10, 0, 10);
            hTrks[r].setOptStat("110");
            for(int j=0; j<3; j++) {
               hAll[r][j] = new H1F("hAll"+r+j, "All hits - R"+(r+1), 100, -100, hmax[j][r]);
               hFst[r][j] = new H1F("hFst"+r+j, "First hit - R"+(r+1), 100, -100, hmax[j][r]);
               hSnd[r][j] = new H1F("hSnd"+r+j, "First hit - R"+(r+1), 100, -100, hmax[j][r]);
               hOn[r][j]  = new H1F("hOn"+r+j, "Hits on track - R"+(r+1), 100, -100, hmax[j][r]);
               hOff[r][j] = new H1F("hOff"+r+j, "Hits off track - R"+(r+1), 100, -100, hmax[j][r]);
               hAll[r][j].setTitleX(title[j]);
               hFst[r][j].setTitleX(title[j]);
               hFst[r][j].setLineColor(2);
               hSnd[r][j].setTitleX(title[j]);
               hSnd[r][j].setLineColor(3);
               hOn[r][j].setTitleX(title[j]);
               hOff[r][j].setTitleX(title[j]);
            }
        }
        
        HipoDataSource reader = new HipoDataSource();
        reader.open("/Users/devita/dcrb/rec_tdccut_tot50.hipo");
        while(reader.hasEvent()) {
            DataEvent event = reader.getNextEvent();
            
            if(reverse==null && event.hasBank("RUN::config")) {
                int run = event.getBank("RUN::config").getInt("run", 0);
                if(run>0) getReverseTT(run);
            }
            
            if(event.hasBank("HitBasedTrkg::HBTracks")) {
                int ntracks = 0;
                DataBank tracks = event.getBank("HitBasedTrkg::HBTracks");
                for(int i=0; i<tracks.rows(); i++) {
                    int sector = tracks.getByte("sector", i);
                    if(sector==2 || sector==5)
                        ntracks++;
                }
                hTrks[0].fill(ntracks);
            }
            if(event.hasBank("TimeBasedTrkg::TBTracks")) {
                int ntracks = 0;
                DataBank tracks = event.getBank("TimeBasedTrkg::TBTracks");
                for(int i=0; i<tracks.rows(); i++) {
                    int sector = tracks.getByte("sector", i);
                    if(sector==2 || sector==5)
                        ntracks++;
                }
                hTrks[1].fill(ntracks);
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
            Map<Integer, List<Hit>> wires = new HashMap();
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
                        int crate = reverse.getIntValue("crate",   sector, layer, wire, order%10);
                        int slot  = reverse.getIntValue("slot",    sector, layer, wire, order%10);
                        int chan  = reverse.getIntValue("channel", sector, layer, wire, order%10);
                        int group = chan/16;
                        
                        Hit h = new Hit(i, sector, layer, wire, order, time, tot);
                        if(!wires.containsKey(h.hashCode()))
                                wires.put(h.hashCode(),new ArrayList<>());
                        wires.get(h.hashCode()).add(h);
                        
                        int r =h.region()-1;
                        hAll[r][0].fill(time);
                        hAll[r][1].fill(tot);
                        hAll[r][2].fill(order);
                        
                        int hash = Hit.generateHashCode(crate, slot, group*16); 
                        if(multiplicity.containsKey(hash))
                            multiplicity.replace(hash, multiplicity.get(hash)+1);
                        else
                            multiplicity.put(hash, 1);
                        
                    }
                }

                for(List<Hit> hits : wires.values()) {
                    Collections.sort(hits);
                    for(int i=0; i<hits.size(); i++) {
                        Hit h = hits.get(i);
                        if(i==0) {
                            hFst[h.region()-1][0].fill(h.tdc());
                            hFst[h.region()-1][1].fill(h.tot());
                            hFst[h.region()-1][2].fill(h.order());
                        }
                        else if(hits.get(0).tot()<50) {
                            hSnd[h.region()-1][0].fill(h.tdc());
                            hSnd[h.region()-1][1].fill(h.tot());
                            hSnd[h.region()-1][2].fill(h.order());                            
                        }
                        if(onHBtrack.containsKey(h.index()+1)) {
                            hOn[h.region()-1][0].fill(h.tdc());
                            hOn[h.region()-1][1].fill(h.tot());
                            hOn[h.region()-1][2].fill(h.order());
                        }
                        else {
                            hOff[h.region()-1][0].fill(h.tdc());
                            hOff[h.region()-1][1].fill(h.tot());
                            hOff[h.region()-1][2].fill(h.order());
                        }
                    }
                }
                for(int key : multiplicity.keySet()) {
                    int layer = tt.getIntValue("layer", Hit.getSector(key), Hit.getLayer(key),Hit.getComponent(key));
                    int r = (layer-1)/12;
//                    if(multiplicity.get(key)>16)
//                        System.out.println(key + " " + Hit.getSector(key) + " " +Hit.getLayer(key) + " " +Hit.getComponent(key));
                    hMult[r].fill(multiplicity.get(key));
                }
            }
        }
        
        int nrow = 3;
        int ncol = 4;
        TCanvas canvas1 = new TCanvas("ToT1", 1000, 800);
        canvas1.divide(ncol, nrow);
        for(int r=0; r<nrow; r++) {
            canvas1.cd(r*ncol);
            canvas1.draw(hMult[r]);
            canvas1.getCanvas().getPad().getAxisY().setLog(true);    
            for(int j=0; j<3; j++) {
                canvas1.cd(r*ncol+1+j);
                canvas1.draw(hAll[r][j]);
                canvas1.draw(hFst[r][j], "same");
                canvas1.draw(hSnd[r][j], "same");
            }
        }
        ncol = 7;
        TCanvas canvas2 = new TCanvas("ToT2", 1200, 800);
        canvas2.divide(ncol, nrow);
        for(int r=0; r<nrow; r++) {
            canvas2.cd(r*ncol);
            canvas2.draw(hTrks[r]);
            canvas2.getCanvas().getPad().getAxisY().setLog(true);    
            for(int j=0; j<3; j++) {
                canvas2.cd(r*ncol+1+j);
                canvas2.draw(hOn[r][j]);
                canvas2.cd(r*ncol+4+j);
                canvas2.draw(hOff[r][j]);
            }
        }
    }
}
