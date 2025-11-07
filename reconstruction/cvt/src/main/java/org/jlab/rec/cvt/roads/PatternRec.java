/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.roads;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import org.jlab.detector.banks.RawDataBank;
import org.jlab.geom.prim.Point3D;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.cross.Cross;
import static org.jlab.rec.cvt.services.CVTEngine.targetDirName;
import org.jlab.rec.cvt.track.Seed;
import org.jlab.rec.cvt.trajectory.Helix;

/**
 *
 * @author veronique
 */
public class PatternRec {
    public static double CTOFRadius = 251.1; //mm
    public static double CTOFThkn = 30.226;
    public static int minNClusters;
    public static double maxDeltaStrips;
    
    
    public static boolean crossBelongsToMap(int paddle, Cross cross, Map<Integer, Set<Road>> paddleRoads) {
        // 1. Compute phi of the cross
        double phiRad = Math.atan2(cross.getPoint().y(), cross.getPoint().x());
        double phiDeg = Math.toDegrees(phiRad);
        if (phiDeg < 0) phiDeg += 360.0;

        // 2. Find its phi bin
        int phiBin = RoadMaker.getPhiBin(phiDeg);

        // 3. Retrieve the list of roads for that paddle
        Set<Road> roads = paddleRoads.get(paddle);
        if (roads == null) {
            return false; // no roads in this paddle
        }

        // 4. Check if this phi bin pattern exists in any road
        for (Road road : roads) {
            Map<Integer, RoadElement>re = road.road; 
            if(re.containsKey(cross.getCluster1().getLayer())) {
                int bin = re.get(cross.getCluster1().getLayer()).phiBin;
                if (bin == phiBin) {
                    return true; // match found
                }
            }
        }

        return false; // no match
    }
    
    public static Set<Integer> getCTOFHitPaddles(DataEvent event) {
        Set<Integer> paddles = new HashSet<>();
        String detADC = "CTOF";
        detADC += "::adc";
        String detTDC = "CTOF";
        detTDC += "::tdc";

        if (event.hasBank(detADC) == false && event.hasBank(detTDC) == false) {
            return paddles;
        }
        if (event.hasBank(detADC) == true) {
            RawDataBank bank = new RawDataBank(detADC);
            bank.read(event);
            int bankSize = bank.rows();
            for (int i = 0; i < bankSize; i++) {
                paddles.add(bank.getShort("component", i));
            }
        }
        if (event.hasBank(detTDC) == true) {
            RawDataBank bank = new RawDataBank(detTDC);
            bank.read(event);
            int bankSize = bank.rows();
            for (int i = 0; i < bankSize; i++) {
                paddles.add(bank.getShort("component", i));
            }
        }
        return paddles;
    }
    
    public static boolean matchSeedToCTOF(Seed seed, Set<Integer> paddles) {
        Helix helix = seed.getHelix();
        if (helix == null) return false;
        if(paddles==null || paddles.isEmpty()) return false;
        List<Point3D> spread = new ArrayList<>();
        spread.addAll(helix.PointSpreadHelix(helix, CTOFRadius));
        spread.addAll(helix.PointSpreadHelix(helix, CTOFRadius + CTOFThkn * 0.5));

        for (Point3D point : spread) {
            double phiRad = Math.atan2(point.y(), point.x());
            double phiDeg = Math.toDegrees(phiRad);
            if (phiDeg < 0) phiDeg += 360.0;  // normalize to [0,360)

            int pad = RoadMaker.getCTOFPaddle(phiDeg);
            if (paddles.contains(pad)) { 
                return true;
            }
        }
        return false;
    }
    
    
    public static List<ArrayList<Cross>> findTrackCandidates (
        List<Cross> crosses, Map<Integer, Set<CompactRoad>> paddleRoads,
        Set<Integer> paddles) {

        List<ArrayList<Cross>> candidates = new ArrayList<>();

        // Group crosses by region for quick lookup
        Map<Integer, List<Cross>> crossesByRegion = crosses.stream()
                .collect(Collectors.groupingBy(Cross::getRegion));

        // Iterate over all paddles that have roads
        for (Map.Entry<Integer, Set<CompactRoad>> entry : paddleRoads.entrySet()) {
            int paddle = entry.getKey(); 
            if(paddles.contains(paddle)) {
                Set<CompactRoad> roads = entry.getValue();
                ArrayList<Cross> track = new ArrayList<>();
                // For each road pattern
                for (CompactRoad road : roads) {
                    List<CompactElement>re = road.elements; 
                    for(int r = 1; r<4; r++) {
                        int l = 2*r-1;
                        //if(re.containsKey(l)) {
                            // Try to find crosses matching each phi bin
                            Cross matchR = findClosestCrossInBin(crossesByRegion.get(l), re.get(l).phiBin);
                            if (matchR != null) track.add(matchR);
                       // }
                    }
                    
                    // only keep if 2 or more crosses found
                    if (track.size() >= 2) {
                        // Avoid duplicates (same cross reused)
                        track.sort(Comparator.comparingInt(Cross::getRegion));
                        if (!alreadyExists(candidates, track)) {
                            candidates.add(track);
                        }
                    }
                }
            }
        }

        return candidates;
    }

    // helper: find the cross with φ-bin close to expected
    private static Cross findClosestCrossInBin(List<Cross> crosses, int targetBin) {
        if (crosses == null) return null;
        double tolerance = 1; // bins
        for (Cross c : crosses) {
            double phiDeg = Math.toDegrees(Math.atan2(c.getPoint().y(), c.getPoint().x()));
            phiDeg = phiDeg % 360.0;
            if (phiDeg < 0) phiDeg += 360.0;
            int bin = RoadMaker.getPhiBin(phiDeg);
            if (Math.abs(bin - targetBin) <= tolerance) {
                return c;
            }
        }
        return null;
    }

    // helper: avoid duplicates (compare IDs)
    private static boolean alreadyExists(List<ArrayList<Cross>> candidates, ArrayList<Cross> newTrack) {
        for (ArrayList<Cross> t : candidates) {
            if (t.containsAll(newTrack) && newTrack.containsAll(t)) return true;
        }
        return false;
    }

    public Map<CompactRoad, List<Cluster>> filterUsingRoads(Map<Integer, List<Cluster>> clusters, Set<Integer> paddles) throws IOException {
        // Candidate roads for this event
        Map<CompactRoad, List<Cluster>> savedclusters = new HashMap<>();
        
        for (int paddle : paddles) {

            String fileName = String.format(targetDirName+"svt_roads_paddle_%02d.bin.gz", paddle);
            try (DataInputStream dis = new DataInputStream(
                    new BufferedInputStream(
                            new GZIPInputStream(new FileInputStream(fileName))))) {

                while (dis.available() > 0) {
                    // Read one road
                    int q = dis.readInt();
                    double p = (double) dis.readFloat();
                    double theta = (double) dis.readFloat();
                    double phi = (double) dis.readFloat();
                    double ztar = (double) dis.readFloat();
                    int nElements = dis.readInt();
                    
                    CompactRoad road = new CompactRoad();

                    road.q = q;
                    road.p = p;
                    road.theta=theta;
                    road.phi=phi;
                    road.z = ztar;

                    for (int i = 0; i < nElements; i++) {
                        int sector = dis.readInt();
                        int layer = dis.readInt();
                        int strip = dis.readInt();
                        int phiBin = dis.readInt();
                        double x = dis.readDouble();
                        double y = dis.readDouble();
                        double z = dis.readDouble();
                        road.elements.add(new CompactElement(sector,layer, strip, phiBin, x, y, z));
                    }
                    for(CompactElement ce : road.elements) {
                        Cluster cl = getClosestStripCluster(ce.sector, ce.layer, ce.strip, clusters); 
                        
                        if(cl!=null) {
                            String key = q+"_"+p+"_"+theta+"_"+phi+"_"+ztar;
                            cl.associatedRoadKey=key;
                            savedclusters
                                .computeIfAbsent(road, k -> new ArrayList<>())
                                .add(cl);
                        }
                    }
                }
            }
        }
        //only keep if it has N clusters:
        savedclusters.entrySet().removeIf(entry -> entry.getValue().size() < minNClusters);
        return savedclusters;
    }

    private Cluster getClosestStripCluster(int sector, int layer, int strip, Map<Integer, List<Cluster>> clusters) {
        Cluster cl = null;
        Cluster scl=null;
        int key = sector * 100 + layer;
        if(clusters.containsKey(key)) {
            List<Cluster> cls = clusters.get(key);

            double deltaStrip=Double.POSITIVE_INFINITY;
            for(Cluster c : cls) {
                if(Math.abs(strip-c.getCentroid())<deltaStrip) {
                    deltaStrip = Math.abs(strip-c.getCentroid());
                    scl =c;
                }
            }
            if(scl!=null) {
                if(deltaStrip<maxDeltaStrips*cls.size()) cl = scl;
            }
        }
        return cl;
    }

    public void selectDetectorClusters(List<Cluster> clusters,Set<Integer> paddles, int index) {
        Map<Integer, List<Cluster>> mclusters = new HashMap<>();
        for (Cluster c : clusters) {
            int key = -1;
            if(index==0) key = c.getSector() * 100 + c.getLayer(); // assumes <100 layers
            if(index==1) key = c.getSector() * 100 + (c.getLayer()+6);
            mclusters
                .computeIfAbsent(key, k -> new ArrayList<>())
                .add(c);
        }
        try {
            Map<CompactRoad, List<Cluster>> rcls=this.filterUsingRoads(mclusters, paddles);
            
            //save road-mapped clusters
            List<Cluster> uniqueClusters = rcls.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.collectingAndThen(
                        Collectors.toMap(Cluster::getId, c -> c, (c1, c2) -> c1),
                        m -> new ArrayList<>(m.values())
                    ));
            
            clusters.clear();
            clusters.addAll(uniqueClusters);

        } catch (IOException ex) {
            System.getLogger(PatternRec.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }
    public void selectClusters(List<ArrayList<Cluster>> clusters,Set<Integer> paddles) {
            this.selectDetectorClusters(clusters.get(0), paddles, 0);
            this.selectDetectorClusters(clusters.get(1), paddles, 1);

    }
}