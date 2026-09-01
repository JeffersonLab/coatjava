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

    final int QUEUE_SIZE = 100;
    final int FRAME_SIZE = 100;

    DataSource reader;
    HipoDataSync writer;

    CompletableFuture readerThread;
    CompletableFuture writerThread;
    ConcurrentLinkedQueue<CompletableFuture> procThreads = new ConcurrentLinkedQueue();

    ConcurrentLinkedQueue<List<Object>> readQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<List<DataEvent>> writeQueue = new ConcurrentLinkedQueue<>();
    
    ProgressPrintout progress = new ProgressPrintout();

    int threads;
    int maxEvents;
    int skipEvents;

    int readEvents;
    int writeEvents;
    int failEvents;
    int fileEvents;
    int maxFileEvents;
    
    public EngineMultiProcessor(OptionParser parser) {
        super(parser);
        threads = parser.getOption("-t").intValue();
        maxEvents = parser.getOption("-n").intValue();
        skipEvents = parser.getOption("-s").intValue();
    }

    public void scaling(String input, int... threads) {
        for (int t : threads) {
            this.threads = t;
            launch(String.format("scaling-%d.hipo",t),input);
        }
    }
   
    /**
     * The thread launcher and collector.
     * @param output name of output file to write
     * @param input names of input files to read
     */
    public void launch(String output, String... input) {
        readEvents = 0;
        writeEvents = 0;
        failEvents = 0;
        readerThread = CompletableFuture.runAsync(() -> { read(input); });
        writerThread = CompletableFuture.runAsync(() -> { write(output); });
        for (int i=0; i<threads; i++) {
            final int j = i;
            procThreads.offer(CompletableFuture.runAsync(() -> { process(j); }));
        }
        while (!writerThread.isDone()) {
            for (CompletableFuture f : procThreads)
                if (f.isDone()) procThreads.remove(f);
            sleep(1000);
            if (readerThread.isDone()) {
                System.err.println("------------------------------------------");
                System.err.println(writeEvents+"+"+skipEvents+"+"+failEvents+"=?"+readEvents);
                System.err.println(readQueue.size()+" -> "+writeQueue.size());
            }
        }
    }
    
    /**
     * The reader thread.
     * @param input input filenames 
     */
    void read(String... input) {

        // convert input filenames to a list:
        List<String> inputs = new ArrayList<>(Arrays.asList(input));

        // initialize the event frame:
        List<Object> frame = new ArrayList<>(FRAME_SIZE);

        // loop over input events:
        while ( (maxEvents < 1 || readEvents < maxEvents) &&
                (maxFileEvents < 1 || fileEvents < maxFileEvents) ) {

            if (reader != null && reader.hasEvent()) {

                // sleep instead of overfilling the read queue:
                if (readQueue.size() > QUEUE_SIZE*threads) sleep(100);

                // read the next event into the frame:
                else {
                    Benchmark.getInstance().resume("read");
                    Object o = null;
                    if (reader instanceof EvioSource evio) {
                        try { o = evio.getEventBuffer(++fileEvents, true); }
                        catch (EvioException ex) {
                            failEvents++;
                            ex.printStackTrace();
                        }
                    }
                    else o = reader.getNextEvent();
                    if (o != null && (skipEvents < 1 || readEvents > skipEvents)) {
                        frame.add(o);
                        if (frame.size() >= FRAME_SIZE) {
                            readQueue.offer(frame);
                            readEvents += frame.size();
                            frame = new ArrayList<>(FRAME_SIZE);
                        }
                    }
                    Benchmark.getInstance().pause("read");
                }
            }

            // open the next input file:
            else if (!inputs.isEmpty()) open(inputs.removeFirst());

            // nothing left to do:
            else break;
        }

        // write leftover, partial frame:
        if (!frame.isEmpty()) {
            System.err.println("writing partial frame: "+frame.size());
            readEvents += frame.size();
            readQueue.offer(frame);
        }
        reader.close();
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
                List<DataEvent> frame = new ArrayList<>(o.size());
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
                }
                writeQueue.offer(frame);
            }
        }
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
                sleep(1000);
            }
            else {
                Benchmark.getInstance().resume("write");
                for (int i=0; i<e.size(); i++) {
                    writer.writeEvent(e.get(i));
                    progress.updateStatus();
                }
                writeEvents += e.size();
                Benchmark.getInstance().pause("write");
            }
        }
    }

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
   
    void open(String filename) {
        fileEvents = 0;
        reader = filename.endsWith(".hipo") ? new HipoDataSource() : new EvioSource();
        reader.open(filename);
        if (reader instanceof HipoDataSource hipo) {
            maxFileEvents = 0;
            updateDictionary(hipo, writer);
        } else {
            maxFileEvents = ((EvioSource)reader).getEventCount();
        }
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
