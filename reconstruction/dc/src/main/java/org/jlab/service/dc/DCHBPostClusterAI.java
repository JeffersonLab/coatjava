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
import org.jlab.rec.dc.cluster.FittedCluster;
import org.jlab.rec.dc.cross.Cross;
import org.jlab.rec.dc.cross.CrossList;
import org.jlab.rec.dc.cross.CrossListFinder;
import org.jlab.rec.dc.hit.FittedHit;
import org.jlab.rec.dc.hit.Hit;
import org.jlab.rec.dc.nn.PatternRec;
import org.jlab.rec.dc.segment.Segment;
import org.jlab.rec.dc.track.Track;
import org.jlab.rec.dc.track.TrackCandListFinder;
import org.jlab.rec.dc.cluster.ClusterFinder;
import org.jlab.rec.dc.cluster.ClusterFitter;
import org.jlab.rec.dc.segment.SegmentFinder;
import org.jlab.rec.dc.cross.CrossMaker;
import org.jlab.rec.dc.trajectory.Road;
import org.jlab.rec.dc.trajectory.RoadFinder;

/**
 *
 * @author ziegler, tongtong
 */
public class DCHBPostClusterAI extends DCEngine {

    public DCHBPostClusterAI() {
        super("DCHAI");
        this.getBanks().init("HitBasedTrkg", "", "AI");
    }
    
    public DCHBPostClusterAI(String outputBankPrefix) {
        super("DCHAI");
        this.getBanks().init("HitBasedTrkg", "", outputBankPrefix);
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
        
        ////// AI-assisted tracking
        /* IO */
        HitReader reader      = new HitReader(this.getBanks(), Constants.getInstance().dcDetector);
        reader.initialize(event);
        RecoBankWriter writer = new RecoBankWriter(this.getBanks());
        // get Field
        Swim dcSwim = new Swim();
        LOGGER.log(Level.FINEST, "HB AI process event");

        //AI
        List<Track> trkcands = null;
        List<Cross> crosses = null;
        List<FittedCluster> clusters = new ArrayList<>();
        List<Segment> segments = null;
        List<FittedHit> fhits = null;

        reader.read_NNHits(event);

        //I) get the lists
        List<Hit> hits = reader.get_DCHits();
        fhits = new ArrayList<>();
        //II) process the hits
        // Exit if hit list is empty
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
        crosses = new ArrayList<>();
        
        LOGGER.log(Level.FINEST, "num cands = "+crosslist.size());
        for (List<Cross> clist : crosslist) {
            crosses.addAll(clist); 
            for(Cross c : clist)
                LOGGER.log(Level.FINEST, "Pass Cross"+c.printInfo());
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
        trkcands = trkcandFinder.getTrackCands(crosslist,
            Constants.getInstance().dcDetector,
            Swimmer.getTorScale(),
            dcSwim, true);

        // track found
        int trkId = 1;
        if (trkcands.size() > 0) {
            // remove overlaps
            trkcandFinder.removeOverlappingTracks(trkcands);
            for (Track trk : trkcands) {
                trk.setIsAITrack(true);
                
                for (Cross c : trk) {
                    clusters.add(c.get_Segment1().get_fittedCluster());
                    clusters.add(c.get_Segment2().get_fittedCluster());
                }
                
                // reset the id
                trk.set_Id(trkId);
                trkcandFinder.matchHits(trk.getStateVecs(),
                        trk,
                        Constants.getInstance().dcDetector,
                        dcSwim);
                trkId++;
            }
        }
        
        ////// Find tracks by rest of clusters using conventional tracking
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
        
        //7) find track candidates with 5 or 6 clusters
        // track candidates with 6 clusters 
        trkcandsConv = trkcandFinder.getTrackCands(crosslistConv,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false); 
        
        // track candidates with 5 clusters
        RoadFinder rf = new RoadFinder();
        List<Road> allRoadsConv = rf.findRoads(segmentsConv, Constants.getInstance().dcDetector);
        List<Segment> Segs2RoadConv = new ArrayList<>();
        List<Segment> psegmentsConv = new ArrayList<>();
        for (Road r : allRoadsConv) { 
            Segs2RoadConv.clear();
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
                for (Segment s : segmentsConv) {
                    if (s.get_Sector() == r.get(ri).get_Sector() &&
                            s.get_Region() == r.get(ri).get_Region() &&
                            s.associatedCrossId == r.get(ri).associatedCrossId &&
                            r.get(ri).associatedCrossId != -1) {
                        if (s.get_Superlayer() % 2 == missingSL % 2){
                            Segs2RoadConv.add(s); 
                            break;
                        }
                    }
                }
            }
            if (Segs2RoadConv.size() == 2) {
                Segment pSegmentConv = rf.findRoadMissingSegment(Segs2RoadConv,
                        Constants.getInstance().dcDetector,
                        r.a);
                if (pSegmentConv != null)
                    psegmentsConv.add(pSegmentConv);
            }
        }

        segmentsConv.addAll(psegmentsConv);
        List<Cross> pcrossesConv = crossMake.find_Crosses(segmentsConv, Constants.getInstance().dcDetector);
        List<Cross> fullPseudoCrossesConv = new ArrayList(); // Cross by two pseudo segments
        for(Cross crs : pcrossesConv){
            if(crs.get_Segment1().get_Id() == -1 && crs.get_Segment2().get_Id() == -1) fullPseudoCrossesConv.add(crs);
        }
        pcrossesConv.removeAll(fullPseudoCrossesConv);        
        CrossList pcrosslistConv = crossLister.candCrossLists(event, pcrossesConv,
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, true);
        List<Track> mistrkcandsConv = trkcandFinder.getTrackCands(pcrosslistConv,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);
        
        //8) Select overlapping tracks from all track candidates with 5 or 6 clusters, and update hits in tracks 
        trkcandsConv.addAll(mistrkcandsConv);
        if (!trkcandsConv.isEmpty()) {
            // remove overlaps
            trkcandFinder.removeOverlappingTracks(trkcandsConv);
            for (Track trk : trkcandsConv) {
                // reset the id
                trk.set_Id(trkId);
                trkcandFinder.matchHits(trk.getStateVecs(),
                        trk,
                        Constants.getInstance().dcDetector,
                        dcSwim);
                trkId++;
            }
        }
                
        //////gather all the hits for pointer bank creation
        trkcands.addAll(trkcandsConv);
        trkId=1;
        for (Track trk : trkcands) {            
            trk.calcTrajectory(trk.getId(), dcSwim, trk.get_Vtx0(), trk.get_pAtOrig(), trk.get_Q());
            for (Cross c : trk) {
                c.set_CrossDirIntersSegWires();
                trkcandFinder.setHitDoubletsInfo(c.get_Segment1());
                trkcandFinder.setHitDoubletsInfo(c.get_Segment2());
                for (FittedHit h1 : c.get_Segment1()) {          
                    h1.set_AssociatedHBTrackID(trkId);
                    //if(h1.get_AssociatedHBTrackID()>0) 
                    fhits.add(h1); 
                }
                for (FittedHit h2 : c.get_Segment2()) {
                    h2.set_AssociatedHBTrackID(trkId);
                    //if(h2.get_AssociatedHBTrackID()>0) 
                    fhits.add(h2); 
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
    
}
