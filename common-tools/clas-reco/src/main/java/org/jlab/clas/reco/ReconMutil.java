package org.jlab.clas.reco;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jlab.coda.jevio.EvioException;
import org.jlab.detector.decode.CLASDecoder;
import org.jlab.detector.serial.SerialHoncho;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.utils.benchmark.Benchmark;
import org.jlab.utils.benchmark.ProgressPrintout;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.system.ClasUtilsFile;

/**
 *
 * @author baltzell
 */
public final class ReconMutil extends EngineProcessor {

    // Performance parameters:
    final int BENCH_SECONDS = 30;
    final int CHUNKS_PER_QUEUE = 100;
    final int EVENTS_PER_CHUNK = 100;

    // File reader and writer:
    Object reader;
    HipoWriterSorted writer;
    SchemaFactory schema;
    SerialHoncho serial;

    // Threads and queues:
    CompletableFuture readerThread;
    CompletableFuture writerThread;
    CompletableFuture rethreadThread;
    ConcurrentLinkedQueue<CompletableFuture> procThreads = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<List<Object>> readQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<List<Event>> writeQueue = new ConcurrentLinkedQueue<>();
    
    // Static parameters:
    int maxEvents;
    int skipEvents;

    // Progress counters:
    int readEvents;
    int writeEvents;
    int failEvents;
    int fileEvents;
    int maxFileEvents;
    ProgressPrintout progress = new ProgressPrintout();

    ReconMutil(OptionParser parser) {
        super(parser);
        maxEvents = parser.getOption("-n").intValue();
        skipEvents = parser.getOption("-s").intValue();
        schema = new SchemaFactory();
        schema.initFromDirectory(ClasUtilsFile.getResourceDir("CLAS12DIR","etc/bankdefs/hipo4"));
        serial = new SerialHoncho(schema);
    }

    /**
     * The command-line entry-point known as "recon-mutil".
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        OptionParser o = EngineProcessor.getParser();
        o.addOption("-t","4","number of threads");
        o.removeOption("-i");
        o.setRequiresInputList(true);
        o.parse(args);
        ReconMutil r = new ReconMutil(o);
        r.launch(Arrays.stream(o.getOption("-t").stringValue().split(",")).mapToInt(Integer::parseInt).toArray(), 
                o.getOption("-o").stringValue(),
                o.getInputList().stream().toArray(String[]::new));
    }

    /**
     * The thread launcher and collector.
     * @param threads number of threads
     * @param output name of output file to write
     * @param input names of input files to read
     */
    void launch(int[] threads, String output, String... input) {
        reset();
        readerThread = CompletableFuture.runAsync(() -> { read(threads[0], input); });
        writerThread = CompletableFuture.runAsync(() -> { write(output); });
        for (int i=0; i<threads[0]; i++) {
            final int j = i;
            procThreads.offer(CompletableFuture.runAsync(() -> { process(j); }));
        }
        while (!writerThread.isDone()) {
            sleep(100);
            for (CompletableFuture f : procThreads)
                if (f.isDone()) procThreads.remove(f);
            if (threads.length > 1 && rethreadThread == null && writeEvents > 100) {
                rethreadThread = CompletableFuture.runAsync(() -> { rethread(BENCH_SECONDS,threads); });
                rethreadThread.join();
                reset();
            }
        }
    }

    /**
     * The reader thread.
     * @param input input filenames 
     */
    void read(int threads, String... input) {

        // convert input filenames to a list:
        List<String> inputs = new ArrayList<>(Arrays.asList(input));

        // initialize the event chunk:
        List<Object> chunk = new ArrayList<>(EVENTS_PER_CHUNK);

        // loop over input events:
        while ( (maxEvents < 1 || readEvents < maxEvents) &&
                (maxFileEvents < 1 || fileEvents < maxFileEvents) ) {

            if (reader != null) {

                // sleep instead of overfilling the read queue:
                if (readQueue.size() > CHUNKS_PER_QUEUE*threads) sleep(1000);

                // read next event into chunk, and fill queue if chunk full:
                else chunk = read(chunk);
            }

            // open the next input file:
            else if (!inputs.isEmpty()) open(inputs.removeFirst());

            // no more events to read:
            else break;
        }

        // write leftover, partial chunk:
        if (!chunk.isEmpty()) {
            System.err.println("writing partial chunk: "+chunk.size());
            readEvents += chunk.size();
            readQueue.offer(chunk);
        }

        if (reader instanceof EvioSource evio) evio.close();
    }

    /**
     * The event processor thread.
     * @param thread unique thread number 
     */
    void process(int thread) {
        while (true) {
            List<Object> o = readQueue.poll();
            if (o == null) {
                if (readerThread.isDone() && readQueue.isEmpty() && 
                        writeEvents+skipEvents+failEvents >= readEvents) break;
                sleep(100);
            }
            else {
                // put the event back on the queue if we're rethreading:
                //if (rethreadThread != null && !rethreadThread.isDone()) readQueue.offer(o);
                List<Event> chunk = new ArrayList<>(o.size());
                for (int i=0; i<o.size(); i++) {
                    HipoDataEvent event;
                    // decode if necessary:
                    if (o.get(i) instanceof ByteBuffer bb) {
                        event = decode(bb);
                    }
                    else {
                        event = new HipoDataEvent(((Event)o.get(i)), schema);
                    }
                    // run it through the engine chain:
                    for (Map.Entry<String,ReconstructionEngine> engine : processorEngines.entrySet()) {
                        Benchmark.getInstance().resume(engine.getValue().getName());
                        try { engine.getValue().processDataEvent(event); }
                        catch (Exception ex) { ex.printStackTrace(); }
                        Benchmark.getInstance().pause(engine.getValue().getName());
                    }

                    Benchmark.getInstance().resume("serial");
                    Event e = event.getHipoEvent();
                    Event t = serial.read(e);
                    t.setEventTag(1);
                    Benchmark.getInstance().pause("serial");
                    chunk.add(e);
                    chunk.add(t);
                }
                writeQueue.offer(chunk);
            }
        }
    }

    /**
     * The writer thread.
     * @param output output filename
     */
    void write(String output) {
        if (output != null) {
            writer = new HipoWriterSorted();
            writer.setCompressionType(2);
            writer.open(output);
        }
        while (true) {
            List<Event> e = writeQueue.poll();
            if (e == null) {
                if (readerThread.isDone() && procThreads.isEmpty() && writeQueue.isEmpty()) {
                    close();
                    break;
                }
                sleep(1000);
            }
            else {
                for (int i=0; i<e.size(); i++) {
                    Benchmark.getInstance().resume("write");
                    if (writer != null) writer.addEvent(e.get(i), e.get(i).getEventTag());
                    Benchmark.getInstance().pause("write");
                    progress.updateStatus();
                }
                writeEvents += e.size();
            }
        }
    }

    /**
     * The rethreader thread.
     * @param seconds delay before switching to next thread count
     * @param threads thread counts to use 
     */
    void rethread(int seconds, int... threads) {
        System.out.println("~~~~~~~~~ Rethreading Initiated ~~~~~~~~~");
        for (int i=0; i<threads.length; i++) {
            for (CompletableFuture f : procThreads) {
                f.cancel(true);
                procThreads.remove(f);
            }
            writeEvents = 0;
            readEvents = 0;
            progress = new ProgressPrintout();
            progress.setInterval(-1);
            Benchmark.getInstance().reset();
            for (int j=0; j<threads[i]; j++) {
                final int k = j;
                procThreads.offer(CompletableFuture.runAsync(() -> { process(k); }));
            }
            while (progress.getNumberOfCalls() < 100) sleep(1000);
            sleep(seconds*1000);
            System.out.println(String.format("\n~~~~~~~~~ Rethreading Count: %d ~~~~~~~~~\n",threads[i]));
            System.out.println(progress.getUpdateString());
            System.out.println(Benchmark.getInstance());
        }
    }

    /**
     * Decode an event.
     * @param bytes the EVIO byte buffer
     * @return decoded event
     */
    HipoDataEvent decode(ByteBuffer bytes) {
        Benchmark.getInstance().resume("evio");
        EvioDataEvent evio = new EvioDataEvent(bytes.array(), ByteOrder.LITTLE_ENDIAN);
        Benchmark.getInstance().pause("evio");
        Benchmark.getInstance().resume("deco");
        HipoDataEvent hipo;
        try {
            CLASDecoder d = decoders.take();
            hipo = d.getDecodedDataEvenet(evio);
            decoders.put(d);
        }
        catch (InterruptedException ex) { hipo = null; }
        Benchmark.getInstance().pause("deco");
        return hipo;
    }
  
    /**
     * Open a new input HIPO/EVIO event file.
     * @param filename 
     */
    void open(String filename) {
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
     * Read the next event into the chunk, and, if it's full, queue the chunk
     * and make a new one.
     * @param chunk
     * @return modified chunk 
     */
    List<Object> read(List<Object> chunk) {
        Benchmark.getInstance().resume("read");
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
            o = ((HipoReader)reader).getEvent(event, fileEvents);
        }
        if (o != null && (skipEvents < 1 || readEvents > skipEvents)) {
            chunk.add(o);
            if (chunk.size() >= EVENTS_PER_CHUNK) {
                readQueue.offer(chunk);
                readEvents += chunk.size();
                chunk = new ArrayList<>(EVENTS_PER_CHUNK);
            }
        }
        Benchmark.getInstance().pause("read");
        return chunk;
    }

    /**
     * Close the output file and print some stuff.
     */
    void close() {
        writer.close();
        System.out.println(Benchmark.getInstance());
        System.out.println(String.format("recon-mutil:::::  Read/Write/Diff = %d/%d/%d",
                readEvents, writeEvents, readEvents-writeEvents));
    }

    /**
     * Forcefully shutdown all threads, close files, and reset queuess and counters.
     */
    void reset() {
        for (CompletableFuture f : procThreads) f.cancel(true);
        if (readerThread != null) readerThread.cancel(true);
        if (writerThread != null) {
            writerThread.cancel(true);
            writer.close();
        }
        readQueue = new ConcurrentLinkedQueue<>();
        writeQueue = new ConcurrentLinkedQueue<>();
        procThreads = new ConcurrentLinkedQueue();
        readEvents = 0;
        writeEvents = 0;
        failEvents = 0;
    }

    /**
     * Catch interruptions in sleep.
     * @param milliseconds 
     */
    void sleep(int milliseconds) {
        try { Thread.sleep(milliseconds); }
        catch (InterruptedException ex) {}
    }
}
