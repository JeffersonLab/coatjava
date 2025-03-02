package org.jlab.rec.dc.nn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public class PatternRec {

    private static final Logger LOGGER = Logger.getLogger(PatternRec.class.getName());

    private final ClusterFinder cfd             = new ClusterFinder();
    private final ClusterFitter cf              = new ClusterFitter();
    private final CrossMaker crf                = new CrossMaker();
    private final RoadFinder rf                 = new RoadFinder();
    private final ClusterCleanerUtilities ct    = new ClusterCleanerUtilities();
    
    public PatternRec() {
        rf.fitPassingCut = 1000;
    }

    public CrossList RecomposeCrossList(List<Segment> clusters, DCGeant4Factory DcDetector) {
        CrossList crossList = new CrossList();
        Map<Integer, List<Cross>> grpCrs = new HashMap<>();
        Map<Integer, List<Segment>> grpCls = new HashMap<>();
        clusters.sort(Comparator.comparing(Segment::get_Region).thenComparing(Segment::get_Superlayer));
        // Group clusters by associated HBTrackID
        for (Segment cls : clusters) {
            
            int index = cls.get(0).get_AssociatedHBTrackID();
            grpCls.computeIfAbsent(index, k -> new ArrayList<>()).add(cls);
        }
        Map<Integer, List<Segment>> regionSegs = new HashMap<>();
        // Process each cluster group
        regionSegs.clear();
        for(Integer I : grpCls.keySet()) {
            List<Segment> segs2Crs = grpCls.get(I);
            //segs2Crs.sort(Comparator.comparing(Segment::get_Region).thenComparing(Segment::get_Superlayer));
            for(Segment s : segs2Crs) {
                regionSegs.computeIfAbsent(s.get_Region(), k -> new ArrayList<>()).add(s);
            }
            
            List<Cross> crosses = new ArrayList<>();
            
            int missingSegmentSL = -1;
            Road evenroad = new Road();
            Road oddroad = new Road();
            int region=0;
            for(int r = 0; r<3; r++) {
                if(regionSegs.containsKey(r+1)) {
                    if(regionSegs.get(r+1).size()==2) {
                        evenroad.add(regionSegs.get(r+1).get(0));
                        oddroad.add(regionSegs.get(r+1).get(1));
                        Cross cr = crf.getCross(regionSegs.get(r+1).get(0), 
                            regionSegs.get(r+1).get(1), DcDetector, 0); 
                        if(cr==null) {
                            //keep only better segment and make a pseudocross: TODO
                        }
                        if(cr!=null) crosses.add(cr);
                    } else {
                        region=r+1;
                        if(regionSegs.get(r+1).get(0).get_RegionSlayer()==1) {
                            missingSegmentSL=2; 
                        } else {
                            missingSegmentSL=1; 
                        } 
                    }
                }
            }
            if(missingSegmentSL==1) {
                Segment mseg = this.missingSegment(oddroad, DcDetector);
                if(mseg!=null) { 
                    Cross cr = this.getPseudoCross(mseg, 
                        regionSegs.get(region).get(0), DcDetector, 0);
                    if(cr!=null) crosses.add(cr);
                }
            } 
            if(missingSegmentSL==2) {
                Segment mseg = this.missingSegment(evenroad, DcDetector);
                if(mseg!=null) {
                    Cross cr = this.getPseudoCross(regionSegs.get(region).get(0),
                            mseg, DcDetector, 0);
                    if(cr!=null) crosses.add(cr);
                }
            } 
            
            grpCrs.put(I, crosses);
            //crossList.trackID = I;
            // Log cross information
            crosses.sort(Comparator.comparing(Cross::get_Region));
            logCrossInfo(crosses);
            // Add valid crosses to the cross list
            if (crosses.size() == 3) {
                crossList.add(crosses);
            }
        }
        return crossList;
    }

    private Segment missingSegment(Road road, DCGeant4Factory DcDetector) {
        if (rf.fitRoad(road, DcDetector)==true) { 
            road.a=rf.qf.a;
            return rf.findRoadMissingSegment(road, DcDetector, road.a);                   
        }                 
        return null;                    
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
            List<Hit> hits = entry.getValue();
            
            int cid = entry.getKey().getSecond();
            
            FittedCluster fclus = this.getFittedCluster(hits,cid, DcDetector);
            
            int tid = entry.getKey().getFirst();
            // Add the fitted cluster as a segment
            Segment seg = createSegment(fclus, DcDetector);
            
            // Add the segment to the appropriate track group
            fclusters.computeIfAbsent(tid, k -> new ArrayList<>()).add(seg);
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
            if (hit.NNTrkId > 0) { 
                Pair<Integer, Integer> index = new Pair<>(hit.NNTrkId, hit.NNClusId);
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

    private Cross getPseudoCross(Segment seg1, Segment seg2, DCGeant4Factory DcDetector, int i) {
        Cross cross = new Cross(seg1.get_Sector(), seg1.get_Region(), 0);
        cross.set_Id(seg1.get_Id()*1000+seg2.get_Id());
        cross.add(seg1);
        cross.add(seg2);
        cross.set_Segment1(seg1);
        cross.set_Segment2(seg2);
        cross.set_CrossParams(DcDetector);

        cross.set_Id(-1);
                        
        cross.set_CrossDirIntersSegWires();
        seg1.associatedCrossId = cross.get_Id();
        seg2.associatedCrossId = cross.get_Id();
        
        return cross;
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


