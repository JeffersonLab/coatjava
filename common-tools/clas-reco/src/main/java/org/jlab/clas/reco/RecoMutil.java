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
import org.jlab.utils.benchmark.Benchmark;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.options.OptionValue;
import org.jlab.utils.system.ClasUtilsFile;
import org.json.JSONObject;

public class RecoMutil extends Porch {

    // Static parameters:
    ClaraYaml yaml;
    double[] fields = null;

    // I/O
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
   
    // FIXME: stuff for deciding when to reload helicities
    final int helicityClock = 30;  // Hz
    final int triggerRate = 25000; // Hz
    final int minReload = 2 * triggerRate / helicityClock;
    int serials = 0;
    int reloads = 0;
    int reload = minReload;
    
    public RecoMutil(OptionParser parser) {
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
        if (benchmark != null) benchmark.resume("read");
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
        if (benchmark != null) benchmark.resume("pause");
        return o;
    }

    @Override
    void readerExit() {
        if (reader != null && reader instanceof EvioSource)
            ((EvioSource)reader).close();
    }

    @Override
    HipoDataEvent[] decode(int thread, Object input) {
        HipoDataEvent event = input instanceof ByteBuffer
                ? decode(thread, (ByteBuffer)input)
                : new HipoDataEvent(((Event)input), fullSchema);
        if (benchmark != null) benchmark.resume(thread, "serial");
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
        if (benchmark != null) benchmark.pause(thread, "serial");
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
            if (benchmark != null) benchmark.resume(thread, engine.getKey());
            try { engine.getValue().processDataEvent(event); }
            catch (Exception ex) { ex.printStackTrace(); }
            if (benchmark != null) benchmark.pause(thread, engine.getKey());
        }
    }

    @Override
    void write(HipoDataEvent dataEvent) {
        Event event = dataEvent.getHipoEvent();
        while (serialPause.get()) ReconUtil.sleep (100);
        if (benchmark != null) benchmark.resume("post");
        synchronized (serialPause) {
            serial.process(dataEvent.getHipoEvent());
        }
        if (benchmark != null) {
            benchmark.pause("post");
            benchmark.resume("write");
        }
        if (writer != null) {
            if (event.getEventTag() > 0 || schemaBankList.isEmpty())
                writer.addEvent(event, event.getEventTag());
            else
                writer.addEvent(event.reduceEvent(schemaBankList), event.getEventTag());
        }
        if (benchmark != null) benchmark.pause("write");
    }

    @Override
    void writerExit() {
        if (writer != null) {
            serial.closure(writer);
            writer.close();
        }
    }

    HipoWriterSorted openWriter(OptionValue schema, String filename) {
        writer = new HipoWriterSorted();
        writer.setCompressionType(2);
        SchemaFactory s = ReconUtil.getSchemaFactory(schema, yaml);
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

    HipoDataEvent decode(int thread, ByteBuffer bytes) {
        if (benchmark != null) benchmark.resume(thread, "evio");
        EvioDataEvent evio = new EvioDataEvent(bytes.array(), ByteOrder.LITTLE_ENDIAN);
        if (benchmark != null) {
            benchmark.pause(thread, "evio");
            benchmark.resume(thread, "deco");
        }
        CLASDecoder d = decoders.poll();
        HipoDataEvent hipo = fields == null ?
                d.getDecodedDataEvent(evio) :
                d.getDecodedDataEvent(evio, fields[0], fields[1]);
        decoders.offer(d);
        if (benchmark != null) benchmark.pause(thread, "deco");
        return hipo;
    }
  
    final void init(OptionParser opt) {
        opt.syncLogLevel(Logger.getLogger(RecoMutil.class.getPackage().getName()));
        maxEvents = opt.getOption("-n").intValue();
        skipEvents = opt.getOption("-s").intValue();
        serial = new SerialHoncho(fullSchema);
        engines = new LinkedHashMap<>();
        if (!opt.getOption("-y").isDefault()) {
            yaml = new ClaraYaml(opt.getOption("-y").stringValue());
            for (JSONObject service : yaml.services()) {
                JSONObject cfg = yaml.filter(service.getString("name"));
                if (cfg.length() > 0) ReconUtil.addEngine(engines, service.getString("name"), service.getString("class"), cfg);
                else ReconUtil.addEngine(engines, service.getString("name"), service.getString("class"), null);
            }
        }
        else if (!opt.getOption("-c").isDefault()) {
            for (String clazz : opt.getOption("-c").stringValue().split(","))
                ReconUtil.addEngine(engines, null, clazz, null);
        }
        else {
            for (String line : ReconUtil.readResourceLines("org/jlab/clas/reco/services.txt"))
                ReconUtil.addEngine(engines, line.split(" ")[0], line.split(" ")[1], null);
        }
        if (!opt.getOption("-B").isDefault()) {
            ReconstructionEngine bg = ReconUtil.addEngine(engines, "BG", "org.jlab.service.bg.BackgroundEngine", null);
            bg.engineConfigMap.put("filename",opt.getOption("-B").stringValue());
        }
        if (!opt.getOption("-f").isDefault()) {
            try {
                fields = Arrays.stream(opt.getOption("-f").stringValue()
                .split(",")).mapToDouble(s -> Double.parseDouble(s)).toArray();
            }
            catch (Exception e) {
                Logger.getLogger(RecoMutil.class.getName()).log(Level.SEVERE, () -> "invalid field option:  -f "+opt.getOption("-f").stringValue());
                System.exit(22);
            }
        }
        if (!opt.getOption("-b").isDefault())
            benchmark = new Benchmark(BENCHMARK_NAMES);
    }

    /**
     * The command-line entry-point known as "recon-mutil".
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        OptionParser opt = new OptionParser("recon-util");
        opt.addOption("-t","4","number of threads");
        opt.addOption("-s","0","number of events to skip");
        opt.addOption("-n","0","number of events to process");
        opt.addOption("-y","0","yaml file");
        opt.addOption("-u","true","update dictionary from writer");
        opt.addOption("-o",null,"output file name");
        opt.addOption("-S",null,"schema directory");
        opt.addOption("-B",null,"background files, comma-separated");
        opt.addOption("-c",null,"comma-separated engine list");
        opt.addOption("-f",null,"field scales for torus and solenoid, comma-separated (T,S)");
        opt.addOption("-b","false","enable benchmarking");
        opt.setRequiresInputList(true);
        opt.parse(args);

        RecoMutil f = new RecoMutil(opt);

        if (!opt.getOption("-o").isDefault())
            f.openWriter(opt.getOption("-S"), opt.getOption("-o").stringValue());
        
        f.launch(Arrays.stream(opt.getOption("-t").stringValue().split(",")).mapToInt(Integer::parseInt).toArray(), 
                opt.getInputList().stream().toArray(String[]::new));
    }

}
