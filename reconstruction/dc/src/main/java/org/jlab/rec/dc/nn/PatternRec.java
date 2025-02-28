package org.jlab.rec.dc.nn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.detector.geant4.v2.DCGeant4Factory;
import org.jlab.rec.dc.cluster.Cluster;
import org.jlab.rec.dc.cluster.ClusterFitter;
import org.jlab.rec.dc.cluster.FittedCluster;
import org.jlab.rec.dc.cross.Cross;
import org.jlab.rec.dc.cross.CrossList;
import org.jlab.rec.dc.cross.CrossMaker;
import org.jlab.rec.dc.hit.FittedHit;
import org.jlab.rec.dc.hit.Hit;
import org.jlab.rec.dc.segment.Segment;
import org.jlab.rec.dc.trajectory.Road;
import org.jlab.rec.dc.trajectory.RoadFinder;
import org.jlab.service.dc.DCEngine;
/**
 *
 * @author ziegler
 */
public class PatternRec {

    private static final Logger LOGGER = Logger.getLogger(PatternRec.class.getName());

    private final ClusterFitter cf = new ClusterFitter();
    private final CrossMaker crf = new CrossMaker();
    private final RoadFinder rf = new RoadFinder();

    public PatternRec() {
        rf.fitPassingCut = 1000;
    }

    public CrossList RecomposeCrossList(List<Segment> clusters, DCGeant4Factory DcDetector) {
        CrossList crossList = new CrossList();
        Map<Integer, List<Cross>> grpCrs = new HashMap<>();
        Map<Integer, List<Segment>> grpCls = new HashMap<>();

        // Group clusters by associated HBTrackID
        for (Segment cls : clusters) {
            int index = cls.get(0).get_AssociatedHBTrackID();
            grpCls.computeIfAbsent(index, k -> new ArrayList<>()).add(cls);
        }

        // Process each cluster group
        for (Map.Entry<Integer, List<Segment>> entry : grpCls.entrySet()) {
            List<Cross> crosses = crf.find_Crosses(entry.getValue(), DcDetector);
            Collections.sort(crosses);

            // Handle cross count less than 3 (pseudocross)
            if (crosses.size() < 3) {
                handlePseudocross(entry.getValue(), DcDetector);
                crosses = crf.find_Crosses(entry.getValue(), DcDetector); // Recompute after pseudocross handling
                Collections.sort(crosses);
            }

            grpCrs.put(entry.getKey(), crosses);

            // Log cross information
            logCrossInfo(crosses);

            // Add valid crosses to the cross list
            if (crosses.size() == 3) {
                crossList.add(crosses);
            }
        }
        return crossList;
    }

    private void handlePseudocross(List<Segment> segments, DCGeant4Factory DcDetector) {
        List<Road> allRoads = rf.findRoads(segments, DcDetector);
        for (Road road : allRoads) {
            List<Segment> Segs2Road = new ArrayList<>();
            int missingSL = -1;

            // Find missing superlayer
            for (int ri = 0; ri < 3; ri++) {
                if (road.get(ri).associatedCrossId == -1) {
                    missingSL = getMissingSuperlayer(road.get(ri).get_Superlayer());
                }
            }

            // Add segments to road
            addSegmentsToRoad(segments, road, missingSL, Segs2Road);

            // If exactly 2 segments are found, add the missing segment
            if (Segs2Road.size() == 2) {
                Segment pSegment = rf.findRoadMissingSegment(Segs2Road, DcDetector, road.a);
                if (pSegment != null) {
                    segments.add(pSegment);
                }
            }
        }
    }

    private int getMissingSuperlayer(int superlayer) {
        return superlayer % 2 == 1 ? superlayer + 1 : superlayer - 1;
    }

    private void addSegmentsToRoad(List<Segment> segments, Road road, int missingSL, List<Segment> Segs2Road) {
        for (int ri = 0; ri < 3; ri++) {
            for (Segment s : segments) {
                if (s.get_Sector() == road.get(ri).get_Sector() &&
                    s.get_Region() == road.get(ri).get_Region() &&
                    s.associatedCrossId == road.get(ri).associatedCrossId &&
                    road.get(ri).associatedCrossId != -1) {
                    if (s.get_Superlayer() % 2 == missingSL % 2)
                        Segs2Road.add(s);
                }
            }
        }
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
            List<Hit> chits = entry.getValue();
            if (chits.size() < 4) {
                System.out.println("Check event "+DCEngine.evNum);
                
                continue;
            }

            int tid = entry.getKey().getFirst();
            int cid = entry.getKey().getSecond();

            // Create the cluster from hits
            Cluster c = new Cluster(chits.get(0).get_Sector(), chits.get(0).get_Superlayer(), cid);
            c.addAll(chits);

            // Create fitted cluster and update hits
            FittedCluster fclus = createFittedCluster(c, DcDetector);

            // Add the fitted cluster as a segment
            Segment seg = createSegment(fclus, DcDetector);

            // Add the segment to the appropriate track group
            fclusters.computeIfAbsent(tid, k -> new ArrayList<>()).add(seg);
        }

        return fclusters;
    }

    private FittedCluster createFittedCluster(Cluster cluster, DCGeant4Factory DcDetector) {
        FittedCluster fclus = new FittedCluster(cluster);
        fclus.set_Id(cluster.get_Id());

        // Update hit parameters and fit the cluster
        for (FittedHit fhit : fclus) {
            fhit.set_AssociatedClusterID(fclus.get_Id());
            fhit.set_TrkgStatus(0);
            fhit.updateHitPosition(DcDetector);
        }

        cf.SetFitArray(fclus, "TSC");
        cf.Fit(fclus, true);
        cf.SetResidualDerivedParams(fclus, false, false, DcDetector);
        return fclus;
    }

    private Segment createSegment(FittedCluster fclus, DCGeant4Factory DcDetector) {
        cf.SetFitArray(fclus, "TSC");
        cf.Fit(fclus, false);
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

    // Pair class implementation remains unchanged
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


