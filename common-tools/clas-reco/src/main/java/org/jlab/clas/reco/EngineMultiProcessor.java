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
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataSource;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;
import org.jlab.utils.benchmark.Benchmark;
import org.jlab.utils.benchmark.ProgressPrintout;
import org.jlab.utils.options.OptionParser;

/**
 *
 * @author baltzell
 */
public class EngineMultiProcessor extends EngineProcessor {
    
    DataSource reader;
    HipoDataSync writer;
    CompletableFuture readerThread;
    CompletableFuture writerThread;
    ProgressPrintout progress = new ProgressPrintout();
    ConcurrentLinkedQueue<CompletableFuture> procThreads = new ConcurrentLinkedQueue();
    ConcurrentLinkedQueue<List<Object>> readQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<List<DataEvent>> writeQueue = new ConcurrentLinkedQueue<>();
    
    int threads;
    int maxEvents = 0;
    int failEvents = 0;
    int maxEventsUser = 0;
    int skipEvents = 0;
    int readEvents = 0;
    int writeEvents = 0;
    
    public EngineMultiProcessor(OptionParser parser) {
        super(parser);
        threads = parser.getOption("-t").intValue();
        maxEventsUser = parser.getOption("-n").intValue();
        skipEvents = parser.getOption("-s").intValue();
    }
   
    /**
     * The thread launcher.
     * @param output
     * @param input 
     */
    public void launch(String output, String... input) {
        readerThread = CompletableFuture.runAsync(() -> { read(input); });
        writerThread = CompletableFuture.runAsync(() -> { write(output); });
        for (int i=0; i<threads; i++) {
            final int j = i;
            procThreads.offer(CompletableFuture.runAsync(() -> { process(j); }));
        }
        while (!writerThread.isDone()) {
            for (CompletableFuture f : procThreads)
                if (f.isDone()) procThreads.remove(f);
            sleep(100);
        }
    }
    
    /**
     * The reader thread.
     * @param input input filenames 
     */
    void read(String... input) {

        // convert input filenames to a list:
        List<String> inputs = new ArrayList<>(Arrays.asList(input));

        // event buffer:
        List<Object> frame = new ArrayList<>(100);

        while (maxEvents < 1 || readEvents < maxEvents) {

            if (reader != null && reader.hasEvent()) {

                // sleep instead of overfilling the read queue:
                if (readQueue.size() > 100*threads) sleep(100);

                // read the next event:
                else {
                    Benchmark.getInstance().resume("read");
                    Object o = null;
                    if (reader instanceof EvioSource evio) {
                        try { o = evio.getEventBuffer(++readEvents, true); }
                        catch (EvioException ex) {
                            failEvents++;
                            ex.printStackTrace();
                        }
                    }
                    else {
                        readEvents++;
                        o = reader.getNextEvent();
                    }
                    if (o != null) {
                        if (skipEvents < 1 || readEvents > skipEvents) {
                            frame.add(o);
                            if (frame.size() >= 100) {
                                readQueue.offer(frame);
                                frame = new ArrayList<>(100);
                            }
                        }
                    }
                    Benchmark.getInstance().pause("read");
                }
            }

            // we're done if there's no more input files:
            else if (inputs.isEmpty()) break;

            // open the next input file:
            else open(inputs.removeFirst());
        }
        if (!frame.isEmpty()) readQueue.offer(frame);
        reader.close();
    }

    /**
     * The event processor thread.
     * @param thread unique thread number 
     */
    void process(int thread) {
        List<DataEvent> frame = new ArrayList<>(100);
        while (true) {
            List<Object> o = readQueue.poll();
            if (o == null) {
                if (readerThread.isDone() && readQueue.isEmpty())
                    if (writeEvents+skipEvents+failEvents >= readEvents) break;
                sleep(100);
            }
            else {
                for (int i=0; i<o.size(); i++) {
                    DataEvent event;
                    // decode if necessary:
                    if (o.get(i) instanceof ByteBuffer bb) event = decode(bb);
                    else event = (HipoDataEvent)o.get(i);
                    // run it through the engine chain:
                    for (Map.Entry<String,ReconstructionEngine> engine : processorEngines.entrySet()) {
                        Benchmark.getInstance().resume(engine.getValue().getName());
                        try { engine.getValue().processDataEvent(event); }
                        catch (Exception ex) { ex.printStackTrace(); }
                        Benchmark.getInstance().pause(engine.getValue().getName());
                    }
                    frame.add(event);
                    if (frame.size() >= 100) {
                        writeQueue.offer(frame);
                        frame = new ArrayList<>(100);
                    }
                }
            }
        }
        if (!frame.isEmpty()) writeQueue.offer(frame);
    }
   
    /**
     * The writer thread.
     * @param output output filename
     */
    void write(String output) {
        writer = new HipoDataSync();
        writer.setCompressionType(2);
        writer.open(output);
        while (true) {
            List<DataEvent> e = writeQueue.poll();
            if (e == null) {
                if (procThreads.isEmpty() && writeQueue.isEmpty()) {
                    close();
                    break;
                }
                sleep(100);
            }
            else {
                Benchmark.getInstance().resume("write");
                for (int i=0; i<e.size(); i++) {
                    writer.writeEvent(e.get(i));
                    if (writeEvents > 100) progress.updateStatus();
                    writeEvents++;
                }
                Benchmark.getInstance().pause("write");
            }
        }
    }

    /**
     * Decoding.
     * @param bytes EVIO byte buffer
     * @return decoded event 
     */
    HipoDataEvent decode(ByteBuffer bytes) {
        Benchmark.getInstance().resume("EVIO");
        EvioDataEvent evio = new EvioDataEvent(bytes.array(), ByteOrder.LITTLE_ENDIAN);
        Benchmark.getInstance().pause("EVIO");
        Benchmark.getInstance().resume("DECO");
        HipoDataEvent hipo;
        try {
            CLASDecoder d = decoders.take();
            hipo = d.getDecodedDataEvenet(evio);
            decoders.put(d);
        }
        catch (InterruptedException ex) { hipo = null; }
        Benchmark.getInstance().pause("DECO");
        return hipo;
    }
    
    void open(String filename) {
        reader = filename.endsWith(".hipo") ? new HipoDataSource() : new EvioSource();
        reader.open(filename);
        maxEvents = maxEventsUser;
        if (reader instanceof HipoDataSource hipo) {
            updateDictionary(hipo, writer);
        } else {
            int n = ((EvioSource)reader).getEventCount();
            maxEvents = maxEventsUser>0 && maxEventsUser < n ? maxEventsUser : n;
        }
        readEvents = 0;
        writeEvents = 0;
    }

    void close() {
        writer.close();
        System.out.println(Benchmark.getInstance());
        System.out.println(String.format("recon-mutil:::::  Read/Write/Diff = %d/%d/%d",
                readEvents, writeEvents, readEvents-writeEvents));
    }

    void sleep(int milliseconds) {
        try { Thread.sleep(milliseconds); }
        catch (InterruptedException ex) {}
    }

    public static void main(String[] args) {
        OptionParser parser = EngineProcessor.getParser();
        parser.addOption("-t","4","number of threads");
        parser.parse(args);
        EngineMultiProcessor proc = new EngineMultiProcessor(parser);
        proc.launch(parser.getOption("-o").stringValue(), parser.getOption("-i").stringValue());
    }
}
