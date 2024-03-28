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
import org.jlab.rec.dc.banks.RecoBankWriter;
import org.jlab.rec.dc.cluster.ClusterFinder;
import org.jlab.rec.dc.cluster.ClusterFitter;
import org.jlab.rec.dc.cluster.FittedCluster;
import org.jlab.rec.dc.cross.Cross;
import org.jlab.rec.dc.cross.CrossMaker;
import org.jlab.rec.dc.hit.FittedHit;
import org.jlab.rec.dc.segment.Segment;
import org.jlab.rec.dc.segment.SegmentFinder;
import org.jlab.rec.dc.track.Track;
import org.jlab.rec.dc.track.TrackCandListWithURWellFinder;
import org.jlab.rec.dc.cross.URWellDCCrossesList;
import org.jlab.rec.dc.trajectory.Road;
import org.jlab.rec.dc.trajectory.RoadFinder;
import org.jlab.rec.urwell.reader.URWellReader;
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
    public boolean processDataEvent(DataEvent event) {
    
        int run = this.getRun(event);
        if(run==0) return true;
        
        /* IO */
        HitReader      reader = new HitReader(this.getBanks(), Constants.getInstance().dcDetector);
        RecoBankWriter writer = new RecoBankWriter(this.getBanks());
        // get Field
        Swim dcSwim = new Swim();
     
        List<Track> trkcands = null;
        List<Cross> crosses = null;
        List<FittedCluster> clusters = null;
        List<Segment> segments = null;
        List<FittedHit> fhits = new ArrayList<>();
        
        //1) read the hits from the banks
        Map<Integer, ArrayList<FittedHit>> hits = reader.read_Hits(event);
        if(hits == null || hits.isEmpty())
            return true;
        //2) find the clusters from these hits
        ClusterFinder clusFinder = new ClusterFinder();
        ClusterFitter cf = new ClusterFitter();
        clusters = clusFinder.RecomposeClusters(hits, Constants.getInstance().dcDetector, cf);
        if (clusters ==null || clusters.isEmpty()) {
            return true;
        }

        //3) find the segments from the fitted clusters
        SegmentFinder segFinder = new SegmentFinder();
        segments = segFinder.get_Segments(clusters,
                event,
                Constants.getInstance().dcDetector, false);

        /* 15 */
        // need 6 segments to make a trajectory
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
        /* 16 */
        CrossMaker crossMake = new CrossMaker();
        crosses = crossMake.find_Crosses(segments, Constants.getInstance().dcDetector);
        if (crosses.isEmpty()) {
            event.appendBanks(
                    writer.fillHBSegmentsBank(event, segments));
            return true;
        }
        /* 17 */
        // Read urwell crosses
        URWellReader uRWellReader = new URWellReader(event, "HB");
        List<URWellCross> urCrosses = uRWellReader.getUrwellCrosses();
        URWellDCCrossesListFinder uRWellDCCrossListLister = new URWellDCCrossesListFinder();        
        // Build 4-crosses combos, where 1 cross from uRWell, and other 3 crosses from DCs
        URWellDCCrossesList urDC4CrossesList = uRWellDCCrossListLister.candURWellDCCrossesLists(event, crosses, urCrosses,
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false, 4
        );
        /* 18 */                
        //6) Build tracks for 4-crosses combos
        TrackCandListWithURWellFinder trkcandFinder = new TrackCandListWithURWellFinder(Constants.HITBASE);
        trkcands = trkcandFinder.getTrackCands(urDC4CrossesList,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);
        /* 19 */
        // track found
        List<URWellCross> urCrossesOnTrks = new ArrayList<URWellCross>();
        int trkId = 1;
        if (!trkcands.isEmpty()) {
            // remove overlaps
            trkcandFinder.removeOverlappingTracks(trkcands);
            for (Track trk : trkcands) {
                // reset the id
                trk.set_Id(trkId);
                trkcandFinder.matchHits(trk.getStateVecs(),
                        trk,
                        Constants.getInstance().dcDetector,
                        dcSwim);
                if(trk.get_URWellCross() != null){
                    urCrossesOnTrks.add(trk.get_URWellCross()); 
                    trk.get_URWellCross().set_tid(trk.get_Id());
                }
                trkId++;
            }
        }
        
        /** Build pseudo-segments based on five real segments at the same sector
         * Firstly, build roads by 3 segments at odd/even superlayers
         * For each road, check if one segment has no associated cross, while other two segments have associated crosses, which are not on tracks
         * If yes, build a pseudo-segment, and then a pseudo-cross with the real segment without associated cross
         */
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
        
        // Remove uRWell crosses on tracks
        urCrosses.removeAll(urCrossesOnTrks);
        // Build pseudo 4-crosses combos, where there is pseudo dc cross
        URWellDCCrossesList purDC4CrossesList = uRWellDCCrossListLister.candURWellDCCrossesLists(event, pcrosses, urCrosses,
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false, 4
        );
        //Build tracks for pseudo 4-crosses combos
        List<Track> mistrkcands = trkcandFinder.getTrackCands(purDC4CrossesList,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);

        // remove overlaps
        List<URWellCross> urCrossesOnTrksWithPSegment = new ArrayList<URWellCross>();
        if (!mistrkcands.isEmpty()) {
            trkcandFinder.removeOverlappingTracks(mistrkcands);
            for (Track trk : mistrkcands) {
                
                // reset the id
                trk.set_Id(trkId);
                trkcandFinder.matchHits(trk.getStateVecs(),
                        trk,
                        Constants.getInstance().dcDetector,
                        dcSwim);
                if(trk.get_URWellCross() != null){
                    urCrossesOnTrksWithPSegment.add(trk.get_URWellCross()); 
                    urCrossesOnTrks.add(trk.get_URWellCross()); 
                    trk.get_URWellCross().set_tid(trk.get_Id());
                }
                trkId++;
            }
        }

        trkcands.addAll(mistrkcands);
        
        ////// Tracking for 3-URDCCrosses combos
        // Add real dc crosses into crossesOnTrack list
        List<Cross> dcCrossesOnTrack = new ArrayList();
        for(Cross dcCross : crosses){
            Segment seg1 = dcCross.get_Segment1();
            Segment seg2 = dcCross.get_Segment2();
            if(seg1.isOnTrack==true && seg2.isOnTrack==true && seg1.associatedCrossId==seg2.associatedCrossId && dcCross.get_Id() != -1){
                dcCrossesOnTrack.add(dcCross);
            }
        }
        
        // Remove real dc crosses on tracks
        crosses.removeAll(dcCrossesOnTrack);
        // Further remove uRWell crosses on tracks with pseudo-segment
        urCrosses.removeAll(urCrossesOnTrksWithPSegment); 
        // Build 3-crosses combos from any of 3 regions
        URWellDCCrossesList urDC3CrossesList = uRWellDCCrossListLister.candURWellDCCrossesLists(event, crosses, urCrosses,
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false, 3
        );
        // Build tracks for 3-crosses combos       
        List<Track> trkcands3URDCCrosses = trkcandFinder.getTrackCands3URDCCrosses(urDC3CrossesList,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);
        if (!trkcands3URDCCrosses.isEmpty()) {
            trkcandFinder.removeOverlappingTracks(trkcands3URDCCrosses);
            for (Track trk : trkcands3URDCCrosses) {
                
                // reset the id
                trk.set_Id(trkId);
                trkcandFinder.matchHits(trk.getStateVecs(),
                        trk,
                        Constants.getInstance().dcDetector,
                        dcSwim);
                                
                if(trk.get_URWellCross() != null){                    
                    urCrossesOnTrks.add(trk.get_URWellCross()); 
                    trk.get_URWellCross().set_tid(trk.get_Id());
                }
                trkId++;
            }
        }
        
        trkcands.addAll(trkcands3URDCCrosses);
        
        ////// Tracking for pseudo 3-DCcrosses combos
        // Remove dc crosses on tracks from pcrosses
        List<Cross> pcrossesOnTrack = new ArrayList();
        for(Cross dcCross : pcrosses){
            Segment seg1 = dcCross.get_Segment1();
            Segment seg2 = dcCross.get_Segment2();
            if(seg1.isOnTrack==true && seg2.isOnTrack==true && seg1.associatedCrossId==seg2.associatedCrossId){
                pcrossesOnTrack.add(dcCross);
            }
        }
        pcrosses.removeAll(pcrossesOnTrack);
        // Build pseudo 3-DCcrosses combos
        URWellDCCrossesList pdc3CrossesList = uRWellDCCrossListLister.candURWellDCCrossesLists(event, pcrosses, new ArrayList(),
                false,
                null,
                Constants.getInstance().dcDetector,
                null,
                dcSwim, false, 3
        );        
        // Build tracks for pseudo 3-DCcrosses combos      
        List<Track> trkcands3pDCCrosses = trkcandFinder.getTrackCands3URDCCrosses(pdc3CrossesList,
                Constants.getInstance().dcDetector,
                Swimmer.getTorScale(),
                dcSwim, false);
        if (!trkcands3pDCCrosses.isEmpty()) {
            trkcandFinder.removeOverlappingTracks(trkcands3pDCCrosses);
            for (Track trk : trkcands3pDCCrosses) {
                
                // reset the id
                trk.set_Id(trkId);
                trkcandFinder.matchHits(trk.getStateVecs(),
                        trk,
                        Constants.getInstance().dcDetector,
                        dcSwim);                                
                trkId++;
            }
        }
        
        trkcands.addAll(trkcands3pDCCrosses);
        
        LOGGER.log(Level.FINE, "Found after 5STg "+mistrkcands.size()+" HB seeds ");
        for(int i = 0; i< trkcands.size(); i++) {
            LOGGER.log(Level.FINE, "cand "+i);
            for(Cross c : trkcands.get(i)) {
                LOGGER.log(Level.FINE, c.printInfo());
            }
            LOGGER.log(Level.FINE, "------------------------------------------------------------------ ");
        }
        
        //gather all the hits and URWell crosses for pointer bank creation        
        for (Track trk : trkcands) {
            trk.calcTrajectory(trk.getId(), dcSwim, trk.get_Vtx0(), trk.get_pAtOrig(), trk.get_Q());
            for (Cross c : trk) {
                c.set_CrossDirIntersSegWires();
                trkcandFinder.setHitDoubletsInfo(c.get_Segment1());
                trkcandFinder.setHitDoubletsInfo(c.get_Segment2());
                for (FittedHit h1 : c.get_Segment1()) {
//                        h1.setSignalPropagTimeAlongWire(dcDetector); //PASS1, not necessary because hits were already updated in trkcandFinder.matchHits
//                        h1.setSignalTimeOfFlight();                  //PASS1
                    if(h1.get_AssociatedHBTrackID()>0) fhits.add(h1);
                }
                for (FittedHit h2 : c.get_Segment2()) {
//                        h2.setSignalPropagTimeAlongWire(dcDetector); //PASS1
//                        h2.setSignalTimeOfFlight();                  //PASS1
                    if(h2.get_AssociatedHBTrackID()>0) fhits.add(h2);
                }
            }
        }
                                       
        // no candidate found, stop here and save the hits,
        // the clusters, the segments, the crosses
        crosses.addAll(dcCrossesOnTrack);
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
