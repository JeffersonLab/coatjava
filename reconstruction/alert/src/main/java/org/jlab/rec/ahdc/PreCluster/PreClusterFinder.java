package org.jlab.rec.ahdc.PreCluster;

import org.jlab.rec.ahdc.Hit.Hit;

import java.util.ArrayList;
import java.util.List;

public class PreClusterFinder {

	private ArrayList<PreCluster> _AHDCPreClusters;

	public PreClusterFinder() {
		_AHDCPreClusters = new ArrayList<>();
	}
	
	private void fill_list(List<Hit> AHDC_hits, ArrayList<ArrayList<Hit>> all_super_layer){
		int nsuper_layers = 8;
		int super_layers[] = {1,2,2,3,3,4,4,5};
		int layers[] = {1,1,2,1,2,1,2,1};
		for(int i = 0; i < nsuper_layers; i++){
			ArrayList<Hit> sxlx = new ArrayList<>();
			for (Hit hit : AHDC_hits) {
				if (hit.getSuperLayerId() == super_layers[i] && hit.getLayerId() == layers[i]) {
					sxlx.add(hit);
				}
			}
			all_super_layer.add(sxlx);
		}
	}

	public void findPreCluster(List<Hit> AHDC_hits) {
		ArrayList<ArrayList<Hit>> all_super_layer = new ArrayList<>();
		fill_list(AHDC_hits,all_super_layer);

		for (ArrayList<Hit> sxlx : all_super_layer) {
			for (Hit hit : sxlx) {
				if (hit.is_NoUsed()) {
					ArrayList<Hit> temp_list = new ArrayList<>();
					temp_list.add(hit);
					hit.setUse(true);
					int expected_wire_plus  = hit.getWireId() + 1;
					int expected_wire_minus = hit.getWireId() - 1;
					if (hit.getWireId() == 1) {
						expected_wire_minus = hit.getNbOfWires();
					}
					if (hit.getWireId() == hit.getNbOfWires() ) {
						expected_wire_plus = 1;
					}

					boolean already_use = false;
					for (Hit hit1 : sxlx) {
						if (hit1.getWireId() == hit.getWireId() && hit1.is_NoUsed()) {
							temp_list.add(hit1);
							hit1.setUse(false);
						}
						if (hit1.getDoca() < 0.6 || hit.getDoca() < 0.6) {continue;}

						if ((hit1.getWireId() == expected_wire_minus || hit1.getWireId() == expected_wire_plus) && ((hit.getDoca() > 1.7 && hit1.getDoca() > 1.7) || hit1.getDoca() > 2.6 || hit.getDoca() > 2.6) && hit1.is_NoUsed() && !already_use) {
							temp_list.add(hit1);
							already_use = true;
							hit1.setUse(true);
						}
					}
					if (!temp_list.isEmpty()) {
						_AHDCPreClusters.add(new PreCluster(temp_list));
					}
				}
			}
		}
	}


	public ArrayList<PreCluster> get_AHDCPreClusters() {
		return _AHDCPreClusters;
	}

	public void set_AHDCPreClusters(ArrayList<PreCluster> _AHDCPreClusters) {
		this._AHDCPreClusters = _AHDCPreClusters;
	}
}
