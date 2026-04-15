package org.jlab.rec.ahdc.Hit;

import java.util.ArrayList;
import java.util.List;

import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.detector.banks.RawDataBank;
import org.jlab.geom.detector.alert.AHDC.AlertDCDetector;
import org.jlab.utils.groups.IndexedTable;

/**
 * Reads raw AHDC hits from the {@code AHDC::adc} bank, applies calibration corrections
 * (time offsets, time-over-threshold, ADC gains), filters them against per-wire cuts in
 * data mode, and builds the list of {@link Hit} objects used by downstream reconstruction.
 * In simulation mode, the per-wire cuts and data-only ADC/ToT corrections are bypassed,
 * and truth information is additionally read from the {@code MC::True} bank into {@link TrueHit}s.
 */
public class HitReader {

    private ArrayList<Hit>     _AHDCHits;
    private ArrayList<TrueHit> _TrueAHDCHits;
    private boolean sim = false;

    /**
     * Constructs a HitReader and eagerly populates the hit lists from the given event.
     * After construction, retrieve the results via {@link #get_AHDCHits()} and
     * (in simulation) {@link #get_TrueAHDCHits()}.
     *
     * @param event                   current event containing the {@code AHDC::adc} bank (and {@code MC::True} in sim)
     * @param detector                AHDC geometry used to resolve wire positions on each hit
     * @param simulation              {@code true} for Monte Carlo events; disables data-only cuts and corrections
     * @param rawHitCutsTable         per-wire acceptance cuts (time, ToT, ADC, pedestal min/max)
     * @param timeOffsetsTable        per-wire {@code t0} offsets applied to the leading-edge time
     * @param timeToDistanceWireTable per-wire T2D calibration coefficients used to convert time to DOCA
     * @param timeOverThresholdTable  per-wire ToT correction factors (applied in data mode only)
     * @param adcGainsTable           per-wire ADC gain corrections (applied in data mode only)
     */
    public HitReader(DataEvent event, AlertDCDetector detector, boolean simulation, IndexedTable rawHitCutsTable, IndexedTable timeOffsetsTable,
					 IndexedTable timeToDistanceWireTable, IndexedTable timeOverThresholdTable, IndexedTable adcGainsTable) {
        sim = simulation;
        fetch_AHDCHits(event, detector, rawHitCutsTable, timeOffsetsTable, timeToDistanceWireTable, timeOverThresholdTable, adcGainsTable);
        if (simulation) fetch_TrueAHDCHits(event);
    }

	/**
	 * Converts a calibrated drift time into a distance-of-closest-approach (DOCA) for a
	 * given wire, using the piecewise T2D calibration stored in {@code timeToDistanceWireTable}.
	 *
	 * <p>The result is a blend of three 1st-order polynomials {@code p1, p2, p3} stitched
	 * together by two logistic transition functions {@code t1, t2}:
	 * {@code doca = p1·(1-t1) + t1·p2·(1-t2) + t2·p3}. The coefficients are looked up
	 * once per call via a hashed index on (sector, layer, wire).
	 *
	 * <p>Expected column order of the calibration row:
	 * p1_int(0), p1_slope(1), p2_int(2), p2_slope(3), p3_int(4), p3_slope(5),
	 * t1_x0(6), t1_width(7), t2_x0(8), t2_width(9), z0(10), z1(11), z2(12),
	 * extra1(13), extra2(14), chi2ndf(15).
	 *
	 * @param sector                  AHDC sector index
	 * @param layer                   packed layer index ({@code superlayer*10 + layer})
	 * @param wire                    wire (component) id within the layer
	 * @param time                    calibrated drift time in ns
	 * @param timeToDistanceWireTable per-wire T2D calibration table
	 * @return the DOCA in mm
	 */
	private double T2Dfunction(int sector, int layer, int wire, double time, IndexedTable timeToDistanceWireTable){
		long hash = timeToDistanceWireTable.getList().getIndexGenerator().hashCode(sector, layer, wire);
		List<Double> t2d = timeToDistanceWireTable.getDoublesByHash(hash);

		double p1 = (t2d.get(0) + t2d.get(1)*time);
		double p2 = (t2d.get(2) + t2d.get(3)*time);
		double p3 = (t2d.get(4) + t2d.get(5)*time);

		double t1 = 1.0/(1.0 + Math.exp(-(time - t2d.get(6))/t2d.get(7)));
		double t2 = 1.0/(1.0 + Math.exp(-(time - t2d.get(8))/t2d.get(9)));

		return (p1)*(1.0 - t1) + (t1)*(p2)*(1.0 - t2) + (t2)*(p3);
	}

	/**
	 * Reads the {@code AHDC::adc} bank, calibrates each raw row, and builds the list of
	 * reconstructed {@link Hit}s. For each row the method:
	 * <ol>
	 *   <li>applies the per-wire time offset {@code t0} (subtracting event start time in data mode),</li>
	 *   <li>in data mode, corrects time-over-threshold and enforces per-wire acceptance cuts
	 *       (time, ToT, ADC, pedestal, and {@code wfType <= 2}) — hits failing the cuts are dropped,</li>
	 *   <li>computes the DOCA from the calibrated time via {@link #T2Dfunction} (DOCA forced to 0
	 *       when {@code time < 0}),</li>
	 *   <li>in data mode, applies the per-wire ADC gain correction,</li>
	 *   <li>instantiates a {@link Hit}, resolves its wire position via the geometry, and stores the
	 *       calibrated ADC and ToT on it.</li>
	 * </ol>
	 * The resulting list is stored via {@link #set_AHDCHits(ArrayList)} (empty if the bank is absent).
	 *
	 * @param event                   current event
	 * @param detector                AHDC geometry used to set each hit's wire position
	 * @param rawHitCutsTable         per-wire acceptance cuts (data mode only)
	 * @param timeOffsetsTable        per-wire {@code t0}
	 * @param timeToDistanceWireTable per-wire T2D coefficients
	 * @param timeOverThresholdTable  per-wire ToT correction factors (data mode only)
	 * @param adcGainsTable           per-wire ADC gain corrections (data mode only)
	 */
	private void fetch_AHDCHits(DataEvent event, AlertDCDetector detector, IndexedTable rawHitCutsTable, IndexedTable timeOffsetsTable,
	                            IndexedTable timeToDistanceWireTable, IndexedTable timeOverThresholdTable, IndexedTable adcGainsTable) {
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

			// Time calibration
			double t0   = timeOffsetsTable.getDoubleValue("t0", sector, number, wire);
			double time = leadingEdgeTime - t0 - startTime;

			// ToT correction
			double totUsed = timeOverThreshold;
			if (!sim) {
				double totCorr = timeOverThresholdTable.getDoubleValue("totCorr", sector, number, wire);
				if (totCorr != 0.0) totUsed = timeOverThreshold * totCorr;

				// Hit selection (cuts) — only applied on data, bypassed in sim
				long hash = rawHitCutsTable.getList().getIndexGenerator().hashCode(sector, number, wire);
				double t_min   = rawHitCutsTable.getDoubleValueByHash("t_min",   hash);
				double t_max   = rawHitCutsTable.getDoubleValueByHash("t_max",   hash);
				double tot_min = rawHitCutsTable.getDoubleValueByHash("tot_min", hash);
				double tot_max = rawHitCutsTable.getDoubleValueByHash("tot_max", hash);
				double adc_min = rawHitCutsTable.getDoubleValueByHash("adc_min", hash);
				double adc_max = rawHitCutsTable.getDoubleValueByHash("adc_max", hash);
				double ped_min = rawHitCutsTable.getDoubleValueByHash("ped_min", hash);
				double ped_max = rawHitCutsTable.getDoubleValueByHash("ped_max", hash);

				boolean passCuts =
					(wfType <= 2) &&
					(adcRaw >= adc_min) && (adcRaw <= adc_max) &&
					(time   >= t_min)   && (time   <= t_max) &&
					(timeOverThreshold >= tot_min) && (timeOverThreshold <= tot_max) &&
					(adcOffset >= ped_min) && (adcOffset <= ped_max);

				if (!passCuts) continue;
			}

			// DOCA from calibrated time
			double doca = (time < 0) ? 0.0 : T2Dfunction(sector, number, wire, time, timeToDistanceWireTable);

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

	/**
	 * Reads Monte-Carlo truth information from the {@code MC::True} bank into a list of
	 * {@link TrueHit}s (particle id and average hit position/energy). Called only when
	 * the reader is constructed with {@code simulation = true}. If the bank is absent,
	 * the resulting list is empty.
	 *
	 * @param event current event
	 */
	private void fetch_TrueAHDCHits(DataEvent event) {

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

	/**
	 * @return the calibrated AHDC hits produced from the current event; never {@code null}
	 *         (empty if the {@code AHDC::adc} bank is missing)
	 */
	public ArrayList<Hit> get_AHDCHits() {
		return _AHDCHits;
	}

	/**
	 * Replaces the internally stored list of AHDC hits. Primarily used by {@link #fetch_AHDCHits}.
	 *
	 * @param hits the list to store
	 */
	public void set_AHDCHits(ArrayList<Hit> hits) {
		this._AHDCHits = hits;
	}

	/**
	 * @return the MC-truth hits for the current event (populated only in simulation mode;
	 *         {@code null} for data events where {@code fetch_TrueAHDCHits} was not called)
	 */
	public ArrayList<TrueHit> get_TrueAHDCHits() {
		return _TrueAHDCHits;
	}

	/**
	 * Replaces the internally stored list of MC-truth hits. Primarily used by {@link #fetch_TrueAHDCHits}.
	 *
	 * @param trueHits the list to store
	 */
	public void set_TrueAHDCHits(ArrayList<TrueHit> trueHits) {
		this._TrueAHDCHits = trueHits;
	}
}
