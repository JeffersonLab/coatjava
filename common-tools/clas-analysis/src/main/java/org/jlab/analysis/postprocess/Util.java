package org.jlab.analysis.postprocess;

import java.sql.Time;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;

import org.jlab.utils.groups.IndexedTable;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.calib.utils.RCDBConstants;

import org.jlab.detector.scalers.DaqScalers;
import org.jlab.detector.helicity.HelicitySequence;
import org.jlab.detector.helicity.HelicitySequenceDelayed;
import org.jlab.detector.helicity.HelicitySequenceManager;

/**
 * Static utility methods for postprocessing.
 * @author baltzell
 */
class Util {

    static final Logger logger = Logger.getLogger(Util.class.getName());

    /**
     * Assign the delay-corrected helicity to the HEL::scaler bank's rows
     * @param event the event containing the scaler reading
     * @param bank the HEL::scaler bank
     * @param seq previously initialized helicity sequence
     */
    public static void assignScalerHelicity(Event event, Bank bank, HelicitySequenceManager seq) {

        // Struck (helicity) scaler readout is always slightly after the helicity
        // state change, i.e., as registered in the FADCs, so its true helicity
        // is offset by one state from its event:
        final int readoutStateOffset = -1;

        // Rows in the HEL::scaler bank correspond to the most recent, consecutive,
        // time-ordered, T-stable intervals.  The first row is the earliest in
        // time, and the last row is the latest.  Here we loop over them:
        for (int row=0; row<bank.getRows(); ++row) {

            // This is the helicity state offset for this HEL::scaler row, where
            // the last row has an offset of -1:
            final int offset = bank.getRows() - row - 1 + readoutStateOffset;

            // Assign delay-corrected helicity to this HEL::scaler row:
            bank.putByte("helicity",row,seq.search(event,offset).value());
            if (seq.getHalfWavePlate(event))
                bank.putByte("helicityRaw",0,(byte)(-1*seq.search(event,offset).value()));
            else
                bank.putByte("helicityRaw",0,seq.search(event,offset).value());
        }
    }

    /**
     * Assign the delay-corrected helicity to the HEL::scaler bank's rows
     * @param timestamp event TI timestamp, e.g., from RUN::config bank
     * @param bank the HEL::scaler bank
     * @param seq previously initialized helicity sequence
     */
    public static void assignScalerHelicity(Long timestamp, Bank bank, HelicitySequence seq) {

        // Struck (helicity) scaler readout is always slightly after the helicity
        // state change, i.e., as registered in the FADCs, so its true helicity
        // is offset by one state from its event:
        final int readoutStateOffset = -1;

        // Rows in the HEL::scaler bank correspond to the most recent, consecutive,
        // time-ordered, T-stable intervals.  The first row is the earliest in
        // time, and the last row is the latest.  Here we loop over them:
        for (int row=0; row<bank.getRows(); ++row) {

            // This is the helicity state offset for this HEL::scaler row, where
            // the last row has an offset of -1:
            final int offset = bank.getRows() - row - 1 + readoutStateOffset;

            // Assign delay-corrected helicity to this HEL::scaler row:
            bank.putByte("helicity",row,seq.search(timestamp,offset).value());
            if (seq.getHalfWavePlate())
                bank.putByte("helicityRaw",0,(byte)(-1*seq.search(timestamp,offset).value()));
            else
                bank.putByte("helicityRaw",0,seq.search(timestamp,offset).value());
        }
    }

    /**
     * @param filenames
     * @param schema
     * @param restream whether to ignore tag-1 banks and rebuild the stream of helicity states from FADC
     * @param conman
     * @return delayd helicity sequnece
     */
    public static HelicitySequenceDelayed getHelicity(List<String> filenames, SchemaFactory schema, boolean restream, ConstantsManager conman) {
        final int run = getRunNumber(filenames);
        IndexedTable helTable = conman.getConstants(run, "/runcontrol/helicity");
        HelicitySequenceDelayed seq = null;
        if (helTable.getIntValue("delay", 0,0,0) == 0) {
            logger.warning("CCDB's helicity delay is zero, disabling helicity postprocessing.");
        }
        else {
            seq = new HelicitySequenceDelayed(helTable);
            if (restream) seq.addStream(schema, conman, filenames);
            else          seq.initialize(filenames);
        }
        return seq;
    }

    /**
     * Rebuild the RUN::scaler and HEL::scaler banks from RAW::scaler
     * @param schema
     * @param conman
     * @param runConfig a RUN::config bank
     * @param rawScaler a RAW::scaler bank
     * @return rebuilt banks
     */
    public static Bank[] rebuildScalers(SchemaFactory schema, ConstantsManager conman, Bank runConfig, Bank rawScaler) {
        if (runConfig.getRows()>0 && rawScaler.getRows()>0) {
            int run = runConfig.getInt("run", 0);
            IndexedTable fcup = conman.getConstants(run, "/runcontrol/fcup");
            IndexedTable slm = conman.getConstants(run, "/runcontrol/slm");
            IndexedTable hel = conman.getConstants(run, "/runcontrol/helicity");
            IndexedTable dsc = conman.getConstants(run, "/daq/config/scalers/dsc1");
            if (fcup != null) {
                DaqScalers ds;
                if (dsc.getIntValue("frequency",0,0,0) < 2e5) {
                    ds = DaqScalers.create(rawScaler, fcup, slm, hel, dsc);
                }
                else {
                    RCDBConstants rcdb = conman.getRcdbConstants(run);
                    Time rst = rcdb.getTime("run_start_time");
                    Date uet = new Date(runConfig.getInt("unixtime",0)*1000L);
                    ds = DaqScalers.create(rawScaler, fcup, slm, hel, rst, uet);
                }
                Bank runScaler = ds.createRunBank(schema);
                Bank helScaler = ds.createHelicityBank(schema);
                return new Bank[]{runScaler,helScaler};
            }
        }
        return new Bank[]{};
    }

    /**
     * Create a new file with all RUN::scaler and HEL::scaler banks rebuilt
     * from RAW::scaler
     * @param conman
     * @param inputFile
     * @param outputFile 
     */
    public static void rebuildScalers(ConstantsManager conman, String inputFile, String outputFile) {
        try (HipoWriterSorted w = new HipoWriterSorted()) {
            HipoReader r = new HipoReader();
            r.open(inputFile);
            Event event = new Event();
            w.setCompressionType(2);
            w.getSchemaFactory().copy(r.getSchemaFactory());
            w.open(outputFile);
            Bank runConfig = new Bank(r.getSchemaFactory().getSchema("RUN::config"));
            Bank rawScaler = new Bank(r.getSchemaFactory().getSchema("RAW::scaler"));
            Bank runScaler = new Bank(r.getSchemaFactory().getSchema("RUN::scaler"));
            Bank helScaler = new Bank(r.getSchemaFactory().getSchema("HEL::scaler"));
            while (r.hasNext()) {
                r.nextEvent(event);
                event.read(runConfig);
                event.read(rawScaler);
                event.remove(runScaler.getSchema());
                event.remove(helScaler.getSchema());
                for (Bank b : rebuildScalers(r.getSchemaFactory(), conman, runConfig, rawScaler)) {
                    event.write(b);
                }
                w.addEvent(event, event.getEventTag());
            }
        }
    }

    /**
     * Get the first "good" run number in RUN::config
     * @param filenames
     * @return run number
     */
    public static int getRunNumber(List<String> filenames) {
        Event event = new Event();
        for (String filename : filenames) {
            HipoReader reader = new HipoReader();
            reader.open(filename);
            Bank bank = reader.getBank("RUN::config");
            while (reader.hasNext()) {
                reader.nextEvent(event);
                event.read(bank);
                if (bank.getRows()>0 && bank.getInt("run",0)>0) {
                    logger.log(Level.INFO, "Found first good run number:  {0}", bank.getInt("run",0));
                    return bank.getInt("run",0);
                }
            }
        }
        return -1;
    }

    /**
     * @param filenames
     * @return first "good" RUN::config.run found 
     */
    public static int getRunNumber(String... filenames) {
        return getRunNumber(Arrays.asList(filenames));
    }

    public static void createTag1Events(HipoWriterSorted writer, Event source, Event destination, Bank... banks) {
        destination.reset();
        for (Bank bank : banks) {
            source.read(bank);
            if (bank.getRows()>0)
                destination.write(bank);
        }
        if (!destination.isEmpty())
            writer.addEvent(destination,1);
    }

}
