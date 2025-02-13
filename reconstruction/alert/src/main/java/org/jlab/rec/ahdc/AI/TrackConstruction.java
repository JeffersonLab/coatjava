package org.jlab.rec.ahdc.AI;

import org.jlab.rec.ahdc.Hit.Hit;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class TrackConstruction {
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


    public ArrayList<ArrayList<PreclusterSuperlayer>> get_all_possible_track(ArrayList<PreclusterSuperlayer> preclusterSuperlayers) {

        // Get seeds to start the track finding algorithm
        ArrayList<PreclusterSuperlayer> seeds = new ArrayList<>();
        for (PreclusterSuperlayer precluster : preclusterSuperlayers) {
            if (precluster.getPreclusters().get(0).get_hits_list().get(0).getSuperLayerId() == 1) seeds.add(precluster);
        }
        seeds.sort(new Comparator<PreclusterSuperlayer>() {
            @Override
            public int compare(PreclusterSuperlayer a1, PreclusterSuperlayer a2) {
                return Double.compare(Math.atan2(a1.getY(), a1.getX()), Math.atan2(a2.getY(), a2.getX()));
            }
        });
        // System.out.println("seeds: " + seeds);

        // Get all possible tracks ----------------------------------------------------------------
        double max_angle = Math.toRadians(60);

        ArrayList<ArrayList<PreclusterSuperlayer>> all_combinations = new ArrayList<>();
        for (PreclusterSuperlayer seed : seeds) {
            double phi_seed = warp_zero_two_pi(Math.atan2(seed.getY(), seed.getX()));

            ArrayList<PreclusterSuperlayer> track = new ArrayList<>();
            for (PreclusterSuperlayer p : preclusterSuperlayers) {
                double phi_p = warp_zero_two_pi(Math.atan2(p.getY(), p.getX()));
                if (angle_in_range(phi_p, phi_seed - max_angle, phi_seed + max_angle)) track.add(p);
            }
            // System.out.println("track: " + track.size());

            ArrayList<ArrayList<PreclusterSuperlayer>> combinations = new ArrayList<>(List.of(new ArrayList<>(List.of(seed))));
            // System.out.println("combinations: " + combinations);

            for (int i = 1; i < 5; ++i) {
                ArrayList<ArrayList<PreclusterSuperlayer>> new_combinations = new ArrayList<>();
                for (ArrayList<PreclusterSuperlayer> combination : combinations) {

                    for (PreclusterSuperlayer precluster : track) {
                        if (precluster.getPreclusters().get(0).get_hits_list().get(0).getSuperLayerId() == seed.getPreclusters().get(0).get_hits_list().get(0).getSuperLayerId() + i) {
                            // System.out.printf("Good Precluster x: %.2f, y: %.2f, r: %.2f%n", precluster.getX(), precluster.getY(), Math.hypot(precluster.getX(), precluster.getY()));
                            // System.out.println("combination: " + combination);

                            ArrayList<PreclusterSuperlayer> new_combination = new ArrayList<>(combination);
                            new_combination.add(precluster);
                            // System.out.println("new_combination: " + new_combination);
                            new_combinations.add(new_combination);
                        }
                    }
                    for (ArrayList<PreclusterSuperlayer> c : new_combinations) {
                        // System.out.println("c.size: " + c.size() +  ", c: " + c);
                    }

                }
                combinations = new_combinations;
                if (combinations.size() > 10000) break;
            }
            for (ArrayList<PreclusterSuperlayer> combination : combinations) {
                if (combination.size() == 5) {
                    all_combinations.add(combination);
                }
            }
        }

        return all_combinations;
    }

}
