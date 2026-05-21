package org.jlab.service.dc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.jlab.clas.swimtools.Swim;
import org.jlab.clas.swimtools.Swimmer;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.dc.Constants;
import org.jlab.rec.dc.banks.HitReader;
import org.jlab.rec.dc.banks.URWellDCClustersReader;
import org.jlab.rec.dc.banks.RecoBankWriter;
import org.jlab.rec.dc.cluster.ClusterFinder;
import org.jlab.rec.dc.cluster.ClusterFitter;
import org.jlab.rec.dc.cluster.FittedCluster;
import org.jlab.rec.dc.cross.Cross;
import org.jlab.rec.dc.cross.CrossList;
import org.jlab.rec.dc.cross.CrossMaker;
import org.jlab.rec.dc.hit.FittedHit;
import org.jlab.rec.dc.segment.Segment;
import org.jlab.rec.dc.segment.SegmentFinder;
import org.jlab.rec.dc.track.Track;
import org.jlab.rec.dc.track.TrackCandListWithURWellFinder;
import org.jlab.rec.dc.cross.URWellDCCrossesList;
import org.jlab.rec.dc.trajectory.Road;
import org.jlab.rec.dc.trajectory.RoadFinderWithURWell;
import org.jlab.rec.urwell.reader.URWellReader;
import org.jlab.rec.urwell.reader.URWellCluster;
import org.jlab.rec.urwell.reader.URWellCross;
import org.jlab.rec.dc.cross.URWellDCCrossesListFinder;

/**
 *
 * @author Tongtong Cao
 */
public class DCURWellHBPostClusterConv extends DCEngine {
    public DCURWellHBPostClusterConv() {
        super("DCHB");
        this.getBanks().init("HitBasedTrkg", "", "HB");
    }
    
    @Override
    public void setDropBanks() {
        super.registerOutputBank(this.getBanks().getHitsBank());
        super.registerOutputBank(this.getBanks().getClustersBank());
        super.registerOutputBank(this.getBanks().getSegmentsBank());
        super.registerOutputBank(this.getBanks().getCrossesBank());
        super.registerOutputBank(this.getBanks().getTracksBank());
        super.registerOutputBank(this.getBanks().getIdsBank());
    }
        
    @Override
    public boolean processDataEventUser(DataEvent event) {
    
        int run = this.getRun(event);
        if(run==0) return true;
        
        // get Field
        Swim dcSwim = new Swim();
     
        List<Segment> segments = null;
        List<FittedCluster> clusters = null;
        List<Cross> crosses = null;
        List<URWellCross> urCrossesOnTrks = new ArrayList<URWellCross>();
        
        ///// Read banks and build lists from hits to crosses
        HitReader      reader = new HitReader(this.getBanks(), Constants.getInstance().dcDetector);        
        RecoBankWriter writer = new RecoBankWriter(this.getBanks());
        
        // read the hits from the banks
        Map<Integer, ArrayList<FittedHit>> hits = reader.read_Hits(event);
        if(hits == null || hits.isEmpty())
            return true;
        
        // find the clusters from these hits
        ClusterFinder clusFinder = new ClusterFinder();
        ClusterFitter cf = new ClusterFitter();
        clusters = clusFinder.RecomposeClusters(hits, Constants.getInstance().dcDetector, cf);
        if (clusters ==null || clusters.isEmpty()) {
            return true;
        }
                
        // find the segments from the fitted clusters
        SegmentFinder segFinder = new SegmentFinder();
        segments = segFinder.get_Segments(clusters,
                event,
                Constants.getInstance().dcDetector, false);      
        if (segments.isEmpty()) {
            return true;
        }
        List<Segment> rmSegs = new ArrayList<>();
        // clean up hit-based segments
        double trkDocOverCellSize;
        for (Segment se : segments) {
            trkDocOverCellSize = 0;
            for (FittedHit fh : se.get_fittedCluster()) {
                trkDocOverCellSize += fh.get_ClusFitDoca() / fh.get_CellSize();
            }
            if (trkDocOverCellSize / se.size() > 1.1) {
                rmSegs.add(se);
            }
        }
        segments.removeAll(rmSegs);
        if(segments == null || segments.isEmpty())
            return true;
        
        // Read urwell crosses
        URWellReader uRWellReader = new URWellReader(event, "HB");
        List<URWellCross> urCrosses = uRWellReader.getUrwellCrosses();
        
        // read uRWell-DC clusters at SL1, and separate uRWell crosses into with and without associated SL1 cluster      
        URWellDCClustersReader uRWellDCClustersReader = new URWellDCClustersReader();
        Map<Integer, List<Integer>> map_clsId_uRWellCrossIds = uRWellDCClustersReader.getMapClsIdURWellCrossIds(event); // segment id is the same as cluster id
        List<URWellCross> urCrossesWithSL1 = new ArrayList();
        List<URWellCross> urCrossesWithoutSL1 = new ArrayList();
        for(Segment seg : segments){
            if(seg.get_Superlayer() == 1){
                if(map_clsId_uRWellCrossIds.keySet().contains(seg.get_Id())) {
                    List<URWellCross> matchedURWellCrosses = new ArrayList();
                    for(int uRWellCrossId : map_clsId_uRWellCrossIds.get(seg.get_Id())){
                        for(URWellCross urCrs : urCrosses){
                            if(urCrs.id() == uRWellCrossId){
                                matchedURWellCrosses.add(urCrs);
                                if(!urCrossesWithSL1.contains(urCrs)) urCrossesWithSL1.add(urCrs);                            
                                break;
                            }
                        }
                    }
                    seg.setMatchedURWellCrosses(matchedURWellCrosses);
                }                
            }                
        }                    
        urCrossesWithoutSL1.addAll(urCrosses);
        urCrossesWithoutSL1.removeAll(urCrossesWithSL1);
                
        // make dc crosses
        CrossMaker crossMake = new CrossMaker();
        crosses = crossMake.find_Crosses(segments, Constants.getInstance().dcDetector);
        if (crosses.isEmpty()) {
            event.appendBanks(
                    writer.fillHBSegmentsBank(event, segments));
            return true;
        }

        ////// Take tracking in order
        ////// Overall, tracks with R0 and more DC clusters are preferred
        ////     Order 1: 6 DC clusters with R0: R0R1R2R3
        ////     Order 2: 5 DC clusters with R0: R0SL1R2R3, R0SL2R2R3, R0R1pR2R3, R0R1R2pR3
        ////     Order 3: 6 DC clusters without R0: R1R2R3
        ////     Order 4: 5 DC clusters without R0: pR1R2R3, R1pR2R3, R1R2pR3
        ////     Order 5: 4 DC clusters with R0: R0R2R3
        
                
        //// Seprate segments and crosses
        List<Segment> segmentsSL1WithURWell = new ArrayList();
        List<Segment> segmentsSL1WithoutURWell = new ArrayList();
        List<Segment> segmentsSL2 = new ArrayList();
        List<Segment> segmentsR2R3 = new ArrayList();
        for(Segment seg : segments){
            if(seg.get_Superlayer() == 1){
                if(!seg.getMatchedURWellCrosses().isEmpty()) segmentsSL1WithURWell.add(seg);
                else segmentsSL1WithoutURWell.add(seg);
            }
            else if(seg.get_Superlayer() == 2) segmentsSL2.add(seg);
            else segmentsR2R3.add(seg);
        }        
        
        List<Cross> crossesR1WithURWell = new ArrayList();
        List<Cross> crossesR1WithoutURWell = new ArrayList();
        List<Cross> crossesR2R3 = new ArrayList();
        for(Cross crs : crosses){
            if(crs.get_Region() == 1){
                if(!crs.get_Segment1().getMatchedURWellCrosses().isEmpty()) {
                    crossesR1WithURWell.add(crs);
                }
                else crossesR1WithoutURWell.add(crs);
            }
            else crossesR2R3.add(crs);                
        }        
        
        List<URWellCross> urCrossesRemaining = new ArrayList();
        
        //// Start tracking
        URWellDCCrossesListFinder uRWellDCCrossListLister = new URWellDCCrossesListFinder(); 
        TrackCandListWithURWellFinder trkcandFinder = new TrackCandListWithURWellFinder(Constants.HITBASE);
        RoadFinderWithURWell rf = new RoadFinderWithURWell(); 
        List<Track> trkcands = new ArrayList();
        
        //// Order 1 - 6 DC clusters with uRWell: R0R1R2R3      
        List<Cross> crossesOrder1 = new ArrayList();
        crossesOrder1.addAll(crossesR1WithURWell);
        crossesOrder1.addAll(crossesR2R3);
        
        // Make cross lists
        URWellDCCrossesList crossListsOrder1 = uRWellDCCrossListLister.candCrossListsWithURWell(event, crossesOrder1, null,
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false, 3
        );
        
        // Tracking
        List<Track> trkcandsOrder1 = trkcandFinder.getTrackCands(crossListsOrder1,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);
        
        // Remove overlaps
        if (!trkcandsOrder1.isEmpty()) trkcandFinder.removeOverlappingTracks(trkcandsOrder1);                   
        
        // Add tracks into track list
        trkcands.addAll(trkcandsOrder1);

        // Remove segments and crosses on tracks from lists
        for(Track trk : trkcandsOrder1){            
            for(URWellCross crs : trk.get_URWellCrosses()){
                if(urCrossesWithSL1.contains(crs)) urCrossesWithSL1.remove(crs);
                if(urCrossesWithoutSL1.contains(crs)) urCrossesWithoutSL1.remove(crs);
            }
                        
            for(Cross crs : trk){
                if(crossesR1WithURWell.contains(crs)) crossesR1WithURWell.remove(crs);
                if(crossesR1WithoutURWell.contains(crs)) crossesR1WithoutURWell.remove(crs);
                if(crossesR2R3.contains(crs)) crossesR2R3.remove(crs);
                for(Segment seg : crs){
                    if(segmentsSL1WithURWell.contains(seg)) segmentsSL1WithURWell.remove(seg);
                    if(segmentsSL1WithoutURWell.contains(seg)) segmentsSL1WithoutURWell.remove(seg);
                    if(segmentsSL2.contains(seg)) segmentsSL2.remove(seg);
                    if(segmentsR2R3.contains(seg)) segmentsR2R3.remove(seg);
                }                     
                
            }
        }        
        urCrossesRemaining.clear();
        urCrossesRemaining.addAll(urCrossesWithSL1);
        urCrossesRemaining.addAll(urCrossesWithoutSL1);        
               
        //// Order 2 - 5 DC clusters with uRWell: R0-SL1R2R3, R0SL2R2R3, R0R1pR2R3, R0R1R2pR3
        List<Segment> segmentsR0R1R2R3Order2 = new ArrayList();
        segmentsR0R1R2R3Order2.addAll(segmentsSL1WithURWell);
        segmentsR0R1R2R3Order2.addAll(segmentsSL2);
        segmentsR0R1R2R3Order2.addAll(segmentsR2R3);
        
        // Build pseudo segments
        List<Road> allRoadsOrder2 = rf.findRoadsWithURWell(segmentsR0R1R2R3Order2, Constants.getInstance().dcDetector);
        List<Segment> psegmentsOrder2 = new ArrayList<>();
        List<Segment> Segs2RoadOrder2 = new ArrayList<>();
        for (Road r : allRoadsOrder2) { 
            Segs2RoadOrder2.clear();
            int missingSL = -1;
            for (int ri = 0; ri < 3; ri++) {
                if (r.get(ri).associatedCrossId == -1) {
                    if (r.get(ri).get_Superlayer() % 2 == 1) {
                        missingSL = r.get(ri).get_Superlayer() + 1;
                    } else {
                        missingSL = r.get(ri).get_Superlayer() - 1;
                    }
                }
            } 
            if(missingSL==-1) 
                continue;
            for (int ri = 0; ri < 3; ri++) {
                for (Segment s : segmentsR0R1R2R3Order2) {
                    if (s.get_Sector() == r.get(ri).get_Sector() &&
                            s.get_Region() == r.get(ri).get_Region() &&
                            s.associatedCrossId == r.get(ri).associatedCrossId &&
                            r.get(ri).associatedCrossId != -1) {
                        if (s.get_Superlayer() % 2 == missingSL % 2){
                            Segs2RoadOrder2.add(s); 
                            break;
                        }
                    }
                }
            }
            if (Segs2RoadOrder2.size() == 2) {
                Segment pSegment = rf.findRoadMissingSegment(Segs2RoadOrder2, Constants.getInstance().dcDetector,r.a);
                if (pSegment != null) psegmentsOrder2.add(pSegment);
            }
        }

        segmentsR0R1R2R3Order2.addAll(psegmentsOrder2);
        
        // Make cross with remaning segments
        List<Cross> crossesOrder2 = crossMake.find_Crosses(segmentsR0R1R2R3Order2, Constants.getInstance().dcDetector);
        List<Cross> fullPseudoCrossesOrder2 = new ArrayList(); // Cross by two pseudo segments
        for(Cross crs : crossesOrder2){
            if(crs.get_Segment1().get_Id() == -1 && crs.get_Segment2().get_Id() == -1) fullPseudoCrossesOrder2.add(crs);
        }
        crossesOrder2.removeAll(fullPseudoCrossesOrder2);
        
        // Make cross lists
        URWellDCCrossesList crossListsOrder2 = uRWellDCCrossListLister.candCrossListsWithURWell(event, crossesOrder2, urCrossesRemaining,
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false, 3
        );
        
        // Tracking
        List<Track> trkcandsOrder2 = trkcandFinder.getTrackCands(crossListsOrder2,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);
        
        // Remove overlaps
        if (!trkcandsOrder2.isEmpty()) trkcandFinder.removeOverlappingTracks(trkcandsOrder2);                   
        
        // Add tracks into track list
        trkcands.addAll(trkcandsOrder2);

        // Remove segments and crosses on tracks from lists
        for(Track trk : trkcandsOrder2){
            for(URWellCross crs : trk.get_URWellCrosses()){
                if(urCrossesWithSL1.contains(crs)) urCrossesWithSL1.remove(crs);
                if(urCrossesWithoutSL1.contains(crs)) urCrossesWithoutSL1.remove(crs);
            }
            
            for(Cross crs : trk){
                if(crossesR1WithURWell.contains(crs)) crossesR1WithURWell.remove(crs);
                if(crossesR1WithoutURWell.contains(crs)) crossesR1WithoutURWell.remove(crs);
                if(crossesR2R3.contains(crs)) crossesR2R3.remove(crs);
                for(Segment seg : crs){
                    if(segmentsSL1WithURWell.contains(seg)) segmentsSL1WithURWell.remove(seg);
                    if(segmentsSL1WithoutURWell.contains(seg)) segmentsSL1WithoutURWell.remove(seg);
                    if(segmentsSL2.contains(seg)) segmentsSL2.remove(seg);
                    if(segmentsR2R3.contains(seg)) segmentsR2R3.remove(seg);
                }                     
                
            }
        }
        urCrossesRemaining.clear();
        urCrossesRemaining.addAll(urCrossesWithSL1);
        urCrossesRemaining.addAll(urCrossesWithoutSL1);   

        //// Order 3 - 6 DC clusters without R0: R1R2R3
        List<Cross> crossesOrder3 = new ArrayList();
        crossesOrder3.addAll(crossesR1WithURWell);
        crossesOrder3.addAll(crossesR1WithoutURWell);
        crossesOrder3.addAll(crossesR2R3);
        
        // Make cross lists                           
        URWellDCCrossesList crossListsOrder3 = uRWellDCCrossListLister.candURWellDCCrossesLists(event, crossesOrder3, new ArrayList(),
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false, 3
        );
        
        // Tracking
        List<Track> trkcandsOrder3 = trkcandFinder.getTrackCands3URDCCrosses(crossListsOrder3,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);
        
        // Remove overlaps
        if (!trkcandsOrder3.isEmpty()) trkcandFinder.removeOverlappingTracks(trkcandsOrder3);                   
        
        // Add tracks into track list
        trkcands.addAll(trkcandsOrder3);

        // Remove segments and crosses on tracks from lists
        for(Track trk : trkcandsOrder3){
            for(Cross crs : trk){
                if(crossesR1WithURWell.contains(crs)) crossesR1WithURWell.remove(crs);
                if(crossesR1WithoutURWell.contains(crs)) crossesR1WithoutURWell.remove(crs);
                if(crossesR2R3.contains(crs)) crossesR2R3.remove(crs);
                for(Segment seg : crs){
                    if(segmentsSL1WithURWell.contains(seg)) segmentsSL1WithURWell.remove(seg);
                    if(segmentsSL1WithoutURWell.contains(seg)) segmentsSL1WithoutURWell.remove(seg);
                    if(segmentsSL2.contains(seg)) segmentsSL2.remove(seg);
                    if(segmentsR2R3.contains(seg)) segmentsR2R3.remove(seg);
                }                     
                
            }
        }

        //// Order 4 - 5 DC clusters without R0: pR1R2R3, R1pR2R3, R1R2pR3
        List<Segment> segmentsR1R2R3Order4 = new ArrayList();
        segmentsR1R2R3Order4.addAll(segmentsSL1WithURWell);
        segmentsR1R2R3Order4.addAll(segmentsSL1WithoutURWell);
        segmentsR1R2R3Order4.addAll(segmentsSL2);
        segmentsR1R2R3Order4.addAll(segmentsR2R3);
        
        // Build pseudo segments
        List<Road> allRoadsOrder4 = rf.findRoads(segmentsR1R2R3Order4, Constants.getInstance().dcDetector);
        List<Segment> psegmentsOrder4 = new ArrayList<>();
        List<Segment> Segs2RoadOrder4 = new ArrayList<>();
        for (Road r : allRoadsOrder4) { 
            Segs2RoadOrder4.clear();
            int missingSL = -1;
            for (int ri = 0; ri < 3; ri++) {
                if (r.get(ri).associatedCrossId == -1) {
                    if (r.get(ri).get_Superlayer() % 2 == 1) {
                        missingSL = r.get(ri).get_Superlayer() + 1;
                    } else {
                        missingSL = r.get(ri).get_Superlayer() - 1;
                    }
                }
            } 
            if(missingSL==-1) 
                continue;
            for (int ri = 0; ri < 3; ri++) {
                for (Segment s : segmentsR1R2R3Order4) {
                    if (s.get_Sector() == r.get(ri).get_Sector() &&
                            s.get_Region() == r.get(ri).get_Region() &&
                            s.associatedCrossId == r.get(ri).associatedCrossId &&
                            r.get(ri).associatedCrossId != -1) {
                        if (s.get_Superlayer() % 2 == missingSL % 2){
                            Segs2RoadOrder4.add(s); 
                            break;
                        }
                    }
                }
            }
            if (Segs2RoadOrder4.size() == 2) {
                Segment pSegment = rf.findRoadMissingSegment(Segs2RoadOrder4, Constants.getInstance().dcDetector,r.a);
                if (pSegment != null) psegmentsOrder4.add(pSegment);
            }
        }

        segmentsR1R2R3Order4.addAll(psegmentsOrder4);
        
        // Make cross with remaning segments
        List<Cross> crossesOrder4 = crossMake.find_Crosses(segmentsR1R2R3Order4, Constants.getInstance().dcDetector);
        List<Cross> fullPseudoCrossesOrder4 = new ArrayList(); // Cross by two pseudo segments
        for(Cross crs : crossesOrder4){
            if(crs.get_Segment1().get_Id() == -1 && crs.get_Segment2().get_Id() == -1) fullPseudoCrossesOrder4.add(crs);
        }
        crossesOrder4.removeAll(fullPseudoCrossesOrder4);
        
        // Make cross lists
        URWellDCCrossesList crossListsOrder4 = uRWellDCCrossListLister.candURWellDCCrossesLists(event, crossesOrder4, new ArrayList(),
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false, 3
        );

        // Tracking
        List<Track> trkcandsOrder4 = trkcandFinder.getTrackCands3URDCCrosses(crossListsOrder4,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);
        
        // Remove overlaps
        if (!trkcandsOrder4.isEmpty()) trkcandFinder.removeOverlappingTracks(trkcandsOrder4);                   
        
        // Add tracks into track list
        trkcands.addAll(trkcandsOrder4);
        
        // Remove segments and crosses on tracks from lists
        for(Track trk : trkcandsOrder4){
            for(Cross crs : trk){
                if(crossesR1WithURWell.contains(crs)) crossesR1WithURWell.remove(crs);
                if(crossesR1WithoutURWell.contains(crs)) crossesR1WithoutURWell.remove(crs);
                if(crossesR2R3.contains(crs)) crossesR2R3.remove(crs);
                for(Segment seg : crs){
                    if(segmentsSL1WithURWell.contains(seg)) segmentsSL1WithURWell.remove(seg);
                    if(segmentsSL1WithoutURWell.contains(seg)) segmentsSL1WithoutURWell.remove(seg);
                    if(segmentsSL2.contains(seg)) segmentsSL2.remove(seg);
                    if(segmentsR2R3.contains(seg)) segmentsR2R3.remove(seg);
                }                     
                
            }
        }
                
        //// Order 5 - 4 DC clusters with R0: R0R2R3 (R1 is replaced by R0)
        List<Cross> crossesOrder5 = new ArrayList();
        crossesOrder5.addAll(crossesR2R3);
        
        // Make cross lists
        URWellDCCrossesList crossListsOrder5 = uRWellDCCrossListLister.candCrossListsWithURWell(event, crossesOrder5, urCrossesRemaining,
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false, 2
        );
        
        // Tracking
        List<Track> trkcandsOrder5 = trkcandFinder.getTrackCands3URDCCrosses(crossListsOrder5,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);
        
        // Remove overlaps
        if (!trkcandsOrder5.isEmpty()) trkcandFinder.removeOverlappingTracks(trkcandsOrder5);                   
        
        // Add tracks into track list
        trkcands.addAll(trkcandsOrder5);

        //// gather all the hits and URWell crosses for pointer bank creation   
        int trkId = 1;
        List<FittedHit> fhits = new ArrayList<>();         
        for (Track trk : trkcands) {
            trk.calcTrajectory(trk.getId(), dcSwim, trk.get_Vtx0(), trk.get_pAtOrig(), trk.get_Q());
            
            // reset the id
            trk.set_Id(trkId);
            trkcandFinder.matchHits(trk.getStateVecs(),
                    trk,
                    Constants.getInstance().dcDetector,
                    dcSwim);
            
            if(!trk.get_URWellCrosses().isEmpty()){
                urCrossesOnTrks.addAll(trk.get_URWellCrosses());
                for(URWellCross crs : trk.get_URWellCrosses()){
                    crs.set_tid(trk.get_Id());
                }
            }            
            
            for (Cross c : trk) {
                c.set_CrossDirIntersSegWires();
                trkcandFinder.setHitDoubletsInfo(c.get_Segment1());
                trkcandFinder.setHitDoubletsInfo(c.get_Segment2());
                for (FittedHit h1 : c.get_Segment1()) {
                    if(h1.get_AssociatedHBTrackID()>0) fhits.add(h1);
                }
                for (FittedHit h2 : c.get_Segment2()) {
                    if(h2.get_AssociatedHBTrackID()>0) fhits.add(h2);
                }
            }
            
            trkId++;
        }
                                       
        // no candidate found, stop here and save the hits,
        // the clusters, the segments, the crosses
        if (trkcands.isEmpty()) {
             event.appendBanks(
                    writer.fillHBHitsBank(event, fhits),
                    writer.fillHBClustersBank(event, clusters),
                    writer.fillHBSegmentsBank(event, segments),
                    writer.fillHBCrossesBank(event, crosses));                      
        } else {
            event.appendBanks(
                    writer.fillHBHitsBank(event, fhits),
                    writer.fillHBClustersBank(event, clusters),
                    writer.fillHBSegmentsBank(event, segments),
                    writer.fillHBCrossesBank(event, crosses),
                    writer.fillHBURWellCrossesBank(event, urCrossesOnTrks),
                    writer.fillHBTracksBank(event, trkcands),
                    writer.fillHBHitsTrkIdBank(event, fhits),
                    writer.fillHBTrajectoryBank(event, trkcands));
        }
        return true;
    }       
}
