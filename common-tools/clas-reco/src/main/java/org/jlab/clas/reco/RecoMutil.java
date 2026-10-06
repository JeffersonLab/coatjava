package org.jlab.clas.reco;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.coda.jevio.EvioException;
import org.jlab.detector.decode.CLASDecoder;
import org.jlab.detector.decode.CLASDecoderPool;
import org.jlab.detector.serial.Occupancer;
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
    Occupancer occupancer = new Occupancer();
    CLASDecoderPool decoders = new CLASDecoderPool(64,"default",null);
    Map<String,ReconstructionEngine> engines = new LinkedHashMap<>();

    // Control flags:
    final Object serialLock = new Object();
    AtomicInteger serialTrigger = new AtomicInteger(0);
   
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
        synchronized (serialLock) {
            tag = serial.read(event.getHipoEvent());
        }
        if (!tag.isEmpty()) {
            if (serial.containsSerial(tag)) serialTrigger.incrementAndGet();
            taggedEvents.incrementAndGet();
        }
        if (benchmark != null) benchmark.pause(thread, "serial");
        return tag.isEmpty() ?
                new HipoDataEvent[]{event} :
                new HipoDataEvent[]{event, new HipoDataEvent(tag, fullSchema)};
    }

    @Override
    void decoderExit(int thread) {
        serialTrigger.incrementAndGet();
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
        if (benchmark != null) benchmark.resume("post");
        synchronized (serialLock) {
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
        occupancer.process(event);
        if (benchmark != null) benchmark.pause("write");
    }

    @Override
    void writerExit() {
        if (writer != null) {
            serial.closure(writer);
            writer.close();
        }
    }

    /**
     * Do some serial stuff, when triggered.
     */
    @Override
    void serial() {
        boolean first = true;
        while (true) {
            if (serialTrigger.get() > 0) {
                first = false;
                // sleep to collect more triggers:
                ReconUtil.sleep(1000);
                // get current number of triggers:
                int t = serialTrigger.get();
                // do the protected stuff:
                synchronized (serialLock) { serial.updateHelicitySequence(); }
                // remove the same number of triggers:
                for (int i=0; i<t; i++) serialTrigger.decrementAndGet();
            }
            else if (first) ReconUtil.sleep(1000);
            else ReconUtil.sleep(10000);
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
  
    String taskset(String t) {
        if (t.contains(",")) {
            int offset = t.endsWith("+") || t.endsWith("-") ? 1 : 0;
            String extra = t.endsWith("+") || t.endsWith("-") ? String.valueOf(t.charAt(t.length()-1)) : "";
            t = String.join(",",Arrays.stream(t.substring(0,t.length()-offset).split(","))
                    .mapToInt(s -> Integer.parseInt(s)).sorted().mapToObj(i -> String.valueOf(i)).toList())
                    + extra;
        }
        taskset = "";
        if (t.endsWith("+") || t.endsWith("-")) {
            taskset = String.valueOf(t.charAt(t.length()-1));
            if (t.contains(","))
                ReconUtil.taskset(0, Arrays.stream(t.split(","))
                .filter(s -> !s.contains("+") && !s.contains("-"))
                .mapToInt(s -> Integer.parseInt(s)).toArray()[0]);
            else if (taskset.equals("-"))
                ReconUtil.taskset(0, Integer.parseInt(String.valueOf(t.charAt(0))));
            else if (taskset.equals("+"))
                ReconUtil.taskset(0, 0);
        }
        return t;
    }
    
    /**
     * Initialize ReconMutil.
     * @param parser 
     */
    final void init(OptionParser parser) {
        taskset(parser.getOption("-t").stringValue());
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
        if (!parser.getOption("-b").isDefault())
            benchmark = new Benchmark("ReconMutil",BENCHMARK_NAMES);
    }

    /**
     * The command-line entry-point known as "recon-mutil".
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        OptionParser opt = new OptionParser("reco-mutil");
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
