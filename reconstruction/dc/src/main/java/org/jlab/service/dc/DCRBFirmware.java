package org.jlab.service.dc;

import java.util.HashMap;
import java.util.Map;
import org.jlab.groot.data.H1F;
import org.jlab.groot.ui.TCanvas;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;

/**
 *
 * @author devita
 */
public class DCRBFirmware {
    


    public static void main(String[] args) {
        
        int[][] window = {{600, 1500, 1300}, {1200, 1200, 1200}};
        H1F[][] hOn  = new H1F[3][2];
        H1F[][] hOff = new H1F[3][2];
        for(int r=0; r<3; r++) {
            for(int j=0; j<2; j++) {
               String title = j==0 ? "TDC (ns)" : "ToT (ns)";
               hOn[r][j]  = new H1F("hOn"+r+j, "Hits on track - R"+(r+1), 100, 0, window[j][r]);
               hOff[r][j] = new H1F("hOff"+r+j, "Hits off track - R"+(r+1), 100, 0, window[j][r]);
               hOn[r][j].setTitleX(title);
               hOff[r][j].setTitleX(title);
            }
        }
        
        HipoDataSource reader = new HipoDataSource();
        reader.open("/Users/devita/NetBeansProjects/coatjava/aaa.rec.hipo");
        while(reader.hasEvent()) {
            DataEvent event = reader.getNextEvent();
            
            Map<Integer,Integer> ontrack = new HashMap<>();
            if(event.hasBank("TimeBasedTrkg::TBHits")) {
                DataBank hits = event.getBank("TimeBasedTrkg::TBHits");
                for(int i=0; i<hits.rows(); i++) {
                    if(hits.getShort("trkID", i)>-1)
                        ontrack.put((int) hits.getShort("id",i), i);
                }
            }
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
                        if(ontrack.containsKey(i+1)) {
                            hOn[r][0].fill(time);
                            hOn[r][1].fill(tot);
                        }
                        else {
                            hOff[r][0].fill(time);
                            hOff[r][1].fill(tot);
                        }
                    }
                }
            }
        }
        
        TCanvas canvas = new TCanvas("ToT", 800, 800);
        canvas.divide(4, 3);
        for(int r=0; r<3; r++) {
            for(int j=0; j<2; j++) {
                canvas.cd(r*4+j);
                canvas.draw(hOn[r][j]);
                canvas.cd(r*4+j+2);
                canvas.draw(hOff[r][j]);
            }
        }
    }
}
