package org.jlab.clas.reco;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.utils.benchmark.Benchmark;
import org.jlab.utils.benchmark.ProgressPrintout;
import org.jlab.utils.options.OptionParser;

/**
 *
 * @author baltzell
 */
public class EngineMultiProcessor extends EngineProcessor {
    
    public EngineMultiProcessor(int threads) {
        super();
        this.threads = threads;
    }
    
    public EngineMultiProcessor(int threads, int events, int skip) {
        super();
        this.threads = threads;
        this.maxEventsUser = events;
        this.skipEvents = skip;
    }
    
    public EngineMultiProcessor(OptionParser parser) {
        super(parser);
        threads = parser.getOption("-t").intValue();
        maxEventsUser = parser.getOption("-n").intValue();
        skipEvents = parser.getOption("-s").intValue();
    }
    
    public static void main(String[] args) {
        OptionParser parser = EngineProcessor.parser();
        parser.addOption("-t","4","number of threads");
        parser.parse(args);
        EngineMultiProcessor proc = new EngineMultiProcessor(parser);
        proc.process(parser.getOption("-o").stringValue(), parser.getOption("-i").stringValue());
    }
    
    public void process(String output, String... input) {
        readerThread = CompletableFuture.runAsync(() -> { read(input); });
        writerThread = CompletableFuture.runAsync(() -> { write(output); });
        for (int i=0; i<threads; i++) {
            final int j = i;
            procThreads.offer(CompletableFuture.runAsync(() -> { process(j); }));
        }
        while (!writerThread.isDone())
            try { Thread.sleep(100); } catch (InterruptedException ex) {}
    }
    
    DataSource reader;
    HipoDataSync writer;
    CompletableFuture readerThread;
    CompletableFuture writerThread;
    
    int threads;
    int maxEvents = 0;
    int maxEventsUser = 0;
    int skipEvents = 0;
    int readEvents = 0;
    int writeEvents = 0;
   
    ArrayList<String> inputs = new ArrayList<>();
    ConcurrentLinkedQueue<CompletableFuture> procThreads = new ConcurrentLinkedQueue();
    ConcurrentLinkedQueue<ByteBuffer> evioQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<DataEvent> hipoQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<DataEvent> writeQueue = new ConcurrentLinkedQueue<>();
    
    ProgressPrintout progress = new ProgressPrintout();
    
    void read(String... input) {
        inputs.addAll(Arrays.asList(input));
        while (maxEvents < 1 || readEvents < maxEvents) {
            if (reader != null && reader.hasEvent()) {
                if (evioQueue.size()+hipoQueue.size() > 100*threads) {
                    try { Thread.sleep(100); }
                    catch (InterruptedException ex) {}
                }
                else {
                    Benchmark.getInstance().resume("read");
                    readEvents++;
                    if (reader instanceof EvioSource evio) {
                        try { evioQueue.offer(evio.getEventBuffer(readEvents, true)); }
                        catch (EvioException ex) { ex.printStackTrace(); }
                    }
                    else {
                        DataEvent event = reader.getNextEvent();
                        if (skipEvents < 1 || readEvents > skipEvents)
                            hipoQueue.offer(event);
                    }
                    Benchmark.getInstance().pause("read");
                }
            }
            else if (inputs.isEmpty()) break;
            else {
                if (inputs.get(0).endsWith(".hipo")) reader = new HipoDataSource();
                else reader = new EvioSource();
                reader.open(inputs.remove(0));
                maxEvents = maxEventsUser;
                if (reader instanceof HipoDataSource)
                    updateDictionary((HipoDataSource)reader, writer);
                else {
                    int n = ((EvioSource)reader).getEventCount();
                    maxEvents = maxEventsUser < n ? maxEventsUser : n;
                }
                readEvents = 0;
            }
        }
    }
    
    void process(int thread) {
        while (true) {
            if (evioQueue.isEmpty() && hipoQueue.isEmpty()) {
                if (readerThread.isDone())
                    if (evioQueue.isEmpty() && hipoQueue.isEmpty()) break;
                try { Thread.sleep(100); }
                catch (InterruptedException ex) {}
            }
            else {
                DataEvent event;
                if (!evioQueue.isEmpty()) {
                    Benchmark.getInstance().resume("EVIO");
                    event = new EvioDataEvent(evioQueue.poll().array(), ByteOrder.LITTLE_ENDIAN);
                    Benchmark.getInstance().pause("EVIO");
                    try { 
                        Benchmark.getInstance().resume("DECO");
                        CLASDecoder d = decoders.take();
                        event = d.getDecodedDataEvent((EvioDataEvent)event);
                        decoders.put(d);
                        Benchmark.getInstance().pause("DECO");
                    }
                    catch (InterruptedException ex) {}
                }
                else if (!hipoQueue.isEmpty())
                    event = hipoQueue.poll();
                else continue;
                for (Map.Entry<String,ReconstructionEngine> engine : this.processorEngines.entrySet()) {
                    Benchmark.getInstance().resume(engine.getValue().getName());
                    try { engine.getValue().processDataEvent(event); }
                    catch (Exception ex) { ex.printStackTrace(); }
                    Benchmark.getInstance().pause(engine.getValue().getName());
                }
                writeQueue.offer(event);
            }
        }
    }
    
    void write(String output) {
        writer = new HipoDataSync();
        writer.setCompressionType(2);
        writer.open(output);
        boolean benching = false;
        while (true) {
            if (writeQueue.isEmpty()) {
                for (CompletableFuture f : procThreads)
                    if (f.isDone()) procThreads.remove(f);
                if (procThreads.isEmpty()) {
                    if (writeQueue.isEmpty()) {
                        writer.close();
                        System.out.println(Benchmark.getInstance());
                        System.out.println(String.format("EngineMultiProcessor:  Read=%d  Write=%d  Diff=%d",
                                readEvents,writeEvents,readEvents-writeEvents));
                        break;
                    }
                }
                try { Thread.sleep(100); }
                catch (InterruptedException ex) {}
            }
            else {
                Benchmark.getInstance().resume("write");
                writer.writeEvent(writeQueue.poll());
                if (writeEvents > 100) {
                    if (!benching) {
                        benching = true;
                        Benchmark.getInstance().printTimer(10);
                    }
                    progress.updateStatus();
                }
                writeEvents++;
                Benchmark.getInstance().pause("write");
            }
        }
    }
}
