package org.jlab.service.dc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.jlab.clas.swimtools.Swim;
import org.jlab.clas.swimtools.Swimmer;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.dc.Constants;
import static org.jlab.rec.dc.Constants.DEBUG;
import org.jlab.rec.dc.nn.AIHitReader;
import org.jlab.rec.dc.banks.RecoBankWriter;
import org.jlab.rec.dc.cluster.FittedCluster;
import org.jlab.rec.dc.cross.Cross;
import org.jlab.rec.dc.cross.CrossList;
import org.jlab.rec.dc.cross.CrossListFinder;
import org.jlab.rec.dc.hit.FittedHit;
import org.jlab.rec.dc.hit.Hit;
import org.jlab.rec.dc.nn.PatternIRec;
import org.jlab.rec.dc.segment.Segment;
import org.jlab.rec.dc.track.Track;
import org.jlab.rec.dc.track.TrackCandListFinder;
import static org.jlab.service.dc.DCEngine.LOGGER;

/**
 *
 * @author ziegler
 */
public class DCHBPostIClusterAI extends DCEngine {

    public DCHBPostIClusterAI() {
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
        AIHitReader reader  = new AIHitReader(this.getBanks(), Constants.getInstance().dcDetector);
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
        List<Cross> crosses = null;
        List<FittedCluster> clusters = null;
        Map<Integer, List<Segment>> segmentsMap = null;
        List<FittedHit> fhits = null;

        reader.read_NNHits(event, useInstarec, enableMulti);

        //I) get the lists
        List<Hit> hits = reader.getDCHits();
        fhits = new ArrayList<>(); 
        
        //II) process the hits
        //1) exit if hit list is empty
        if (hits.isEmpty()) {
            return true;
        }
        PatternIRec pr = new PatternIRec();
        segmentsMap = pr.RecomposeSegments(hits, Constants.getInstance().dcDetector);
        
        if (segmentsMap.isEmpty()) { 
            return true;
        } 
        //crossList
        List<CrossList> crosslists = new ArrayList<>();
        List<Segment> segments = new ArrayList<>();
        crosses = new ArrayList<>();
        for(Integer it : segmentsMap.keySet()) { 
            for(Segment se : segmentsMap.get(it)) {
                if(se.get_Id()>0)
                    segments.add(se); 
            }
            
            
            CrossList crosslist = pr.RecomposeCrossList(segmentsMap.get(it), Constants.getInstance().dcDetector);
            crosslists.add(crosslist);
            for (List<Cross> clist : crosslist) {
                crosses.addAll(clist); 
            }
        }
       
        LOGGER.log(Level.FINE, "num cands = "+crosses.size());
        for(Cross c : crosses)
            LOGGER.log(Level.FINE, "Pass Cross"+c.printInfo());
        
        if (crosses.isEmpty()) {
            clusters = new ArrayList<>();
            for(Segment seg : segments) {
                clusters.add(seg.get_fittedCluster());
            }
            event.appendBanks(writer.fillHBHitsBank(event, fhits),    
                    writer.fillHBClustersBank(event, clusters),
                    writer.fillHBSegmentsBank(event, segments));
            return true;
        } 
        // update B field
        CrossListFinder crossLister = new CrossListFinder();
        for(Cross cr : crosses) {
            if(DEBUG) 
                System.out.println("in list: "+cr.printInfo());
            crossLister.updateBFittedHits(event, cr, null, Constants.getInstance().dcDetector, null, dcSwim);
        }
        //find the list of  track candidates
        TrackCandListFinder trkcandFinder = new TrackCandListFinder(Constants.HITBASE);
        
        for(CrossList crosslist : crosslists) {
            List<Track> ts= trkcandFinder.getTrackCands(crosslist,
            Constants.getInstance().dcDetector,
            Swimmer.getTorScale(),
            dcSwim, true);
            
            if(ts!=null) {
                trkcands.addAll(ts);
            }
        }
        
        if(DEBUG) {   
            System.out.println("HB SEEDS:");
            for(Track t : trkcands) {
                int trkId = t.get(0).get_Segment1().get(0).NNTrkId;
                t.set_Id(trkId);
                t.printInfo();
            }
        }
        
        // track found
        clusters = new ArrayList<>();
        int trkId = 1;
        if (!trkcands.isEmpty()) {
            // remove overlaps
            if(!useInstarec) trkcandFinder.removeOverlappingTracks(trkcands);
            for (Track trk : trkcands) {
                // reset the id
                if(useInstarec) trkId = trk.get(0).get_Segment1().get(0).NNTrkId;
                trk.set_Id(trkId);
                trkcandFinder.matchHits(trk.getStateVecs(),
                        trk,
                        Constants.getInstance().dcDetector,
                        dcSwim);
                for (Cross c : trk) {
                    c.set_CrossDirIntersSegWires();
                    if(c.get_Segment1().get_fittedCluster().get_Id()>0) {
                        clusters.add(c.get_Segment1().get_fittedCluster());
                        trkcandFinder.setHitDoubletsInfo(c.get_Segment1());
                        for (FittedHit h1 : c.get_Segment1()) {
                            h1.set_AssociatedHBTrackID(trkId);
                            fhits.add(h1); 
                        }
                    }
                    if(c.get_Segment2().get_fittedCluster().get_Id()>0) {
                        clusters.add(c.get_Segment2().get_fittedCluster());
                        trkcandFinder.setHitDoubletsInfo(c.get_Segment2());
                        for (FittedHit h2 : c.get_Segment2()) {
                            h2.set_AssociatedHBTrackID(trkId);
                            fhits.add(h2); 
                        }
                    }
                }
                trk.calcTrajectory(trk.getId(), dcSwim, trk.get_Vtx0(), trk.get_pAtOrig(), trk.get_Q());
                
                trkId++;
            }
        }
        
                // no candidate found, stop here and save the hits,
        // the clusters, the segments0, the crosses
        if (trkcands.isEmpty()) {
            event.appendBanks(
                    writer.fillHBHitsBank(event, fhits),    
                    writer.fillHBSegmentsBank(event, segments),
                    writer.fillHBCrossesBank(event, crosses));
        }
        else {
            if(Constants.DEBUG || Constants.DEBUGLIGHT) {
            if(trkcands.size()!=AIHitReader.getNNTrks() && !enableMulti) System.out.println("event "+this.getEvent(event)+" expected HB trks "+AIHitReader.getNNTrks()+
               " found tracks "+ trkcands.size());
            
        }
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
