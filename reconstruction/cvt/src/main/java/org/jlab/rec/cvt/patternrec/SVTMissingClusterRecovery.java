/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.patternrec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jlab.detector.base.DetectorType;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.rec.cvt.Constants;
import org.jlab.rec.cvt.Geometry;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.cross.Cross;
import org.jlab.rec.cvt.patternrec.fit.HelixClusterFitter;
import org.jlab.rec.cvt.patternrec.fit.HelixClusterRoadFitter;
import org.jlab.rec.cvt.track.Seed;

/**
 *
 * @author veronique
 */

/**
 * Recover missing SVT cluster(s) for a fitted seed that currently has only 2 SVT crosses.
 *
 * It searches for missing cluster candidates on the missing region layers, adds them to the
 * seed cluster list, and ranks recovered candidates with HelixClusterRoadFitter.
 */
public class SVTMissingClusterRecovery {

    private final HelixClusterRoadFitter roadFitter = new HelixClusterRoadFitter();

    private static final class ClusterScore {
        final Cluster cluster;
        final double score;

        ClusterScore(Cluster cluster, double score) {
            this.cluster = cluster;
            this.score = score;
        }
    }

    private static final class PredictedState {
        final int layer;
        final int sector;
        final double radius;
        final double phi;
        final double z;
        final Point3D point;

        PredictedState(int layer, int sector, double radius, double phi, double z, Point3D point) {
            this.layer = layer;
            this.sector = sector;
            this.radius = radius;
            this.phi = phi;
            this.z = z;
            this.point = point;
        }
    }

    private static final class RankedSeed {
        final Seed seed;
        final double score;

        RankedSeed(Seed seed, double score) {
            this.seed = seed;
            this.score = score;
        }
    }

    private final double bfield;
    private final double xbeam;
    private final double ybeam;

    private double phiWindow = Math.toRadians(12.0);
    private double zWindow = 40.0;
    private int maxCandidatesPerLayer = 8;
    private int maxReturnedSeeds = 4;
    private boolean allowNeighborSector = true;
    private boolean debug = false;

    public SVTMissingClusterRecovery(double bfield, double xb, double yb) {
        this.bfield = bfield;
        this.xbeam = xb;
        this.ybeam = yb;
        if(Constants.getInstance().seedingDebugMode) debug=true;
    }

    public void setPhiWindowRadians(double phiWindow) {
        this.phiWindow = phiWindow;
    }

    public void setZWindow(double zWindow) {
        this.zWindow = zWindow;
    }

    public void setMaxCandidatesPerLayer(int maxCandidatesPerLayer) {
        this.maxCandidatesPerLayer = maxCandidatesPerLayer;
    }

    public void setMaxReturnedSeeds(int maxReturnedSeeds) {
        this.maxReturnedSeeds = maxReturnedSeeds;
    }

    public void setAllowNeighborSector(boolean allowNeighborSector) {
        this.allowNeighborSector = allowNeighborSector;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    private boolean addClusterIfAbsent(Seed seed, Cluster cluster) {
        if (seed == null || cluster == null) {
            return false;
        }

        if (seed.getClusters() != null) {
            for (Cluster existing : seed.getClusters()) {
                if (existing == null) {
                    continue;
                }
                if (existing.getId() == cluster.getId()) {
                    return false;
                }
            }
        }

        seed.add_Cluster(cluster);
        return true;
    }
    
    /**
     * Recover missing-cluster candidates for one fitted seed.
     *
     * Returns the original seed plus recovered candidates, best first.
     */
    public List<Seed> recover(Seed seed, List<Cluster> allSvtClusters, int polarity) {
        if (seed == null) {
            return Collections.emptyList();
        }

        List<Seed> out = new ArrayList<>();
        out.add(seed);

        List<Cross> svtCrosses = getSVTCrosses(seed);
        if (svtCrosses.size() != 2) {
            return out;
        }

        Integer missingRegion = findMissingRegion(seed);
        if (missingRegion == null) {
            return out;
        }

        int innerLayer = regionToInnerLayer(missingRegion);
        int outerLayer = regionToOuterLayer(missingRegion);

        PredictedState predInner = predictAtLayer(seed, innerLayer);
        PredictedState predOuter = predictAtLayer(seed, outerLayer);

        if (predInner == null || predOuter == null) {
            if (debug) {
                System.out.println("SVTMissingClusterRecovery: prediction failed for missing region " + missingRegion);
            }
            return out;
        }

        Set<Integer> usedClusterIds = getUsedClusterIds(seed);

        List<Cluster> innerCandidates = findCandidateClusters(
                allSvtClusters, usedClusterIds, innerLayer, predInner.sector, predInner.phi, predInner.z);

        List<Cluster> outerCandidates = findCandidateClusters(
                allSvtClusters, usedClusterIds, outerLayer, predOuter.sector, predOuter.phi, predOuter.z);

        if (debug) {
            System.out.println("SVTMissingClusterRecovery for seed:");
            System.out.println(seed);
            System.out.println("  missing region = " + missingRegion);
            System.out.println("  missing layers = " + innerLayer + ", " + outerLayer);
            System.out.println("  inner candidates = " + innerCandidates.size());
            for (Cluster cl : innerCandidates) {
                System.out.println(cl);
            }
            System.out.println("  outer candidates = " + outerCandidates.size());
            for (Cluster cl : outerCandidates) {
                System.out.println(cl);
            }
        }
        List<RankedSeed> recovered = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Seed bestAfterInner = null;
        Seed bestAfterOuter = null;

        // ---------------------------------------------------------------------
        // Step 1: find the best inner-layer recovery candidate
        // ---------------------------------------------------------------------
        if (!innerCandidates.isEmpty()) {
            List<RankedSeed> innerRecovered = new ArrayList<>();

            for (Cluster cIn : innerCandidates) {
                Seed candidate = cloneSeed(seed);
                // guard against duplicates / wrong layer reuse
                if (!addClusterIfAbsent(candidate, cIn)) {
                    continue;
                }
                HelixClusterFitter.FitResult fit = fitClusters(candidate, polarity);
                if (fit == null) {
                    continue;
                }

                String key = seedKey(candidate);
                if (!seen.add(key)) {
                    continue;
                }

                innerRecovered.add(new RankedSeed(candidate, scoreRecoveredSeed(candidate, fit)));
            }

            if (!innerRecovered.isEmpty()) {
                innerRecovered.sort(Comparator.comparingDouble(rs -> rs.score));
                bestAfterInner = innerRecovered.get(0).seed;
                recovered.add(innerRecovered.get(0));
            }
        }
        
        // ---------------------------------------------------------------------
        // Step 2: find the best outer-layer recovery candidate
        // ---------------------------------------------------------------------
        if (!innerCandidates.isEmpty()) {
            List<RankedSeed> outerRecovered = new ArrayList<>();

            for (Cluster cOut : outerCandidates) {
                Seed candidate = cloneSeed(seed);
                // guard against duplicates / wrong layer reuse
                if (!addClusterIfAbsent(candidate, cOut)) {
                    continue;
                }
                HelixClusterFitter.FitResult fit = fitClusters(candidate, polarity);
                if (fit == null) {
                    continue;
                }

                String key = seedKey(candidate);
                if (!seen.add(key)) {
                    continue;
                }

                outerRecovered.add(new RankedSeed(candidate, scoreRecoveredSeed(candidate, fit)));
            }

            if (!outerRecovered.isEmpty()) {
                outerRecovered.sort(Comparator.comparingDouble(rs -> rs.score));
                bestAfterOuter = outerRecovered.get(0).seed;
                recovered.add(outerRecovered.get(0));
            }
        }

        // ---------------------------------------------------------------------
        // Step 3: starting from the best inner-recovered seed (if any),
        // find the best outer-layer recovery candidate
        // ---------------------------------------------------------------------
        Seed baseForOuter = (bestAfterInner != null) ? bestAfterInner : cloneSeed(seed);
        if (!outerCandidates.isEmpty()) {
            List<RankedSeed> outerRecovered = new ArrayList<>();

            for (Cluster cOut : outerCandidates) {
                Seed candidate = cloneSeed(baseForOuter);
                candidate.getClusters().clear();
                candidate.add_Clusters(baseForOuter.getClusters());
                // guard against duplicates / wrong layer reuse
                if (!addClusterIfAbsent(candidate, cOut)) {
                    continue;
                }
                HelixClusterFitter.FitResult fit = fitClusters(candidate, polarity);
                if (fit == null) {
                    continue;
                }

                String key = seedKey(candidate);
                if (!seen.add(key)) {
                    continue;
                }

                outerRecovered.add(new RankedSeed(candidate, scoreRecoveredSeed(candidate, fit)));
            }

            if (!outerRecovered.isEmpty()) {
                outerRecovered.sort(Comparator.comparingDouble(rs -> rs.score));
                recovered.add(outerRecovered.get(0));
            }
        }

        if (recovered.isEmpty()) {
            return out;
        }

        recovered.sort(Comparator.comparingDouble(rs -> rs.score));

        int kept = 0;
        for (RankedSeed rs : recovered) {
            out.add(rs.seed);
            kept++;
            if (kept >= maxReturnedSeeds) {
                break;
            }
        }
        return out;
    }
    

    /**
     * Apply recovery to a whole seed list.
     */
    public List<Seed> recoverAll(List<Seed> seeds, List<Cluster> allSvtClusters, int polarity) {
        if (seeds == null || seeds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Seed> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Seed seed : seeds) {
            List<Seed> recovered = recover(seed, allSvtClusters, polarity);
            for (Seed s : recovered) {
                String key = seedKey(s);
                if (seen.add(key)) {
                    out.add(s);
                }
            }
        }

        return out;
    }

    private HelixClusterFitter.FitResult fitClusters(Seed seed, int polarity) {
        try {
            LinkedHashSet<Cluster> clusterSet = new LinkedHashSet<>(seed.getClusters());
            HelixClusterFitter.FitResult fit = roadFitter.fitRoad(clusterSet, polarity, xbeam, ybeam);
            if (fit != null && Double.isFinite(fit.chi2)) {
                return fit;
            }
        } catch (Exception ex) {
            if (debug) {
                System.out.println("roadFitter failed: " + ex.getMessage());
            }
        }
        return null;
    }

    private List<Cross> getSVTCrosses(Seed seed) {
        List<Cross> out = new ArrayList<>();
        for (Cross c : seed.getCrosses()) {
            if (c != null && c.getDetector() == DetectorType.BST) {
                out.add(c);
            }
        }
        return out;
    }

    private Integer findMissingRegion(Seed seed) {
        boolean[] hasRegion = new boolean[4];

        for (Cross c : seed.getCrosses()) {
            if (c == null || c.getDetector() != DetectorType.BST) {
                continue;
            }
            int region = c.getRegion();
            if (region >= 1 && region <= 3) {
                hasRegion[region] = true;
            }
        }

        int missingCount = 0;
        int missingRegion = -1;
        for (int r = 1; r <= 3; r++) {
            if (!hasRegion[r]) {
                missingCount++;
                missingRegion = r;
            }
        }

        return missingCount == 1 ? missingRegion : null;
    }

    private int regionToInnerLayer(int region) {
        return 2 * region - 1;
    }

    private int regionToOuterLayer(int region) {
        return 2 * region;
    }

    private PredictedState predictAtLayer(Seed seed, int layer) {
        try {
            double radius = Geometry.getInstance().getSVT().getLayerRadius(layer);
            Point3D p = seed.getHelix().getPointAtRadius(radius);
            if (p == null) {
                return null;
            }

            double phi = Math.atan2(p.y(), p.x());
            double z = p.z();
            int sector = Geometry.getInstance().getSVT().getSector(layer, p);

            return new PredictedState(layer, sector, radius, phi, z, p);
        } catch (Exception ex) {
            if (debug) {
                System.out.println("predictAtLayer failed for layer " + layer + ": " + ex.getMessage());
            }
            return null;
        }
    }

    private List<Cluster> findCandidateClusters(List<Cluster> allClusters,
                                                Set<Integer> usedClusterIds,
                                                int layer,
                                                int sector,
                                                double phiPred,
                                                double zPred) {
        if (allClusters == null || allClusters.isEmpty()) {
            return Collections.emptyList();
        }

        List<ClusterScore> scored = new ArrayList<>();

        for (Cluster cl : allClusters) {
            if (cl == null) {
                continue;
            }
            if (usedClusterIds != null && usedClusterIds.contains(cl.getId())) {
                continue;
            }
            if (cl.getLayer() != layer) {
                continue;
            }

            int csec = cl.getSector();
            if (csec != sector && !(allowNeighborSector && isNeighborSector(csec, sector))) {
                continue;
            }

            Point3D rep = representativePoint(cl);
            double phi = Math.atan2(rep.y(), rep.x());
            double z = rep.z();

            double dphi = Math.abs(deltaPhi(phi, phiPred));
            double dz = Math.abs(z - zPred);

            if (dphi > phiWindow || dz > zWindow) {
                continue;
            }

            double score = dphi + 0.05 * dz;
            scored.add(new ClusterScore(cl, score));
        }

        scored.sort(Comparator.comparingDouble(cs -> cs.score));

        List<Cluster> out = new ArrayList<>();
        int n = Math.min(maxCandidatesPerLayer, scored.size());
        for (int i = 0; i < n; i++) {
            out.add(scored.get(i).cluster);
        }
        return out;
    }

    private boolean isNeighborSector(int s1, int s2) {
        int nsec = 18;
        int d = Math.abs(s1 - s2);
        return Math.min(d, nsec - d) == 1;
    }

    private double deltaPhi(double a, double b) {
        double d = a - b;
        while (d > Math.PI) {
            d -= 2.0 * Math.PI;
        }
        while (d < -Math.PI) {
            d += 2.0 * Math.PI;
        }
        return d;
    }

    /**
     * Representative point for candidate lookup only.
     */
    private Point3D representativePoint(Cluster cluster) {
        Line3D line = cluster.getLine();
        Point3D o = line.origin();
        Point3D e = line.end();
        return new Point3D(
                0.5 * (o.x() + e.x()),
                0.5 * (o.y() + e.y()),
                0.5 * (o.z() + e.z())
        );
    }

    private Seed cloneSeed(Seed seed) {
        Seed copy = new Seed();
        copy.setHelix(seed.getHelix());
        copy.setCrosses(seed.getCrosses());
        copy.setDoca(seed.getDoca());
        copy.setRho(seed.getRho());
        copy.setPhi(seed.getPhi());
        copy.setStatus(seed.getStatus());

        return copy;
    }

    private String seedKey(Seed seed) {
        List<Integer> ids = new ArrayList<>();
        for (Cluster c : seed.getClusters()) {
            ids.add(c.getId());
        }
        Collections.sort(ids);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append('-');
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    private double scoreRecoveredSeed(Seed seed, HelixClusterFitter.FitResult fit) {
        int nClusters = seed.getClusters().size();
        int nLayers = countSVTLayers(seed);

        double score = 0.0;
        score += 100.0 * Math.max(0, 6 - nLayers);
        score += 10.0 * Math.max(0, 6 - nClusters);
        score += Double.isFinite(fit.chi2) ? fit.chi2 : 1.0e6;

        return score;
    }

    private int countSVTLayers(Seed seed) {
        boolean[] hasLayer = new boolean[7];

        if (seed.getClusters() != null) {
            for (Cluster cl : seed.getClusters()) {
                if (cl != null) {
                    int layer = cl.getLayer();
                    if (layer >= 1 && layer <= 6) {
                        hasLayer[layer] = true;
                    }
                }
            }
        }

        int count = 0;
        for (int l = 1; l <= 6; l++) {
            if (hasLayer[l]) {
                count++;
            }
        }
        return count;
    }

    private Set<Integer> getUsedClusterIds(Seed seed) {
        Set<Integer> used = new HashSet<>();

        for (Cross c : seed.getCrosses()) {
            if (c == null) {
                continue;
            }
            if (c.getCluster1() != null) {
                used.add(c.getCluster1().getId());
            }
            if (c.getCluster2() != null) {
                used.add(c.getCluster2().getId());
            }
        }

        if (seed.getClusters() != null) {
            for (Cluster cl : seed.getClusters()) {
                if (cl != null) {
                    used.add(cl.getId());
                }
            }
        }

        return used;
    }
}