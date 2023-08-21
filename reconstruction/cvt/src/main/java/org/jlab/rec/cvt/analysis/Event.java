/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.bmt.BMTType;

/**
 *
 * @author ziegler
 */
public class Event {
    
    public Map<Integer, Track>  recTracks;
    public Track mcGenTrack;
    public Track bgFreeTrack;
    
    private Track getMCGenTrack(DataBank bank) {
        double px = bank.getFloat("px", 0);
        double py = bank.getFloat("py", 0);
        double pz = bank.getFloat("pz", 0);
        //double vx = bank.getFloat("vx", 0);
        //double vy = bank.getFloat("vy", 0);
        //double vz = bank.getFloat("vz", 0);
        double p = Math.sqrt(px*px+py*py+pz*pz);
        double theta = Math.toDegrees(Math.acos(pz/p));
        double phi =  Math.toDegrees(Math.atan2(py,px));
        int pid = bank.getInt("pid", 0);
        int charge = 0;
        if(pid/100==0) {
            charge = (int) -Math.signum(pid);
        } else {
            charge = (int) -Math.signum(pid);
        }
        return new Track(999, charge, p, theta, phi);
    }
    
    private Track getRecTrackFromBank(DataBank bank, int row) { 
        int id = bank.getInt("ID", row); 
        int q = bank.getByte("q", row);
        double pt = bank.getFloat("pt", row);
        double p = bank.getFloat("p", row);
        double tandip = bank.getFloat("tandip", row);
        double phi0 = bank.getFloat("phi0", row); 
        //double d0 = bank.getFloat("d0", row);
        //double x0 = bank.getFloat("x0", row);
        //double y0 = bank.getFloat("y0", row);
        //double z0 = bank.getFloat("z0", row);
        double px = pt*Math.cos(phi0);
        double py = pt*Math.sin(phi0);
        double pz = pt*tandip;
        //double vx = -d0 * Math.sin(phi0) + x0;
        //double vy = d0 * Math.cos(phi0) + y0;
        //double vz = z0;
        double theta = Math.toDegrees(Math.acos(pz/p));
        double phi =  Math.toDegrees(Math.atan2(py,px));
        
        return new Track(id, q, p, theta, phi);
    }
    
    private void getTracks(DataEvent event) {
        recTracks = new HashMap<>();
        if(event.hasBank("MC::Particle")) {
            DataBank bank = event.getBank("MC::Particle");
            mcGenTrack = this.getMCGenTrack(bank);
        }
        if(event.hasBank("CVT::STracks")) { 
            DataBank bank = event.getBank("CVT::STracks"); 
            bgFreeTrack = this.getRecTrackFromBank(bank, 0); 
        }
        if(event.hasBank("CVT::Tracks")) { 
            DataBank bank = event.getBank("CVT::Tracks"); 
            double dp=99999;
            double dtheta=9999; 
            for(int i =0; i<bank.rows(); i++) {
                Track iTrk = this.getRecTrackFromBank(bank, i);
                if(bgFreeTrack!=null) { //is withing specs
                    if(Math.abs(iTrk.p-bgFreeTrack.p)<dp && Math.abs(iTrk.theta-bgFreeTrack.theta)<dtheta
                            && Math.abs(iTrk.p-bgFreeTrack.p)<3*5.0/100.0 && Math.toRadians(Math.abs(iTrk.theta-bgFreeTrack.theta))<3*5.0/1000.0 ) {
                        dp = Math.abs(iTrk.p-bgFreeTrack.p);
                        dtheta = Math.abs(iTrk.theta-bgFreeTrack.theta);
                    }
                }
                if(dp<99999) iTrk.isAccidental = false;
                recTracks.put(iTrk.tid, iTrk);
            }
        }
        
        
    }
  
    public List<HitPos> BSTHits;
    public List<HitPos> BMTHits;
    public Map<Integer, List<HitPos>> BSTSameSecLyr;
    public Map<Integer, List<HitPos>> BMTSameSecLyr;
    public Map<Integer, Float> BMTTimes;
    public Map<Integer, Float> BMTEnergies;
    
    private void getHits(DataEvent event, Map<Integer, Track>  recTrack, Track  bgFreeTrack) {
        BSTHits = new ArrayList<>();
        BMTHits = new ArrayList<>();
        BSTSameSecLyr = new HashMap();
        BMTSameSecLyr = new HashMap();
        BMTTimes = new HashMap();
        BMTEnergies = new HashMap();
        if(event.hasBank("BMT::Hits")) {
            DataBank bank = event.getBank("BMT::Hits"); 
            for(int i =0; i<bank.rows(); i++) { 
                float t = bank.getFloat("time", i);
                float en = bank.getFloat("energy", i);
                int id = bank.getInt("ID", i);
                BMTTimes.put(id, t);
                BMTEnergies.put(id, en);
            }
                }
        if(event.hasBank("BST::HitsPos")) {
            DataBank bank = event.getBank("BST::HitsPos"); 
            for(int i =0; i<bank.rows(); i++) { 
                HitPos hp = new HitPos(bank, i, recTrack, bgFreeTrack);
                hp.setDetType(BMTType.UNDEFINED);
                if(isNotPartOfAccidental(hp.getrTrack()))
                    BSTHits.add(hp);
                int secLyr = hp.getSector()*100+hp.getLayer();
                if(BSTSameSecLyr.containsKey(secLyr)) {
                    BSTSameSecLyr.get(secLyr).add(hp);
                } else {
                    BSTSameSecLyr.put(secLyr, new ArrayList<>());
                    BSTSameSecLyr.get(secLyr).add(hp);
                }
            }
        }
        if(event.hasBank("BMT::HitsPos")) {
            DataBank bank = event.getBank("BMT::HitsPos");
            for(int i =0; i<bank.rows(); i++) {
                HitPos hp = new HitPos(bank, i, recTrack, bgFreeTrack);
                hp.setDetType(getDetectorType(hp.getLayer()));
                if(isNotPartOfAccidental(hp.getrTrack()))
                    BMTHits.add(hp);
                int secLyr = hp.getSector()*100+hp.getLayer();
                if(BMTSameSecLyr.containsKey(secLyr)) {
                    BMTSameSecLyr.get(secLyr).add(hp);
                } else {
                    BMTSameSecLyr.put(secLyr, new ArrayList<>());
                    BMTSameSecLyr.get(secLyr).add(hp);
                }
            }
        }
    }
    
    private void setNearestNeighbors(double docaMax) {
        for(HitPos hp : BSTHits) {
            int secLyr = hp.getSector()*100+hp.getLayer();
            hp.findNearestNeighbors(BSTSameSecLyr.get(secLyr), docaMax);
        }
        for(HitPos hp : BMTHits) {
            int secLyr = hp.getSector()*100+hp.getLayer();
            hp.findNearestNeighbors(BMTSameSecLyr.get(secLyr), docaMax);
        }
    }
    
    public void processEvent(DataEvent event) {
        this.getTracks(event);
        this.getHits(event, recTracks, bgFreeTrack);
        this.setNearestNeighbors(this.docaMax);
    }
    
    private BMTType getDetectorType(int layer) {
        if(layer == 1 || layer == 4 || layer == 6) return BMTType.C;
        else return BMTType.Z;
    }
    
    public double docaMax = 1.0; //1 cm

    private boolean isNotPartOfAccidental(Track trk) {
        boolean value = false;
        if(trk==null) value = true;
        if(trk!=null ) {
            if(trk.isAccidental) {
                value = false;
            } else {
                value = true;
            }
        }
        return value;
    }
}
