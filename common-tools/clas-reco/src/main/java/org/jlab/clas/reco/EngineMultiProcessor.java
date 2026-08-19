package org.jlab.clas.reco;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataSource;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;
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
        this.maxEvents = events;
        this.skipEvents = skip;
    }

    public EngineMultiProcessor(OptionParser parser) {
        super(parser);
        threads = parser.getOption("-t").intValue();
        maxEvents = parser.getOption("-n").intValue();
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
        for (int i=0; i<threads; i++)
            procThreads.add(CompletableFuture.runAsync(() -> { process(); }));
        while (!writerThread.isDone())
            try { Thread.sleep(100); } catch (InterruptedException ex) {}
    }

    DataSource reader;
    HipoDataSync writer;
    CompletableFuture readerThread;
    CompletableFuture writerThread;

    int threads;
    int maxEvents = 0;
    int skipEvents = 0;
    int readEvents = 0;

    ArrayList<CompletableFuture> procThreads = new ArrayList<>();
    ArrayList<String> inputs = new ArrayList<>();
    ProgressPrintout progress = new ProgressPrintout();
    ConcurrentLinkedQueue<DataEvent> readQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<DataEvent> writeQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<DataEvent> procQueue = new ConcurrentLinkedQueue<>();

    void read(String... input) {
        inputs.addAll(Arrays.asList(input));
        while (maxEvents < 1 || readEvents < maxEvents) {
            if (reader != null && reader.hasEvent()) {
                if (readQueue.size() > 100*threads) {
                    try { Thread.sleep(100); }
                    catch (InterruptedException ex) {}
                }
                else {
                    DataEvent event = reader.getNextEvent();
                    readEvents++;
                    if (skipEvents < 1 || readEvents > skipEvents)
                        readQueue.offer(event);
                }
            }
            else if (inputs.isEmpty()) break;
            else {
                if (inputs.get(0).endsWith(".hipo")) reader = new HipoDataSource();
                else reader = new EvioSource();
                reader.open(inputs.remove(0));
                updateDictionary((HipoDataSource)reader, writer);
            }
        }
    }

    void write(String output) {
        writer = new HipoDataSync();
        writer.setCompressionType(2);
        writer.open(output);
        while (true) {
            if (writeQueue.isEmpty()) {
                if (procThreads.stream().filter(x->!x.isDone()).collect(Collectors.toList()).isEmpty()) {
                    if (writeQueue.isEmpty()) {
                        progress.showStatus();
                        writer.close();
                        break;
                    }
                }
                try { Thread.sleep(100); }
                catch (InterruptedException ex) {}
            }
            else {
                writer.writeEvent(writeQueue.poll());
                progress.updateStatus();
            }
        }
    }

    void process() {
        while (true) {
            if (readQueue.isEmpty()) {
                if (readerThread.isDone())
                    if (readQueue.isEmpty()) break;
                try { Thread.sleep(100); }
                catch (InterruptedException ex) {}
            }
            else {
                DataEvent event = readQueue.poll();
                procQueue.offer(event);
                processEvent(event);
                writeQueue.offer(event);
                procQueue.remove(event);
            }
        }
    }
}
