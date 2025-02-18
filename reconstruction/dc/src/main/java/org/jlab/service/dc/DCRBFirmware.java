package org.jlab.service.dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.detector.base.DetectorType;
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
    
    public IndexedTable tt = null;
    public IndexedTable reverse = null;
    
    private void getReverseTT(int run) {
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
    
    public void getTables(DataEvent event) {
        if(reverse==null && event.hasBank("RUN::config")) {
            int run = event.getBank("RUN::config").getInt("run", 0);
            if(run>0) this.getReverseTT(run);
        }
    }
    
    public Map<Integer,Integer> getTrackMap(DataEvent event, String mode, H1F[] htracks) {
        String hits = "HitBasedTrkg::HBHitTrkId";
        String tracks = "HitBasedTrkg::HBTracks";
        String rtracks = "RECHB::Track";
        String particles = "RECHB::Particle";
        String tid = "tid";
        if(mode == "TB") {
            hits = "TimeBasedTrkg::TBHits";
            tracks = "TimeBasedTrkg::TBTracks";
            rtracks = "REC::Track";
            particles = "REC::Particle";
            tid = "trkID";
        }
        Map<Integer,Integer> onTrack = new HashMap<>();
        Map<Integer,Integer> goodTracks = new HashMap<>();
        if(event.hasBank(hits) && event.hasBank(tracks) && event.hasBank(rtracks) && event.hasBank(particles)) {
            DataBank pBank = event.getBank(particles);
            DataBank rBank = event.getBank(rtracks);
            DataBank tBank = event.getBank(tracks);
            DataBank hBank = event.getBank(hits);
            for(int i=0; i<rBank.rows(); i++) {
                if(rBank.getByte("detector",i)==DetectorType.DC.getDetectorId()) {
                    int pindex = rBank.getShort("pindex", i);
                    int index  = rBank.getShort("index", i);
                    int sector = rBank.getByte("sector", i);
                    int NDF    = rBank.getShort("NDF", i);
                    double chi2pid = pBank.getFloat("chi2pid", pindex);
                    double vz      = pBank.getFloat("vz", pindex);
                    if(Math.abs(chi2pid)<3 && Math.abs(vz+4)<10 && (sector==2 || sector==5)) {
                        goodTracks.put(tBank.getInt("id", index), index);
                        htracks[1].fill(NDF);
                        htracks[2].fill(vz);
                    }
                }
            }
            htracks[0].fill(goodTracks.size());
            for(int i=0; i<hBank.rows(); i++) {
                if(goodTracks.containsKey(hBank.getInt(tid, i))) {
                    onTrack.put((int) hBank.getShort("id",i), i);
                }
            }
        }
        return onTrack;
    }
    
    public void fill(H1F[] hits, Hit h, Hit hp) {
        this.fill(hits, h, hp, 1);
    }
    
    public void fill(H1F[] hits, Hit h, Hit hp, double weight) {
        hits[0].fill(h.tdc(), weight);
        hits[1].fill(h.tot(), weight);
        if(hp!=null) {
            if(h.tdc()>hp.tdc())
                hits[2].fill(h.tdc()-hp.tdc()-hp.tot(), weight);
            else
                hits[2].fill(hp.tdc()-h.tdc()-h.tot(), weight);
        }
        hits[3].fill(h.order(), weight);
    }
    
    
    public void plot(String canvasTitle, H1F[] mult, H1F[][]... hits) {
        int nrow = mult.length;
        int ncol = hits[0][0].length+1;
        TCanvas canvas = new TCanvas(canvasTitle, 1000, 800);
        canvas.divide(ncol, nrow);
        for(int r=0; r<nrow; r++) {
            canvas.cd(r*ncol);
            canvas.draw(mult[r]);
            canvas.getCanvas().getPad().getAxisY().setLog(true);    
            for(int j=0; j<ncol-1; j++) {
                canvas.cd(r*ncol+1+j);
                for(int k=0; k<hits.length; k++) {
                    if(k==0) 
                        canvas.draw(hits[k][r][j]);
                    else
                        canvas.draw(hits[k][r][j], "same");
                }
            }
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
        GStyle.getAxisAttributesX().setTitleFontSize(20);
        GStyle.getAxisAttributesX().setLabelFontSize(16);
        GStyle.getAxisAttributesY().setTitleFontSize(20);
        GStyle.getAxisAttributesY().setLabelFontSize(16);
        GStyle.getAxisAttributesZ().setLabelFontSize(12);
        GStyle.getAxisAttributesX().setLabelFontName("Arial");
        GStyle.getAxisAttributesY().setLabelFontName("Arial");
        GStyle.getAxisAttributesZ().setLabelFontName("Arial");
        GStyle.getAxisAttributesX().setTitleFontName("Arial");
        GStyle.getAxisAttributesY().setTitleFontName("Arial");
        GStyle.getAxisAttributesZ().setTitleFontName("Arial");
        GStyle.setGraphicsFrameLineWidth(1);
        GStyle.getH1FAttributes().setLineWidth(2);

        int[][] hmin = {{-100, -100, -100}, {-100, -100, -100},{-100, -100, -100}, {-50, -50, -50}};
        int[][] hmax = {{600, 1500, 1300}, {1200, 1200, 1200},{600, 600, 600}, {150, 150, 150}};
        String[] title = {"TDC (ns)", "ToT (ns)", "#DeltaT", "Order Type"};
        double[] tmin = {0, 0, -15};
        double[] tmax = {10, 40, 15};
        int[]    tbin = {10, 40, 100};
        String[] ttitle = {"Number of tracks", "NDF", "vz(cm)"};
        
        H1F[]   hMult = new H1F[3];
        H1F[]   hTrks = new H1F[3];
        H1F[][] hAll = new H1F[3][4];
        H1F[][] hFst = new H1F[3][4];
        H1F[][] hSnd = new H1F[3][4];
        H1F[][] hOn  = new H1F[3][4];
        H1F[][] hOff = new H1F[3][4];
        for(int r=0; r<3; r++) {
            hTrks[r] = new H1F("hTrks"+r, " ", tbin[r], tmin[r], tmax[r]);
            hTrks[r].setTitleX(ttitle[r]);
            hTrks[r].setOptStat("110");
            hMult[r] = new H1F("hMult"+r, "Region "+(r+1), 64, 0, 64);        
            hMult[r].setTitleX("Number of hits per group");
            for(int j=0; j<4; j++) {
               hAll[r][j] = new H1F("hAll"+r+j, "Region "+(r+1), 100, hmin[j][r], hmax[j][r]);
               hFst[r][j] = new H1F("hFst"+r+j, "Region "+(r+1), 100, hmin[j][r], hmax[j][r]);
               hSnd[r][j] = new H1F("hSnd"+r+j, "Region "+(r+1), 100, hmin[j][r], hmax[j][r]);
               hOn[r][j]  = new H1F("hOn"+r+j, "Region "+(r+1), 100, hmin[j][r], hmax[j][r]);
               hOff[r][j] = new H1F("hOff"+r+j, "Region "+(r+1), 100, hmin[j][r], hmax[j][r]);
               hAll[r][j].setTitleX(title[j]);
               hFst[r][j].setTitleX(title[j]);
               hFst[r][j].setLineColor(2);
               hSnd[r][j].setTitleX(title[j]);
               hSnd[r][j].setLineColor(3);
               hOn[r][j].setTitleX(title[j]);
               hOff[r][j].setTitleX(title[j]);
            }
        }
        DCRBFirmware dcrb = new DCRBFirmware();
        
        HipoDataSource reader = new HipoDataSource();
        reader.open("/Users/devita/dcrb/rec_alltdc.hipo");
        while(reader.hasEvent()) {
            DataEvent event = reader.getNextEvent();
            
            dcrb.getTables(event);
             
            Map<Integer,Integer> onHBtrack = dcrb.getTrackMap(event, "HB", hTrks);
            
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
                        int crate = dcrb.reverse.getIntValue("crate",   sector, layer, wire, order%10);
                        int slot  = dcrb.reverse.getIntValue("slot",    sector, layer, wire, order%10);
                        int chan  = dcrb.reverse.getIntValue("channel", sector, layer, wire, order%10);
                        int group = chan/16;
                        
                        Hit h = new Hit(i, sector, layer, wire, order, time, tot);
                        if(!wires.containsKey(h.hashCode()))
                                wires.put(h.hashCode(),new ArrayList<>());
                        wires.get(h.hashCode()).add(h);
                        
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
                        Hit hpre = null;
                        Hit hpost = null;
                        if(i>0) hpre = hits.get(i-1);
                        if(i<hits.size()-1) hpost = hits.get(i+1);
                        dcrb.fill(hAll[h.region()-1], h, hpost);
                        if(i==0) {
                            dcrb.fill(hFst[h.region()-1], h, hpost);
                        }
                        else if(i>0 && hits.get(0).tot()<50 && hits.get(i).tot()>50) {
                            dcrb.fill(hSnd[h.region()-1], h, hpre, 10);                        
                        }
                        if(onHBtrack.containsKey(h.index()+1)) {
                            dcrb.fill(hOn[h.region()-1], h, hpost);
                        }
                        else {
                            dcrb.fill(hOff[h.region()-1], h, hpre);
                        }
                    }
                }
                for(int key : multiplicity.keySet()) {
                    int layer = dcrb.tt.getIntValue("layer", Hit.getSector(key), Hit.getLayer(key),Hit.getComponent(key));
                    int r = (layer-1)/12;
//                    if(multiplicity.get(key)>16)
//                        System.out.println(key + " " + Hit.getSector(key) + " " +Hit.getLayer(key) + " " +Hit.getComponent(key));
                    hMult[r].fill(multiplicity.get(key));
                }
            }
        }
        
        dcrb.plot("All Hits", hMult, hAll, hFst, hSnd);
        dcrb.plot("Hits on track", hTrks, hOn);
        dcrb.plot("Hits off track", hTrks, hOff);
    }
}
