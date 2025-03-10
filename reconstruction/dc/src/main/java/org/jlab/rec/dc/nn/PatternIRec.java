package org.jlab.rec.dc.nn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.detector.geant4.v2.DCGeant4Factory;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.dc.Constants;
import org.jlab.rec.dc.cluster.Cluster;
import org.jlab.rec.dc.cluster.ClusterCleanerUtilities;
import org.jlab.rec.dc.cluster.ClusterFinder;
import org.jlab.rec.dc.cluster.ClusterFitter;
import org.jlab.rec.dc.cluster.FittedCluster;
import org.jlab.rec.dc.cross.Cross;
import org.jlab.rec.dc.cross.CrossList;
import org.jlab.rec.dc.cross.CrossMaker;
import org.jlab.rec.dc.hit.FittedHit;
import org.jlab.rec.dc.hit.Hit;
import static org.jlab.rec.dc.nn.AIHitReader.OUTLIERCUT;
import org.jlab.rec.dc.segment.Segment;
import org.jlab.rec.dc.timetodistance.TimeToDistanceEstimator;
import org.jlab.rec.dc.trajectory.Road;
import org.jlab.rec.dc.trajectory.RoadFinder;
import org.jlab.utils.groups.IndexedTable;
/**
 *
 * @author ziegler
 */
public class PatternIRec {

    private static final Logger LOGGER = Logger.getLogger(PatternIRec.class.getName());

    private final ClusterFinder cfd             = new ClusterFinder();
    private final ClusterFitter cf              = new ClusterFitter();
    private final CrossMaker crf                = new CrossMaker();
    private final RoadFinder rf                 = new RoadFinder(1);
    private final ClusterCleanerUtilities ct    = new ClusterCleanerUtilities();
    private final PatternRec pr                 = new PatternRec();
    
    public PatternIRec(boolean TBT) {
        rf.fitPassingCut = 1000;
        this.TBT = TBT;
    }
    private boolean TBT = false;
    
    public CrossList RecomposeCrossList(List<Segment> clusters, DCGeant4Factory DcDetector) {
        CrossList crossList = new CrossList();
        Map<Integer, List<Segment>> grpCls = new HashMap<>();
        clusters.sort(Comparator.comparing(Segment::get_Region).thenComparing(Segment::get_Superlayer));
        // Group clusters by associated HBTrackID
        for (Segment cls : clusters) {
            int index = cls.get(0).get_AssociatedHBTrackID();
            grpCls.computeIfAbsent(index, k -> new ArrayList<>()).add(cls);
        }
        // Process each cluster group
        for(Integer I : grpCls.keySet()) {
            List<Segment> segs2Crs = grpCls.get(I);
            //CrossList cl = pr.RecomposeCrossList(segs2Crs, DcDetector);
            //List<Cross> crosses = cl.getCrosses();
            List<Cross> crosses = this.RecomposeFHCrossList(segs2Crs, DcDetector, I);
            if(crosses==null) continue;
            if(Constants.DEBUG) {
                System.out.println("NNTrk "+I);
                for(Cross s : crosses)
                    System.out.println(s.printInfo());
            }
            if(crosses.size()<3) continue;
            int cntpscrs=0;
            for(Cross c : crosses)
                if(c.isPseudoCross) cntpscrs++;
            if(cntpscrs>1) continue;
            this.logCrossInfo(crosses);
            
            crossList.add(crosses);
        }
        return crossList;
    }

        public List<Cross> RecomposeFHCrossList(List<Segment> segs2Crs, DCGeant4Factory DcDetector, int i) {
        
        Map<Integer, List<Segment>> regionSegs = new HashMap<>();
        // Process each cluster group
        segs2Crs.sort(Comparator.comparing(Segment::get_Region).thenComparing(Segment::get_Superlayer));
        for(Segment s : segs2Crs) {
            regionSegs.computeIfAbsent(s.get_Region(), k -> new ArrayList<>()).add(s);
        }

        Map<Integer, Cross> crsMap = new HashMap<>();
        for(int r = 0; r<3; r++) {
            if(regionSegs.containsKey(r+1)) {
                if(regionSegs.get(r+1).size()==2) {
                    Cross cr = crf.getCross(regionSegs.get(r+1).get(0), 
                        regionSegs.get(r+1).get(1), DcDetector, 0,0); 
                    if(cr==null) {
                        cr = crf.getCross(regionSegs.get(r+1).get(0), 
                        regionSegs.get(r+1).get(1), DcDetector, 0,2);//loosen cut
                        if(cr==null) {
                            cr = this.findBestSegmentCross(regionSegs.get(r+1).get(0), 
                            regionSegs.get(r+1).get(1), segs2Crs, DcDetector);
                        }
                    } 
                    if(cr!=null)  crsMap.put(cr.get_Region(), cr);
                } else {
                    Cross pseudo=this.getMissingSegmentPCross(regionSegs.get(r+1).get(0), null, segs2Crs, DcDetector);
                    if(pseudo!=null) crsMap.put(regionSegs.get(r+1).get(0).get_Region(), pseudo);
                }
            }
        }

        
        List<Cross> crosses = new ArrayList<>(crsMap.values());
        int nonspeudo =0;
        for(Cross c : crosses) {
            if(c.get_Id()!=-2) 
                nonspeudo++;
        }
        if(nonspeudo<2)
            return null;
        
        crosses.sort(Comparator.comparing(Cross::get_Region));//sanity check - should already be sorted

        logCrossInfo(crosses);
        if(Constants.DEBUG) {
            System.out.println("NNTrk RCL sorted "+i);
            for(Cross s : crosses)
                System.out.println(s.printInfo());
        }

        return crosses;
    }
    
    private void logCrossInfo(List<Cross> crosses) {
        for (Cross c : crosses) {
            LOGGER.log(Level.FINE, "AI" + c.printInfo() + c.get_Segment1().printInfo() + c.get_Segment2().printInfo());
        }
    }

    public Map<Integer, List<Segment>> RecomposeSegments(List<Hit> fhits, DCGeant4Factory DcDetector) {
        Map<Integer, List<Segment>> fclusters = new HashMap<>();
        Map<Pair<Integer, Integer>, List<Hit>> grpHits = new HashMap<>();

        // Group hits by NNTrkId and NNClusId
        for (Hit hit : fhits) {
            if (hit.NNTrkId > 0) {
                Pair<Integer, Integer> index = new Pair<>(hit.NNTrkId, hit.NNClusId);
                grpHits.computeIfAbsent(index, k -> new ArrayList<>()).add(hit);
            }
        }

        // Process each hit group
        for (Map.Entry<Pair<Integer, Integer>, List<Hit>> entry : grpHits.entrySet()) {
            //List<Hit> hits = entry.getValue();
            List<Hit> hits = this.Uniq(entry.getValue());
            
            int cid = entry.getKey().getSecond();
            
            FittedCluster fclus = this.getFittedCluster(hits,cid, DcDetector);
            
            int tid = entry.getKey().getFirst();
            // Add the fitted cluster as a segment
            Segment seg = createSegment(fclus, DcDetector);
            
            // Add the segment to the appropriate track group
            fclusters.computeIfAbsent(tid, k -> new ArrayList<>()).add(seg);
        }
        if(Constants.DEBUG) {
            for(Integer i : fclusters.keySet()) {
                System.out.println("NNTrk RSEGS"+i);
                for(Segment s : fclusters.get(i))
                    System.out.println(s.printInfo());
            }
        }
        return fclusters;
    }

    public Map<Integer, List<Segment>> RecomposeFHSegments(DataEvent event, 
            List<FittedHit> fhits, IndexedTable constants, DCGeant4Factory DcDetector, 
            TimeToDistanceEstimator tde) {
        Map<Integer, List<Segment>> fclusters = new HashMap<>();
        Map<Pair<Integer, Integer>, List<FittedHit>> grpHits = new HashMap<>();

        // Group hits by NNTrkId and NNClusId
        for (FittedHit hit : fhits) {
            if (hit.get_AssociatedHBTrackID() > 0) { 
                Pair<Integer, Integer> index = new Pair<>(hit.get_AssociatedHBTrackID(), 
                        hit.get_AssociatedClusterID());
                grpHits.computeIfAbsent(index, k -> new ArrayList<>()).add(hit);
            }
        }

        // Process each hit group
        for (Map.Entry<Pair<Integer, Integer>, List<FittedHit>> entry : grpHits.entrySet()) {
            List<FittedHit> chits = entry.getValue();

            int tid = entry.getKey().getFirst();
            int cid = entry.getKey().getSecond();

            // Create the cluster from hits
            Cluster c = new Cluster(chits.get(0).get_Sector(), chits.get(0).get_Superlayer(), cid);
            
            // Create fitted cluster and update hits
            FittedCluster fclus = createFittedCluster(event, c, chits, constants,DcDetector,tde);
            
            // Add the fitted cluster as a segment
            Segment seg = createSegment(fclus, DcDetector);
            // Add the segment to the appropriate track group
            fclusters.computeIfAbsent(tid, k -> new ArrayList<>()).add(seg);
        }

        return fclusters;
    }

    private FittedCluster createFittedCluster(DataEvent event, Cluster cluster,
            List<FittedHit> chits, IndexedTable constants, DCGeant4Factory DcDetector, 
            TimeToDistanceEstimator tde) {
        FittedCluster fclus = new FittedCluster(cluster);
        fclus.set_Id(cluster.get_Id());
        fclus.removeAll(cluster);
        fclus.addAll(chits);
         // Update hit parameters and fit the cluster
        for (FittedHit fhit : fclus) {
            fhit.set_AssociatedClusterID(fclus.get_Id());
            fhit.set_TrkgStatus(0);
            fhit.updateHitPositionWithTime(event, 0, fhit.getB(), constants, DcDetector, tde);
        }
        for(int i =0; i<5; i++) {//iterate to improve local angle
            cf.SetFitArray(fclus, "TSC");
            cf.Fit(fclus, true);
            cf.SetResidualDerivedParams(fclus, true, false, DcDetector);
            cf.Fit(fclus, true);
            cf.SetSegmentLineParameters(fclus.get(0).get_Z(), fclus);
             // Update hit parameters and fit the cluster
            for (FittedHit fhit : fclus) {
                fhit.set_AssociatedClusterID(fclus.get_Id());
                fhit.set_TrkgStatus(0);
                fhit.updateHitPositionWithTime(event, fclus.get_clusterLineFitSlope(), fhit.getB(), constants, DcDetector, tde);
            }
        }
        
        return fclus;
    }
    
    private FittedCluster createFittedCluster(Cluster cluster, DCGeant4Factory DcDetector, boolean fit) {
        int cid = cluster.get(0).NNClusId;
        int tid = cluster.get(0).NNTrkId;
        FittedCluster fclus = new FittedCluster(cluster);
        fclus.set_Id(cluster.get_Id());

        // Update hit parameters and fit the cluster
        for (FittedHit fhit : fclus) {
            fhit.set_AssociatedClusterID(cid);
            fhit.set_AssociatedHBTrackID(tid);
            fhit.set_TrkgStatus(0);
            fhit.NNClusId = cid;
            fhit.NNTrkId  = tid;
            fhit.updateHitPosition(DcDetector);
        }
        if(fit) {
            cf.SetFitArray(fclus, "TSC");
            cf.Fit(fclus, true);
            cf.SetResidualDerivedParams(fclus, false, false, DcDetector);
        }
        return fclus;
    }

    private Segment createSegment(FittedCluster fclus, DCGeant4Factory DcDetector) {
        cf.SetFitArray(fclus, "TSC");
        cf.Fit(fclus, true);
        cf.SetSegmentLineParameters(fclus.get(0).get_Z(), fclus);

        Segment seg = new Segment(fclus);
        seg.set_fitPlane(DcDetector);
        double sumRes = 0;
        double sumTime = 0;
        for (FittedHit h : seg) {
            sumRes += h.get_TimeResidual();
            sumTime += h.get_Time();
        }
        seg.set_ResiSum(sumRes);
        seg.set_TimeSum(sumTime);
        return seg;
    }

    private FittedCluster getFittedCluster(List<Hit> hits, int cid, DCGeant4Factory DcDetector) {
        //Algorithm to reject outliers 
        List<Hit> chits = new ArrayList<>();
        List<Hit> rhits = new ArrayList<>();
        //select only the hits passing the doca requirement
        for(Hit h : hits) {
            if(!h.outlier) {
                chits.add(h);
            } else {
                rhits.add(h);
            }
        }

        //Good hits cluster
        Cluster c = new Cluster(chits.get(0).get_Sector(), chits.get(0).get_Superlayer(), cid);
        c.addAll(chits);

        // Create fitted cluster and update hits
        FittedCluster fclus = createFittedCluster(c, DcDetector, true);

        if(rhits.isEmpty()) return fclus;
        
        //bad hits cluster
        Cluster c2 = new Cluster(rhits.get(0).get_Sector(), rhits.get(0).get_Superlayer(), cid);
        c2.addAll(rhits);

        // Create fitted cluster and update hits
        FittedCluster fclus2 = createFittedCluster(c2, DcDetector, false);

        double zref= fclus.get(0).get_Z();
        //Fit it
        cf.SetFitArray(fclus, "TSC");
        cf.Fit(fclus, true);
        double sl = fclus.get_clusterLineFitSlope();
        double it = fclus.get_clusterLineFitIntercept();
        Line3D FitLine = new Line3D();
        Point3D pointOnTrk = new Point3D(zref, sl * zref + it, 0);
        Vector3D trkDir = new Vector3D(1, sl, 0);
        trkDir.unit();
        FitLine.set(pointOnTrk, trkDir);
        for (FittedHit fhit : fclus2) {
            fhit.updateHitPosition(DcDetector);
            Point3D Wire = new Point3D(fhit.get_Z(), fhit.get_X(), 0);
            double trkDoca = FitLine.distance(Wire).length() * Constants.COS6;
            if(trkDoca/fhit.get_CellSize()<OUTLIERCUT) {
                fclus.add(fhit); 
            }
        }
        //refit the updated cluster
        cf.SetFitArray(fclus, "TSC");
        cf.Fit(fclus, true);
        cf.SetResidualDerivedParams(fclus, false, false, DcDetector);
        
        return fclus;
        
    }
        
    private Cross findBestSegmentCross(Segment s1, Segment s2, List<Segment> segments, DCGeant4Factory DcDetector) {
        Cross crs1 = this.getMissingSegmentPCross(s1, s2, segments, DcDetector);
        Cross crs2 = this.getMissingSegmentPCross(s2, s1, segments, DcDetector);

        // If crs1 is null, return crs2 (if not null)
        if (crs1 == null) {
            return crs2;
        }

        // If crs2 is null, return crs1
        if (crs2 == null) {
            return crs1;
        }

        // Both are not null, return the one with the lower roadchi2 value
        return (crs1.getPseudoSegment().roadchi2 < crs2.getPseudoSegment().roadchi2) ? crs1 : crs2;
    }
    
    private Cross getMissingSegmentPCross(Segment s1, Segment wrong, List<Segment> segments, DCGeant4Factory DcDetector) {
        Cross cr1 = null;
        List<Segment> Segs2Road1 = new ArrayList<>();
        List<Segment> Segs2Road2 = new ArrayList<>();
        if(wrong!=null) {
            for (Segment s : segments) {
                if(s.get_Id()==wrong.get_Id()) continue; //exclude this superlayer from the road
                Segs2Road2.add(s);
                if(s.get_Superlayer() % 2 == wrong.get_Superlayer() % 2) {
                    Segs2Road1.add(s);
                }
            } 
        } else {
            int slm = 0;
            if(s1.get_RegionSlayer()==1) {
                slm = s1.get_Superlayer()+1;
            } else {
                slm = s1.get_Superlayer()-1;
            }
            for (Segment s : segments) { 
                Segs2Road2.add(s);
                if(s.get_Superlayer() % 2 == slm % 2) {
                    Segs2Road1.add(s);
                }
            }
        }
        if (Segs2Road1.size() == 2) {
            Road r = new Road();
            r.addAll(Segs2Road2);
            rf.fitRoad(r, DcDetector);
            
            Segment pSegment = rf.findRoadMissingSegment(Segs2Road1, 
                        Constants.getInstance().dcDetector,
                        rf.polyfit);
            if(pSegment!=null) {
                cf.Fit(pSegment.get_fittedCluster(), true); 
                if(Constants.DEBUG) System.out.println("Missing Segment "+pSegment.printInfo());
            }
            pSegment.roadchi2 =rf.polyfit_chi2_ov_ndf;
            if(s1.get_RegionSlayer()==1) {
                if(!TBT) {
                    cr1 = crf.getCross(s1, pSegment, DcDetector, 0, 2);
                } else {
                    cr1 = crf.getCrossNoCuts(s1, pSegment, DcDetector, 0); //make a cross anyway, let the KF sort out the hits on tracks for the segment
                }
            } else {
                if(!TBT) {
                    cr1 = crf.getCross(pSegment, s1, DcDetector, 0, 2);
                } else {
                    cr1 = crf.getCrossNoCuts(pSegment, s1, DcDetector, 0); //make a cross anyway, let the KF sort out the hits on tracks for the segment
                }
            }
            if(cr1!=null) { 
                cr1.setPseudoSegment(pSegment);
                if(Constants.DEBUG) System.out.println("Pseudo-cross "+cr1.printInfo());
            }
        }
        
        return cr1;
    }    

    public static List<Hit> Uniq(List<Hit> hits) {
        Set<Hit> uniqueHits = new HashSet<>(hits); // Convert to Set to remove duplicates
        return new ArrayList<>(uniqueHits); // Convert back to List
    }
    
    // Pair class implementation 
    class Pair<T, U> {
        private final T first;
        private final U second;

        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }

        public T getFirst() {
            return first;
        }

        public U getSecond() {
            return second;
        }

        @Override
        public int hashCode() {
            return Objects.hash(first, second);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pair<?, ?> pair = (Pair<?, ?>) o;
            return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
        }
    }
}


