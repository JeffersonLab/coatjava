package org.jlab.service.dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import org.jlab.clas.swimtools.Swim;
import org.jlab.clas.swimtools.Swimmer;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.dc.Constants;
import org.jlab.rec.dc.banks.HitReader;
import org.jlab.rec.dc.banks.RecoBankWriter;
import org.jlab.rec.dc.banks.URWellDCClustersReader;
import org.jlab.rec.dc.cluster.ClusterFinder;
import org.jlab.rec.dc.cluster.ClusterFitter;
import org.jlab.rec.dc.cluster.FittedCluster;
import org.jlab.rec.dc.cross.Cross;
import org.jlab.rec.dc.cross.CrossList;
import org.jlab.rec.dc.cross.CrossListFinder;
import org.jlab.rec.dc.cross.CrossMaker;
import org.jlab.rec.dc.hit.FittedHit;
import org.jlab.rec.dc.hit.Hit;
import org.jlab.rec.dc.nn.PatternRec;
import org.jlab.rec.dc.segment.Segment;
import org.jlab.rec.dc.track.Track;
import org.jlab.rec.dc.track.TrackCandListWithURWellFinder;
import org.jlab.rec.dc.cross.URWellDCCrossesList;
import org.jlab.rec.dc.cross.URWellDCCrossesListFinder;
import org.jlab.rec.dc.segment.SegmentFinder;
import org.jlab.rec.dc.track.TrackCandListFinder;
import org.jlab.rec.dc.trajectory.Road;
import org.jlab.rec.dc.trajectory.RoadFinderWithURWell;
import org.jlab.rec.urwell.reader.URWellCross;
import org.jlab.rec.urwell.reader.URWellReader;

/**
 *
 * @author Tongtong Cao
 */
public class DCURWellHBPostClusterAI extends DCEngine {

    public DCURWellHBPostClusterAI() {
        super("DCHAI");
        this.getBanks().init("HitBasedTrkg", "", "AI");
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
        if(run==0) {
            LOGGER.log(Level.INFO, "RUN=0: Skipping event");
            return true;
        }
        
        /* IO */
        HitReader reader      = new HitReader(this.getBanks(), Constants.getInstance().dcDetector);
        reader.initialize(event);
        RecoBankWriter writer = new RecoBankWriter(this.getBanks());
        // get Field
        Swim dcSwim = new Swim();

        LOGGER.log(Level.FINEST, "HB AI process event");
        
        List<Track> trkcands = new ArrayList();
        List<Cross> crosses = null;
        List<FittedCluster> clusters = new ArrayList<>();
        List<Segment> segments = null;
        List<FittedHit> fhits = null;
                
        ////// AI-assisted tracking

        reader.read_NNHits(event);

        // Get the lists
        List<Hit> hits = reader.get_DCHits();
        fhits = new ArrayList<>();
        
        if (hits.isEmpty()) {
            return true;
        }
        
        // segments
        PatternRec pr = new PatternRec();
        segments = pr.RecomposeSegments(hits, Constants.getInstance().dcDetector);
        Collections.sort(segments);

        if (segments.isEmpty()) {
            return true;
        } 
        
        // crossLists
        CrossList crosslists = pr.RecomposeCrossList(segments, Constants.getInstance().dcDetector);
        crosses = new ArrayList<>();
        
        LOGGER.log(Level.FINEST, "num cands = "+crosslists.size());
        for (List<Cross> clist : crosslists) {
            crosses.addAll(clist); 
            for(Cross c : clist)
                LOGGER.log(Level.FINEST, "Pass Cross"+c.printInfo());
        }
        if (crosses.isEmpty()) {
            clusters = new ArrayList<>();
            for(Segment seg : segments) {
                clusters.add(seg.get_fittedCluster());
            }
            event.appendBanks(
                    writer.fillHBHitsBank(event, fhits),    
                    writer.fillHBClustersBank(event, clusters),
                    writer.fillHBSegmentsBank(event, segments));
            return true;
        } 
        // update B field
        CrossListFinder crossLister = new CrossListFinder();
        for(Cross cr : crosses) {
            crossLister.updateBFittedHits(event, cr, null, Constants.getInstance().dcDetector, null, dcSwim);
        }
        
        // Read urwell crosses, and make urwell-dc-crosses combos
        URWellReader uRWellReader = new URWellReader(event, "HB");
        List<URWellCross> urCrosses = uRWellReader.getUrwellCrosses(); 
        
        // Set NNTrkId for uRwell crosses
        Map<Integer, List<URWellCross>> map_nnTrkId_urCrses = new HashMap();
        if (event.hasBank(this.getBanks().getAiBank())) {       
            Map<Integer, List<Integer>> map_nnTrkId_urCrsIds = new HashMap();
            DataBank bankAI = event.getBank(this.getBanks().getAiBank());
            for (int i = 0; i < bankAI.rows(); i++) {
                int nnTrkId  = (int)bankAI.getByte("id", i);
                int ur1Id = (int)bankAI.getShort("ur1", i);
                int ur2Id = (int)bankAI.getShort("ur2", i);
                
                List<Integer> urCrsIds = new ArrayList();                
                if(ur1Id > 0) urCrsIds.add(ur1Id);
                if(ur2Id > 0) urCrsIds.add(ur2Id);
                map_nnTrkId_urCrsIds.put(nnTrkId, urCrsIds);
            }
            
            Map<Integer, URWellCross> map_urCrsId_urCrs = new HashMap();
            for(URWellCross crs : urCrosses){
                map_urCrsId_urCrs.put(crs.id(), crs);
            }                        
            for(int nnTrkId : map_nnTrkId_urCrsIds.keySet()){
                List<URWellCross> urCrses = new ArrayList();
                for(int crsId : map_nnTrkId_urCrsIds.get(nnTrkId)){
                    if(map_urCrsId_urCrs.containsKey(crsId)) {
                        map_urCrsId_urCrs.get(crsId).setNNTrkId(nnTrkId);
                        urCrses.add(map_urCrsId_urCrs.get(crsId));
                    }
                }
                map_nnTrkId_urCrses.put(nnTrkId, urCrses);
            }            
        }                                             
                                
        // separate cross lists with and without associated uRWell
        TrackCandListWithURWellFinder trkcandFinder = new TrackCandListWithURWellFinder(Constants.HITBASE);  
        
        if(!map_nnTrkId_urCrses.isEmpty()){
            URWellDCCrossesList uRWellDCCrossesLists = new URWellDCCrossesList();
            uRWellDCCrossesLists.set_URWellDCCrossesList(crosslists, map_nnTrkId_urCrses); 


            URWellDCCrossesList crosslistsWithURWell = new URWellDCCrossesList();
            URWellDCCrossesList crosslistsWithoutURWell = new URWellDCCrossesList();

            for(int i = 0; i < uRWellDCCrossesLists.get_URWellDCCrossesList().size(); i++){
                if(!uRWellDCCrossesLists.get_URWellDCCrossesList().get(i).get_DCCrosses().isEmpty()){
                    if(!uRWellDCCrossesLists.get_URWellDCCrossesList().get(i).get_URWellCrosses().isEmpty()) crosslistsWithURWell.add(uRWellDCCrossesLists.get_URWellDCCrossesList().get(i));
                    else crosslistsWithoutURWell.add(uRWellDCCrossesLists.get_URWellDCCrossesList().get(i));
                }
            }
            
            List<Track> trkcandsWithURWell = trkcandFinder.getTrackCands(crosslistsWithURWell,
                    Constants.getInstance().dcDetector,
                    Swimmer.getTorScale(),
                    dcSwim, true);
            List<Track> trkcandsWithoutURWell = trkcandFinder.getTrackCands3URDCCrosses(crosslistsWithoutURWell,
                    Constants.getInstance().dcDetector,
                    Swimmer.getTorScale(),
                    dcSwim, true);       

            trkcands.addAll(trkcandsWithURWell);
            trkcands.addAll(trkcandsWithoutURWell);
            
            if (!trkcands.isEmpty()) {
                // remove overlaps
                trkcandFinder.removeOverlappingTracks(trkcands);
                for (Track trk : trkcands) {
                    trk.setIsAITrack(true);

                    for (Cross c : trk) {
                        clusters.add(c.get_Segment1().get_fittedCluster());
                        clusters.add(c.get_Segment2().get_fittedCluster());
                    }
                }
            }
        }

        ////// Conventional tracking with R0 and without R0 in order
        ////     6 or 5 DC clusters With R0: R0R1R2R3, R0-SL1R2R3, R0SL2R2R3, R0R1pR2R3, R0R1R2pR3
        ////     6 or 5 DC clustersWithout R0: R1R2R3, pR1R2R3, R1pR2R3, R1R2pR3
        ////     4 DC clusters with R0: R0R2R3
        
        List<FittedCluster> clustersConv = null;
        List<Segment> segmentsConv = null;
        List<Cross> crossesConv = null;
        List<Track> trkcandsConv = null;
        
        //1) read hits from the banks
        Map<Integer, ArrayList<FittedHit>> hitsConv = reader.read_Hits(event);
        
        //2) find clusters from these hits
        ClusterFinder clusFinder = new ClusterFinder();
        ClusterFitter cf = new ClusterFitter();
        clustersConv = clusFinder.RecomposeClusters(hitsConv, Constants.getInstance().dcDetector, cf);

        //3) remove clusters which are on tracks
        List<FittedCluster> removedClustersConv = new ArrayList();
        for(FittedCluster cls : clustersConv){
            boolean flag = false;
            for(Track trk : trkcands){
                if(flag) break;
                for(Cross crs : trk){
                    if(cls.get_Id() == crs.get_Segment1().get_Id() || cls.get_Id() == crs.get_Segment2().get_Id()) {
                        removedClustersConv.add(cls);
                        flag = true;
                        break;
                    }
                }
            }
        }
        clustersConv.removeAll(removedClustersConv);
        clusters.addAll(clustersConv);
        
        //4) find segments from clusters
        SegmentFinder segFinder = new SegmentFinder();
        segmentsConv = segFinder.get_Segments(clustersConv,
                event,
                Constants.getInstance().dcDetector, false);
        List<Segment> rmSegsConv = new ArrayList<>();
        // clean up hit-based segments
        double trkDocOverCellSize;
        for (Segment se : segmentsConv) {
            trkDocOverCellSize = 0;
            for (FittedHit fh : se.get_fittedCluster()) {
                trkDocOverCellSize += fh.get_ClusFitDoca() / fh.get_CellSize();
            }
            if (trkDocOverCellSize / se.size() > 1.1) {
                rmSegsConv.add(se);
            }
        }
        segmentsConv.removeAll(rmSegsConv);
        segments.addAll(segmentsConv);
        
        //5) find crosses from segments
        CrossMaker crossMake = new CrossMaker();
        crossesConv = crossMake.find_Crosses(segmentsConv, Constants.getInstance().dcDetector);
        crosses.addAll(crossesConv);
        
        //6) find cross lists from crosses
        CrossList crosslistConv = crossLister.candCrossLists(event, crossesConv,
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false); 
        
        //7) uRWell crosses
        // read uRWell-DC clusters at SL1, and separate uRWell crosses into with and without associated SL1 cluster      
        URWellDCClustersReader uRWellDCClustersReader = new URWellDCClustersReader();
        Map<Integer, List<Integer>> map_clsId_uRWellCrossIds = uRWellDCClustersReader.getMapClsIdURWellCrossIds(event); // segment id is the same as cluster id
        for(Segment seg : segments){
            if(seg.get_Superlayer() == 1){
                if(map_clsId_uRWellCrossIds.keySet().contains(seg.get_Id())) {
                    List<URWellCross> matchedURWellCrosses = new ArrayList();
                    for(int uRWellCrossId : map_clsId_uRWellCrossIds.get(seg.get_Id())){
                        for(URWellCross urCrs : urCrosses){
                            if(urCrs.id() == uRWellCrossId){
                                matchedURWellCrosses.add(urCrs);                                                           
                                break;
                            }
                        }
                    }
                    seg.setMatchedURWellCrosses(matchedURWellCrosses);
                }
            }            
        }          
        
        List<URWellCross> urCrossesWithSL1Conv = new ArrayList();
        List<URWellCross> urCrossesWithoutSL1Conv = new ArrayList();
        for(Segment seg : segmentsConv){
            if(seg.get_Superlayer() == 1){
                if(map_clsId_uRWellCrossIds.keySet().contains(seg.get_Id())) {
                    List<URWellCross> matchedURWellCrosses = new ArrayList();
                    for(int uRWellCrossId : map_clsId_uRWellCrossIds.get(seg.get_Id())){
                        for(URWellCross urCrs : urCrosses){
                            if(urCrs.id() == uRWellCrossId){
                                matchedURWellCrosses.add(urCrs);
                                if(!urCrossesWithSL1Conv.contains(urCrs)) urCrossesWithSL1Conv.add(urCrs);                            
                                break;
                            }
                        }
                    }
                    seg.setMatchedURWellCrosses(matchedURWellCrosses);
                }
            }            
        }        
        urCrossesWithoutSL1Conv.addAll(urCrosses);
        urCrossesWithoutSL1Conv.removeAll(urCrossesWithSL1Conv);
        for(Track trk : trkcands){
            for(URWellCross crs : trk.get_URWellCrosses()){
                if(urCrossesWithoutSL1Conv.contains(crs)) urCrossesWithoutSL1Conv.remove(crs);
            }
        }
        
        //8) tracking
        URWellDCCrossesListFinder uRWellDCCrossListLister = new URWellDCCrossesListFinder(); 
        RoadFinderWithURWell rf = new RoadFinderWithURWell();
        
        //// Seprate segments and crosses
        List<Segment> segmentsSL1WithURWellConv = new ArrayList();
        List<Segment> segmentsSL1WithoutURWellConv = new ArrayList();
        List<Segment> segmentsSL2Conv = new ArrayList();
        List<Segment> segmentsR2R3Conv = new ArrayList();
        for(Segment seg : segmentsConv){
            if(seg.get_Superlayer() == 1){
                if(!seg.getMatchedURWellCrosses().isEmpty()) segmentsSL1WithURWellConv.add(seg);
                else segmentsSL1WithoutURWellConv.add(seg);
            }
            else if(seg.get_Superlayer() == 2) segmentsSL2Conv.add(seg);
            else segmentsR2R3Conv.add(seg);
        }        
        
        List<Cross> crossesR1WithURWellConv = new ArrayList();
        List<Cross> crossesR1WithoutURWellConv = new ArrayList();
        List<Cross> crossesR2R3Conv = new ArrayList();
        for(Cross crs : crossesConv){
            if(crs.get_Region() == 1){
                if(!crs.get_Segment1().getMatchedURWellCrosses().isEmpty()) {
                    crossesR1WithURWellConv.add(crs);
                }
                else crossesR1WithoutURWellConv.add(crs);
            }
            else crossesR2R3Conv.add(crs);                
        }        
        
        List<URWellCross> urCrossesRemainingConv = new ArrayList();
        urCrossesRemainingConv.clear();
        urCrossesRemainingConv.addAll(urCrossesWithSL1Conv);
        urCrossesRemainingConv.addAll(urCrossesWithoutSL1Conv); 

        //// 6 or 5 DC clusters With R0
        /// Type 1: R0R1R2R3
        List<Cross> crossesType1Order1 = new ArrayList();
        crossesType1Order1.addAll(crossesR1WithURWellConv);
        crossesType1Order1.addAll(crossesR2R3Conv);
        
        // Make cross lists
        URWellDCCrossesList crossListsType1Order1 = uRWellDCCrossListLister.candCrossListsWithURWell(event, crossesType1Order1, null,
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false, 3
        );
        
        // Tracking
        List<Track> trkcandsType1Order1 = trkcandFinder.getTrackCands(crossListsType1Order1,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);
        
        
        /// Type 2: R0-SL1R2R3, R0SL2R2R3, R0R1pR2R3, R0R1R2pR3
        List<Segment> segmentsType2Order1 = new ArrayList();
        segmentsType2Order1.addAll(segmentsSL1WithURWellConv);
        segmentsType2Order1.addAll(segmentsSL2Conv);
        segmentsType2Order1.addAll(segmentsR2R3Conv);
        
        // Build pseudo segments
        List<Road> allRoadsType2Order1 = rf.findRoadsWithURWell(segmentsType2Order1, Constants.getInstance().dcDetector);
        List<Segment> psegmentsType2Order1 = new ArrayList<>();
        List<Segment> Segs2RoadType2Order1 = new ArrayList<>();
        for (Road r : allRoadsType2Order1) { 
            Segs2RoadType2Order1.clear();
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
                for (Segment s : segmentsType2Order1) {
                    if (s.get_Sector() == r.get(ri).get_Sector() &&
                            s.get_Region() == r.get(ri).get_Region() &&
                            s.associatedCrossId == r.get(ri).associatedCrossId &&
                            r.get(ri).associatedCrossId != -1) {
                        if (s.get_Superlayer() % 2 == missingSL % 2){
                            Segs2RoadType2Order1.add(s); 
                            break;
                        }
                    }
                }
            }
            if (Segs2RoadType2Order1.size() == 2) {
                Segment pSegment = rf.findRoadMissingSegment(Segs2RoadType2Order1, Constants.getInstance().dcDetector,r.a);
                if (pSegment != null) psegmentsType2Order1.add(pSegment);
            }
        }

        segmentsType2Order1.addAll(psegmentsType2Order1);
        
        // Make cross with remaning segments
        List<Cross> crossesType2Order1 = crossMake.find_Crosses(segmentsType2Order1, Constants.getInstance().dcDetector);
        List<Cross> fullPseudoCrossesType2Order1 = new ArrayList(); // Cross by two pseudo segments
        for(Cross crs : crossesType2Order1){
            if(crs.get_Segment1().get_Id() == -1 && crs.get_Segment2().get_Id() == -1) fullPseudoCrossesType2Order1.add(crs);
        }
        crossesType2Order1.removeAll(fullPseudoCrossesType2Order1);
        
        // Make cross lists
        URWellDCCrossesList crossListsType2Order1 = uRWellDCCrossListLister.candCrossListsWithURWell(event, crossesType2Order1, urCrossesRemainingConv,
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false, 3
        );
        
        // Tracking
        List<Track> trkcandsType2Order1 = trkcandFinder.getTrackCands(crossListsType2Order1,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);
        

        
        /// Combine all types together
        List<Track> trkcandsOrder1 = new ArrayList();
        trkcandsOrder1.addAll(trkcandsType1Order1);
        trkcandsOrder1.addAll(trkcandsType2Order1);
        
        // Remove overlaps        
        if (trkcandsOrder1.size() > 0) {
            // remove overlaps
            trkcandFinder.removeOverlappingTracks(trkcandsOrder1);
            for (Track trk : trkcandsOrder1) {
                trk.setIsAITrack(false);
                
                for (Cross c : trk) {
                    clusters.add(c.get_Segment1().get_fittedCluster());
                    clusters.add(c.get_Segment2().get_fittedCluster());
                }
            }
        }              
        
        // Add tracks into track list
        trkcands.addAll(trkcandsOrder1);
        
        // Remove segments and crosses on tracks from lists
        for(Track trk : trkcandsOrder1){
            for(URWellCross crs : trk.get_URWellCrosses()){
                if(urCrossesWithSL1Conv.contains(crs)) urCrossesWithSL1Conv.remove(crs);
                if(urCrossesWithoutSL1Conv.contains(crs)) urCrossesWithoutSL1Conv.remove(crs);
            }
            
            for(Cross crs : trk){
                if(crossesR1WithURWellConv.contains(crs)) crossesR1WithURWellConv.remove(crs);
                if(crossesR1WithoutURWellConv.contains(crs)) crossesR1WithoutURWellConv.remove(crs);
                if(crossesR2R3Conv.contains(crs)) crossesR2R3Conv.remove(crs);
                for(Segment seg : crs){
                    if(segmentsSL1WithURWellConv.contains(seg)) segmentsSL1WithURWellConv.remove(seg);
                    if(segmentsSL1WithoutURWellConv.contains(seg)) segmentsSL1WithoutURWellConv.remove(seg);
                    if(segmentsSL2Conv.contains(seg)) segmentsSL2Conv.remove(seg);
                    if(segmentsR2R3Conv.contains(seg)) segmentsR2R3Conv.remove(seg);
                }                     
                
            }
        }        
        urCrossesRemainingConv.clear();
        urCrossesRemainingConv.addAll(urCrossesWithSL1Conv);
        urCrossesRemainingConv.addAll(urCrossesWithoutSL1Conv);  
        
        //// 6 or 5 DC clusters Without R0
        /// Type 1: R1R2R3
        List<Cross> crossesType1Order2 = new ArrayList();
        crossesType1Order2.addAll(crossesR1WithURWellConv);
        crossesType1Order2.addAll(crossesR1WithoutURWellConv);
        crossesType1Order2.addAll(crossesR2R3Conv);
        
        // Make cross lists                           
        URWellDCCrossesList crossListsType1Order2 = uRWellDCCrossListLister.candURWellDCCrossesLists(event, crossesType1Order2, new ArrayList(),
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false, 3
        );
        
        // Tracking
        List<Track> trkcandsType1Order2 = trkcandFinder.getTrackCands3URDCCrosses(crossListsType1Order2,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);
        
        /// Type 2: pR1R2R3, R1pR2R3, R1R2pR3
        List<Segment> segmentsR1R2R3Type2Order2 = new ArrayList();
        segmentsR1R2R3Type2Order2.addAll(segmentsSL1WithURWellConv);
        segmentsR1R2R3Type2Order2.addAll(segmentsSL1WithoutURWellConv);
        segmentsR1R2R3Type2Order2.addAll(segmentsSL2Conv);
        segmentsR1R2R3Type2Order2.addAll(segmentsR2R3Conv);
        
        // Build pseudo segments
        List<Road> allRoadsType2Order2 = rf.findRoads(segmentsR1R2R3Type2Order2, Constants.getInstance().dcDetector);
        List<Segment> psegmentsType2Order2 = new ArrayList<>();
        List<Segment> Segs2RoadType2Order2 = new ArrayList<>();
        for (Road r : allRoadsType2Order2) { 
            Segs2RoadType2Order2.clear();
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
                for (Segment s : segmentsR1R2R3Type2Order2) {
                    if (s.get_Sector() == r.get(ri).get_Sector() &&
                            s.get_Region() == r.get(ri).get_Region() &&
                            s.associatedCrossId == r.get(ri).associatedCrossId &&
                            r.get(ri).associatedCrossId != -1) {
                        if (s.get_Superlayer() % 2 == missingSL % 2){
                            Segs2RoadType2Order2.add(s); 
                            break;
                        }
                    }
                }
            }
            if (Segs2RoadType2Order2.size() == 2) {
                Segment pSegment = rf.findRoadMissingSegment(Segs2RoadType2Order2, Constants.getInstance().dcDetector,r.a);
                if (pSegment != null) psegmentsType2Order2.add(pSegment);
            }
        }

        segmentsR1R2R3Type2Order2.addAll(psegmentsType2Order2);
        
        // Make cross with remaning segments
        List<Cross> crossesType2Order2 = crossMake.find_Crosses(segmentsR1R2R3Type2Order2, Constants.getInstance().dcDetector);
        List<Cross> fullPseudoCrossesType2Order2 = new ArrayList(); // Cross by two pseudo segments
        for(Cross crs : crossesType2Order2){
            if(crs.get_Segment1().get_Id() == -1 && crs.get_Segment2().get_Id() == -1) fullPseudoCrossesType2Order2.add(crs);
        }
        crossesType2Order2.removeAll(fullPseudoCrossesType2Order2);
        
        // Make cross lists
        URWellDCCrossesList crossListsType2Order2 = uRWellDCCrossListLister.candURWellDCCrossesLists(event, crossesType2Order2, new ArrayList(),
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false, 3
        );

        // Tracking
        List<Track> trkcandsType2Order2 = trkcandFinder.getTrackCands3URDCCrosses(crossListsType2Order2,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);
        
        
         /// Combine all types together
        List<Track> trkcandsOrder2 = new ArrayList();
        trkcandsOrder2.addAll(trkcandsType1Order2);
        trkcandsOrder2.addAll(trkcandsType2Order2);
        
        // Remove overlaps        
        if (trkcandsOrder2.size() > 0) {
            // remove overlaps
            trkcandFinder.removeOverlappingTracks(trkcandsOrder2);
            for (Track trk : trkcandsOrder2) {
                trk.setIsAITrack(false);
                
                for (Cross c : trk) {
                    clusters.add(c.get_Segment1().get_fittedCluster());
                    clusters.add(c.get_Segment2().get_fittedCluster());
                }
            }
        }              
        
        // Add tracks into track list
        trkcands.addAll(trkcandsOrder2); 
        

                  
        // Remove segments and crosses on tracks from lists
        for(Track trk : trkcandsOrder2){
            for(Cross crs : trk){
                if(crossesR1WithURWellConv.contains(crs)) crossesR1WithURWellConv.remove(crs);
                if(crossesR1WithoutURWellConv.contains(crs)) crossesR1WithoutURWellConv.remove(crs);
                if(crossesR2R3Conv.contains(crs)) crossesR2R3Conv.remove(crs);
                for(Segment seg : crs){
                    if(segmentsSL1WithURWellConv.contains(seg)) segmentsSL1WithURWellConv.remove(seg);
                    if(segmentsSL1WithoutURWellConv.contains(seg)) segmentsSL1WithoutURWellConv.remove(seg);
                    if(segmentsSL2Conv.contains(seg)) segmentsSL2Conv.remove(seg);
                    if(segmentsR2R3Conv.contains(seg)) segmentsR2R3Conv.remove(seg);
                }                     
                
            }
        } 
                
        //// 4 DC clusters Without R0
        /// Type 1: R0R2R3
        List<Cross> crossesType1Order3 = new ArrayList();
        crossesType1Order3.addAll(crossesR2R3Conv);
        
        // Make cross lists
        URWellDCCrossesList crossListsType1Order3 = uRWellDCCrossListLister.candCrossListsWithURWell(event, crossesType1Order3, urCrossesRemainingConv,
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false, 2
        );
        
        // Tracking
        List<Track> trkcandsType1Order3 = trkcandFinder.getTrackCands3URDCCrosses(crossListsType1Order3,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);        
        
        
        // Remove overlaps        
        if (trkcandsType1Order3.size() > 0) {
            // remove overlaps
            trkcandFinder.removeOverlappingTracks(trkcandsType1Order3);
            for (Track trk : trkcandsType1Order3) {
                trk.setIsAITrack(false);
                
                for (Cross c : trk) {
                    clusters.add(c.get_Segment1().get_fittedCluster());
                    clusters.add(c.get_Segment2().get_fittedCluster());
                }
            }
        }              
        
        // Add tracks into track list
        trkcands.addAll(trkcandsType1Order3); 
      
        //gather all the hits and URWell crosses for pointer bank creation  
        int trkId = 1;
        List<URWellCross> urCrossesOnTrks = new ArrayList<>();
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
                    writer.fillHBSegmentsBank(event, segments),
                    writer.fillHBCrossesBank(event, crosses));
        }
        else {
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
    
    
    public List<Segment> get_segments_convOnly(List<Segment> segmentsAI, List<Segment> segmentsConv){
        List<Segment> segmentsShare = new ArrayList<>();
        for(Segment segmentConv : segmentsConv){            
            for(Segment segmentAI: segmentsAI){
                if(segmentConv.get_Id() == segmentAI.get_Id()){
                    segmentsShare.add(segmentConv);
                    break;
                }
            }
        }
        
        List<Segment> segmentsConvOnly = new ArrayList<>();
        segmentsConvOnly.addAll(segmentsConv);
        segmentsConvOnly.removeAll(segmentsShare);            
        
        return segmentsConvOnly;        
    }            
}
