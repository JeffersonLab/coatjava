package org.jlab.rec.ahdc.PreCluster;

import org.jlab.rec.ahdc.Hit.Hit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class PreClusterFinder {

	private ArrayList<PreCluster> _AHDCPreClusters;
	private int getNextWire(int wireId, int totalWires) { return wireId == totalWires ? 1 : wireId + 1; }
    private int getPreviousWire(int wireId, int totalWires) { return wireId == 1 ? totalWires : wireId - 1; }

	public PreClusterFinder() {
		_AHDCPreClusters = new ArrayList<>();
	}
	
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

    public void findPreclusters(List<Hit> AHDC_hits) {
		AHDC_hits.sort(Comparator.comparingDouble(Hit::getRadius));
        ArrayList<ArrayList<Hit>> all_super_layer = fillAllLayers(AHDC_hits);
        ArrayList<PreCluster> preclusters = new ArrayList<>();

        for (ArrayList<Hit> layer : all_super_layer) {
            Map<Integer, Hit> wireToHit = new HashMap<>();
            for (Hit hit : layer) {
                if (hit.is_NoUsed()) wireToHit.put(hit.getWireId(), hit);
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
        _AHDCPreClusters = preclusters;
    }	


	public ArrayList<PreCluster> get_AHDCPreClusters() {
		return _AHDCPreClusters;
	}

	public void set_AHDCPreClusters(ArrayList<PreCluster> _AHDCPreClusters) {
		this._AHDCPreClusters = _AHDCPreClusters;
	}
}
