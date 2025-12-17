package org.jlab.rec.ahdc.Hit;

import java.util.ArrayList;

import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.detector.banks.RawDataBank;
import org.jlab.geom.detector.alert.AHDC.AlertDCDetector;
import org.jlab.rec.alert.constants.CalibrationConstantsLoader;

public class HitReader {

    private ArrayList<Hit>     _AHDCHits;
    private ArrayList<TrueHit> _TrueAHDCHits;
    private boolean sim = false;

    public HitReader(DataEvent event, AlertDCDetector detector, boolean simulation) {
        sim = simulation;
        fetch_AHDCHits(event, detector);
        if (simulation) fetch_TrueAHDCHits(event);
    }

    public final void fetch_AHDCHits(DataEvent event, AlertDCDetector detector) {

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
            int number     = bankDGTZ.getByte("layer", i);      // e.g. 11,12,21,... (this matches CCDB "layer")
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

            // CCDB key
            int key_value = sector * 10000 + number * 100 + wire;

            // -----------------------------
            // Raw hit cuts
            // -----------------------------
	    // double[] rawHitCuts = CalibrationConstantsLoader.AHDC_RAW_HIT_CUTS.get(key_value);
            //if (rawHitCuts == null) continue;


           double[] rawHitCuts = CalibrationConstantsLoader.AHDC_RAW_HIT_CUTS.get(key_value);
           if (rawHitCuts == null) {throw new IllegalStateException("Missing CCDB table /calibration/alert/ahdc/raw_hit_cuts for key=" + key_value+ " (check run/variation + key mapping)");
           }
	    
            double t_min   = rawHitCuts[0];
            double t_max   = rawHitCuts[1];
            double tot_min = rawHitCuts[2];
            double tot_max = rawHitCuts[3];
            double adc_min = rawHitCuts[4];
            double adc_max = rawHitCuts[5];
            double ped_min = rawHitCuts[6];
            double ped_max = rawHitCuts[7];

            // -----------------------------
            // Time calibration + t->d
            // -----------------------------
            //double[] timeOffsets = CalibrationConstantsLoader.AHDC_TIME_OFFSETS.get(key_value);
            //if (timeOffsets == null) continue;

	    //  double[] timeOffsets = CalibrationConstantsLoader.AHDC_TIME_OFFSETS.get(key_value);
            //if (timeOffsets == null) {
	    //throw new IllegalStateException("Missing AHDC time_offsets for key=" + key_value);
	    //}

	  double[] timeOffsets = CalibrationConstantsLoader.AHDC_TIME_OFFSETS.get(key_value);
	    
          if (timeOffsets == null) {
           throw new IllegalStateException("Missing CCDB /calibration/alert/ahdc/time_offsets for key=" + key_value + " (check run/variation + key mapping)");
          }



	  double[] time2distance = CalibrationConstantsLoader.AHDC_TIME_TO_DISTANCE.get(key_value);
         if (time2distance == null) { throw new IllegalStateException("Missing CCDB table /calibration/alert/ahdc/time_to_distance for key=" + key_value+ " (check run/variation + key mapping)");
      }

//          double[] time2distance = CalibrationConstantsLoader.AHDC_TIME_TO_DISTANCE.get(10101);
//         if (time2distance == null) continue;

            double t0 = timeOffsets[0];

            double p0 = time2distance[0];
            double p1 = time2distance[1];
            double p2 = time2distance[2];
            double p3 = time2distance[3];
            double p4 = time2distance[4];
            double p5 = time2distance[5];

            double time = leadingEdgeTime - t0 - startTime;

            // -----------------------------
            // ToT correction (new CCDB)
            // convention: ToT_corr = ToT_raw * totCorr
            // -----------------------------
            double totUsed = timeOverThreshold;
            if (!sim) {
                double[] totArr = CalibrationConstantsLoader.AHDC_TIME_OVER_THRESHOLD.get(key_value);
                if (totArr != null && totArr.length > 0) {
                    double totCorr = totArr[0];
                    totUsed = timeOverThreshold * totCorr;
                }
            }

            // -----------------------------
            // Hit selection (cuts)
            // NOTE: we cut on totUsed (corrected ToT). If you want RAW-ToT cuts,
            // replace totUsed with timeOverThreshold in the condition.
            // -----------------------------
            boolean passCuts =
                    (wfType <= 2) &&
                    (adcRaw >= adc_min) && (adcRaw <= adc_max) &&
                    (time   >= t_min)   && (time   <= t_max) &&
                    (totUsed >= tot_min) && (totUsed <= tot_max) &&
                    (adcOffset >= ped_min) && (adcOffset <= ped_max);

            if (!passCuts && !sim) continue;

            // -----------------------------
            // DOCA from calibrated time
            // -----------------------------
            double doca = p0
                    + p1*Math.pow(time, 1.0)
                    + p2*Math.pow(time, 2.0)
                    + p3*Math.pow(time, 3.0)
                    + p4*Math.pow(time, 4.0)
                    + p5*Math.pow(time, 5.0);

            if (time < 0) doca = 0.0;

            // -----------------------------
            // ADC gain calibration (new gains schema: gainCorr is index 0)
            // convention: ADC_cal = ADC_raw * gainCorr
            // -----------------------------
            double adcCal = adcRaw;
            if (!sim) {
                double[] gainArr = CalibrationConstantsLoader.AHDC_ADC_GAINS.get(key_value);
                if (gainArr != null && gainArr.length > 0) {
                    double gainCorr = gainArr[0];
                    adcCal = adcRaw * gainCorr;
                }
            }

            Hit h = new Hit(id, superlayer, layer, wire, doca, adcCal, time);
            h.setWirePosition(detector);
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
