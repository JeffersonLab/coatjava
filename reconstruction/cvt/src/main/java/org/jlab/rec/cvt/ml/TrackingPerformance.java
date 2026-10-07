/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.ml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.rec.cvt.track.Seed;
import org.jlab.rec.cvt.track.Track;

/**
 *
 * @author ziegler
 */
public class TrackingPerformance {
    public static void MatchHitToMC(Hit h, Map<Integer, Integer> map) {
        if(map.containsKey(h.getId())) {
            h.setAssociateMCTrkId(map.get(h.getId()));
        }
    }
    
    public static Map<Integer, Integer> TruthMap(DataBank mcTrue, int detId) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int k = 0; k < mcTrue.rows(); k++) {
            if(mcTrue.getInt("mtid", k)==0 && mcTrue.getByte("detector", k) == detId) {
                map.put(mcTrue.getInt("hitn", k), mcTrue.getInt("tid", k));
            }
        }
        return map;
    }

    public static void MatchHitsToMC(List<ArrayList<Hit>>hits, DataBank mcTrue){ //1.
        Map smap = TrackingPerformance.TruthMap(mcTrue, DetectorType.BST.getDetectorId());
        Map bmap = TrackingPerformance.TruthMap(mcTrue, DetectorType.BMT.getDetectorId());
        for(Hit h : hits.get(0)) {
            TrackingPerformance.MatchHitToMC(h, smap);
        }
        for(Hit h : hits.get(1)) {
            TrackingPerformance.MatchHitToMC(h, bmap);
        }
    }
   
    private static double calcPurity(Seed s, int mctid) {
        int nTotalHits=0;
        int nMCMatchedHits=0;
        for(Cluster c: s.getClusters()) {
            for(Hit h : c) {
                nTotalHits++;
                if(h.getAssociateMCTrkId()==mctid) {
                    nMCMatchedHits++;
                }
            }
        }
        
        if(nTotalHits==0) return 0;
        return (double) nMCMatchedHits/(double) nTotalHits;
    }
    
    private static double calcPurity(Track t, int mctid) {
        return calcPurity(t.getSeed(), mctid);
    }
    
    private static void setPurity(Seed s, int mctid) {
        double purity = calcPurity(s, mctid);
        s.setPurity(purity);
    }
    
    private static void setPurity(Track t, int mctid) {
        double purity = calcPurity(t, mctid);
        t.setPurity(purity);
    }
    
    private static void setSeedsPurity(List<Seed> seeds) { //2.
        for(Seed s : seeds) { 
            int mcmatchId = matchToMCtID(s);
            double purity = calcPurity(s, mcmatchId);
            s.setPurity(purity);
        }
    }
    
    private static void setTracksPurity(List<Track> tracks) { //3.
        for(Track t : tracks) {
            int mcmatchId = matchToMCtID(t);
            double purity = calcPurity(t, mcmatchId);
            t.setPurity(purity);
        }
    }
    
    private static int matchToMCtID(Seed s) {
        Map<Integer, List<Hit>> map = new HashMap<>();
        for(Cluster c: s.getClusters()) {
            for(Hit h : c) {
                if(!map.containsKey(h.getAssociateMCTrkId())) {
                    map.put(h.getAssociateMCTrkId(), new ArrayList<>());
                    map.get(h.getAssociateMCTrkId()).add(h);
                } else {
                    map.get(h.getAssociateMCTrkId()).add(h);
                }
            }
        }
        Map.Entry<Integer, List<Hit>> maxEntry = map.entrySet()
        .stream()
        .max(Comparator.comparingInt(e -> e.getValue().size()))
        .orElse(null); // returns null if map is empty
        
        if(maxEntry!=null) {
            return maxEntry.getKey();
        }
        return -1;
    }
    
    private static int matchToMCtID(Track t) {
        Map<Integer, List<Hit>> map = new HashMap<>();
        for(Cluster c: t.getSeed().getClusters()) {
            for(Hit h : c) {
                if(h.getAssociateMCTrkId()==-1) continue;
                if(!map.containsKey(h.getAssociateMCTrkId())) {
                    map.put(h.getAssociateMCTrkId(), new ArrayList<>());
                    map.get(h.getAssociateMCTrkId()).add(h);
                } else {
                    map.get(h.getAssociateMCTrkId()).add(h);
                }
            }
        }
        Map.Entry<Integer, List<Hit>> maxEntry = map.entrySet()
        .stream()
        .max(Comparator.comparingInt(e -> e.getValue().size()))
        .orElse(null); // returns null if map is empty
        
        if(maxEntry!=null) {
            return maxEntry.getKey(); 
        }
        return -1;
    }
    
    private static List<Map<Integer, List<Hit>>> getMCHOTs(List<ArrayList<Hit>>hits){ //0.
        Map<Integer, List<Hit>> map1 = new HashMap<>();
        Map<Integer, List<Hit>> map2 = new HashMap<>();
        List<Map<Integer, List<Hit>>> maps = new ArrayList<>();
        
        for(Hit h : hits.get(0)) {
            if(!map1.containsKey(h.getAssociateMCTrkId())) {
                    map1.put(h.getAssociateMCTrkId(), new ArrayList<>());
                    map1.get(h.getAssociateMCTrkId()).add(h); 
            } else {
                map1.get(h.getAssociateMCTrkId()).add(h); 
            }
        }
        for(Hit h : hits.get(1)) {
            if(!map2.containsKey(h.getAssociateMCTrkId())) {
                    map2.put(h.getAssociateMCTrkId(), new ArrayList<>());
                    map2.get(h.getAssociateMCTrkId()).add(h);
            } else {
                map2.get(h.getAssociateMCTrkId()).add(h);
            }
        }
        maps.add(map1);
        maps.add(map2);
        
        return maps;
    }
    
    private static double[] calcEfficiency(Seed s, List<Map<Integer, List<Hit>>> maps) {
        int nMCMatchedBstHits=0;
        int nMCMatchedBmtHits=0;
        int mcmatchId = matchToMCtID(s);
        int nbst = 0;
        if(!maps.get(0).isEmpty() && 
                        maps.get(0).containsKey(mcmatchId)) {
            nbst=maps.get(0).get(mcmatchId).size();
        }
        int nbmt = 0;
        if(!maps.get(1).isEmpty() && 
                        maps.get(1).containsKey(mcmatchId)) {
            nbmt=maps.get(1).get(mcmatchId).size();
        }
        for(Cluster c: s.getClusters()) {
            for(Hit h : c) { 
                if(h.getAssociateMCTrkId()==mcmatchId && h.getDetector()==DetectorType.BST) {
                    nMCMatchedBstHits++;
                }
                if(h.getAssociateMCTrkId()==mcmatchId && h.getDetector()==DetectorType.BMT) {
                    nMCMatchedBmtHits++;
                }
            }
        }
        
        double bstE = 0;
        double bmtE = 0;
        double E = 0;
        if(nbst!=0)
            bstE = (double) nMCMatchedBstHits/(double)nbst;
        if(nbmt!=0)
            bmtE = (double) nMCMatchedBmtHits/(double)nbmt;
        if(nbst+nbmt!=0)
            E = (double) (nMCMatchedBstHits+nMCMatchedBmtHits)/(double)(nbst+nbmt);
        
        return new double[] {bstE, bmtE, E};
    }
    
    private static double[] calcEfficiency(Track t, List<Map<Integer, List<Hit>>> maps) { 
         return calcEfficiency(t.getSeed(), maps);
    }
    
    private static void setSeedsEfficiency(List<Seed> seeds, List<Map<Integer, List<Hit>>> maps) { //4.
        for(Seed s : seeds) {
            
            double[] effs = TrackingPerformance.calcEfficiency(s, maps);
            s.setEffs(effs);
        }
    }
    
    private static void setTracksEfficiency(List<Track> tracks, List<Map<Integer, List<Hit>>> maps) { //4.
        for(Track t : tracks) {
            double[] effs = TrackingPerformance.calcEfficiency(t, maps); 
            t.setEffs(effs);
        }
    }
    
    public static void TrackingPerformance(DataEvent event, List<ArrayList<Hit>>hits, 
            List<Seed> seeds, List<Track> tracks) {
        if (event.hasBank("MC::True")) {
            DataBank mcTrue = event.getBank("MC::True");
            MatchHitsToMC(hits, mcTrue);
        }
        List<Map<Integer, List<Hit>>> hmaps = getMCHOTs(hits);
        
        if(seeds!=null) {
            setSeedsPurity(seeds);
            setSeedsEfficiency(seeds, hmaps);
        }
        if(tracks!=null) {
            setTracksEfficiency(tracks, hmaps);
            setTracksPurity(tracks);
        }
    }
}

   
