/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.rec.cvt.patternrec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlab.clas.swimtools.Swim;
import org.jlab.detector.base.DetectorType;
import org.jlab.rec.cvt.Constants;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.cross.Cross;
import org.jlab.rec.cvt.fit.CircleFitPars;
import org.jlab.rec.cvt.fit.CircleFitter;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.rec.cvt.patternrec.fit.HelixClusterFitter.FitResult;
import org.jlab.rec.cvt.patternrec.fit.HelixClusterRoadFitter;
import org.jlab.rec.cvt.services.RecUtilities;
import org.jlab.rec.cvt.svt.SVTParameters;
import org.jlab.rec.cvt.track.Seed;
import org.jlab.rec.cvt.track.Track;

/**
 *
 * @author veronique
 */

/**
 * SVT-only seeder based on the existing phi-bin logic.
 *
 * Workflow:
 *  1) build 2-hit / 3-hit SVT seedlets in phi bins
 *  2) prefit circles and reject bad residual combinations
 *  3) run Seed.fit(...)
 *  4) remove overlaps using HelixClusterRoadFitter chi2/ndf
 */
public class SVTSeeder {

    private static final int NBINS = 36;
    private static final double[] PHI_SHIFTS = {0.0, 65.0, 90.0};
    private static final double BEAM_WEIGHT = 0.1;
    private static final double CIRCLE_CHI2_CUT = 10.0;
    private static final double PHI_RANGE_CUT_DEG = 45.0;

    private final HelixClusterRoadFitter roadFitter = new HelixClusterRoadFitter();

    private final Map<Double, ArrayList<Cross>> seedMap = new HashMap<>();
    private final Map<Integer, Map<Integer, ArrayList<Cross>>> sortedCrosses = new HashMap<>();
    private final List<Seed> seedScan = new ArrayList<>();
    private final List<Double> xs = new ArrayList<>();
    private final List<Double> ys = new ArrayList<>();
    private final List<Double> ws = new ArrayList<>();

    private final double bfield;
    private final double xbeam;
    private final double ybeam;
    private Swim swimmer;
    private SVTMissingClusterRecovery cr;
    
    public SVTSeeder(Swim swimmer, double xb, double yb) {
        this.swimmer = swimmer;
        float[] b = new float[3];
        this.swimmer.BfieldLab(0, 0, 0, b);
        bfield = Math.abs(b[2]);
        xbeam = xb;
        ybeam = yb;
        
        cr = new SVTMissingClusterRecovery(bfield, xbeam, ybeam);
    }

    
    public List<Seed> findSeeds(List<Cross> svtCrosses,
                           int polarity,
                           Set<Integer> paddles,
                           List<Cluster> ssaClusters) {
        List<Cross> usable = filterSVTCrosses(svtCrosses);
        findSeedCrossList(usable);
        cleanupCircleSeeds();

        List<Seed> fittedSeeds = new ArrayList<>();
        for (Seed seed : getSeedScan()) {
            if (seed.getCrosses().size() < 2) {
                continue;
            }

            boolean fitStatus = seed.fit(Constants.SEEDFITITERATIONS, xbeam, ybeam, bfield);
            if (fitStatus && seed.isGood()) {
                fittedSeeds.add(seed);
            }
        }

        if (fittedSeeds.isEmpty()) {
            if(Constants.getInstance().seedingDebugMode)
                System.out.println("**************************** NO ACCEPTED SEEDS !!!!!!!!!!!!!!!!!!!!!!!!!");
            return fittedSeeds;
        }

        // Keep only fitted seeds that match CTOF
        fittedSeeds = keepSeedsMatchingCTOF(fittedSeeds, paddles, xbeam, ybeam);
        if (fittedSeeds.isEmpty()) {
            if(Constants.getInstance().seedingDebugMode)
                System.out.println("**************************** NO MATCH TO CTOF!!!!!!!!!!!!!!!!!!!!!!!!!");
            return fittedSeeds;
        }
        //Search for missing clusters
        List<Seed> clsRecSeeds = cr.recoverAll(fittedSeeds, ssaClusters, polarity);
        
        if(Constants.getInstance().seedingDebugMode){
            System.out.println("**********************************************************************Before overlap remover *********************************************************************");
            for(Seed s : clsRecSeeds) System.out.println(s.toString());
        }
        
        //return clsRecSeeds;
        // Then remove overlaps on the CTOF-matched subset
        List<Seed> clsRecSeedsOlvRm = removeOverlappingSeedsWithRoadFit(clsRecSeeds, polarity);
        if(Constants.getInstance().seedingDebugMode){
            System.out.println("*********************************************************************After overlap remover *********************************************************************");
            for(Seed s : clsRecSeeds) System.out.println(s.toString());
        }
        return clsRecSeedsOlvRm;
    }
    
    /**
     * Finds SVT seedlets from SVT crosses only.
     */
    public void findSeedCrossList(List<Cross> crosses) {
        seedMap.clear();
        seedScan.clear();

        for (double phiShift : PHI_SHIFTS) {
            findSeedCrossesFixedBin(crosses, phiShift);
        }

        seedMap.forEach((key, value) -> fitSeed(value));
    }

    public void fitSeed(List<Cross> seedCrosses) {
        CircleFitPars pars = fitCircle(seedCrosses);
        if (pars == null) {
            return;
        }

        double d = pars.doca();
        double r = pars.rho();
        double f = pars.phi();

        for (Cross c : seedCrosses) {
            double residual = calcResidual(r, c, d, f);
            if (Math.abs(residual) > SVTParameters.RESIMAX) {
                if (Constants.getInstance().seedingDebugMode) {
                    System.out.println("SVT seed prefit failed on " + c.printInfo()
                            + " with residual " + Math.abs(residual)
                            + " > " + SVTParameters.RESIMAX);
                }
                return;
            }
        }

        seedScan.add(new Seed(seedCrosses, d, r, f));
    }

    private List<Cross> filterSVTCrosses(List<Cross> crosses) {
        List<Cross> out = new ArrayList<>();
        if (crosses == null) {
            return out;
        }
        
        for (Cross c : crosses) {
            if (c == null) {
                continue;
            }
            if (c.getDetector() != DetectorType.BST) {
                continue;
            }
            
            out.add(c);
        }
        return out;
    }

    private void findSeedCrossesFixedBin(List<Cross> crosses, double phiShift) {
        sortedCrosses.clear();
        int[][] layerCounts = new int[NBINS][3];

        for (Cross cross : crosses) {
            cross.reset();

            double phi = Math.toDegrees(cross.getPoint().toVector3D().phi()) + phiShift;
            if (phi < 0.0) {
                phi += 360.0;
            }

            int binIdx = (int) (phi / (360.0 / NBINS));
            if (binIdx > NBINS - 1) {
                binIdx = NBINS - 1;
            }

            int regionIdx = cross.getRegion() - 1;

            sortedCrosses
                    .computeIfAbsent(binIdx, k -> new HashMap<>())
                    .computeIfAbsent(regionIdx, k -> new ArrayList<>())
                    .add(cross);

            layerCounts[binIdx][regionIdx]++;
        }

        for (int b = 0; b < NBINS; b++) {
            for (int r = 0; r < 3; r++) {
                if (layerCounts[b][r] == 0) {
                    layerCounts[b][r] = 1;
                }
            }
        }

        List<Cross> hits = new ArrayList<>(3);

        for (int bin = 0; bin < NBINS; bin++) {
            if (!sortedCrosses.containsKey(bin)) {
                continue;
            }

            for (int i1 = 0; i1 < layerCounts[bin][0]; i1++) {
                for (int i2 = 0; i2 < layerCounts[bin][1]; i2++) {
                    for (int i3 = 0; i3 < layerCounts[bin][2]; i3++) {
                        hits.clear();

                        addIfPresent(hits, bin, 0, i1);
                        addIfPresent(hits, bin, 1, i2);
                        addIfPresent(hits, bin, 2, i3);

                        if (hits.size() == 3) {
                            if (checkZ(new ArrayList<>(hits))) {
                                addToSeedMap(new ArrayList<>(hits));
                            }
                        } else if (hits.size() == 2) {
                            addToSeedMap(new ArrayList<>(hits));
                        }
                    }
                }
            }
        }
    }

    private void addIfPresent(List<Cross> hits, int bin, int region, int index) {
        Map<Integer, ArrayList<Cross>> binMap = sortedCrosses.get(bin);
        if (binMap == null) {
            return;
        }
        List<Cross> list = binMap.get(region);
        if (list == null) {
            return;
        }
        hits.add(list.get(index));
    }

    private void cleanupCircleSeeds() {
        for (Seed seed : getSeedScan()) {
            List<Cross> seedCrosses = seed.getCrosses();
            boolean circleFitOk = false;

            while (!circleFitOk && seedCrosses.size() >= 3) {
                CircleFitter circleFit = buildCircleFitter(seedCrosses);
                circleFitOk = circleFit.fitStatus(xs, ys, ws, xs.size());
                CircleFitPars pars = circleFit.getFit();

                if (pars == null) {
                    break;
                }

                double chi2PerNdf = pars.chisq() / Math.max(1.0, (double) (xs.size() - 3));

                if (!circleFitOk || chi2PerNdf > CIRCLE_CHI2_CUT) {
                    double d = pars.doca();
                    double r = pars.rho();
                    double f = pars.phi();

                    seed.setDoca(d);
                    seed.setRho(r);
                    seed.setPhi(f);

                    Cross worst = null;
                    for (Cross c : seedCrosses) {
                        double residual = calcResidual(r, c, d, f);
                        if (Math.abs(residual) > SVTParameters.RESIMAX) {
                            worst = c;
                            break;
                        }
                    }

                    if (worst != null) {
                        seedCrosses.remove(worst);
                    } else {
                        break;
                    }
                }
            }
        }
    }

    private CircleFitPars fitCircle(List<Cross> seedCrosses) {
        CircleFitter circleFit = buildCircleFitter(seedCrosses);
        boolean statusOk = circleFit.fitStatus(xs, ys, ws, xs.size());
        if (!statusOk) {  
            return null;
        }
        return circleFit.getFit();
    }

    private CircleFitter buildCircleFitter(List<Cross> seedCrosses) {
        loadCircleFitArrays(seedCrosses);
        return new CircleFitter(xbeam, ybeam);
    }

    private void loadCircleFitArrays(List<Cross> seedCrosses) {
        xs.clear();
        ys.clear();
        ws.clear();

        ((ArrayList<Double>) xs).ensureCapacity(seedCrosses.size() + 1);
        ((ArrayList<Double>) ys).ensureCapacity(seedCrosses.size() + 1);
        ((ArrayList<Double>) ws).ensureCapacity(seedCrosses.size() + 1);

        xs.add(0, xbeam);
        ys.add(0, ybeam);
        ws.add(0, BEAM_WEIGHT);

        for (Cross c : seedCrosses) {
            xs.add(c.getPoint().x());
            ys.add(c.getPoint().y());
            ws.add(1.0 / (c.getPointErr().x() * c.getPointErr().x()
                    + c.getPointErr().y() * c.getPointErr().y()));
        }
    }

    private double calcResidual(double r, Cross c, double d, double f) {
        double xi = c.getPoint().x() - xbeam;
        double yi = c.getPoint().y() - ybeam;
        double ri = Math.sqrt(xi * xi + yi * yi);
        double fi = Math.atan2(yi, xi);
        return calcResi(r, ri, d, f, fi);
    }

    private double calcResi(double r, double ri, double d, double f, double fi) {
        return 0.5 * r * ri * ri - (1 + r * d) * ri * Math.sin(f - fi) + 0.5 * r * d * d + d;
    }
    
    private void addToSeedMap(ArrayList<Cross> hitList) {
        ArrayList<Cross> hits = new ArrayList<>(hitList);
        double seedIdx = 0.0;
        int s = hitList.size();
        int index = (int) Math.pow(2, s);

        for (Cross c : hitList) {
            seedIdx += c.getId() * Math.pow(10, index);
            index -= 4;
        }

        seedMap.put(seedIdx, hits);
    }

    private boolean checkZ(ArrayList<Cross> hits) {
        if (hits.size() != 3) {
            return true;
        }

        if (hits.get(0).getDetector() != DetectorType.BST
                || hits.get(1).getDetector() != DetectorType.BST
                || hits.get(2).getDetector() != DetectorType.BST) {
            return true;
        }

        Cross c1 = hits.get(0);
        Cross c2 = hits.get(1);
        Cross c3 = hits.get(2);

        double r1 = c1.getPoint().toVector3D().rho();
        double r2 = c2.getPoint().toVector3D().rho();
        double r3 = c3.getPoint().toVector3D().rho();

        double z1 = c1.getPoint().z();
        double z2 = c2.getPoint().z();
        double z3 = c3.getPoint().z();

        double slope = (z1 - z3) / (r1 - r3);
        double intercept = -slope * r1 + z1;
        double zCalc = slope * r2 + intercept;
        double zErr = c2.getPointErr().z();

        return Math.abs(zCalc - z2) < zErr * 20.0;
    }

    /**
    * Greedy overlap remover using HelixClusterRoadFitter on the clusters behind each seed.
    *
    * Ranking:
    *   1) more SVT crosses
    *   2) lower road-fit chi2/ndf
    *   3) lower seed circle-fit chi2/ndf
    *
    * Two seeds are considered overlapping if they share at least one SVT cross.
    *
    * Important:
    *   Overlapping seeds are not automatically rejected. If the competing road-fit
    *   chi2/ndf values are close, both seeds are kept to avoid prematurely rejecting
    *   a potentially good seed.
    */
   private List<Seed> removeOverlappingSeedsWithRoadFit(List<Seed> seeds, int polarity) {

       final double ROAD_CHI2_ABS_TOL = 2.0;   // tune
       final double ROAD_CHI2_REL_TOL = 0.25;  // tune: 25%

       List<RankedSeed> ranked = new ArrayList<>(seeds.size());

       for (Seed seed : seeds) {
           ranked.add(rankSeed(seed, polarity));
       }

       ranked.sort(Comparator
               .comparingInt(RankedSeed::nCrosses).reversed()
               .thenComparingDouble(RankedSeed::roadChi2PerNdf)
               .thenComparingDouble(RankedSeed::seedCircleChi2PerNdf));

       List<RankedSeed> selectedRanked = new ArrayList<>();

       for (RankedSeed candidate : ranked) {

           List<RankedSeed> overlaps = new ArrayList<>();

           for (RankedSeed accepted : selectedRanked) {
               if (sharesAnyCross(candidate.seed, accepted.seed)) {
                   overlaps.add(accepted);
               }
           }

           if (overlaps.isEmpty()) {
               selectedRanked.add(candidate);
               continue;
           }

           /*
            * The old algorithm rejected here immediately.
            *
            * Instead, reject only if at least one already-selected overlapping seed
            * is clearly better. If the road chi2/ndf is close, keep the candidate.
            */
           boolean clearlyWorseThanAnOverlap = false;

           for (RankedSeed accepted : overlaps) {
               if (isClearlyWorseOverlap(candidate,
                                         accepted,
                                         ROAD_CHI2_ABS_TOL,
                                         ROAD_CHI2_REL_TOL)) {
                   clearlyWorseThanAnOverlap = true;
                   break;
               }
           }

           if (!clearlyWorseThanAnOverlap) {
               selectedRanked.add(candidate);
           }
       }

       List<Seed> selected = new ArrayList<>(selectedRanked.size());

       for (RankedSeed rs : selectedRanked) {
           selected.add(rs.seed);

           for (Cross c : rs.seed.getCrosses()) {
               c.isInSeed = true;
           }

           rs.seed.setStatus(2);
       }

       return selected;
   }

   /**
    * Decide whether the candidate should be rejected because an overlapping
    * already-selected seed is clearly better.
    *
    * If the road chi2/ndf values are close, the candidate is kept.
    */
   private boolean isClearlyWorseOverlap(RankedSeed candidate,
                                         RankedSeed accepted,
                                         double absTol,
                                         double relTol) {

       double cRoad = candidate.roadChi2PerNdf();
       double aRoad = accepted.roadChi2PerNdf();

       /*
        * If either road chi2 is unusable, fall back to the original ranking logic.
        */
       if (!Double.isFinite(cRoad) || !Double.isFinite(aRoad)) {
           return compareRank(candidate, accepted) > 0;
       }

       double diff = cRoad - aRoad;

       /*
        * Candidate is better or equal in road chi2.
        * Do not reject it.
        */
       if (diff <= 0.0) {
           return false;
       }

       /*
        * Candidate has worse road chi2, but only slightly.
        * Keep it as an ambiguous overlap.
        */
       double scale = Math.max(Math.abs(aRoad), 1.0);
       double tol = Math.max(absTol, relTol * scale);

       if (diff <= tol) {
           return false;
       }

       /*
        * Candidate is clearly worse in road chi2.
        */
       return true;
   }
   
   /**
    * Returns:
    *   < 0 if a ranks better than b
    *   > 0 if a ranks worse than b
    *     0 if equivalent by ranking
    */
   private int compareRank(RankedSeed a, RankedSeed b) {

       int cmp = Integer.compare(b.nCrosses(), a.nCrosses());
       if (cmp != 0) return cmp;

       cmp = Double.compare(a.roadChi2PerNdf(), b.roadChi2PerNdf());
       if (cmp != 0) return cmp;

       return Double.compare(a.seedCircleChi2PerNdf(), b.seedCircleChi2PerNdf());
   }
   
    private RankedSeed rankSeed(Seed seed, int polarity) {
        double roadChi2 = Double.POSITIVE_INFINITY;

        try {
            LinkedHashSet<Cluster> clusters = new LinkedHashSet<>();
            clusters.addAll(seed.getClusters());

            if (!clusters.isEmpty()) {
                FitResult fit = roadFitter.fitRoad(clusters, polarity, xbeam, ybeam);
                if (fit != null && Double.isFinite(fit.chi2)) {
                    roadChi2 = (fit.ndf > 0) ? fit.chi2 / fit.ndf : fit.chi2;
                }
            }
        } catch (Exception ex) {
            roadChi2 = Double.POSITIVE_INFINITY;
        }

        return new RankedSeed(seed, roadChi2);
    }

    /**
    * Returns true if two seeds share at least one SVT cross.
    */
   private boolean sharesAnyCross(Seed a, Seed b) {

       if (a == null || b == null) {
           return false;
       }

       if (a.getCrosses() == null || b.getCrosses() == null) {
           return false;
       }

       Set<Integer> ids = new HashSet<>();

       for (Cross c : a.getCrosses()) {
           if (c != null) {
               ids.add(c.getId());
           }
       }

       for (Cross c : b.getCrosses()) {
           if (c != null && ids.contains(c.getId())) {
               return true;
           }
       }

       return false;
   }
   


    public List<Seed> getSeedScan() {
        return seedScan;
    }

    public void setSeedScan(List<Seed> seeds) {
        seedScan.clear();
        if (seeds != null) {
            seedScan.addAll(seeds);
        }
    }

    
    private static final class RankedSeed {
        private final Seed seed;
        private final double roadChi2PerNdf;

        private RankedSeed(Seed seed, double roadChi2PerNdf) {
            this.seed = seed;
            this.roadChi2PerNdf = roadChi2PerNdf;
        }

        private int nCrosses() {
            return seed.getCrosses().size();
        }

        private double roadChi2PerNdf() {
            return roadChi2PerNdf;
        }

        private double seedCircleChi2PerNdf() {
            return seed.getCircleFitChi2PerNDF();
        }
    }
    ////CTOF matching 
    public List<Seed> keepSeedsMatchingCTOF(List<Seed> seeds,
                                         Set<Integer> paddles,
                                         double xb,
                                         double yb) {
        if (seeds == null || seeds.isEmpty()) {
            return Collections.emptyList();
        }

        if (paddles == null || paddles.isEmpty()) {
            return new ArrayList<>(seeds);
        }

        List<Seed> matchedSeeds = new ArrayList<>();
        if (Constants.getInstance().seedingDebugMode) 
                System.out.println("Before CTOF matching");
        for (Seed s : seeds) {
            if (Constants.getInstance().seedingDebugMode) {
                System.out.println(s.toString());
            }

            int[] spaddles = RecUtilities.getPaddleForSeed(s, xb, yb);

            boolean matched = false;
            if (spaddles != null) {
                for (int spaddle : spaddles) { 
                    if (paddles.contains(spaddle)) { 
                        matched = true;
                        break;
                    }
                }
            }

            if (matched) { 
                s.setKey(s.new Key(s));
                matchedSeeds.add(s);
            }
        }

        return matchedSeeds;
    }
    //CTOF matching for tracks
    public List<Track> keepTracksMatchingCTOF(List<Track> tracks,
                                         Set<Integer> paddles,
                                         double xb,
                                         double yb) {
        if (tracks == null || tracks.isEmpty()) {
            return Collections.emptyList();
        }

        if (paddles == null || paddles.isEmpty()) {
            return new ArrayList<>(tracks);
        }

        List<Track> matchedTracks = new ArrayList<>();
        if (Constants.getInstance().seedingDebugMode) 
                System.out.println("Tracks Before CTOF matching");
        for (Track t : tracks) {
            if (Constants.getInstance().seedingDebugMode) {
                System.out.println(t.toString());
            }

            int[] spaddles = RecUtilities.getPaddleForTrack(t, xb, yb);

            boolean matched = false;
            if (spaddles != null) {
                for (int spaddle : spaddles) { 
                    if (paddles.contains(spaddle)) { 
                        matched = true;
                        break;
                    }
                }
            }

            if (matched) { 
                matchedTracks.add(t);
            }
        }

        return matchedTracks;
    }
    
    public List<Seed> rejectSeedsWithBgClus(List<Seed> seeds) {
        List<Seed> pass = new ArrayList<>();
        for(Seed s : seeds) {
            boolean seedOK=true;
            for(Cluster c : s.getClusters()) {
                if(getMCClusterPurity(c)<1.0) seedOK=false;
            }
            if(seedOK)pass.add(s);
        }
        
        return pass;
    }
    
    public List<Seed> keepSeedsWithPurity(List<Seed> seeds, double p) {
        List<Seed> pass = new ArrayList<>();
        for(Seed s : seeds) {
            if(getMCSeedPurity(s)>p) {
                pass.add(s);
            }
        }
        return pass;
    }

    public double getMCClusterPurity(Cluster cl) {
        double ei =0;
        double et =0;
        for(Hit h : cl) {
            et +=h.getStrip().getEdep();
            if(h.MCstatus==0) ei+=h.getStrip().getEdep();
        }
        return 100*ei/(double) et;
    }
    
    public double getMCSeedPurity(Seed s) {
        double ei =0;
        double et =0;
        for(Cluster c : s.getClusters()) {
            et +=1;
            ei+=getMCClusterPurity(c);
        }
        return ei/(double) et;
    }
}