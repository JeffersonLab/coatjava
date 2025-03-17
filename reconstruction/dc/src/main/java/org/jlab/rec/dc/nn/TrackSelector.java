/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.dc.nn;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlab.io.base.DataBank;
import org.jlab.rec.dc.track.Track;
/**
 *
 * @author ziegler
 */
public class TrackSelector {

    int NNCOMMONSLY = 1;
    
    /**
     * Lists tracks in subgroups organized by P and Q
     * @param tracks
     * @return 
     */
    private  List<List<Track>> groupTracks(List<Track> tracks, double PCloseness) {
        List<List<Track>> sublists = new ArrayList<>();

        // Loop over all tracks
        for (int i = 0; i < tracks.size(); i++) {
            Track currentTrack = tracks.get(i);
            boolean isGrouped = false;
            currentTrack.setSegmentIds();
            // Try to add currentTrack to an existing group
            for (List<Track> sublist : sublists) {
                Track firstTrackInGroup = sublist.get(0);

                // Check if the first track in the group satisfies the conditions with the current track
                if (Math.abs(currentTrack.get_pAtOrig().z() - firstTrackInGroup.get_pAtOrig().z()) < PCloseness && 
                    currentTrack.get_Q() == firstTrackInGroup.get_Q() 
                        && currentTrack.getSector() == firstTrackInGroup.getSector()) {
                    sublist.add(currentTrack);
                    isGrouped = true;
                    break;
                }
            }

            // If the current track wasn't added to any group, create a new group
            if (!isGrouped) {
                List<Track> newGroup = new ArrayList<>();
                newGroup.add(currentTrack);
                sublists.add(newGroup);
            }
        }

        return sublists;
    }

    public void removeInstarecOverlappingTracks(DataBank bankAI, boolean enableMulti, 
            List<Track> tracks) {
        System.out.println("RESOLVING OVERLAPS");
        if (!enableMulti) return;
        List<Track> selectedTrks = new ArrayList<>();
        List<List<Track>> trkGrps = groupTracks(tracks, 0.5);
        for(List<Track> lt : trkGrps) { 
            resolveOverlaps(lt);
            selectedTrks.addAll(lt);
        }
        
        tracks.clear();
        tracks.addAll(selectedTrks);
    }
    

    private Set<Integer> findCommonValues(Track track1, Track track2) {
        track1.setSegmentIds();
        track2.setSegmentIds();
        Set<Integer> set1 = track1.getSegmentIds();
        Set<Integer> set2 = track2.getSegmentIds();

        // Retain only common elements between both sets
        set1.retainAll(set2);
        return set1;
    }
    
    // Method to find all tracks that share common values exceeding NNCOMMONSLY
    private List<List<Track>> findTracksWithCommonValues(List<Track> tracks) {
        List<List<Track>> result = new ArrayList<>();
        int n = tracks.size();
        // A map to track if a track has been visited
        boolean[] visited = new boolean[n];

        // Create a graph where tracks are nodes and edges represent shared common values
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }

        // Build the graph: Create edges between tracks that share enough common values
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Set<Integer> commonValues = findCommonValues(tracks.get(i), tracks.get(j));
                if (commonValues.size() > NNCOMMONSLY) {
                    // Add an edge between tracks i and j (since they overlap)
                    graph.get(i).add(j);
                    graph.get(j).add(i);
                }
            }
        }

        // Use DFS to find all connected components (groups of overlapping tracks)
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                List<Track> component = new ArrayList<>();
                dfs(i, visited, graph, tracks, component);
                result.add(component);
            }
        }

        return result;
    }

    // DFS to explore all tracks connected to the current track
    private void dfs(int index, boolean[] visited, List<List<Integer>> graph, List<Track> tracks, List<Track> component) {
        visited[index] = true;
        component.add(tracks.get(index));

        // Traverse all adjacent nodes (tracks) that are connected
        for (int neighbor : graph.get(index)) {
            if (!visited[neighbor]) {
                dfs(neighbor, visited, graph, tracks, component);
            }
        }
    }
    
    private void resolveOverlaps(List<Track> tracks) { 
        if(tracks.size()==1) return ;
        Map<Integer, Track> fList = new HashMap<>();
        List<List<Track>> otrkmap = this.findTracksWithCommonValues(tracks);
        for(List<Track> sList : otrkmap) {
            for(Track t : sList) {
            System.out.println(Arrays.toString(t.getSegmentIds().toArray()));
        }
            // Sort by FitChi2 / FitNDF and select the best track
            sList.sort(Comparator.comparingDouble(a -> a.get_FitChi2() / (double) a.get_FitNDF()));
            // Ensure sList is not empty before accessing the first element
            if (!sList.isEmpty()) {
                sList = sList.subList(0, Math.min(1, sList.size()));
                fList.put(sList.get(0).getId(),sList.get(0));
            }
        }
        tracks.clear();
        tracks.addAll(new ArrayList<>(fList.values()));
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
    
    public static int[] removeArrayZeros(int[] arr) {
        int arrSize=0;
        for(int i = 0; i<arr.length; i++) {
            if(arr[i]>0) { 
                arrSize++;
            }
        }
        
        int[] newArr = new int[arrSize];
        int arrCnt=0;
        for(int i = 0; i<arr.length; i++) {
            if(arr[i]>0) {
                newArr[arrCnt]=arr[i]; 
                arrCnt++;
            }
        }
        return newArr;
    }
}
