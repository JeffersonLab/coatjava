package org.jlab.clas.reco;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.jlab.utils.benchmark.ProgressPrintout;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.system.ClasUtilsFile;
import org.json.JSONObject;

/**
 * 
 * @author baltzell
 */
final class ReconMutil {

    static final String[] BENCHMARK_NAMES = new String[]{"evio","deco","serial","post","write"};
    
    // Performance parameters:
    final int BENCH_SECONDS = 30;
    final int EVENTS_PER_CHUNK = 20;

    // Static parameters:
    int maxEvents;
    int skipEvents;
    ClaraYaml yaml;
    OptionParser parser;
    double[] fields = null;

    // File I/O:
    Object reader;
    HipoWriterSorted writer;
    List<Bank> schemaBankList;
    static final SchemaFactory fullSchema = new SchemaFactory();
    static { fullSchema.initFromDirectory(ClasUtilsFile.getResourceDir("CLAS12DIR","etc/bankdefs/hipo4")); }
    
    // Processors:
    SerialHoncho serial;
    CLASDecoderPool decoders = new CLASDecoderPool(64,"default",null);
    Map<String,ReconstructionEngine> engines = new LinkedHashMap<>();

    // Threads:
    CompletableFuture readerThread;
    CompletableFuture writerThread;
    ConcurrentLinkedQueue<CompletableFuture> decoThreads = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<CompletableFuture> procThreads = new ConcurrentLinkedQueue<>();

    // Queues:
    ConcurrentLinkedQueue<List<Object>> decoQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<List<HipoDataEvent>> procQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<List<Event>> writeQueue = new ConcurrentLinkedQueue<>();

    // Progress counters:
    volatile int readEvents;
    volatile int failEvents;
    volatile int fileEvents;
    volatile int maxFileEvents;
    volatile int writeEvents;
    volatile AtomicInteger taggedEvents = new AtomicInteger();
    volatile ProgressPrintout progress = new ProgressPrintout();
    volatile Benchmark benchmark = null;
    TimerTask statsShow = new TimerTask() { @Override public void run() { show(); } };

    // Control flags:
    final AtomicBoolean serialPause = new AtomicBoolean(true);
    
    ReconMutil(OptionParser parser) {
        init(parser);
    }

    /**
     * The thread launcher and collector.
     * @param threads number of threads
     * @param output name of output file to write
     * @param input names of input files to read
     */
    void launch(int[] threads, String output, String... input) {

        reset();

        System.out.println(String.format("recon-mutil::  spawning 2*%d+2 threads",threads[0]));
        
        // spawn all the threads:
        readerThread = CompletableFuture.runAsync(() -> { reader(threads[0], input); });
        writerThread = CompletableFuture.runAsync(() -> { writer(output); });
        for (int i=0; i<threads[0]; i++) {
            final int j = i;
            ReconUtil.addAndRemove(decoThreads, CompletableFuture.runAsync(() -> { decoder(j); }));
            ReconUtil.addAndRemove(procThreads, CompletableFuture.runAsync(() -> { processor(j); }));
        }
       
        // start a period status printout:
        ReconUtil.runPeriodic(10, statsShow);
        
        // perform scaling test:
        CompletableFuture rethreadThread = threads.length < 2 ? null :
            CompletableFuture.runAsync(() -> { rethreader(BENCH_SECONDS,threads); });

        // wait for finish:
        writerThread.join();
        if (rethreadThread != null) rethreadThread.join();
    }

    /**
     * The reader thread.
     * @param input input filenames 
     */
    void reader(int threads, String... input) {

        // convert input filenames to a list:
        List<String> inputs = new ArrayList<>(Arrays.asList(input));

        // initialize the event chunk:
        List<Object> output = new ArrayList<>(EVENTS_PER_CHUNK);

        // loop over input events:
        while ( (maxEvents < 1 || writeEvents < maxEvents+taggedEvents.get()) &&
                (maxFileEvents < 1 || fileEvents < maxFileEvents) ) {

            if (reader != null) {

                // sleep instead of overfilling the read queue (100K events, ~2GB):
                if (readEvents > 1e5) ReconUtil.sleep(1000);

                // read next event into chunk, and fill queue if chunk full:
                else output = read(output);
            }

            // open the next input file:
            else if (!inputs.isEmpty()) open(inputs.removeFirst());

            // no more events to read:
            else break;
        }

        // write leftover, partial chunk:
        if (!output.isEmpty()) {
            readEvents += output.size();
            decoQueue.offer(output);
        }

        if (reader instanceof EvioSource evio) evio.close();
    }

    /**
     * The decoder thread.
     * @param thread thread number
     */
    void decoder(int thread) {
        final int helicityClock = 30;  // Hz
        final int triggerRate = 25000; // Hz
        final int minReload = 2 * triggerRate / helicityClock;
        int serials = 0;
        int reloads = 0;
        int reload = minReload;
        while (true) {
            List<Object> input = decoQueue.poll();
            if (input == null) {
                if (decoQueue.isEmpty() && readerThread.isDone() && decoQueue.isEmpty())
                    break;
                ReconUtil.sleep(100);
            }
            else {
                List<HipoDataEvent> output = new ArrayList<>(input.size());
                for (int i=0; i<input.size(); i++) {
                    HipoDataEvent event = input.get(i) instanceof ByteBuffer
                            ? decode(thread, (ByteBuffer)input.get(i))
                            : new HipoDataEvent(((Event)input.get(i)), fullSchema);
                    output.add(event);
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
                    if (!tag.isEmpty()) {
                        output.add(new HipoDataEvent(tag, fullSchema));
                        taggedEvents.incrementAndGet();
                    }
                    if (benchmark != null) benchmark.pause(thread, "serial");
                }
                procQueue.offer(output);
            }
        }
        if (thread == 0) updateHelicity();
    }

    /**
     * The data processor thread.
     * @param thread thread number 
     */
    void processor(int thread) {
        while (true) {
            if (maxEvents > 0 && writeEvents > maxEvents+taggedEvents.get()) {
                stopProcessing();
                break;
            }
            List<HipoDataEvent> input = procQueue.poll();
            if (input == null) {
                if (procQueue.isEmpty() && decoThreads.isEmpty() && procQueue.isEmpty()) {
                    if (writeEvents+skipEvents+failEvents >= readEvents+taggedEvents.get())
                        break;
                }
                ReconUtil.sleep(100);
            }
            else {
                List<Event> output = new ArrayList<>(input.size());
                for (int i=0; i<input.size(); i++) {
                    if (input.get(i).getHipoEvent().getEventTag() == 0) {
                        for (Map.Entry<String,ReconstructionEngine> engine : engines.entrySet()) {
                            if (benchmark != null) benchmark.resume(thread, engine.getKey());
                            try { engine.getValue().processDataEvent(input.get(i)); }
                            catch (Exception ex) { ex.printStackTrace(); }
                            if (benchmark != null) benchmark.pause(thread, engine.getKey());
                        }
                    }
                    output.add(input.get(i).getHipoEvent());
                }
                writeQueue.offer(output);
            }
        }
    }

    /**
     * The writer thread.
     * @param output output filename
     */
    void writer(String output) {
        if (output != null) writer = open(output, yaml);
        while (true) {
            List<Event> e = writeQueue.poll();
            if (e == null) {
                if (decoThreads.isEmpty() && procThreads.isEmpty() && procQueue.isEmpty() && writeQueue.isEmpty())
                    break;
                ReconUtil.sleep(1000);
            }
            else {
                for (int i=0; i<e.size(); i++) {
                    while (serialPause.get()) ReconUtil.sleep (100);
                    if (benchmark != null) benchmark.resume("post");
                    synchronized (serialPause) {
                        serial.process(e.get(i));
                    }
                    if (benchmark != null) {
                        benchmark.pause("post");
                        benchmark.resume("write");
                    }
                    if (writer != null) {
                        if (e.get(i).getEventTag() > 0 || schemaBankList.isEmpty())
                            writer.addEvent(e.get(i), e.get(i).getEventTag());
                        else
                            writer.addEvent(e.get(i).reduceEvent(schemaBankList), e.get(i).getEventTag());
                    }
                    if (benchmark != null) benchmark.pause("write");
                    progress.updateStatus();
                }
                writeEvents += e.size();
            }
        }
        close();
    }

    /**
     * The rethreader thread.
     * @param seconds delay before switching to next thread count
     * @param threads thread counts to use 
     */
    void rethreader(int seconds, int... threads) {
        System.out.println("recon-mutil::  ~~~~~~~~~ rethreading initiated ~~~~~~~~~");
        //progress.setInterval(-1);
        Map<Integer,Benchmark> benches = new LinkedHashMap<>();
        for (int i=threads[0]; i<64; i++) {
            final int j = i;
            ReconUtil.addAndRemove(decoThreads, CompletableFuture.runAsync(() -> { decoder(j); }));
        }
        while (writeEvents < 100 || !ReconUtil.isDone(decoThreads))
            ReconUtil.sleep(1000);
        for (CompletableFuture cf : procThreads) cf.cancel(true);
        System.out.println("recon-mutil::  ~~~~~~~~~ rethreading primed ~~~~~~~~~");
        for (int thread : threads) {
            ReconUtil.sleep(1000);
            benchmark = new Benchmark(BENCHMARK_NAMES);
            for (int j=0; j<thread; j++) {
                final int k = j;
                ReconUtil.addAndRemove(procThreads, CompletableFuture.runAsync(() -> { processor(k); }));
            }
            ReconUtil.sleep(seconds*1000);
            for (CompletableFuture cf : procThreads) cf.cancel(true);
            System.out.println(String.format("\nrecon-mutil:: ~~~~~~~~~ rethreading count: %d ~~~~~~~~~\n",thread));
            System.out.println(progress.getUpdateString());
            System.out.println(benchmark);
            benches.put(thread, benchmark);
        }
        String csv = ReconUtil.toCSV(benches);
        System.out.println(csv);
        ReconUtil.writeFile("scaling.txt", csv);
        stopProcessing();
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
  
    /**
     * Open a new input HIPO/EVIO event file.
     * @param filename 
     */
    void open(String filename) {
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
    }

    /**
     * Open a new writer and initialize its schema.
     * @param filename output filename
     * @param yaml the configuration
     */
    HipoWriterSorted open(String filename, ClaraYaml yaml) {
        HipoWriterSorted writer = new HipoWriterSorted();
        writer.setCompressionType(2);
        SchemaFactory s = ReconUtil.getSchemaFactory(parser.getOption("-S"), yaml);
        writer.getSchemaFactory().copy(s);
        schemaBankList = ReconUtil.getBankList(s, yaml);
        writer.open(filename);
        return writer;
    }
 
    /**
     * Read the next event into the chunk, and, if it's full, queue the chunk
     * and make a new one.
     * @param chunk
     * @return modified chunk 
     */
    List<Object> read(List<Object> chunk) {
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
        if (o != null && (skipEvents < 1 || readEvents > skipEvents)) {
            chunk.add(o);
            if (chunk.size() >= EVENTS_PER_CHUNK) {
                decoQueue.offer(chunk);
                readEvents += chunk.size();
                chunk = new ArrayList<>(EVENTS_PER_CHUNK);
            }
        }
        if (benchmark != null) benchmark.pause("read");
        return chunk;
    }

    /**
     * Close the output file.
     */
    void close() {
        serial.closure(writer);
        writer.close();
        if (parser.getOption("-t").stringValue().split(",").length == 1)
            if (benchmark != null) System.out.println(benchmark);
        System.out.println(String.format("recon-mutil :: read/write/tagged/diff = %d/%d/%d/%d",
                readEvents, writeEvents, taggedEvents.get(), writeEvents-readEvents-taggedEvents.get()));
    }

    /**
     * Forcefully shutdown all threads, close files, and reset queues and counters.
     */
    void reset() {
        serialPause.set(true);
        for (CompletableFuture cf : decoThreads) cf.cancel(true);
        for (CompletableFuture cf : procThreads) cf.cancel(true);
        if (readerThread != null) readerThread.cancel(true);
        if (writerThread != null) {
            writerThread.cancel(true);
            close();
        }
        decoQueue = new ConcurrentLinkedQueue<>();
        writeQueue = new ConcurrentLinkedQueue<>();
        procThreads = new ConcurrentLinkedQueue();
        readEvents = 0;
        writeEvents = 0;
        failEvents = 0;
        taggedEvents.set(0);
    }

    /**
     * Stop processing, somewhat cleanly.
     */
    void stopProcessing() {
        if (readerThread != null) readerThread.cancel(true);
        for (CompletableFuture cf : decoThreads) cf.cancel(true);
        for (CompletableFuture cf : procThreads) cf.cancel(true);
        decoQueue.clear();
        procQueue.clear();
        writeQueue.clear();
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
        if (!parser.getOption("-b").isDefault() || parser.getOption("-t").stringValue().split(",").length > 1)
            benchmark = new Benchmark(BENCHMARK_NAMES);
    }

    /**
     * Print the thread, queue, and event states.
     */
    void show() {
        String s1 = String.format("threads(r/d/p/w)=(%b/%b:%d/%b:%d/%b)",
                !readerThread.isDone(), !ReconUtil.isDone(decoThreads), decoThreads.size(), !ReconUtil.isDone(procThreads), procThreads.size(), !writerThread.isDone());
        String s2 = String.format(" queues(d/p/w)=(%d/%d/%d)",
                decoQueue.size()*EVENTS_PER_CHUNK, procQueue.size()*EVENTS_PER_CHUNK, writeQueue.size()*EVENTS_PER_CHUNK);
        String s3 = String.format(" events(r/w/t/f)=(%d/%d/%d/%d)",
                readEvents, writeEvents, taggedEvents.get(), failEvents);
        Logger.getLogger(ReconMutil.class.getName()).log(Level.CONFIG, () -> s1+" "+s2+" "+s3);
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
        o.addOption("-b","false","enable benchmarking");
        o.setRequiresInputList(true);
        o.parse(args);
        ReconMutil r = new ReconMutil(o);
        r.launch(Arrays.stream(o.getOption("-t").stringValue().split(",")).mapToInt(Integer::parseInt).toArray(), 
                o.getOption("-o").stringValue(),
                o.getInputList().stream().toArray(String[]::new));
    }
    
}
