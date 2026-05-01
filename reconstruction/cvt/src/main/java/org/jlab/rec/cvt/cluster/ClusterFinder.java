    package org.jlab.rec.cvt.cluster;

    import java.util.ArrayList;
    import java.util.Collections;
    import java.util.Comparator;
    import java.util.List;
    import org.jlab.rec.cvt.hit.Hit;

    /**
     * ClusterFinder
     *
     * Standard cluster finding:
     * - build contiguous strip groups in each sector/layer
     * - optionally split oversized groups
     * - create Cluster objects from the final hit lists
     */
    public class ClusterFinder {

        public ClusterFinder() {
        }

        // A hit array is used to identify contiguous strip groups
        private Hit[][][] HitArray;

        // Maximum array dimensions
        private final int nstrip = 1200;
        private final int nlayr = 6;
        private final int nsec = 18;

        // Default split cap
        private static final int DEFAULT_MAX_CLUSTER_SIZE = 20;

        /**
         * Default entry point.
         * Uses the default maximum cluster size.
         */
        public ArrayList<Cluster> findClusters(List<Hit> hits2) {
            return findClusters(hits2, DEFAULT_MAX_CLUSTER_SIZE);
        }

        /**
         * Main cluster finding entry point with tunable maximum cluster size.
         *
         * @param hits2 input hits
         * @param maxClusterSize maximum allowed size of each final cluster after splitting
         */
        public ArrayList<Cluster> findClusters(List<Hit> hits2, int maxClusterSize) {
            ArrayList<Cluster> clusters = new ArrayList<>();

            if (maxClusterSize < 1) {
                throw new IllegalArgumentException("maxClusterSize must be >= 1");
            }

            // Initialize the hit array
            HitArray = new Hit[nstrip][nlayr][nsec];

            // Fill the array with valid hits
            for (Hit hit : hits2) {

                if (hit.getStrip().getStrip() == -1) {
                    continue;
                }

                // Skip bad-status strips
                if (hit.getStrip().getStatus() > 0 && hit.getStrip().getStatus() < 5) {
                    continue;
                }

                int w = hit.getStrip().getStrip();
                int l = hit.getLayer();
                int s = hit.getSector();

                if (w > 0 && w < nstrip) {
                    HitArray[w - 1][l - 1][s - 1] = hit;
                }
            }

            int cid = 1;

            // Scan each sector/layer independently
            for (int s = 0; s < nsec; s++) {
                for (int l = 0; l < nlayr; l++) {

                    int si = 0;

                    while (si < nstrip) {

                        // Start a raw cluster candidate if current or next strip has a hit
                        if (HitArray[si][l][s] != null
                                || (si < nstrip - 1 && HitArray[si + 1][l][s] != null)) {

                            ArrayList<Hit> rawHits = new ArrayList<>();

                            // Collect all contiguous hits until a gap is reached
                            while ((si < nstrip - 1 && HitArray[si + 1][l][s] != null)
                                    || (si < nstrip && HitArray[si][l][s] != null)) {

                                if (HitArray[si][l][s] != null) {
                                    rawHits.add(HitArray[si][l][s]);
                                }
                                si++;
                                if (si >= nstrip) {
                                    break;
                                }
                            }

                            if (!rawHits.isEmpty()) {

                                // Split oversized raw clusters into smaller subclusters
                                List<ArrayList<Hit>> splitClusters = splitClusterHits(rawHits, maxClusterSize);

                                // Build Cluster objects from each final subcluster
                                for (ArrayList<Hit> hits : splitClusters) {

                                    Cluster this_cluster = new Cluster(
                                            hits.get(0).getDetector(),
                                            hits.get(0).getType(),
                                            hits.get(0).getSector(),
                                            l + 1,
                                            cid++
                                    );

                                    this_cluster.setId(clusters.size() + 1);
                                    this_cluster.addAll(hits);

                                    // Optional smoothing inside a cluster
                                    smoothInternalEdep(hits);

                                    for (Hit h : hits) {
                                        h.setAssociatedClusterID(this_cluster.getId());
                                    }

                                    this_cluster.calc_CentroidParams();
                                    Collections.sort(this_cluster);
                                    clusters.add(this_cluster);
                                }
                            }
                        }

                        si++;
                    }
                }
            }

            return clusters;
        }

        /**
         * Split one raw contiguous cluster candidate into final subclusters with
         * size <= maxClusterSize.
         *
         * Strategy:
         * - if size <= maxClusterSize: keep as is
         * - otherwise:
         *   1. sort by strip number
         *   2. find local energy maxima as seed locations
         *   3. split the run between neighboring maxima
         *   4. enforce the hard maximum size
         */
        
        private List<ArrayList<Hit>> splitClusterHits(List<Hit> rawHits, int maxClusterSize) {
            ArrayList<Hit> hits = new ArrayList<>(rawHits);
            hits.sort(Comparator.comparingInt(h -> h.getStrip().getStrip()));

            List<ArrayList<Hit>> result = new ArrayList<>();

            if (hits.size() <= maxClusterSize) {
                result.add(hits);
                return result;
            }

            // Find candidate local maxima in deposited energy
            List<Integer> seeds = findLocalMaxima(hits);

            // Always keep at least one seed: the global maximum
            int gmax = indexOfGlobalMaximum(hits);
            if (!seeds.contains(gmax)) {
                seeds.add(gmax);
                Collections.sort(seeds);
            }

            // If no seeds were found, fall back to direct size enforcement
            if (seeds.isEmpty()) {
                return splitRunToMaxSize(hits, maxClusterSize);
            }

            // Build preliminary contiguous regions using boundaries midway between seeds
            List<Integer> bounds = new ArrayList<>();
            bounds.add(0);

            for (int i = 0; i < seeds.size() - 1; i++) {
                int leftSeed = seeds.get(i);
                int rightSeed = seeds.get(i + 1);

                int split = (leftSeed + rightSeed) / 2 + 1;
                bounds.add(split);
            }

            bounds.add(hits.size());

            for (int i = 0; i < bounds.size() - 1; i++) {
                int i0 = bounds.get(i);
                int i1 = bounds.get(i + 1);

                if (i0 >= i1) {
                    continue;
                }

                ArrayList<Hit> part = new ArrayList<>(hits.subList(i0, i1));
                result.addAll(splitRunToMaxSize(part, maxClusterSize));
            }

            return result;
        }

        /**
         * Find local maxima in deposited energy.
         */
        private List<Integer> findLocalMaxima(List<Hit> hits) {
            List<Integer> seeds = new ArrayList<>();
            int n = hits.size();

            if (n == 0) {
                return seeds;
            }

            if (n == 1) {
                seeds.add(0);
                return seeds;
            }

            for (int i = 0; i < n; i++) {
                double e = edep(hits.get(i));

                if (i == 0) {
                    if (e > edep(hits.get(1))) {
                        seeds.add(i);
                    }
                } else if (i == n - 1) {
                    if (e > edep(hits.get(n - 2))) {
                        seeds.add(i);
                    }
                } else {
                    double el = edep(hits.get(i - 1));
                    double er = edep(hits.get(i + 1));

                    if (e >= el && e > er) {
                        seeds.add(i);
                    }
                }
            }

            return seeds;
        }

        /**
         * Return the index of the strip with the largest deposited energy.
         */
        private int indexOfGlobalMaximum(List<Hit> hits) {
            int imax = 0;
            for (int i = 1; i < hits.size(); i++) {
                if (edep(hits.get(i)) > edep(hits.get(imax))) {
                    imax = i;
                }
            }
            return imax;
        }

        /**
         * Hard size cap:
         * split a contiguous run into groups of size <= maxClusterSize.
         *
         * The algorithm peels off chunks of maxClusterSize, and for the final remainder,
         * it tries to keep the strongest neighboring hits together.
         */
        private List<ArrayList<Hit>> splitRunToMaxSize(List<Hit> run, int maxClusterSize) {
            List<ArrayList<Hit>> out = new ArrayList<>();
            int n = run.size();

            if (n <= maxClusterSize) {
                out.add(new ArrayList<>(run));
                return out;
            }

            // Special optimized behavior for maxClusterSize == 2
            // since this is the most common SVT use case.
            if (maxClusterSize == 2) {
                return splitRunToPairs(run);
            }

            int i = 0;
            while (i < n) {
                int remaining = n - i;

                if (remaining <= maxClusterSize) {
                    out.add(new ArrayList<>(run.subList(i, n)));
                    break;
                }

                // Avoid leaving a trailing remainder of 1 if possible:
                // for example with max=3 and remaining=4, prefer 2+2 instead of 3+1
                int chunkSize = maxClusterSize;
                if (remaining == maxClusterSize + 1) {
                    chunkSize = maxClusterSize / 2 + (maxClusterSize % 2);
                }

                out.add(new ArrayList<>(run.subList(i, i + chunkSize)));
                i += chunkSize;
            }

            return out;
        }

        /**
         * Special case splitter for max size = 2.
         * For the last 3-strip remainder, choose the better pairing using energy affinity:
         * - [0,1] + [2]
         * - [0] + [1,2]
         */
        private List<ArrayList<Hit>> splitRunToPairs(List<Hit> run) {
            List<ArrayList<Hit>> out = new ArrayList<>();
            int n = run.size();

            if (n <= 2) {
                out.add(new ArrayList<>(run));
                return out;
            }

            int i = 0;

            while (n - i > 3) {
                out.add(new ArrayList<>(run.subList(i, i + 2)));
                i += 2;
            }

            int rem = n - i;

            if (rem == 1 || rem == 2) {
                out.add(new ArrayList<>(run.subList(i, n)));
                return out;
            }

            // rem == 3
            double affL = edep(run.get(i)) + edep(run.get(i + 1));
            double affR = edep(run.get(i + 1)) + edep(run.get(i + 2));

            if (affL >= affR) {
                out.add(new ArrayList<>(run.subList(i, i + 2)));
                out.add(new ArrayList<>(run.subList(i + 2, i + 3)));
            } else {
                out.add(new ArrayList<>(run.subList(i, i + 1)));
                out.add(new ArrayList<>(run.subList(i + 1, i + 3)));
            }

            return out;
        }

        /**
         * Optional smoothing of the deposited energy for the internal strip of a 3-hit cluster.
         *
         * With aggressive splitting this rarely acts, but it is left here for compatibility.
         */
        private void smoothInternalEdep(List<Hit> hits) {
            if (hits.size() > 2) {
                for (int hi = 1; hi < hits.size() - 1; hi++) {
                    double em = hits.get(hi).getStrip().getEdep();
                    double el = hits.get(hi - 1).getStrip().getEdep();
                    double er = hits.get(hi + 1).getStrip().getEdep();

                    if (em < el && em < er) {
                        hits.get(hi).getStrip().setEdep(0.5 * (el + er));
                    }
                }
            }
        }

        /**
         * Convenience accessor for deposited energy.
         */
        private double edep(Hit h) {
            return h.getStrip().getEdep();
        }
    }