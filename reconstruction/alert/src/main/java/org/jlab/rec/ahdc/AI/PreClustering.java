package org.jlab.rec.ahdc.AI;

import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.PreCluster.PreCluster;

import java.util.*;

public class PreClustering {

    private int getNextWire(int wireId, int totalWires) { return wireId == totalWires ? 1 : wireId + 1; }
    private int getPreviousWire(int wireId, int totalWires) { return wireId == 1 ? totalWires : wireId - 1; }

    private ArrayList<ArrayList<Hit>> fillAllLayers(List<Hit> hits) {
        // Define layer configurations: [superLayer, layer]
        int[][] layerConfig = {{1, 1}, {2, 1}, {2, 2}, {3, 1}, {3, 2}, {4, 1}, {4, 2}, {5, 1}};

        ArrayList<ArrayList<Hit>> layers = new ArrayList<>();
        for (int i = 0; i < layerConfig.length; i++) { layers.add(new ArrayList<>()); }

        for (Hit hit : hits) {
            for (int i = 0; i < layerConfig.length; i++) {
                if (layerConfig[i][0] == hit.getSuperLayerId() && layerConfig[i][1] == hit.getLayerId()) {
                    hit.setUse(false);
                    layers.get(i).add(hit);
                    break;
                }
            }
        }

        Comparator<Hit> phiComparator = Comparator.comparingDouble(Hit::getPhi);
        for (ArrayList<Hit> layer : layers) { layer.sort(phiComparator); }

        return layers;
    }

    public ArrayList<PreCluster> find_preclusters_for_AI(List<Hit> AHDC_hits) {
        ArrayList<ArrayList<Hit>> all_super_layer = fillAllLayers(AHDC_hits);
        ArrayList<PreCluster> preclusters = new ArrayList<>();

        for (ArrayList<Hit> layer : all_super_layer) {
            Map<Integer, Hit> wireToHit = new HashMap<>();
            for (Hit hit : layer) {
                if (hit.is_NoUsed()) {
                    wireToHit.put(hit.getWireId(), hit);
                }
            }

            for (Hit seedHit : layer) {
                if (seedHit.is_NoUsed()) {
                    ArrayList<Hit> clusterHits = new ArrayList<>();
                    Queue<Hit> toProcess = new LinkedList<>();

                    toProcess.add(seedHit);
                    seedHit.setUse(true);
                    wireToHit.remove(seedHit.getWireId());

                    while (!toProcess.isEmpty()) {
                        Hit currentHit = toProcess.poll();
                        clusterHits.add(currentHit);

                        int totalWires = currentHit.getNbOfWires();
                        int nextWire = getNextWire(currentHit.getWireId(), totalWires);
                        int prevWire = getPreviousWire(currentHit.getWireId(), totalWires);

                        for (int adjacentWire : new int[]{nextWire, prevWire}) {
                            Hit adjacentHit = wireToHit.get(adjacentWire);
                            if (adjacentHit != null) {
                                adjacentHit.setUse(true);
                                wireToHit.remove(adjacentWire);
                                toProcess.add(adjacentHit);
                            }
                        }
                    }

                    preclusters.add(new PreCluster(clusterHits));
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
