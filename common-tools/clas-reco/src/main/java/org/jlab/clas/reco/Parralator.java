package org.jlab.clas.reco;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.jlab.coda.jevio.EvioException;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.utils.benchmark.Benchmark;
import org.jlab.utils.benchmark.ProgressPrintout;

public abstract class Parralator {
    
    // Performance parameters:
    final int BENCH_SECONDS = 30;
    final int EVENTS_PER_CHUNK = 10;

    // Static parameters:
    int maxEvents;
    int skipEvents;
    
    Object reader;

    // Queues:
    ConcurrentLinkedQueue<List<Object>> decoQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<List<HipoDataEvent>> procQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<List<Event>> writeQueue = new ConcurrentLinkedQueue<>();

    // Threads:
    CompletableFuture readerThread;
    CompletableFuture writerThread;
    ConcurrentLinkedQueue<CompletableFuture> decoThreads = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<CompletableFuture> procThreads = new ConcurrentLinkedQueue<>();

    // Progress counters:
    volatile int readEvents;
    volatile int failEvents;
    volatile int fileEvents;
    volatile int maxFileEvents;
    volatile int writeEvents;
    volatile AtomicInteger taggedEvents = new AtomicInteger();
    volatile ProgressPrintout progress = new ProgressPrintout();

    abstract HipoDataEvent[] decode(Object o);
    abstract void close();
    abstract void process(HipoDataEvent event);
    abstract void write(Event event);
    abstract HipoWriterSorted open(String filename);
    
    /**
     * The thread launcher and collector.
     * @param threads number of threads
     * @param output name of output file to write
     * @param input names of input files to read
     */
    void launch(int[] threads, String... input) {

        reset();

        System.out.println(String.format("recon-mutil::  spawning 2*%d+2 threads",threads[0]));
      
        // spawn all the threads:
        readerThread = CompletableFuture.runAsync(() -> { read(threads[0], input); });
        writerThread = CompletableFuture.runAsync(() -> { write(); });
        for (int i=0; i<threads[0]; i++) {
            final int j = i;
            decoThreads.offer(CompletableFuture.runAsync(() -> { decode(j); }));
            procThreads.offer(CompletableFuture.runAsync(() -> { process(j); }));
        }

        // perform scaling test:
        if (threads.length > 1) {
            while (writeEvents < 100) ReconUtil.sleep(1000);
            CompletableFuture.runAsync(() -> { rethread(BENCH_SECONDS,threads); }).join();
            reset();
        }

        // wait for finish:
        while (!writerThread.isDone()) {
            for (CompletableFuture f : decoThreads) if (f.isDone()) decoThreads.remove(f);
            for (CompletableFuture f : procThreads) if (f.isDone()) procThreads.remove(f);
            ReconUtil.sleep(1000);
            //show();
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
     * Forcefully shutdown all threads, close files, and reset queues and counters.
     */
    void reset() {
        for (CompletableFuture f : procThreads) f.cancel(true);
        for (CompletableFuture f : decoThreads) f.cancel(true);
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
        Benchmark.getInstance().pause("read");
        return chunk;
    }

    /**

    /**
     * The decoder thread.
     * @param thread thread number
     */
    void decode(int thread) {
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
                    for (HipoDataEvent event : decode(input.get(i)))
                        output.add(event);
                }
                procQueue.offer(output);
            }
        }
    }

    /**
     * The data processor thread.
     * @param thread thread number 
     */
    void process(int thread) {
        while (true) {
            if (maxEvents > 0 && writeEvents > maxEvents+taggedEvents.get()) {
                readerThread.cancel(true);
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
                //if (rethreadThread != null && !rethreadThread.isDone()) readQueue.offer(o);
                List<Event> output = new ArrayList<>(input.size());
                for (int i=0; i<input.size(); i++) {
                    if (input.get(i).getHipoEvent().getEventTag() == 0) {
                        process(input.get(i));
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
    void write() {
        while (true) {
            List<Event> e = writeQueue.poll();
            if (e == null) {
                if (readerThread.isDone() && procThreads.isEmpty() && writeQueue.isEmpty()) {
                    close();
                    break;
                }
                ReconUtil.sleep(1000);
            }
            else {
                for (int i=0; i<e.size(); i++) {
                    write(e.get(i));
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
            while (progress.getNumberOfCalls() < 100) ReconUtil.sleep(1000);
            ReconUtil.sleep(seconds*1000);
            System.out.println(String.format("\n~~~~~~~~~ Rethreading Count: %d ~~~~~~~~~\n",threads[i]));
            System.out.println(progress.getUpdateString());
            System.out.println(Benchmark.getInstance());
        }
    }

}
