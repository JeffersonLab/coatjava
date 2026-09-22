package org.jlab.clas.reco;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.coda.jevio.EvioException;
import org.jlab.detector.decode.CLASDecoder;
import org.jlab.detector.decode.CLASDecoderPool;
import org.jlab.detector.serial.SerialHoncho;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.utils.ClaraYaml;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.system.ClasUtilsFile;
import org.json.JSONObject;

/**
 *
 * @author baltzell
 */
public class Furniture extends Porch {

    // Static parameters:
    ClaraYaml yaml;
    OptionParser parser;
    double[] fields = null;

    HipoWriterSorted writer;

    List<Bank> schemaBankList;
    static final SchemaFactory fullSchema = new SchemaFactory();
    static { fullSchema.initFromDirectory(ClasUtilsFile.getResourceDir("CLAS12DIR","etc/bankdefs/hipo4")); }
    
    // Processors:
    SerialHoncho serial;
    CLASDecoderPool decoders = new CLASDecoderPool(64,"default",null);
    Map<String,ReconstructionEngine> engines = new LinkedHashMap<>();

    // Control flags:
    final AtomicBoolean serialPause = new AtomicBoolean(true);
    
    final int helicityClock = 30;  // Hz
    final int triggerRate = 25000; // Hz
    final int minReload = 2 * triggerRate / helicityClock;
    int serials = 0;
    int reloads = 0;
    int reload = minReload;
    
    public Furniture(OptionParser parser) {
        init(parser);
    }

    @Override
    Object open(String filename) {
        if (reader != null && reader instanceof EvioSource)
            ((EvioSource)reader).close(); 
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
    
    @Override
    Object read() {
        benchmark.resume("read");
        Object o = null;
        if (reader instanceof EvioSource evio) {
            try { o = evio.getEventBuffer(++fileEvents, true); }
            catch (EvioException ex) {
                failEvents++;
                ex.printStackTrace();
            }
        }
        else {
            Event event = new Event();
            o = ((HipoReader)reader).getEvent(event, fileEvents++);
        }
        benchmark.resume("pause");
        return o;
    }

    @Override
    void readerExit() {
        if (reader != null && reader instanceof EvioSource)
            ((EvioSource)reader).close();
    }

    /**
     * 
     * @param thread
     * @param input
     * @return 
     */
    @Override
    HipoDataEvent[] decode(int thread, Object input) {
        HipoDataEvent event = input instanceof ByteBuffer
                ? decode(thread, (ByteBuffer)input)
                : new HipoDataEvent(((Event)input), fullSchema);
        benchmark.resume(thread, "serial");
        Event tag;
        synchronized (serialPause) {
            tag = serial.read(event.getHipoEvent());
        }
        if (thread == 0 && ++serials > reload) {
            updateHelicity();
            serials = 0;
            reload += 10 * reloads * minReload;
            reloads++;
        }
        if (!tag.isEmpty()) taggedEvents.incrementAndGet();
        benchmark.pause(thread, "serial");
        return tag.isEmpty() ?
                new HipoDataEvent[]{event} :
                new HipoDataEvent[]{event, new HipoDataEvent(tag, fullSchema)};
    }

    @Override
    void decoderExit(int thread) {
        if (thread == 0) {
            while (decoThreads.size() > 1) ReconUtil.sleep(100);
            updateHelicity();
        }
    }

    @Override
    void process(int thread, HipoDataEvent event) {
        for (Map.Entry<String,ReconstructionEngine> engine : engines.entrySet()) {
            benchmark.resume(thread, engine.getKey());
            try { engine.getValue().processDataEvent(event); }
            catch (Exception ex) { ex.printStackTrace(); }
            benchmark.pause(thread, engine.getKey());
        }
    }

    @Override
    void write(HipoDataEvent dataEvent) {
        Event event = dataEvent.getHipoEvent();
        while (serialPause.get()) ReconUtil.sleep (100);
        benchmark.resume("post");
        synchronized (serialPause) {
            serial.process(dataEvent.getHipoEvent());
        }
        benchmark.pause("post");
        benchmark.resume("write");
        if (writer != null) {
            if (event.getEventTag() > 0 || schemaBankList.isEmpty())
                writer.addEvent(event, event.getEventTag());
            else
                writer.addEvent(event.reduceEvent(schemaBankList), event.getEventTag());
        }
        benchmark.pause("write");
    }

    @Override
    void writerExit() {
        if (writer != null) {
            serial.closure(writer);
            writer.close();
        }
    }

    HipoWriterSorted openWriter(String filename) {
        writer = new HipoWriterSorted();
        writer.setCompressionType(2);
        SchemaFactory s = ReconUtil.getSchemaFactory(parser.getOption("-S"), yaml);
        writer.getSchemaFactory().copy(s);
        schemaBankList = ReconUtil.getBankList(s, yaml);
        writer.open(filename);
        return writer;
    }

    void updateHelicity() {
        serialPause.set(true);
        ReconUtil.sleep(1000);
        synchronized (serialPause) {
            serial.updateHelicitySequence();
        }
        serialPause.set(false);
    }

    /**
     * Decode an event.
     * @param bytes the EVIO byte buffer
     * @return decoded event
     */
    HipoDataEvent decode(int thread, ByteBuffer bytes) {
        benchmark.resume(thread, "evio");
        EvioDataEvent evio = new EvioDataEvent(bytes.array(), ByteOrder.LITTLE_ENDIAN);
        benchmark.pause(thread, "evio");
        benchmark.resume(thread, "deco");
        CLASDecoder d = decoders.poll();
        HipoDataEvent hipo = fields == null ?
                d.getDecodedDataEvent(evio) :
                d.getDecodedDataEvent(evio, fields[0], fields[1]);
        decoders.offer(d);
        benchmark.pause(thread, "deco");
        return hipo;
    }
  
    /**
     * Initialize ReconMutil.
     * @param parser 
     */
    final void init(OptionParser parser) {
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
                ReconUtil.addEngine(engines, line.split(" ")[0], line.split(" ")[1], null);
        }
        if (!parser.getOption("-B").isDefault()) {
            ReconstructionEngine bg = ReconUtil.addEngine(engines, "BG", "org.jlab.service.bg.BackgroundEngine", null);
            bg.engineConfigMap.put("filename",parser.getOption("-B").stringValue());
        }
        if (!parser.getOption("-f").isDefault()) {
            try {
                fields = Arrays.stream(parser.getOption("-f").stringValue()
                .split(",")).mapToDouble(s -> Double.parseDouble(s)).toArray();
            }
            catch (Exception e) {
                Logger.getLogger(ReconMutil.class.getName()).log(Level.SEVERE, () -> "invalid field option:  -f "+parser.getOption("-f").stringValue());
                System.exit(22);
            }
        }
    }

    /**
     * The command-line entry-point known as "recon-mutil".
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        OptionParser o = ReconUtil.getParser();
        o.removeOption("-i");
        o.removeOption("-o");
        o.removeOption("-c");
        o.removeOption("-P");
        o.addOption("-t","4","number of threads");
        o.addOption("-o", null, "output file name");
        o.addOption("-c","2","comma-separated engine list");
        o.addOption("-f",null,"field scales for torus and solenoid, comma-separated (T,S)");
        o.setRequiresInputList(true);
        o.parse(args);
        Furniture f = new Furniture(o);
        if (!o.getOption("-o").isDefault())
            f.openWriter(o.getOption("-o").stringValue());
        f.launch(Arrays.stream(o.getOption("-t").stringValue().split(",")).mapToInt(Integer::parseInt).toArray(), 
                o.getOption("-o").stringValue(),
                o.getInputList().stream().toArray(String[]::new));
    }
}
