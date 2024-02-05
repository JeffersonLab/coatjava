package org.jlab.analysis.postprocess;

import java.util.logging.Logger;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.utils.system.ClasUtilsFile;
import org.jlab.detector.scalers.DaqScalers;
import org.jlab.detector.scalers.DaqScalersSequence;
import org.jlab.detector.helicity.HelicityBit;
import org.jlab.detector.helicity.HelicitySequenceDelayed;
import org.jlab.logging.DefaultLogger;
import org.jlab.utils.options.OptionParser;

/**
 * Calls routines to rebuild HEL::flip banks, do analysis and per-event lookup of
 * delayed helicity and beam charge from tag-1 events and update REC::Event
 * accordingly, and adds tag-1 events for configuration banks.
 * 
 * WARNING:  Multiple run numbers
 * 
 * FIXME:  delay=8 is hardcoded below, should come from CCDB.  
 *
 * @author wphelps
 * @author baltzell
 */

public class Tag1ToEvent {

    static final Logger LOGGER = Logger.getLogger(Tag1ToEvent.class.getName());

    public static void main(String[] args) {

        DefaultLogger.debug();

        // Parse command-line options:
        OptionParser parser = new OptionParser("postprocess");
        parser.addOption("-q","0","do beam charge and livetime (0/1=false/true)");
        parser.addOption("-d","0","do delayed helicity (0/1=false/true)");
        parser.addOption("-f","0","rebuild the HEL::flip banks (0/1=false/true)");
        parser.addRequired("-o","output.hipo");
        parser.parse(args);
        if (parser.getInputList().isEmpty()) {
            parser.printUsage();
            LOGGER.severe("No input file(s) specified.");
            System.exit(1);
        }
        final boolean doHelicityDelay = parser.getOption("-d").intValue() != 0;
        final boolean doBeamCharge = parser.getOption("-q").intValue() != 0;
        final boolean doRebuildFlips = parser.getOption("-f").intValue() != 0;
        if (!doHelicityDelay && !doBeamCharge && !doRebuildFlips) {
            parser.printUsage();
            LOGGER.severe("At least one of -q/-d/-f is required.");
            System.exit(1);
        }

        // Initialize event counters:
        long badCharge = 0;
        long goodCharge = 0;
        long badHelicity = 0;
        long goodHelicity = 0;

        try (HipoWriterSorted writer = new HipoWriterSorted()) {

            // Setup the output file writer:
            writer.getSchemaFactory().initFromDirectory(ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4"));
            writer.setCompressionType(2);
            writer.open(parser.getOption("-o").stringValue());

            // Setup event and bank stores:
            Event event = new Event();
            Event configEvent = new Event();
            Bank runConfigBank = new Bank(writer.getSchemaFactory().getSchema("RUN::config"));
            Bank recEventBank = new Bank(writer.getSchemaFactory().getSchema("REC::Event"));
            Bank helScalerBank = new Bank(writer.getSchemaFactory().getSchema("HEL::scaler"));
            Bank helFlipBank = new Bank(writer.getSchemaFactory().getSchema("HEL::flip"));
            Bank[] configBanks = new Bank[]{new Bank(writer.getSchemaFactory().getSchema(ReconstructionEngine.CONFIG_BANK_NAME))};

            // Prepare to read from CCDB:
            ConstantsManager conman = new ConstantsManager();
            conman.init("/runcontrol/hwp");
 
            // Initialize the scaler sequence from tag-1 events:
            LOGGER.info("Initializing scaler sequence from RUN/HEL::scaler ...");
            DaqScalersSequence chargeSeq = DaqScalersSequence.readSequence(parser.getInputList());

            // Initialize the helicity sequence:
            // FIXME:  get delay windows from CCDB
            HelicitySequenceDelayed helSeq = new HelicitySequenceDelayed(8);
            if (doRebuildFlips) {
                // Read all events in all the files once, to rebuild helicity sequence:
                LOGGER.info("Rebuilding helicity sequence from HEL::adc ...");
                helSeq.addStream(writer.getSchemaFactory(), conman, parser.getInputList());
            }
            else {
                // Just read the helicity sequence from existing HEL::flip banks in tag-1 events:
                LOGGER.info("Initializing helicity sequence from HEL::flip ...");
                helSeq.initialize(parser.getInputList());
            }

            // Loop over the input HIPO files:
            LOGGER.info("Starting post-processing ...");
            for (String filename : parser.getInputList()) {

                HipoReader reader = new HipoReader();
                reader.open(filename);

                while (reader.hasNext()) {

                    // Read banks:
                    reader.nextEvent(event);
                    event.read(recEventBank);
                    event.read(helScalerBank);
                    event.read(runConfigBank);

                    // Remove banks to be modified or rebuilt:
                    event.remove(recEventBank.getSchema());
                    event.remove(helScalerBank.getSchema());
                    if (doRebuildFlips) {
                        event.remove(helFlipBank.getSchema());
                    }

                    // Do event lookups for helicity and scaler sequences:
                    DaqScalers ds = chargeSeq.get(event);
                    HelicityBit hb = helSeq.search(runConfigBank.getLong("timestamp", 0));
                    HelicityBit hbraw = helSeq.getHalfWavePlate() ? HelicityBit.getFlipped(hb) : hb;

                    // Increment event counters:
                    if (Math.abs(hb.value()) == 1) ++goodHelicity; else ++badHelicity;
                    if (ds == null) ++badCharge; else ++goodCharge;

                    // Write delay-corrected helicty to REC::Event and HEL::scaler:
                    if (doHelicityDelay) {
                        System.out.println(runConfigBank.getInt("event",0)+" "+hbraw.value()+"/"+hb.value());
                        recEventBank.putByte("helicity",0,hb.value());
                        recEventBank.putByte("helicityRaw",0,hbraw.value());
                        RebuildScalers.assignScalerHelicity(runConfigBank.getLong("timestamp",0), helScalerBank, helSeq);
                    }

                    // Write beam charge to REC::Event:
                    if (doBeamCharge && ds!=null) {
                        recEventBank.putFloat("beamCharge",0, (float) ds.dsc2.getBeamChargeGated());
                        recEventBank.putDouble("liveTime",0,ds.dsc2.getLivetime());
                    }

                    // Write the modified banks back to the original event:
                    event.write(recEventBank);
                    event.write(helScalerBank);

                    // Copy config banks to new tag-1 events:
                    createTag1Events(writer, event, configEvent, configBanks);

                    writer.addEvent(event, event.getEventTag());
                }

                reader.close();
            }

            // Write new HEL::flip banks:
            if (doRebuildFlips) {
                LOGGER.info("Writing rebuilt HEL::flip banks ...");
                helSeq.writeFlips(writer, 1);
            }
        }

        LOGGER.info(String.format("Tag1ToEvent:  Good Helicity Fraction: %.2f%%",100*(float)goodHelicity/(goodHelicity+badHelicity)));
        LOGGER.info(String.format("Tag1ToEvent:  Good Charge   Fraction: %.2f%%",100*(float)goodCharge/(goodCharge+badCharge)));
    }

    private static void createTag1Events(HipoWriterSorted writer, Event source, Event destination, Bank... banks) {
        destination.reset();
        for (Bank bank : banks) {
            source.read(bank);
            if (bank.getRows()>0) {
                destination.write(bank);
            }
        }
        if (!destination.isEmpty()) {
            writer.addEvent(destination,1);
        }
    }

}

