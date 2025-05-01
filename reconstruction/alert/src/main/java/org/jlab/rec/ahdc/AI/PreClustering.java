package org.jlab.rec.ahdc.AI;

import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.PreCluster.PreCluster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class PreClustering {

    private ArrayList<Hit> fill(List<Hit> hits, int super_layer, int layer) {

        ArrayList<Hit> result = new ArrayList<>();
        for (Hit hit : hits) {
            if (hit.getSuperLayerId() == super_layer && hit.getLayerId() == layer) result.add(hit);
        }
        return result;
    }

    public ArrayList<PreCluster> find_preclusters_for_AI(List<Hit> AHDC_hits) {
        ArrayList<PreCluster> preclusters = new ArrayList<>();

        ArrayList<Hit> s1l1 = fill(AHDC_hits, 1, 1);
        ArrayList<Hit> s2l1 = fill(AHDC_hits, 2, 1);
        ArrayList<Hit> s2l2 = fill(AHDC_hits, 2, 2);
        ArrayList<Hit> s3l1 = fill(AHDC_hits, 3, 1);
        ArrayList<Hit> s3l2 = fill(AHDC_hits, 3, 2);
        ArrayList<Hit> s4l1 = fill(AHDC_hits, 4, 1);
        ArrayList<Hit> s4l2 = fill(AHDC_hits, 4, 2);
        ArrayList<Hit> s5l1 = fill(AHDC_hits, 5, 1);

        // Sort hits of each layers by phi:
        Comparator<Hit> comparator = new Comparator<>() {
            @Override
            public int compare(Hit a1, Hit a2) {
                return Double.compare(a1.getPhi(), a2.getPhi());
            }
        };

        s1l1.sort(comparator);
        s2l1.sort(comparator);
        s2l2.sort(comparator);
        s3l1.sort(comparator);
        s3l2.sort(comparator);
        s4l1.sort(comparator);
        s4l2.sort(comparator);
        s5l1.sort(comparator);

        ArrayList<ArrayList<Hit>> all_super_layer = new ArrayList<>(Arrays.asList(s1l1, s2l1, s2l2, s3l1, s3l2, s4l1, s4l2, s5l1));

        for (ArrayList<Hit> p : all_super_layer) {
            for (Hit hit : p) {
                hit.setUse(false);
            }
        }

        for (ArrayList<Hit> p : all_super_layer) {
            for (Hit hit : p) {
                if (hit.is_NoUsed()) {
                    ArrayList<Hit> temp = new ArrayList<>();
                    temp.add(hit);
                    hit.setUse(true);
                    int expected_wire_plus  = hit.getWireId() + 1;
                    int expected_wire_minus = hit.getWireId() - 1;
                    if (hit.getWireId() == 1)
                        expected_wire_minus = hit.getNbOfWires();
                    if (hit.getWireId() == hit.getNbOfWires() )
                        expected_wire_plus = 1;


                    boolean has_next = true;
                    while (has_next) {
                        has_next = false;
                        for (Hit hit1 : p) {
                            if (hit1.is_NoUsed() && (hit1.getWireId() == expected_wire_minus || hit1.getWireId() == expected_wire_plus)) {
                                temp.add(hit1);
                                hit1.setUse(true);
                                has_next = true;
                                break;
                            }
                        }
                    }
                    if (!temp.isEmpty()) preclusters.add(new PreCluster(temp));
                }
            }
        }
        return preclusters;
    }

    public ArrayList<PreclusterSuperlayer> merge_preclusters(ArrayList<PreCluster> preclusters) {
        double distance_max = 8.0;

        ArrayList<PreclusterSuperlayer> superpreclusters = new ArrayList<>();
        for (PreCluster precluster : preclusters) {
            if (!precluster.is_Used()) {
                ArrayList<PreCluster> tmp = new ArrayList<>();
                tmp.add(precluster);
                precluster.set_Used(true);
                for (PreCluster other : preclusters) {
                    if (precluster.get_hits_list().get(precluster.get_hits_list().size() - 1).getSuperLayerId() == other.get_hits_list().get(other.get_hits_list().size() - 1).getSuperLayerId() && precluster.get_hits_list().get(precluster.get_hits_list().size() - 1).getLayerId() != other.get_hits_list().get(other.get_hits_list().size() - 1).getLayerId() && !other.is_Used()) {
                        double dx = precluster.get_X() - other.get_X();
                        double dy = precluster.get_Y() - other.get_Y();
                        double distance = Math.sqrt(dx * dx + dy * dy);

                        if (distance < distance_max) {
                            other.set_Used(true);
                            tmp.add(other);
                        }
                    }
                }

                if (!tmp.isEmpty()) superpreclusters.add(new PreclusterSuperlayer(tmp));
            }
        }

        return superpreclusters;
    }




}
