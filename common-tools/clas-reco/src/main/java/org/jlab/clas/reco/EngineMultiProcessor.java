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
    List<ConcurrentLinkedQueue<DataEvent>> splitQueue;
    
    ProgressPrintout progress = new ProgressPrintout();

    int maxEvents;
    int skipEvents;

    int readEvents;
    int writeEvents;
    int failEvents;
    int fileEvents;
    int maxFileEvents;
    
    public EngineMultiProcessor(OptionParser parser) {
        super(parser);
        maxEvents = parser.getOption("-n").intValue();
        skipEvents = parser.getOption("-s").intValue();
    }

    public void scaling(String input, int... threads) {
        for (int t : threads) {
            launch(t, String.format("scaling-%d.hipo",t), input);
        }
    }
   
    /**
     * The thread launcher and collector.
     * @param threads number of threads
     * @param output name of output file to write
     * @param input names of input files to read
     */
    public void launch(int threads, String output, String... input) {
        readEvents = 0;
        writeEvents = 0;
        failEvents = 0;
        splitQueue = new ArrayList<>(threads);
        readerThread = CompletableFuture.runAsync(() -> { read(threads, input); });
        writerThread = CompletableFuture.runAsync(() -> { write(output); });
        for (int i=0; i<threads; i++) {
            final int j = i;
            procThreads.offer(CompletableFuture.runAsync(() -> { process(j); }));
            splitQueue.add(i, new ConcurrentLinkedQueue<>());
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
    void read(int threads, String... input) {

        // convert input filenames to a list:
        List<String> inputs = new ArrayList<>(Arrays.asList(input));

        // initialize the event frame:
        List<Object> frame = new ArrayList<>(FRAME_SIZE);

        // loop over input events:
        while ( (maxEvents < 1 || readEvents < maxEvents) &&
                (maxFileEvents < 1 || fileEvents < maxFileEvents) ) {

            if (reader != null && reader.hasEvent()) {

                // sleep instead of overfilling the read queue:
                if (readQueue.size() > QUEUE_SIZE*threads) sleep(1000);

                // read next event into frame, and fill queue if frame full:
                else frame = read(frame);
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
                for (int i=0; i<e.size(); i++) {
                    Benchmark.getInstance().resume("write");
                    writer.writeEvent(e.get(i));
                    Benchmark.getInstance().pause("write");
                    progress.updateStatus();
                }
                writeEvents += e.size();
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

    /**
     * Read the next event into the frame, and, if it's full, queue the frame
     * and make a new one.
     * @param frame
     * @return modified frame 
     */
    List<Object> read(List<Object> frame) {
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
        return frame;
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
        proc.launch(parser.getOption("-t").intValue(),
                    parser.getOption("-o").stringValue(),
                    parser.getOption("-i").stringValue());
    }
}
