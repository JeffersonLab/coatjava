package org.jlab.service.ahdc;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;
import org.jlab.rec.ahdc.Banks.RecoBankWriter;
import org.jlab.rec.ahdc.Hit.Hit;
import org.jlab.rec.ahdc.Hit.HitReader;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.detector.alert.AHDC.AlertDCDetector;
import org.jlab.geom.detector.alert.AHDC.AlertDCFactory;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.detector.pulse.ModeAHDC;

/** AHDCEngine reconstruction service.
 *
 *  Reads AHDC::adc, applies calibration, and writes calibrated AHDC::hits.
 *
 *  Track finding (preclustering, AI/CV track finders, DOCA refinement,
 *  helix fit) lives in ALERTEngine.
 */
public class AHDCEngine extends ReconstructionEngine {
    static final Logger LOGGER = Logger.getLogger(AHDCEngine.class.getName());

    private AlertDCDetector factory = null;
    private ModeAHDC ahdcExtractor = new ModeAHDC();

    // AHDC calibration tables (instance-level, refreshed on run change)
    private IndexedTable ahdcTimeOffsetsTable;
    private IndexedTable ahdcTimeToDistanceWireTable;
    private IndexedTable ahdcRawHitCutsTable;
    private IndexedTable ahdcAdcGainsTable;
    private IndexedTable ahdcTimeOverThresholdTable;

    int Run = -1;

    public AHDCEngine() { super("ALERT", "ouillon", "1.0.1"); }

    @Override
    public void detectorChanged(int run) {
        // FIXME:  move geometry initialization here
    }

    @Override
    public boolean init() {

        factory = (new AlertDCFactory()).createDetectorCLAS(new DatabaseConstantProvider());

        Map<String, Integer> tableMap = new HashMap<>();
        tableMap.put("/calibration/alert/ahdc/time_offsets", 3);
        tableMap.put("/calibration/alert/ahdc/time_to_distance_wire", 3);
        tableMap.put("/calibration/alert/ahdc/raw_hit_cuts", 3);
        tableMap.put("/calibration/alert/ahdc/gains", 3);
        tableMap.put("/calibration/alert/ahdc/time_over_threshold", 3);

        requireConstants(tableMap);
        this.getConstantsManager().setVariation("default");
        this.registerOutputBank("AHDC::hits");

        return true;
    }

    @Override
    public boolean processDataEventUser(DataEvent event) {

        ahdcExtractor.update(30, null, event, "AHDC::wf", "AHDC::adc");

        if (event.hasBank("RUN::config")) {
            DataBank bank = event.getBank("RUN::config");
            int newRun = bank.getInt("run", 0);
            if (newRun <= 0) {
                LOGGER.warning("AHDCEngine:  got run <= 0 in RUN::config, skipping event.");
                return false;
            }
            if(Run != newRun) {
                ahdcTimeOffsetsTable        = this.getConstantsManager().getConstants(newRun, "/calibration/alert/ahdc/time_offsets");
                ahdcTimeToDistanceWireTable = this.getConstantsManager().getConstants(newRun, "/calibration/alert/ahdc/time_to_distance_wire");
                ahdcRawHitCutsTable         = this.getConstantsManager().getConstants(newRun, "/calibration/alert/ahdc/raw_hit_cuts");
                ahdcAdcGainsTable           = this.getConstantsManager().getConstants(newRun, "/calibration/alert/ahdc/gains");
                ahdcTimeOverThresholdTable  = this.getConstantsManager().getConstants(newRun, "/calibration/alert/ahdc/time_over_threshold");
                Run = newRun;
            }
        }

        if (event.hasBank("AHDC::adc")) {
            boolean simulation = event.hasBank("MC::Particle");
            HitReader hitReader = new HitReader(event, factory, simulation,
                    ahdcRawHitCutsTable, ahdcTimeOffsetsTable, ahdcTimeToDistanceWireTable,
                    ahdcTimeOverThresholdTable, ahdcAdcGainsTable);
            ArrayList<Hit> AHDC_Hits = hitReader.get_AHDCHits();

            RecoBankWriter writer = new RecoBankWriter();
            DataBank recoHitsBank = writer.fillAHDCHitsBank(event, AHDC_Hits);
            if (recoHitsBank != null) event.appendBank(recoHitsBank);
        }
        return true;
    }

    public static void main(String[] args) {

        double starttime = System.nanoTime();

        int    nEvent     = 0;
        int    maxEvent   = 10;
        String inputFile  = "output1.hipo";
        String outputFile = "output.hipo";

        if (new File(outputFile).delete()) System.out.println("output.hipo is delete.");

        System.err.println(" \n[PROCESSING FILE] : " + inputFile);

        AHDCEngine en = new AHDCEngine();

        HipoDataSource reader = new HipoDataSource();

        en.init();

        reader.open(inputFile);
        HipoDataSync   writer = new HipoDataSync();
        writer.open(outputFile);

        while (reader.hasEvent() && nEvent < maxEvent) {
            nEvent++;
            DataEvent event = reader.getNextEvent();
            System.out.println("Event: " + nEvent);

            en.processDataEvent(event);
            writer.writeEvent(event);

        }
        writer.close();

        System.out.println("finished " + (System.nanoTime() - starttime) * Math.pow(10, -9));
    }
}
