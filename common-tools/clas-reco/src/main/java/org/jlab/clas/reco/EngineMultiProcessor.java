package org.jlab.clas.reco;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jlab.coda.jevio.EvioException;
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

    DataSource reader;
    HipoDataSync writer;
    CompletableFuture readerThread;

    int threads;
    int maxEvents = 0;
    int skipEvents = 0;
    int readEvents = 0;
    ArrayList<String> inputs = new ArrayList<>();
    ProgressPrintout progress = new ProgressPrintout();
    ConcurrentLinkedQueue<DataEvent> readQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<DataEvent> writeQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<DataEvent> procQueue = new ConcurrentLinkedQueue<>();

    public EngineMultiProcessor(OptionParser parser) {
        super(parser);
        threads = parser.getOption("-t").intValue();
        maxEvents = parser.getOption("-n").intValue();
        skipEvents = parser.getOption("-s").intValue();
    }

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

    void read(String... input) throws InterruptedException, EvioException {
        inputs.addAll(Arrays.asList(input));
        while (maxEvents < 1 || readEvents < maxEvents) {
            if (reader != null && reader.hasEvent()) {
                if (readQueue.size() > 10*threads) {
                    Thread.sleep(100);
                    continue;
                }
                DataEvent event = reader.getNextEvent();
                readEvents++;
                if (skipEvents < 1 || readEvents > skipEvents)
                    readQueue.offer(event);
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

    void write(String output) throws InterruptedException {
        writer = new HipoDataSync();
        writer.setCompressionType(2);
        writer.open(output);
        while (true) {
            if (writeQueue.isEmpty()) {
                if (readerThread.isDone()) {
                    if (readQueue.isEmpty() && procQueue.isEmpty()) {
                        progress.showStatus();
                        writer.close();
                        break;
                    }
                }
                Thread.sleep(100);
            }
            else {
                writer.writeEvent(writeQueue.poll());
                progress.updateStatus();
            }
        }
    }

    void process() throws InterruptedException {
        while (true) {
            if (readQueue.isEmpty()) {
                if (readerThread.isDone()) break;
                Thread.sleep(100);
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

    public void process(String output, String... input) {
        // start reader thread:
        readerThread = CompletableFuture.supplyAsync(() -> {
            try { read(input); } catch (InterruptedException | EvioException ex) {}
            return true;
        });
        // start writer thread:
        CompletableFuture writerThread = CompletableFuture.supplyAsync(() -> {
            try { write(output); } catch (InterruptedException ex) {}
            return true;
        });
        // start processor threads:
        for (int i=0; i<threads; i++) {
            CompletableFuture.supplyAsync(() -> {
                try { process(); } catch (InterruptedException ex) {}
                return true;
            });
        }
        // wait for finish:
        while (!writerThread.isDone()) {
            try { Thread.sleep(100); } catch (InterruptedException ex) {}
        }
    }

    public static void main(String[] args) {
        OptionParser parser = EngineProcessor.parser();
        parser.addOption("-t","4","number of threads");
        parser.parse(args);

        EngineMultiProcessor proc = new EngineMultiProcessor(parser);
        proc.process(parser.getOption("-o").stringValue(), parser.getOption("-i").stringValue());
    }
}
