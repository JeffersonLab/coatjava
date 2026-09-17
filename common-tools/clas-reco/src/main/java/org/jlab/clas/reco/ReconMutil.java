package org.jlab.clas.reco;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.jlab.detector.serial.SerialHoncho;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.utils.ClaraYaml;
import org.jlab.utils.benchmark.Benchmark;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.system.ClasUtilsFile;
import org.json.JSONObject;

/**
 * 
 * @author baltzell
 */
final class ReconMutil extends Parralator {

    ClaraYaml yaml;
    OptionParser parser;
    HipoWriterSorted writer;
    List<Bank> schemaBankList;
    SerialHoncho serial;
    Map<String,ReconstructionEngine> engines = new LinkedHashMap<>();
    final Object serialLock = new Object();
    static final SchemaFactory fullSchema = new SchemaFactory();
    static { fullSchema.initFromDirectory(ClasUtilsFile.getResourceDir("CLAS12DIR","etc/bankdefs/hipo4")); }
    int serials;
    int minReload = 5000;
    int reload = minReload;
    int reloads = 0;

    ReconMutil(OptionParser parser) {
        init(parser);
    }

    @Override
    HipoDataEvent[] decode(int thread, Object o) {
        HipoDataEvent event = o instanceof ByteBuffer
                ? Parralator.decode(thread, (ByteBuffer)o)
                : new HipoDataEvent(((Event)o), fullSchema);
        Event tag;
        Benchmark.getInstance().resume(thread, "serial");
        synchronized (serialLock) {
            tag = serial.read(event.getHipoEvent());
        }
        HipoDataEvent[] ret = new HipoDataEvent[1+(tag.isEmpty()?0:1)];
        ret[0] = event;
        if (!tag.isEmpty()) {
            ret[1] = new HipoDataEvent(tag, fullSchema);
            taggedEvents.incrementAndGet();
        }
        if (thread == 0 && ++serials > reload) {
            updateHelicity();
            serials = 0;
            reload += 10 * reloads * minReload;
            reloads++;
        }
        Benchmark.getInstance().pause(thread, "serial");
        return ret;
    }

    @Override
    void declosure() {
        updateHelicity();
    }

    @Override
    void process(int thread, HipoDataEvent event) {
        for (Map.Entry<String,ReconstructionEngine> engine : engines.entrySet()) {
            Benchmark.getInstance().resume(thread, engine.getKey());
            try { engine.getValue().processDataEvent(event); }
            catch (Exception ex) { ex.printStackTrace(); }
            Benchmark.getInstance().pause(thread, engine.getKey());
        }
    }

    @Override
    void write(Event event) {
        Benchmark.getInstance().resume("post");
        synchronized (serialLock) {
            serial.process(event);
        }
        Benchmark.getInstance().pause("post");
        Benchmark.getInstance().resume("write");
        if (writer != null) {
            if (event.getEventTag() > 0 || schemaBankList.isEmpty())
                writer.addEvent(event, event.getEventTag());
            else
                writer.addEvent(event.reduceEvent(schemaBankList), event.getEventTag());
        }
        Benchmark.getInstance().pause("write");
        progress.updateStatus();
        writeEvents++;
    }

    /**
     * Close the output file.
     */
    @Override
    void close() {
        if (writer != null) {
            serial.closure(writer);
            writer.close();
        }
        super.close();
    }


    /**
     * Open a new input HIPO/EVIO event file.
     * @param filename 
     */
    @Override
    Object openReader(String filename) {
        fileEvents = 0;
        if (filename.endsWith(".hipo")) {
            reader = new HipoReader();
            ((HipoReader)reader).open(filename);
            maxFileEvents = ((HipoReader)reader).getEventCount();
        }
        else {
            reader = new EvioSource();
            ((EvioSource)reader).open(filename);
            maxFileEvents = ((EvioSource)reader).getEventCount();
        }
        return reader;
    }

    /**
     * Open a new writer and initialize its schema.
     * @param filename output filename
     * @param yaml the configuration
     */
    HipoWriterSorted openWriter(String filename) {
        writer = new HipoWriterSorted();
        writer.setCompressionType(2);
        SchemaFactory s = ReconUtil.getSchemaFactory(parser, yaml);
        writer.getSchemaFactory().copy(s);
        schemaBankList = ReconUtil.getBankList(s, yaml);
        writer.open(filename);
        return writer;
    }
 
    void updateHelicity() {
        paused.set(true);
        ReconUtil.sleep(1000);
        synchronized (serialLock) {
            serial.updateHelicitySequence();
        }
        paused.set(false);
    }

    /**
     * Initialize ReconMutil.
     * @param parser 
     */
    void init(OptionParser parser) {
        this.parser = parser;
        parser.syncLogLevel(Logger.getLogger(ReconMutil.class.getPackage().getName()));
        maxEvents = parser.getOption("-n").intValue();
        skipEvents = parser.getOption("-s").intValue();
        serial = new SerialHoncho(fullSchema);
        engines = new LinkedHashMap<>();
        if (!parser.getOption("-y").isDefault()) {
            yaml = new ClaraYaml(parser.getOption("-y").stringValue());
            for (JSONObject service : yaml.services()) {
                JSONObject cfg = yaml.filter(service.getString("name"));
                if (cfg.length() > 0) ReconUtil.addEngine(engines, service.getString("name"), service.getString("class"), cfg);
                else ReconUtil.addEngine(engines, service.getString("name"), service.getString("class"), null);
            }
        }
        else if (!parser.getOption("-c").isDefault()) {
            for (String clazz : parser.getOption("-c").stringValue().split(","))
                ReconUtil.addEngine(engines, null, clazz, null);
        }
        else {
            for (String line : ReconUtil.readResourceLines("org/jlab/clas/reco/services.txt"))
                ReconUtil.addEngine(engines, line.split(" ")[0],line.split(" ")[1],null);
        }
        if (!parser.getOption("-B").isDefault()) {
            ReconstructionEngine bg = ReconUtil.addEngine(engines, "BG","org.jlab.service.bg.BackgroundEngine",null);
            bg.engineConfigMap.put("filename",parser.getOption("-B").stringValue());
        }
    }

    /**
     * The command-line entry-point known as "recon-mutil".
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        OptionParser o = EngineProcessor.getParser();
        o.removeOption("-i");
        o.removeOption("-o");
        o.removeOption("-c");
        o.addOption("-t","4","number of threads");
        o.addOption("-o", null, "output file name");
        o.addOption("-c","2","comma-separated engine list");
        o.setRequiresInputList(true);
        o.parse(args);
        ReconMutil r = new ReconMutil(o);
        if (!o.getOption("-o").isDefault())
            r.openWriter(o.getOption("-o").stringValue());
        r.launch(Arrays.stream(o.getOption("-t").stringValue().split(",")).mapToInt(Integer::parseInt).toArray(), 
                o.getInputList().stream().toArray(String[]::new));
    }

}
