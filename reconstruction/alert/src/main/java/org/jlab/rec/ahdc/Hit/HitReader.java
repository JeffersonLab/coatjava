package org.jlab.rec.ahdc.Hit;

import java.util.ArrayList;
import java.util.List;

import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.detector.banks.RawDataBank;
import org.jlab.geom.detector.alert.AHDC.AlertDCDetector;
import org.jlab.utils.groups.IndexedTable;

public class HitReader {

    private ArrayList<Hit>     _AHDCHits;
    private ArrayList<TrueHit> _TrueAHDCHits;
    private boolean sim = false;

    private IndexedTable rawHitCutsTable;
    private IndexedTable timeOffsetsTable;
    private IndexedTable timeToDistanceWireTable;
    private IndexedTable timeOverThresholdTable;
    private IndexedTable adcGainsTable;

    public HitReader(DataEvent event, AlertDCDetector detector, boolean simulation,
                     IndexedTable rawHitCuts,
                     IndexedTable timeOffsets,
                     IndexedTable timeToDistanceWire,
                     IndexedTable timeOverThreshold,
                     IndexedTable adcGains) {
        sim = simulation;
        fetch_AHDCHits(event, detector, rawHitCuts, timeOffsets, timeToDistanceWire, timeOverThreshold, adcGains);
        if (simulation) fetch_TrueAHDCHits(event);
    }

	public double T2Dfunction(int sector, int layer, int wire, double time){
		long hash = timeToDistanceWireTable.getList().getIndexGenerator().hashCode(sector, layer, wire);
		List<Double> t2d = timeToDistanceWireTable.getDoublesByHash(hash);

		// T2D function consists of three 1st order polynomials (p1, p2, p3) and two transition functions (t1, t2).
		// Column order: p1_int(0), p1_slope(1), p2_int(2), p2_slope(3), p3_int(4), p3_slope(5),
		//               t1_x0(6), t1_width(7), t2_x0(8), t2_width(9), z0(10), z1(11), z2(12), extra1(13), extra2(14), chi2ndf(15)

		double p1 = (t2d.get(0) + t2d.get(1)*time);
		double p2 = (t2d.get(2) + t2d.get(3)*time);
		double p3 = (t2d.get(4) + t2d.get(5)*time);

		double t1 = 1.0/(1.0 + Math.exp(-(time - t2d.get(6))/t2d.get(7)));
		double t2 = 1.0/(1.0 + Math.exp(-(time - t2d.get(8))/t2d.get(9)));

		return (p1)*(1.0 - t1) + (t1)*(p2)*(1.0 - t2) + (t2)*(p3);
	}

	public final void fetch_AHDCHits(DataEvent event, AlertDCDetector detector,
	                                 IndexedTable rawHitCuts, IndexedTable timeOffsets,
	                                 IndexedTable timeToDistanceWire, IndexedTable totCorrTable,
	                                 IndexedTable adcGains) {
		this.rawHitCutsTable = rawHitCuts;
		this.timeOffsetsTable = timeOffsets;
		this.timeToDistanceWireTable = timeToDistanceWire;
		this.timeOverThresholdTable = totCorrTable;
		this.adcGainsTable = adcGains;

		ArrayList<Hit> hits = new ArrayList<>();

		if (!event.hasBank("AHDC::adc")) {
			this.set_AHDCHits(hits);
			return;
		}

		double startTime = 0.0;
		if (event.hasBank("REC::Event") && !sim) {
			DataBank bankRecEvent = event.getBank("REC::Event");
			startTime = bankRecEvent.getFloat("startTime", 0);
		}

		RawDataBank bankDGTZ = new RawDataBank("AHDC::adc");
		bankDGTZ.read(event);

		for (int i = 0; i < bankDGTZ.rows(); i++) {

			int id         = bankDGTZ.trueIndex(i) + 1;
			int number     = bankDGTZ.getByte("layer", i);
			int layer      = number % 10;
			int superlayer = (number % 100) / 10;
			int sector     = bankDGTZ.getInt("sector", i);
			int wire       = bankDGTZ.getShort("component", i);

			// RAW quantities from bank
			double adcRaw            = bankDGTZ.getInt("ADC", i);
			double leadingEdgeTime   = bankDGTZ.getFloat("leadingEdgeTime", i);
			double timeOverThreshold = bankDGTZ.getFloat("timeOverThreshold", i);
			double adcOffset         = bankDGTZ.getFloat("ped", i);
			int    wfType            = bankDGTZ.getShort("wfType", i);

			// Raw hit cuts
			double t_min   = rawHitCutsTable.getDoubleValue("t_min",   sector, number, wire);
			double t_max   = rawHitCutsTable.getDoubleValue("t_max",   sector, number, wire);
			double tot_min = rawHitCutsTable.getDoubleValue("tot_min", sector, number, wire);
			double tot_max = rawHitCutsTable.getDoubleValue("tot_max", sector, number, wire);
			double adc_min = rawHitCutsTable.getDoubleValue("adc_min", sector, number, wire);
			double adc_max = rawHitCutsTable.getDoubleValue("adc_max", sector, number, wire);
			double ped_min = rawHitCutsTable.getDoubleValue("ped_min", sector, number, wire);
			double ped_max = rawHitCutsTable.getDoubleValue("ped_max", sector, number, wire);

			// Time calibration
			double t0   = timeOffsetsTable.getDoubleValue("t0", sector, number, wire);
			double time = leadingEdgeTime - t0 - startTime;

			// ToT correction
			double totUsed = timeOverThreshold;
			if (!sim) {
				double totCorr = timeOverThresholdTable.getDoubleValue("totCorr", sector, number, wire);
				if (totCorr != 0.0) totUsed = timeOverThreshold * totCorr;
			}

			// Hit selection (cuts)
			boolean passCuts =
				(wfType <= 2) &&
				(adcRaw >= adc_min) && (adcRaw <= adc_max) &&
				(time   >= t_min)   && (time   <= t_max) &&
				(timeOverThreshold >= tot_min) && (timeOverThreshold <= tot_max) &&
				(adcOffset >= ped_min) && (adcOffset <= ped_max);

			if (!passCuts && !sim) continue;

			// DOCA from calibrated time
			double doca = T2Dfunction(sector, number, wire, time);
			if (time < 0) doca = 0.0;

			// ADC gain calibration
			double adcCal = adcRaw;
			if (!sim) {
				double gainCorr = adcGainsTable.getDoubleValue("gainCorr", sector, number, wire);
				if (gainCorr != 0.0) adcCal = adcRaw * gainCorr;
			}

			Hit h = new Hit(id, superlayer, layer, wire, doca, adcRaw, time);
			h.setWirePosition(detector);
			h.setADC(adcCal);
			h.setToT(totUsed);
			hits.add(h);
		}

		this.set_AHDCHits(hits);
	}

	public final void fetch_TrueAHDCHits(DataEvent event) {

		ArrayList<TrueHit> truehits = new ArrayList<>();

		if (event.hasBank("MC::True")) {
			DataBank bankSIMU = event.getBank("MC::True");
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

	public void set_AHDCHits(ArrayList<Hit> hits) {
		this._AHDCHits = hits;
	}

	public ArrayList<TrueHit> get_TrueAHDCHits() {
		return _TrueAHDCHits;
	}

	public void set_TrueAHDCHits(ArrayList<TrueHit> trueHits) {
		this._TrueAHDCHits = trueHits;
	}
}
