package org.jlab.rec.ahdc.Hit;

import java.util.ArrayList;

import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.detector.banks.RawDataBank;
import org.jlab.rec.alert.constants.CalibrationConstantsLoader;

public class HitReader {

	private ArrayList<Hit>     _AHDCHits;
	private ArrayList<TrueHit> _TrueAHDCHits;

	public HitReader(DataEvent event, boolean simulation) {
		fetch_AHDCHits(event);
		if (simulation) fetch_TrueAHDCHits(event);
	}

	public void fetch_AHDCHits(DataEvent event) {
		ArrayList<Hit> hits = new ArrayList<>();
		
		if (event.hasBank("AHDC::adc")) {

			RawDataBank bankDGTZ = new RawDataBank("AHDC::adc");
        	bankDGTZ.read(event);
		//DataBank bankDGTZ = event.getBank("ALRTDC::adc");

		
			for (int i = 0; i < bankDGTZ.rows(); i++) {
				int    id         = bankDGTZ.trueIndex(i) + 1;
				int    number     = bankDGTZ.getByte("layer", i);
				int    layer      = number % 10;
				int    superlayer = (int) (number % 100) / 10;
				int    sector     = bankDGTZ.getInt("sector", i);
				int    wire       = bankDGTZ.getShort("component", i);
				double adc        = bankDGTZ.getInt("ADC", i);
				double leadingEdgeTime = bankDGTZ.getFloat("leadingEdgeTime", i);
				
				// use calibration constants
				int key_value = sector*10000 + number*100 + wire;
				double[] timeOffsets = CalibrationConstantsLoader.AHDC_TIME_OFFSETS.get( key_value );
				double[] time2distance = CalibrationConstantsLoader.AHDC_TIME_TO_DISTANCE.get( 10101 ); // the time to distance table has only one row ! (10101 is its only key)
				double t0 = timeOffsets[0];
				double p0 = time2distance[0];
				double p1 = time2distance[1];
				double p2 = time2distance[2];
				double p3 = time2distance[3];
				double p4 = time2distance[4];
				double p5 = time2distance[5];
				
				double time = leadingEdgeTime - t0;
				// we may prevent time to be too small or too big
				// CONDITION TO BE ADDED
				// we should also use a flag to prevent to read the ccdb if reconstructed event if from simulation
				// TO BE DONE
				//double doca       = bankDGTZ.getShort("ped", i) / 1000.0;
				double doca = p0 + p1*Math.pow(time,1.0) + p2*Math.pow(time,2.0) + p3*Math.pow(time,3.0) + p4*Math.pow(time,4.0) + p5*Math.pow(time, 5.0);
				hits.add(new Hit(id, superlayer, layer, wire, doca, adc, time));
			}
		}
		this.set_AHDCHits(hits);
	}

	public void fetch_TrueAHDCHits(DataEvent event) {
		ArrayList<TrueHit> truehits = new ArrayList<>();

		DataBank bankSIMU = event.getBank("MC::True");

		if (event.hasBank("MC::True")) {
			for (int i = 0; i < bankSIMU.rows(); i++) {
				int    pid    = bankSIMU.getInt("pid", i);
				double x_true = bankSIMU.getFloat("avgX", i);
				double y_true = bankSIMU.getFloat("avgY", i);
				double z_true = bankSIMU.getFloat("avgZ", i);
				double trackE = bankSIMU.getFloat("trackE", i);

				truehits.add(new TrueHit(pid, x_true, y_true, z_true, trackE));
			}
		}
		this.set_TrueAHDCHits(truehits);
	}

	public ArrayList<Hit> get_AHDCHits() {
		return _AHDCHits;
	}

	public void set_AHDCHits(ArrayList<Hit> _AHDCHits) {
		this._AHDCHits = _AHDCHits;
	}

	public ArrayList<TrueHit> get_TrueAHDCHits() {
		return _TrueAHDCHits;
	}

	public void set_TrueAHDCHits(ArrayList<TrueHit> _TrueAHDCHits) {
		this._TrueAHDCHits = _TrueAHDCHits;
	}

}
