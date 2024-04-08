package org.jlab.service.dc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.jlab.clas.swimtools.Swim;
import org.jlab.clas.swimtools.Swimmer;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.dc.Constants;
import org.jlab.rec.dc.banks.HitReader;
import org.jlab.rec.dc.banks.RecoBankWriter;
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
import org.jlab.rec.dc.segment.SegmentFinder;
import org.jlab.rec.dc.track.Track;
import org.jlab.rec.dc.track.TrackCandListFinder;
import org.jlab.rec.dc.trajectory.Road;
import org.jlab.rec.dc.trajectory.RoadFinder;

/**
 *
 * @author ziegler
 */
public class DCHBPostClusterAI extends DCEngine {

    public DCHBPostClusterAI() {
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
    public boolean processDataEvent(DataEvent event) {
        
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
        /* 2 */
        
        /* 5 */
        LOGGER.log(Level.FINE, "HB AI process event");
        /* 7 */
        /* 8 */
        //AI
        List<Track> trkcands = new ArrayList<>();
        List<Cross> crosses = new ArrayList<>();
        List<FittedCluster> clusters = new ArrayList<>();
        List<Segment> segments = null;
        List<FittedHit> fhits = null;

        reader.read_NNHits(event);

        //I) get the lists
        List<Hit> hits = reader.get_DCHits();
        fhits = new ArrayList<>();
        //II) process the hits
        //1) exit if hit list is empty
        if (hits.isEmpty()) {
            return true;
        }
        PatternRec pr = new PatternRec();
        segments = pr.RecomposeSegments(hits, Constants.getInstance().dcDetector);
        Collections.sort(segments);

        if (segments.isEmpty()) {
            return true;
        } 
        //crossList
        CrossList crosslist = pr.RecomposeCrossList(segments, Constants.getInstance().dcDetector);
        
        LOGGER.log(Level.FINE, "num cands = "+crosslist.size());
        for (List<Cross> clist : crosslist) {
            crosses.addAll(clist); 
            for(Cross c : clist)
                LOGGER.log(Level.FINE, "Pass Cross"+c.printInfo());
        }
        if (crosses.isEmpty()) {
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
        //find the list of  track candidates
        TrackCandListFinder trkcandFinder = new TrackCandListFinder(Constants.HITBASE);
        List<Track> trkcands1 = trkcandFinder.getTrackCands(crosslist,
            Constants.getInstance().dcDetector,
            Swimmer.getTorScale(),
            dcSwim, true);

        // track found
        int trkId = 1;
        if (trkcands1.size() > 0) {
            // remove overlaps
            trkcandFinder.removeOverlappingTracks(trkcands1);
            for (Track trk : trkcands1) {
                // reset the id
                trk.set_Id(trkId);
                trkcandFinder.matchHits(trk.getStateVecs(),
                        trk,
                        Constants.getInstance().dcDetector,
                        dcSwim);
                trkId++;
            }
        }
        
        trkcands.addAll(trkcands1);
        
        //// Find crosses only from conventional reconstruction (not exist in AI-assisted reconstruction)
        // Find segments from conventional reconstruction
        List<FittedCluster> clustersConv = null;
        List<Segment> segmentsConv = null;        
        Map<Integer, ArrayList<FittedHit>> hitsConv = reader.read_Hits(event);
        if(hitsConv != null && !hitsConv.isEmpty()){
            //find the segments from these hits
            ClusterFinder clusFinder = new ClusterFinder();
            ClusterFitter cf = new ClusterFitter();
            clustersConv = clusFinder.RecomposeClusters(hitsConv, Constants.getInstance().dcDetector, cf);
            if (clustersConv !=null && !clustersConv.isEmpty()) {                   
                //find the segments from the fitted clusters
                SegmentFinder segFinder = new SegmentFinder();
                segmentsConv = segFinder.get_Segments(clustersConv,
                        event,
                        Constants.getInstance().dcDetector, false);

                if (!segmentsConv.isEmpty()) {
                    List<Segment> rmSegs = new ArrayList<>();
                    // clean up hit-based segments
                    double trkDocOverCellSize;
                    for (Segment se : segmentsConv) {
                        trkDocOverCellSize = 0;
                        for (FittedHit fh : se.get_fittedCluster()) {
                            trkDocOverCellSize += fh.get_ClusFitDoca() / fh.get_CellSize();
                        }
                        if (trkDocOverCellSize / se.size() > 1.1) {
                            rmSegs.add(se);
                        }
                    }
                    segmentsConv.removeAll(rmSegs);
                }       
            }
        }
        // find segments and clusters exist in convential reconstruction, but does not exit in AI-assisted reconstruction
        List<Segment> segmentsConvOnly = get_segments_convOnly(segments, segmentsConv);  
        segments.addAll(segmentsConvOnly);
       // Find crosses exist in convential reconstruction, but does not exit in AI-assisted reconstruction
        CrossMaker crossMake = new CrossMaker();
        List<Cross> crossesConvOnly = crossMake.find_Crosses(segmentsConvOnly, Constants.getInstance().dcDetector);
                
        //// Collect remaining crosses, which are not on tracks
        // Remove real dc crosses on tracks
        // Add real dc crosses into crossesOnTrack list from previous stage
        List<Cross> dcCrossesOnTrack1 = new ArrayList();
        for(Cross dcCross : crosses){
            Segment seg1 = dcCross.get_Segment1();
            Segment seg2 = dcCross.get_Segment2();
            if(seg1.isOnTrack==true && seg2.isOnTrack==true && seg1.associatedCrossId==seg2.associatedCrossId){
                dcCrossesOnTrack1.add(dcCross);
            }
        }
        crosses.removeAll(dcCrossesOnTrack1);
        // Add real dc crosses by segments from conventional only
        crosses.addAll(crossesConvOnly);
        
        //
        CrossList crosslist2 = crossLister.candCrossLists(event, crosses,
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false);
        //find the list of  track candidates for remaining AI crosses with additional of conventral corsses not existing in AI-assisted reconstruction
        List<Track> trkcands2 = trkcandFinder.getTrackCands(crosslist2,
            Constants.getInstance().dcDetector,
            Swimmer.getTorScale(),
            dcSwim, true);
        if (trkcands2.size() > 0) {
            // remove overlaps
            trkcandFinder.removeOverlappingTracks(trkcands2);
            for (Track trk : trkcands2) {
                // reset the id
                trk.set_Id(trkId);
                trkcandFinder.matchHits(trk.getStateVecs(),
                        trk,
                        Constants.getInstance().dcDetector,
                        dcSwim);
                trkId++;
            }
        }
        
        trkcands.addAll(trkcands2);                                                  
                                
        // Further remove real dc crosses on tracks
        // Add real dc crosses into crossesOnTrack list from previous stage
        List<Cross> dcCrossesOnTrack2 = new ArrayList();
        for(Cross dcCross : crosses){
            Segment seg1 = dcCross.get_Segment1();
            Segment seg2 = dcCross.get_Segment2();
            if(seg1.isOnTrack==true && seg2.isOnTrack==true && seg1.associatedCrossId==seg2.associatedCrossId){
                dcCrossesOnTrack2.add(dcCross);
            }
        }
        crosses.removeAll(dcCrossesOnTrack2);
                
        // Find conventional pseudo-segments and construct pseudo-crosses
        List<Segment> crossSegsNotOnTrack = new ArrayList<>();
        List<Segment> psegments = new ArrayList<>();
        for (Cross c : crosses) {
            if (!c.get_Segment1().isOnTrack)
                crossSegsNotOnTrack.add(c.get_Segment1());
            if (!c.get_Segment2().isOnTrack)
                crossSegsNotOnTrack.add(c.get_Segment2());
        }

        RoadFinder rf = new RoadFinder();
        List<Road> allRoads = rf.findRoads(segments, Constants.getInstance().dcDetector);
        List<Segment> Segs2Road = new ArrayList<>();
        for (Road r : allRoads) { 
            Segs2Road.clear();
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
                for (Segment s : crossSegsNotOnTrack) {
                    if (s.get_Sector() == r.get(ri).get_Sector() &&
                            s.get_Region() == r.get(ri).get_Region() &&
                            s.associatedCrossId == r.get(ri).associatedCrossId &&
                            r.get(ri).associatedCrossId != -1) {
                        if (s.get_Superlayer() % 2 == missingSL % 2)
                            Segs2Road.add(s); 
                    }
                }
            }
            if (Segs2Road.size() == 2) {
                Segment pSegment = rf.findRoadMissingSegment(Segs2Road,
                        Constants.getInstance().dcDetector,
                        r.a);
                if (pSegment != null)
                    psegments.add(pSegment);
            }
        }

        segments.addAll(psegments);
        List<Cross> pcrosses = crossMake.find_Crosses(segments, Constants.getInstance().dcDetector);

        CrossList pcrosslist = crossLister.candCrossLists(event, pcrosses,
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, true);

        // Find track candidates with conventional pseudo-cross
        List<Track> trkcands3 = trkcandFinder.getTrackCands(pcrosslist,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);

        // remove overlaps
        if (!trkcands3.isEmpty()) {
            trkcandFinder.removeOverlappingTracks(trkcands3);
            for (Track trk : trkcands3) {
                
                // reset the id
                trk.set_Id(trkId);
                trkcandFinder.matchHits(trk.getStateVecs(),
                        trk,
                        Constants.getInstance().dcDetector,
                        dcSwim);
                trkId++;
            }
        }

        trkcands.addAll(trkcands3);
                     
        //gather all the hits and URWell crosses for pointer bank creation        
        for (Track trk : trkcands) {
            trk.calcTrajectory(trk.getId(), dcSwim, trk.get_Vtx0(), trk.get_pAtOrig(), trk.get_Q());
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
        }
        
        // no candidate found, stop here and save the hits,
        // the clusters, the segments, the crosses
        crosses.addAll(dcCrossesOnTrack1);
        crosses.addAll(dcCrossesOnTrack2);
        for(Cross c: crosses){
            clusters.add(c.get_Segment1().get_fittedCluster());
            clusters.add(c.get_Segment2().get_fittedCluster()); 
        }
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
