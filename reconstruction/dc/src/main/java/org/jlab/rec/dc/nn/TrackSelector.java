/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.dc.nn;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jlab.io.base.DataBank;
import org.jlab.rec.dc.segment.Segment;
import org.jlab.rec.dc.track.Track;
/**
 *
 * @author ziegler
 */
public class TrackSelector {

    private Map<IntArrayKey, Track> getInstarecRecoTrks(List<Track> trkcands) {
        Map<IntArrayKey, Track> trackList = new HashMap<>();
        
        // Iterate over track candidates
        trkcands.forEach(track -> {
            Map<Integer, Segment> trkSegs = new HashMap<>();
            List<Segment> tsegs = track.get_ListOfHBSegments();
            
            // Populate segments map
            tsegs.forEach(s -> trkSegs.put(s.get_Superlayer() - 1, s));
            
            // Initialize IDs array
            int[] ids = new int[6];
            Arrays.fill(ids, -1); // Default to -1
            
            // Get segment IDs
            for (int s = 0; s < 6; s++) {
                Segment segment = trkSegs.get(s);
                if (segment != null) {
                    ids[s] = segment.get_Id();
                }
            }
            trackList.put(new IntArrayKey(ids), track);
        });
        
        return trackList;
    }

    private Map<AIHitReader.TrackInfo, List<AIHitReader.TrackInfo>> processTrackInfo(DataBank bankAI, boolean enableMulti) {
        Map<AIHitReader.TrackInfo, List<AIHitReader.TrackInfo>> trackInfoLM = new HashMap<>();
        List<AIHitReader.TrackInfo> trackInfoL = new ArrayList<>();

        // Iterate over rows in the bank
        for (int j = 0; j < bankAI.rows(); j++) {
            int[] ids = new int[6];
            double[] tPars = new double[5];

            // Populate IDs array
            for (int s = 0; s < 6; s++) {
                ids[s] = (int) bankAI.getShort("c" + (s + 1), j);
            }

            tPars[3] = (double) bankAI.getShort("id", j);
            tPars[4] = (double) bankAI.getFloat("prob", j);
            int status = (int) bankAI.getShort("status", j);

            AIHitReader.TrackInfo ti = new AIHitReader.TrackInfo(ids, tPars);
            if (status != 0) {
                if (enableMulti) {
                    trackInfoLM.computeIfAbsent(ti, k -> new ArrayList<>()).add(ti);
                } else {
                    trackInfoL.add(ti);
                }
            } else {
                trackInfoL.add(ti);
            }
        }

        // Handle overlaps between tracks
        trackInfoLM.keySet().forEach(ti -> {
            trackInfoL.forEach(tj -> {
                Set<Integer> setA = new LinkedHashSet<>(Arrays.stream(ti.getIds())  
                          .boxed()             
                          .collect(Collectors.toSet())); 
                Set<Integer> setB = new LinkedHashSet<>(Arrays.stream(tj.getIds())  
                          .boxed()             
                          .collect(Collectors.toSet())); 
                setA.retainAll(setB);
                if (!setA.isEmpty()) {
                    trackInfoLM.get(ti).add(tj);
                }
            });
        });

        return trackInfoLM;
    }

    public void removeInstarecOverlappingTracks(DataBank bankAI, boolean enableMulti, List<Track> tracks) {
        if (!enableMulti) return;
        Map<IntArrayKey, Track> selectedTrks = new HashMap<>();
        Map<AIHitReader.TrackInfo, List<AIHitReader.TrackInfo>> trackInfoLs = processTrackInfo(bankAI, enableMulti);
        Map<IntArrayKey, Track> instarecRecoTrks = getInstarecRecoTrks(tracks);
        Map<IntArrayKey, Track> selected = new HashMap<>();

        // Process tracks and filter out overlaps
        trackInfoLs.values().forEach(tis -> {
            selected.clear();
            tis.forEach(ti -> {
                IntArrayKey ik = new IntArrayKey(ti.getIds());
                if (instarecRecoTrks.containsKey(ik)) {
                    Track track = instarecRecoTrks.get(ik); 
                    track.printInfo();
                    selected.put(ik, track);
                } 
            });

            // Sort by FitChi2 / FitNDF and select best track
            List<Track> sList = new ArrayList<>(selected.values());
            sList.sort(Comparator.comparingDouble(a -> a.get_FitChi2() / (double) a.get_FitNDF()));
            sList = sList.subList(0, Math.min(1, selected.size()));

            Track ts = sList.get(0);  // Initialize ts outside the loop
            for(IntArrayKey ii : selected.keySet()) {
                Track tri = selected.get(ii);
                if(tri.get_Id() == ts.get_Id()) {
                    selectedTrks.put(ii, ts);  // Use ts here
                }
            }
        });

        // Handle remaining overlaps and filter based on FitChi2/FitNDF
        resolveOverlaps(selectedTrks);

        // Remove overlaps
        selectedTrks.keySet().removeIf(tj -> selectedTrks.get(tj).get_Id() < 0);
        
        //check for seeds that are not in the list of selected tracks and add if not overlapping
        // Process tracks and filter out overlaps
        trackInfoLs.keySet().forEach(tis -> {
        IntArrayKey ik = new IntArrayKey(tis.getIds());
            //check for overlap with selected tracks
            if (selectedTrks.containsKey(ik)) {
                //System.out.println("Found track for " + Arrays.toString(tis.getIds()) + ": \n"); 
            } else {
                //System.out.println("No track found for " + Arrays.toString(tis.getIds()));
                //check overlaps with the rest of the selected tracks
                
                boolean overlaps = selectedTrks.keySet().stream().anyMatch(ti -> checkOverlap(ti, ik));
                if(!overlaps) {
                    selectedTrks.put(ik, instarecRecoTrks.get(ik));
                }
            }
        });

        
        tracks.clear();
        tracks.addAll(selectedTrks.values());
    }

    private boolean checkOverlap(IntArrayKey ti, IntArrayKey tj
    //        , Map<IntArrayKey, Track> selectedTrks
    ) {
        // Convert int[] to Integer[] using stream
        Set<Integer> setA = new LinkedHashSet<>(Arrays.stream(ti.getArray()).boxed().
                                                collect(Collectors.toSet()));
        Set<Integer> setB = new LinkedHashSet<>(Arrays.stream(tj.getArray()).boxed().
                                                collect(Collectors.toSet()));
        setA.retainAll(setB);
        return !setA.isEmpty() && setA.size() != setB.size();
    }

    private void resolveOverlaps(Map<IntArrayKey, Track> selectedTrks) { 
        selectedTrks.keySet().forEach(ti -> {
            selectedTrks.keySet().forEach(tj -> { 
                if (!ti.equals(tj) && selectedTrks.get(ti).get_Id() != selectedTrks.get(tj).get_Id()
                        && selectedTrks.get(ti).get_Id()>0 && selectedTrks.get(tj).get_Id()>0) {
                    // Convert int[] to Integer[] using stream
                    Set<Integer> setA = new LinkedHashSet<>(Arrays.stream(ti.getArray()).boxed().
                                                            collect(Collectors.toSet()));
                    Set<Integer> setB = new LinkedHashSet<>(Arrays.stream(tj.getArray()).boxed().
                                                            collect(Collectors.toSet()));
                    setA.retainAll(setB);
                    if (!setA.isEmpty() && setA.size() != setB.size()) {
                        //System.out.println("Overlap detected: " + setA);
                        if (selectedTrks.get(tj).get_FitChi2() / selectedTrks.get(tj).get_FitNDF() < 
                            selectedTrks.get(ti).get_FitChi2() / selectedTrks.get(ti).get_FitNDF()) {
                            selectedTrks.get(ti).set_Id(-selectedTrks.get(ti).get_Id());
                            //System.out.println("flagged for rm "+selectedTrks.get(ti));
                        } else {
                            selectedTrks.get(tj).set_Id(-selectedTrks.get(tj).get_Id());
                           // System.out.println("flagged for rm "+selectedTrks.get(ti));
                        }
                    }
                }
            });
        });
    }


    class IntArrayKey {
        private final int[] array;

        public IntArrayKey(int[] array) {
            this.array = array.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IntArrayKey that = (IntArrayKey) o;
            return Arrays.equals(array, that.array);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(array);
        }

        public int[] getArray() {
            return array;
        }
        
    }
}
