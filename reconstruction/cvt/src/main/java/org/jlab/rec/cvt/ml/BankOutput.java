/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.ml;

import java.util.ArrayList;
import java.util.List;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.bmt.BMTType;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.rec.cvt.track.Seed;
import org.jlab.rec.cvt.track.Track;

/**
 *
 * @author ziegler
 */
public class BankOutput {
    public static DataBank fillHitsBank(DataEvent event, List<ArrayList<Hit>>hits, String bankName) {
        if (hits == null || hits.isEmpty()) return null;
        DataBank bank = event.createBank(bankName, hits.get(0).size()+hits.get(1).size());
        int index=0;
        for(int i = 0; i < hits.size(); i++) {
            for(int j = 0; j < hits.get(i).size(); j++) {
                bank.setShort("id", index, (short) hits.get(i).get(j).getId());
                bank.setShort("sidx", index, (short) hits.get(i).get(j).getSeedBankRow());
                bank.setShort("tidx", index, (short) hits.get(i).get(j).getTrackBankRow());
                bank.setShort("recsid", index, (short) hits.get(i).get(j).getAssociatedSeedID());
                bank.setShort("rectid", index, (short) hits.get(i).get(j).getAssociatedTrackID());
                bank.setShort("mctid", index, (short) hits.get(i).get(j).getAssociateMCTrkId());
                bank.setByte("sector", index, (byte) hits.get(i).get(j).getSector());
                int layer = hits.get(i).get(j).getLayer();
                if(i>0) layer+=6;
                bank.setByte("layer", index, (byte) layer);
                bank.setByte("type", index, getType(hits.get(i).get(j)));
                bank.setShort("strip", index, (short) hits.get(i).get(j).getStrip().getStrip());
                bank.setByte("order", index, (byte) hits.get(i).get(j).MCstatus);
                bank.setShort("cid", index, (short) hits.get(i).get(j).getAssociatedClusterID());
                bank.setFloat("cweight", index, (float) hits.get(i).get(j).getCweight());
                bank.setFloat("sweight", index, (float) hits.get(i).get(j).getSweight());
                if(hits.get(i).get(j).getDetector()==DetectorType.BST ||
                        (hits.get(i).get(j).getDetector()==DetectorType.BMT 
                        && hits.get(i).get(j).getType()==BMTType.Z)) {
                    bank.setFloat("x1",   index,  (float) hits.get(i).get(j).getStrip().getLine().origin().x()/10);
                    bank.setFloat("y1",   index,  (float) hits.get(i).get(j).getStrip().getLine().origin().y()/10);
                    bank.setFloat("z1",   index,  (float) hits.get(i).get(j).getStrip().getLine().origin().z()/10);
                    bank.setFloat("x2",   index,  (float) hits.get(i).get(j).getStrip().getLine().end().x()/10);
                    bank.setFloat("y2",   index,  (float) hits.get(i).get(j).getStrip().getLine().end().y()/10);
                    bank.setFloat("z2",   index,  (float) hits.get(i).get(j).getStrip().getLine().end().z()/10);

                }
                if(hits.get(i).get(j).getDetector()==DetectorType.BMT 
                        && hits.get(i).get(j).getType()==BMTType.C) {
                    bank.setFloat("x1",   index,  (float) hits.get(i).get(j).getStrip().getArc().origin().x()/10);
                    bank.setFloat("y1",   index,  (float) hits.get(i).get(j).getStrip().getArc().origin().y()/10);
                    bank.setFloat("z1",   index,  (float) hits.get(i).get(j).getStrip().getArc().origin().z()/10);
                    bank.setFloat("x2",   index,  (float) hits.get(i).get(j).getStrip().getArc().end().x()/10);
                    bank.setFloat("y2",   index,  (float) hits.get(i).get(j).getStrip().getArc().end().y()/10);
                    bank.setFloat("z2",   index,  (float) hits.get(i).get(j).getStrip().getArc().end().z()/10);

                }
            
                index++;
            }  
        }
        return bank;
    }

    public static DataBank fillSeedsBank(DataEvent event, List<Seed> seeds, String bankName) {
       if (seeds == null || seeds.isEmpty()) return null;

       DataBank bank = event.createBank(bankName, seeds.size());
       
       for (int i = 0; i < seeds.size(); i++) {
           if(seeds.get(i)==null)
               continue;
           bank.setShort("id", i, (short) seeds.get(i).getId());
           bank.setFloat("purity", i, (float) seeds.get(i).getPurity());
           bank.setFloat("efficiency", i, (float) seeds.get(i).getEffs()[2]);
       }
       return bank;
    }
    
    public static DataBank fillTracksBank(DataEvent event, List<Track> tracks, String bankName) {
       if (tracks == null || tracks.isEmpty()) return null;

       DataBank bank = event.createBank(bankName, tracks.size());
       
       for (int i = 0; i < tracks.size(); i++) {
           if(tracks.get(i)==null)
               continue;
           bank.setShort("id", i, (short) tracks.get(i).getId());
           bank.setFloat("purity", i, (float) tracks.get(i).getPurity());
           bank.setFloat("efficiency", i, (float) tracks.get(i).getEffs()[2]);
       }
       return bank;
    }
            
            
    private static byte getType(Hit h) {
        DetectorType detector = h.getDetector();

        if (detector == DetectorType.BST) {
            return 0;
        }

        if (detector == DetectorType.BMT) {
            BMTType type = h.getType();
            switch (type) {
                case Z: return 1;
                case C: return 2;
                default: return -1;
            }
        }

    return -1;
}

}
