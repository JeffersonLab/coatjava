package org.jlab.rec.ahdc.AI;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jlab.rec.ahdc.Hit.Hit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class TrackConstruction {
    private int max_number_of_track_candidates = 10000;
    private double max_angle = Math.toRadians(60);

    public TrackConstruction() {}

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

    private double warp_zero_two_pi(double angle) { return mod(angle, 2. * Math.PI); }

    private boolean angle_in_range(double angle, double lower, double upper) { return warp_zero_two_pi(angle - lower) <= warp_zero_two_pi(upper - lower); }


    private ArrayList<ArrayList<Integer>> cartesian_product(ArrayList<ArrayList<Integer>> v1, ArrayList<Integer> v2, MutableBoolean too_much_track_candidates, int number_of_track_candidates) {
        ArrayList<ArrayList<Integer>> result = new ArrayList<>();
        for (ArrayList<Integer> i : v1) {
            if (too_much_track_candidates.booleanValue()) break;
            for (int j : v2) {
                ArrayList<Integer> newCombination = new ArrayList<>(i);
                newCombination.add(j);
                result.add(newCombination);
                
                if (number_of_track_candidates + result.size() > max_number_of_track_candidates) {
                    too_much_track_candidates.setValue(true);
                    break;
                }
            }
            

        }
        return result;
    }

    public boolean get_all_possible_track(ArrayList<PreclusterSuperlayer> preclusterSuperlayers, ArrayList<ArrayList<PreclusterSuperlayer>> all_track_candidates) {

        ArrayList<Integer> seed_index = new ArrayList<>();
        for (int i = 0; i < preclusterSuperlayers.size(); i++) {
            if (preclusterSuperlayers.get(i).getPreclusters().get(0).get_Super_layer() == 1) seed_index.add(i);
        }

        // System.out.println("New event: -------------------------------------------------------------------------");

        boolean sucess = true;
        int number_of_track_candidates = 0;
        for (int s : seed_index) {
            if (!sucess) break;
            // Find all superpreclusters that have a phi angle within phi angle of the seed +/- 60 degrees
            // The goal is to reduce the number of superpreclusters to loop over
            double phi_seed = warp_zero_two_pi(Math.atan2(preclusterSuperlayers.get(s).getY(), preclusterSuperlayers.get(s).getX()));  // phi angle of the seed
            ArrayList<Integer> all_superpreclusters = new ArrayList<>();                                                                   // all superpreclusters that are within phi angle of the seed
            for (int i = 0; i < preclusterSuperlayers.size(); ++i) {
                double phi_p = warp_zero_two_pi(Math.atan2(preclusterSuperlayers.get(i).getY(), preclusterSuperlayers.get(i).getX()));
                if (angle_in_range(phi_p, phi_seed - max_angle, phi_seed + max_angle)) {
                    all_superpreclusters.add(i);
                }
            }

            // Sort the superpreclusters by superlayer to have a simpler loops after
            ArrayList<ArrayList<Integer>> superpreclusters_s1 = new ArrayList<>(List.of(new ArrayList<>(List.of(s))));
            ArrayList<Integer> superpreclusters_s3 = new ArrayList<>(new ArrayList<>());
            ArrayList<Integer> superpreclusters_s4 = new ArrayList<>(new ArrayList<>());
            ArrayList<Integer> superpreclusters_s2 = new ArrayList<>(new ArrayList<>());
            ArrayList<Integer> superpreclusters_s5 = new ArrayList<>(new ArrayList<>());

            for (int i = 0; i < all_superpreclusters.size(); i++) {
                if (preclusterSuperlayers.get(all_superpreclusters.get(i)).getPreclusters().get(0).get_Super_layer() == 2)
                    superpreclusters_s2.add(all_superpreclusters.get(i));
                if (preclusterSuperlayers.get(all_superpreclusters.get(i)).getPreclusters().get(0).get_Super_layer() == 3)
                    superpreclusters_s3.add(all_superpreclusters.get(i));
                if (preclusterSuperlayers.get(all_superpreclusters.get(i)).getPreclusters().get(0).get_Super_layer() == 4)
                    superpreclusters_s4.add(all_superpreclusters.get(i));
                if (preclusterSuperlayers.get(all_superpreclusters.get(i)).getPreclusters().get(0).get_Super_layer() == 5)
                    superpreclusters_s5.add(all_superpreclusters.get(i));
            }

            // Find all possible combinations of superpreclusters on different superlayers
            MutableBoolean too_much_track_candidates = new MutableBoolean();
            too_much_track_candidates.setFalse();
            ArrayList<ArrayList<Integer>> combinations_s1_s2 = cartesian_product(superpreclusters_s1, superpreclusters_s2, too_much_track_candidates, number_of_track_candidates);
            ArrayList<ArrayList<Integer>> combinations_s1_s2_s3 = cartesian_product(combinations_s1_s2, superpreclusters_s3, too_much_track_candidates, number_of_track_candidates);
            ArrayList<ArrayList<Integer>> combinations_s1_s2_s3_s4 = cartesian_product(combinations_s1_s2_s3, superpreclusters_s4, too_much_track_candidates, number_of_track_candidates);
            ArrayList<ArrayList<Integer>> combinations_s1_s2_s3_s4_s5 = cartesian_product(combinations_s1_s2_s3_s4, superpreclusters_s5, too_much_track_candidates, number_of_track_candidates);
            number_of_track_candidates += combinations_s1_s2_s3_s4_s5.size();
            if (too_much_track_candidates.booleanValue()) sucess = false;
            // System.out.println("combinations_s1_s2_s3_s4_s5");
            for (ArrayList<Integer> combination : combinations_s1_s2_s3_s4_s5) {
                // System.out.println("combination: "+combination);
                ArrayList<PreclusterSuperlayer> track_candidate = new ArrayList<>();
                for (int index : combination) {
                    track_candidate.add(preclusterSuperlayers.get(index));
                }
                all_track_candidates.add(track_candidate);
            }

        }

        //System.out.println("nb of track candidates: " + all_track_candidates.size() + " sucess: " + sucess);
        System.out.print(sucess+", ");


        return sucess;
    }

}
