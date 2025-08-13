package org.jlab.detector.decode;

import java.io.File;
import java.util.ArrayList;
import java.util.TreeSet;
import org.jlab.detector.helicity.HelicitySequence;
import org.jlab.detector.helicity.HelicityState;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioSource;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.utils.benchmark.Benchmark;
import org.jlab.utils.benchmark.ProgressPrintout;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.system.ClasUtilsFile;

/**
 * Cleanup wrapper of CLASDecoder
 */
public class CLASDecoder4b extends CLASDecoder4 {
  
    private EvioSource reader;
    private HipoWriterSorted writer;
    private SchemaFactory schema;
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

    public CLASDecoder4b(OptionParser o) {
        super();
        init(o);
    }

    private void init(OptionParser o){
        eventCounter = 0;
        writer = null;
        filenames = new ArrayList<>(o.getInputList());
        schema = new SchemaFactory();
        schema.initFromDirectory(ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4"));
        config  = new Bank(schema.getSchema("RUN::config"));
        helicity = new Bank(schema.getSchema("HEL::adc"));
        helicities = new TreeSet<>();
        progress = new ProgressPrintout();
        progress.addBenchmarks();
        torus = o.getOption("-t").doubleValueOrNull();
        solenoid = o.getOption("-s").doubleValueOrNull();
        runNumber = o.getOption("-r").intValue();
        maxEvents = o.getOption("-n").intValue();
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

    public boolean hasNext() {
        if (maxEvents > 0 && eventCounter > maxEvents) return false;
        if (reader != null && reader.hasEvent()) return true;
        return !filenames.isEmpty();
    }

    public void processNextEvent() {
        if (reader == null || !reader.hasEvent()) {
            reader = new EvioSource();
            reader.open(filenames.remove(0));
        }
        EvioDataEvent evio = (EvioDataEvent)reader.getNextEvent();
        Event event = super.getDecodedEvent(evio, runNumber, eventCounter, torus, solenoid);
        Benchmark.getInstance().resume("EVENT");
        event.read(config);
        event.read(helicity);
        helicities.add(HelicityState.createFromFadcBank(helicity, config, detectorDecoder.scalerManager));
        Event taggedEvent = super.createTaggedEvent(event, "RAW::epics","RAW::scaler","RUN::scaler","HEL::scaler");
        if (writer != null) {
            if (!taggedEvent.isEmpty())
                writer.addEvent(taggedEvent, 1);
            writer.addEvent(event,0);
        }
        progress.updateStatus();
        if (++eventCounter%25000 == 0) System.gc();
        Benchmark.getInstance().pause("EVENT");
    }

    public void close() {
        if (writer != null) {
            HelicitySequence.writeFlips(writer, helicities);
            writer.close();
        }
    }

    public static void main(String[] args) {
        // hijack arguments, when run from an IDE:
        if (System.console() == null && args.length == 0) {
            File f = new File("x.hipo");
            if (f.exists()) f.delete();
            args = new String[]{"-o","x.hipo",System.getenv("HOME")+"/data/clas_005038.evio.00001"};
            System.setProperty("CLAS12DIR", System.getenv("HOME")+"/sw/coatjava/dev/coatjava");
        }
        OptionParser opts = CLASDecoder4.getOptionParser();
        opts.parse(args);
        CLASDecoder4b decoder = new CLASDecoder4b(opts);
        while (decoder.hasNext()) decoder.processNextEvent();
        decoder.close();
    }

}
