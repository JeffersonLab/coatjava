package org.jlab.detector.decode;

import java.io.File;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.logging.Logger;
import org.jlab.detector.helicity.HelicitySequence;
import org.jlab.detector.helicity.HelicityState;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioSource;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.utils.benchmark.Benchmark;
import org.jlab.utils.benchmark.ProgressPrintout;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.system.ClasUtilsFile;

/**
 * Wrapper of everything (previously) in CLAS12Decoder4's main method.
 *
 */
public class CLASDecoder4U extends CLASDecoder {

    private static final Logger logger = Logger.getLogger("CLASDecoder4U");
    private EvioSource reader;
    private HipoWriterSorted writer;
    private Bank config;
    private Bank helicity;
    private ArrayList<String> filenames;
    private TreeSet<HelicityState> helicities;
    private ProgressPrintout progress;
    private Double torus;
    private Double solenoid;
    private int runNumber;
    private int maxEvents;
    private int eventCounter;

    public CLASDecoder4U(OptionParser o) {
        super();
        init(o);
    }

    public CLASDecoder4U(String... filename) {
        super();
        OptionParser o = getOptionParser();
        o.parse(filename);
        init(o);
    }

    public static OptionParser getOptionParser() {
        OptionParser p = new OptionParser("decoder");
        p.setDescription("CLAS12 Data Decoder");
        p.addOption("-n", "-1", "maximum number of events to process");
        p.addOption("-c", "2", "compression type (0-NONE, 1-LZ4 Fast, 2-LZ4 Best, 3-GZIP)");
        p.addOption("-r", "-1","run number in the header bank (-1 means use CODA run)");
        p.addOption("-t", null,"torus current in the header bank (null means use RCDB)");
        p.addOption("-s", null,"solenoid current in the header bank (null means use RCDB)");
        p.addOption("-b", null, "run benchmark timers");
        p.addOption("-x", null,"CCDB timestamp (MM/DD/YYYY-HH:MM:SS)");
        p.addOption("-V","default","CCDB variation");
        p.addOption("-o",null,"output HIPO filename");
        p.setRequiresInputList(true);
        return p;
    }

    public void disableProgressMeter() { progress=null; }

    /**
     * @return whether there's another event to get 
     */    
    public boolean hasNext() {
        if (maxEvents > 0 && eventCounter > maxEvents) return false;
        if (reader != null && reader.hasEvent()) return true;
        return !filenames.isEmpty();
    }

    /**
     * @return the next event
     */
    public Event getNext() {
        EvioDataEvent evio = getNextEvioEvent();
        Event event = getDecodedEvent(evio, runNumber, eventCounter, torus, solenoid);
        if (writer != null) process(event);
        if (progress != null) progress.updateStatus();
        if (++eventCounter%25000 == 0) System.gc();
        return event;
    }

    /**
     * Close the writer, after writing HEL::flip banks.
     */
    public void close() {
        if (writer != null) {
            HelicitySequence.writeFlips(writer, helicities);
            writer.close();
        }
    }

    private EvioDataEvent getNextEvioEvent() {
        if (reader == null || !reader.hasEvent()) {
            reader = new EvioSource();
            reader.open(filenames.remove(0));
        }
        return (EvioDataEvent)reader.getNextEvent();
    }

    private void process(Event event) {
        event.read(config);
        event.read(helicity);
        helicities.add(HelicityState.createFromFadcBank(helicity, config, detectorDecoder.scalerManager));
        Event tag = createTaggedEvent(event, "RAW::epics","RAW::scaler","RUN::scaler","HEL::scaler");
        if (!tag.isEmpty())
            writer.addEvent(tag, 1);
        writer.addEvent(event,0);
    }

    private void init(OptionParser o){
        eventCounter = 0;
        writer = null;
        filenames = new ArrayList<>(o.getInputList());
        config  = new Bank(schemaFactory.getSchema("RUN::config"));
        helicity = new Bank(schemaFactory.getSchema("HEL::adc"));
        helicities = new TreeSet<>();
        progress = new ProgressPrintout();
        torus = o.getOption("-t").getValue()==null?null:o.getOption("-t").doubleValue();
        solenoid = o.getOption("-s").getValue()==null?null:o.getOption("-s").doubleValue();
        runNumber = o.getOption("-r").intValue();
        maxEvents = o.getOption("-n").intValue();
        if (o.getOption("-b").getValue() != null) {
            benchmark = true;
            Benchmark.getInstance().printTimer(10);
        }
        if (runNumber > 0) setRunNumber(runNumber, true);
        if (o.getOption("-x").getValue() != null)
            detectorDecoder.setTimestamp(o.getOption("-x").stringValue());
        if (o.getOption("-v").getValue() != null)
            detectorDecoder.setVariation(o.getOption("-v").stringValue());
        if (o.getOption("-o").getValue() != null) {
            writer = new HipoWriterSorted();
            writer.setCompressionType(o.getOption("-c").intValue());
            writer.getSchemaFactory().initFromDirectory(ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4"));
            writer.open(o.getOption("-o").stringValue());
        }
    }

    /**
     * The command-line "decoder" program.
     * @param args 
     */
    public static void main(String[] args) {

        // hijack arguments, when run from an IDE:
        if (System.console() == null && args.length == 0) {
            // delete output file, if necessary:
            File f = new File("tmp.hipo");
            if (f.exists()) f.delete();
            // setup decoder command-line options:
            args = new String[]{"-o","tmp.hipo",System.getenv("HOME")+"/data/clas_005038.evio.00001"};
            // try to find bankdefs:
            System.setProperty("CLAS12DIR", System.getenv("HOME")+"/sw/coatjava/dev/coatjava");
        }

        // parse command-line options:
        OptionParser opts = getOptionParser();
        opts.parse(args);

        // run the decoder:
        CLASDecoder4U decoder = new CLASDecoder4U(opts);
        while (decoder.hasNext()) decoder.getNext();
        decoder.close();
    }

}