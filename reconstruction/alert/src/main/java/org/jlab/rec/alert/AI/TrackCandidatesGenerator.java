package org.jlab.rec.alert.AI;

import org.apache.commons.lang3.mutable.MutableBoolean;

import java.util.*;

/**
 * The TrackConstruction class is responsible for constructing all possible track 
 * candidates from a set of superpreclusters.
 */
public class TrackCandidatesGenerator {
    static final private int MAX_NUMBER_OF_TRACK_CANDIDATES = 10000;
    static final private double MAX_ANGLE = Math.toRadians(60);

    /**
     * Default constructor.
     */
    public TrackCandidatesGenerator() {}

    /**
     * Computes the modulo operation, which returns the remainder of the division
     * of one number by another. This method handles floating-point edge cases
     * to ensure accurate results within the expected range.
     *
     * @param x The dividend.
     * @param y The divisor. If y is 0, the method returns x.
     * @return The result of x modulo y. The result is in the range:
     *         - [0..y) if y > 0
     *         - (y..0] if y < 0
     *         Special cases are handled to avoid floating-point inaccuracies.
     */
    private double mod(double x, double y) {

        if (0. == y) return x;

        double m = x - y * Math.floor(x / y);
        // handle boundary cases resulted from floating-point cut off:
        if (y > 0) {               // modulo range: [0..y)
            if (m >= y) return 0;  // Mod(-1e-16             , 360.    ): m= 360.
            if (m < 0) {
                if (y + m == y) return 0;  // just in case...
                else return y + m;         // Mod(106.81415022205296 , _TWO_PI ): m= -1.421e-14
            }
        } else {                   // modulo range: (y..0]
            if (m <= y) return 0;  // Mod(1e-16              , -360.   ): m= -360.
            if (m > 0) {
                if (y + m == y) return 0;  // just in case...
                else return y + m;         // Mod(-106.81415022205296, -_TWO_PI): m= 1.421e-14
            }
        }

        return m;
    }

    
    /**
     * Wraps an angle to the range [0, 2π).
     *
     * @param angle The angle to wrap.
     * @return The angle wrapped to the range [0, 2π).
     */
    private double warpZeroTwoPi(double angle) { return mod(angle, 2. * Math.PI); }

    /**
     * Checks if an angle is within a specified range.
     *
     * @param angle The angle to check.
     * @param lower The lower bound of the range.
     * @param upper The upper bound of the range.
     * @return {@code true} if the angle is within the range, {@code false} otherwise.
     */
    private boolean angleInRange(double angle, double lower, double upper) { return warpZeroTwoPi(angle - lower) <= warpZeroTwoPi(upper - lower); }

    /**
     * Computes the Cartesian product of two lists of integers, ensuring the number of track candidates
     * does not exceed the maximum allowed limit.
     *
     * @param v1 The first list of integer combinations.
     * @param v2 The second list of integers to combine with the first list.
     * @param too_much_track_candidates A mutable boolean that is set to {@code true} if the number of track candidates exceeds the maximum limit.
     * @param number_of_track_candidates The current count of track candidates.
     * @return A list of all possible combinations of integers from {@code v1} and {@code v2}.
     */
    private ArrayList<ArrayList<Integer>> cartesianProduct(ArrayList<ArrayList<Integer>> v1, ArrayList<Integer> v2, MutableBoolean too_much_track_candidates, int number_of_track_candidates) {
        ArrayList<ArrayList<Integer>> result = new ArrayList<>();
        for (ArrayList<Integer> i : v1) {
            if (too_much_track_candidates.booleanValue()) break;
            for (int j : v2) {
                if (too_much_track_candidates.booleanValue()) break;
                ArrayList<Integer> n = new ArrayList<>(i);
                n.add(j);
                result.add(n);

                if (number_of_track_candidates + result.size() >= MAX_NUMBER_OF_TRACK_CANDIDATES) {
                    too_much_track_candidates.setValue(true);
                    break;
                }
            }
            

        }
        return result;
    }

    public boolean getAllPossibleTrack(ArrayList<InterCluster> interClusters, ArrayList<ArrayList<InterCluster>> all_track_candidates) {

        /*
        Identify all superpreclusters located in the first superlayer.
        These superpreclusters serve as seeds for constructing track candidates.
        A track candidate always starts from a seed.
        */
        ArrayList<Integer> seed_index = new ArrayList<>();
        for (int i = 0; i < interClusters.size(); i++) {
            if (!interClusters.get(i).getPreclusters().isEmpty() &&
            interClusters.get(i).getSuperlayer() == 1) {
            seed_index.add(i);
            }
        }


        boolean sucess = true;
        int number_of_track_candidates = 0;

        // Loop over all seeds to construct track candidates
        for (int s : seed_index) {
            // Check if the number of track candidates exceeds the maximum limit if so, stop the loop
            if (!sucess) break;

            // Find all superpreclusters that have a phi angle within phi angle of the seed +/- 60 degrees
            // The goal is to reduce the number of superpreclusters to loop over
            double phi_seed = warpZeroTwoPi(Math.atan2(interClusters.get(s).getY(), interClusters.get(s).getX()));  // phi angle of the seed
            ArrayList<Integer> all_superpreclusters = new ArrayList<>();                                                                   // all superpreclusters that are within phi angle of the seed
            for (int i = 0; i < interClusters.size(); ++i) {
                double phi_p = warpZeroTwoPi(Math.atan2(interClusters.get(i).getY(), interClusters.get(i).getX()));
                if (angleInRange(phi_p, phi_seed - MAX_ANGLE, phi_seed + MAX_ANGLE)) {
                    all_superpreclusters.add(i);
                }
            }

            // Sort the superpreclusters by superlayer to have a simpler loops after
            ArrayList<Integer> superpreclusters_s1 = new ArrayList<>(List.of(s));
            ArrayList<Integer> superpreclusters_s3 = new ArrayList<>();
            ArrayList<Integer> superpreclusters_s4 = new ArrayList<>();
            ArrayList<Integer> superpreclusters_s2 = new ArrayList<>();
            ArrayList<Integer> superpreclusters_s5 = new ArrayList<>();

            for (int i = 0; i < all_superpreclusters.size(); i++) {
                if (interClusters.get(all_superpreclusters.get(i)).getPreclusters().get(0).get_Super_layer() == 2)
                    superpreclusters_s2.add(all_superpreclusters.get(i));
                else if (interClusters.get(all_superpreclusters.get(i)).getPreclusters().get(0).get_Super_layer() == 3)
                    superpreclusters_s3.add(all_superpreclusters.get(i));
                else if (interClusters.get(all_superpreclusters.get(i)).getPreclusters().get(0).get_Super_layer() == 4)
                    superpreclusters_s4.add(all_superpreclusters.get(i));
                else if (interClusters.get(all_superpreclusters.get(i)).getPreclusters().get(0).get_Super_layer() == 5)
                    superpreclusters_s5.add(all_superpreclusters.get(i));
            }

            MutableBoolean too_much_track_candidates = new MutableBoolean(); // Need to be a mutable boolean to be able to change it in the cartesian_product method
            too_much_track_candidates.setFalse();

            // Find all possible combinations of superpreclusters on different superlayers
            ArrayList<ArrayList<Integer>> combinations_s1_s2 = cartesianProduct(new ArrayList<>(List.of(superpreclusters_s1)), superpreclusters_s2, too_much_track_candidates, number_of_track_candidates);
            ArrayList<ArrayList<Integer>> combinations_s1_s2_s3 = cartesianProduct(combinations_s1_s2, superpreclusters_s3, too_much_track_candidates, number_of_track_candidates);
            ArrayList<ArrayList<Integer>> combinations_s1_s2_s3_s4 = cartesianProduct(combinations_s1_s2_s3, superpreclusters_s4, too_much_track_candidates, number_of_track_candidates);
            ArrayList<ArrayList<Integer>> combinations_s1_s2_s3_s4_s5 = cartesianProduct(combinations_s1_s2_s3_s4, superpreclusters_s5, too_much_track_candidates, number_of_track_candidates);
            
            // Keep track of the number of track candidates
            number_of_track_candidates += combinations_s1_s2_s3_s4_s5.size();
            if (too_much_track_candidates.booleanValue()) sucess = false; // If the number of track candidates exceeds the maximum limit, set success to false
            
            // Add all track candidates to the list of all track candidates
            // And switch back from index to superprecluster
            for (ArrayList<Integer> combination : combinations_s1_s2_s3_s4_s5) {
                ArrayList<InterCluster> track_candidate = new ArrayList<>();
                for (int index : combination) {
                    track_candidate.add(interClusters.get(index));
                }
                all_track_candidates.add(track_candidate);
            }
        }

        return sucess;
    }

}
