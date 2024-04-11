/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.rec.cvt.services.CVTReconstruction;
import org.jlab.utils.groups.IndexedTable;

/**
 *
 * @author veronique
 */
public class AIHitSelector {
    
    private HashMap<Double, List<ArrayList<Hit>>> sortHashMapBySmallestDifference(Map<Double, List<ArrayList<Hit>>> inputHashMap) {
        // Convert the HashMap to a List of Map.Entry objects
        List<Map.Entry<Double, List<ArrayList<Hit>>>> entryList = new ArrayList<>(inputHashMap.entrySet());

        // Sort the list based on the smallest difference between keys
        Collections.sort(entryList, new Comparator<Map.Entry<Double, List<ArrayList<Hit>>>>() {
            @Override
            public int compare(Map.Entry<Double, List<ArrayList<Hit>>> entry1, Map.Entry<Double, List<ArrayList<Hit>>> entry2) {
                double key1 = entry1.getKey();
                double key2 = entry2.getKey();
                double diff1 = getKeyDifference(key1, key2);
                double diff2 = getKeyDifference(key2, key1);
                return Double.compare(diff1, diff2);
            }

            private double getKeyDifference(double key1, double key2) {
                return Math.abs(key1 - key2);
            }
        });

        // Create a new LinkedHashMap to store the sorted entries
        HashMap<Double, List<ArrayList<Hit>>> sortedHashMap = new LinkedHashMap<>();
        for (Map.Entry<Double, List<ArrayList<Hit>>> entry : entryList) {
            sortedHashMap.put(entry.getKey(), entry.getValue());
        }

        return sortedHashMap;
    }

    
    private List<Hit> findCommonHits(List<Hit> list1, List<Hit> list2) {
        List<Hit> commonHits = new ArrayList<>();

        for (Hit hit1 : list1) {
            for (Hit hit2 : list2) {
                if (hit1.getSector() == hit2.getSector() && hit1.getLayer() == hit2.getLayer()) {
                    commonHits.add(hit1);
                    break; // If a match is found, no need to check the same hit again in list2
                }
            }
        }

        return commonHits;
    }
    
    private boolean overlaps(List<ArrayList<Hit>> hitlists) {
        if(hitlists.size()<2) return false;
        ArrayList<Hit> hits1 = hitlists.get(0);
        ArrayList<Hit> hits2 = hitlists.get(1);
        boolean pass = false;
        if(this.findCommonHits(hits1, hits2).size()>1)
            pass = true;
        return pass;
    }
    
    private Map<Double, List<ArrayList<Hit>>> selectedHits(Map<Double, List<ArrayList<Hit>>> trkHits) {
        Map<Double, List<ArrayList<Hit>>> selectedTrkHits = new HashMap<>();
        Map<Double, List<ArrayList<Hit>>> sortedTrkHits = this.sortHashMapBySmallestDifference(trkHits);
        Iterator<Map.Entry<Double, List<ArrayList<Hit>>>> itr = sortedTrkHits.entrySet().iterator(); 
        int count=0;  
        
        while(itr.hasNext() && count<2) { 
             Map.Entry<Double, List<ArrayList<Hit>>> entry = itr.next(); 
             if(entry.getValue().get(0).size()>4) {
                count++;
                selectedTrkHits.put(entry.getKey(), entry.getValue());
             }
        } 
        List<ArrayList<Hit>> hitlists = new ArrayList<>();
        for(Double i : selectedTrkHits.keySet()) {   
            hitlists.add(selectedTrkHits.get(i).get(0));
        }
        if(this.overlaps(hitlists)==true) {
            return selectedTrkHits;
        } 
//        else {
//            System.out.println("NO HITS");
//            for(Double i : selectedTrkHits.keySet()) {   
//                for(Hit h : selectedTrkHits.get(i).get(0))
//                    System.out.println("phi "+i+" "+h.toString());
//                }
//        }
        return null;
    }
    public Map<Double, List<ArrayList<Hit>>> getHits(DataEvent event, CVTReconstruction reco, 
            IndexedTable svtStatus, IndexedTable bmtStatus, IndexedTable bmtTime, 
            IndexedTable bmtStripVoltage, IndexedTable bmtStripThreshold) {
        this.getListHitsOnTrack(event);
        if(pidMapBST.isEmpty()) return null;
        
        Map<Double, List<ArrayList<Hit>>> trkHits = new HashMap<>();
        
        List<ArrayList<Hit>>  hits = reco.readHits(event, svtStatus, bmtStatus, bmtTime, 
                                                          bmtStripVoltage, bmtStripThreshold);
        List<Hit> bstHits = hits.get(0);
        List<Hit> bmtHits = hits.get(1);
        
        if(bstHits.isEmpty()) return null;
        
        
        for(Double i : phiMapBST.keySet()) {   
            ArrayList<Hit> sbstHits = new ArrayList<>();
            ArrayList<Hit> sbmtHits = new ArrayList<>();
            for(int j = 0; j<phiMapBST.get(i).size(); j++) {
                int sid = phiMapBST.get(i).get(j);
                for(int k =0; k<bstHits.size(); k++) {
                    if(bstHits.get(k).getId()==sid) {
                        sbstHits.add(bstHits.get(k));
                    }
                }
            }
            if(phiMapBMT.containsKey(i)) {
                for(int j = 0; j<phiMapBMT.get(i).size(); j++) {
                    int sid = phiMapBMT.get(i).get(j);
                    for(int k =0; k<bmtHits.size(); k++) {
                        if(bmtHits.get(k).getId()==sid) {
                            sbmtHits.add(bmtHits.get(k));
                        }
                    }
                }
            }
           List<ArrayList<Hit>> selectedhits = new ArrayList<>();  
           selectedhits.add(sbstHits);
           selectedhits.add(sbmtHits);
           
           trkHits.put(i, selectedhits);
        }
        
        return this.selectedHits(trkHits);
        
    }
    public Map<Double, List<ArrayList<Hit>>> getHitsWithBg(DataEvent event, CVTReconstruction reco, 
            IndexedTable svtStatus, IndexedTable bmtStatus, IndexedTable bmtTime, 
            IndexedTable bmtStripVoltage, IndexedTable bmtStripThreshold) {
        this.getListHitsOnTrack(event);
        if(pidMapBST.isEmpty()) return null;
        
        Map<Double, List<ArrayList<Hit>>> trkHits = new HashMap<>();
        
        List<ArrayList<Hit>>  hits = reco.readHits(event, svtStatus, bmtStatus, bmtTime, 
                                                          bmtStripVoltage, bmtStripThreshold);
        List<Hit> bstHits = hits.get(0);
        List<Hit> bmtHits = hits.get(1);
        
        if(bstHits.isEmpty()) return null;
        
        Map<Double, Integer> trkIds = new HashMap<>();
        int tid =0;
        for(Double i : phiMapBST.keySet()) { 
            trkIds.put(i, tid++);
        }
        
        for(Double i : phiMapBST.keySet()) {  
            ArrayList<Hit> sbstHits = new ArrayList<>();
            ArrayList<Hit> sbmtHits = new ArrayList<>();
            for(int j = 0; j<phiMapBST.get(i).size(); j++) {
                int sid = phiMapBST.get(i).get(j);
                for(int k =0; k<bstHits.size(); k++) {
                    if(bstHits.get(k).getId()==sid) {
                        bstHits.get(k).setTrueAssociatedTrackID(trkIds.get(i)); 
                        sbstHits.add(bstHits.get(k));
                    }
                }
            }
            if(phiMapBMT.containsKey(i)) {
                for(int j = 0; j<phiMapBMT.get(i).size(); j++) {
                    int sid = phiMapBMT.get(i).get(j);
                    for(int k =0; k<bmtHits.size(); k++) {
                        if(bmtHits.get(k).getId()==sid) {
                            bmtHits.get(k).setTrueAssociatedTrackID(trkIds.get(i)); 
                            sbmtHits.add(bmtHits.get(k));
                        }
                    }
                }
            }
           List<ArrayList<Hit>> selectedhits = new ArrayList<>();  
           selectedhits.add(sbstHits);
           selectedhits.add(sbmtHits);
           
           trkHits.put(i, selectedhits);
        }
        List<ArrayList<Hit>> selectedhits = new ArrayList<>();  
        ArrayList<Hit> sbstHits = new ArrayList<>();
        ArrayList<Hit> sbmtHits = new ArrayList<>();
        for(int k =0; k<bmtHits.size(); k++) { 
            if(bmtHits.get(k).getTrueAssociatedTrackID()==-1) {
                sbmtHits.add(bmtHits.get(k));
            }
        } 
        for(int k =0; k<bstHits.size(); k++) { 
            if(bstHits.get(k).getTrueAssociatedTrackID()==-1) {
                sbstHits.add(bstHits.get(k));
            }
        } 
        selectedhits.add(sbstHits);
        selectedhits.add(sbmtHits);

        trkHits.put(999.0, selectedhits);
        
        return trkHits;
        
    }
    
    Map<Integer, List<Integer>> pidMapBST = new HashMap<>();
    Map<Integer, List<Integer>> pidMapBMT = new HashMap<>();
    Map<Double, List<Integer>> phiMapBST = new HashMap<>();
    Map<Double, List<Integer>> phiMapBMT = new HashMap<>();
    private void  getListHitsOnTrack(DataEvent event) {
        pidMapBST.clear();
        pidMapBMT.clear();
        phiMapBST.clear();
        phiMapBMT.clear();
        if(!event.hasBank("MC::True")) return ;
        if(!event.hasBank("MC::Particle")) return ;
        
        DataBank mcTrueBank = event.getBank("MC::True");
        DataBank mcPartBank = event.getBank("MC::Particle");
        
        for(int i = 0; i < mcTrueBank.rows(); i++) {
            int pid = mcTrueBank.getInt("pid", i);
            int hitn = mcTrueBank.getInt("hitn", i);
            int det = mcTrueBank.getInt("detector", i);
            if(det==2) {
                if(pidMapBST.containsKey(pid)) {
                    pidMapBST.get(pid).add(hitn);
                } else {
                    pidMapBST.put(pid, new ArrayList<>());
                    pidMapBST.get(pid).add(hitn);
                }
            }
            if(det==1) {
                if(pidMapBMT.containsKey(pid)) {
                    pidMapBMT.get(pid).add(hitn);
                } else {
                    pidMapBMT.put(pid, new ArrayList<>());
                    pidMapBMT.get(pid).add(hitn);
                }
            }
        }
        for(int i = 0; i < mcPartBank.rows(); i++) {
            int pid = mcPartBank.getInt("pid", i);
            double px = (double) mcPartBank.getFloat("px", i);
            double py = (double) mcPartBank.getFloat("py", i);
            double phi = Math.atan2(py, px);
            if(pidMapBST.containsKey(pid)) {
                phiMapBST.put(phi, pidMapBST.get(pid));
                if(pidMapBMT.containsKey(pid)) {
                    phiMapBMT.put(phi, pidMapBMT.get(pid));
                }
            }
        }
    }
    
    
}
