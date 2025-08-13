package org.jlab.detector.decode;

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

public class CLASDecoder4b extends CLASDecoder4 {
  
    private OptionParser options;
    private HipoWriterSorted writer;
    private SchemaFactory schema;
    private Bank config;
    private Bank helicity;
    private TreeSet<HelicityState> helicities;
    private ProgressPrintout progress;
    private Double torus;
    private Double solenoid;
    private int runNumber;
    private int maxEvents;
    private int eventCounter;

    public static void main(String[] args) {
        OptionParser parser = CLASDecoder4.getOptionParser();
        parser.parse(args);
        CLASDecoder4b decoder = new CLASDecoder4b(parser);
        decoder.process();
    }

    public CLASDecoder4b(OptionParser o) {
        super();
        init(o);
    }

    private void init(OptionParser o) {
        options = o;
        eventCounter = 0;
        writer = null;
        schema = new SchemaFactory();
        schema.initFromDirectory(ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4"));
        config  = new Bank(schema.getSchema("RUN::config"));
        helicity = new Bank(schema.getSchema("HEL::adc"));
        helicities = new TreeSet<>();
        progress = new ProgressPrintout();
        progress.addBenchmarks();
        torus = o.getOption("-t").doubleValueOrDefault(null);
        solenoid = o.getOption("-s").doubleValueOrDefault(null);
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

    private void processNextEvent(EvioDataEvent evio) {
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
    
    private void process() {
        for (String inputFile : options.getInputList()) {
            EvioSource reader = new EvioSource();
            reader.open(inputFile);
            while (reader.hasEvent()) {
                processNextEvent((EvioDataEvent)reader.getNextEvent());
                if (maxEvents > 0 && eventCounter >= maxEvents) break;
            }
        }
        if (writer != null) {
            HelicitySequence.writeFlips(writer, helicities);
            writer.close();
        }
    }

}
