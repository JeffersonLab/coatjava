package org.jlab.clas.reco;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jlab.coda.jevio.EvioException;
import org.jlab.detector.decode.CLASDecoder;
import org.jlab.detector.decode.CLASDecoderPool;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.utils.benchmark.Benchmark;
import org.jlab.utils.benchmark.ProgressPrintout;

public abstract class Parralator {
    
    // Performance parameters:
    final int BENCH_SECONDS = 30;
    final int EVENTS_PER_CHUNK = 10;

    // Static parameters:
    int maxEvents;
    int skipEvents;
   
    // Processors:
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
    AtomicBoolean paused = new AtomicBoolean(true);

    abstract Object openReader(String filename);
    abstract HipoDataEvent[] decode(int thread, Object o);
    abstract void declosure();
    abstract void process(int thread, HipoDataEvent event);
    abstract void write(Event event);
    
    /**
     * The thread launcher and collector.
     * @param threads number of threads
     * @param input names of input files to read
     */
    public void launch(int[] threads, String... input) {

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
            else if (!inputs.isEmpty()) reader = openReader(inputs.removeFirst());

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
        paused.set(true);
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
    final void decode(int thread) {
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
                    for (HipoDataEvent event : decode(thread, input.get(i)))
                        output.add(event);
                }
                procQueue.offer(output);
            }
        }
        if (thread == 0) declosure();
    }

    /**
     * The data processor thread.
     * @param thread thread number 
     */
    final void process(int thread) {
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
                    while (paused.get()) ReconUtil.sleep(100);
                    if (input.get(i).getHipoEvent().getEventTag() == 0)
                        process(thread, input.get(i));
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
    final void write() {
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
    final void rethread(int seconds, int... threads) {
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

    /**
     * Decode an event.
     * @param thread
     * @param bytes the EVIO byte buffer
     * @return decoded event
     */
    public static HipoDataEvent decode(int thread, ByteBuffer bytes) {
        Benchmark.getInstance().resume(thread, "evio");
        EvioDataEvent evio = new EvioDataEvent(bytes.array(), ByteOrder.LITTLE_ENDIAN);
        Benchmark.getInstance().pause(thread, "evio");
        Benchmark.getInstance().resume(thread, "deco");
        CLASDecoder d = CLASDecoderPool.getInstance().take();
        HipoDataEvent hipo = d.getDecodedDataEvenet(evio);
        CLASDecoderPool.getInstance().put(d);
        Benchmark.getInstance().pause(thread, "deco");
        return hipo;
    }
  
    /**
     * Print the thread, queue, and event states.
     */
    public void show() {
        String s1 = String.format("threads(r/d/p/w)=(%b/%d/%d/%b)",
                !readerThread.isDone(), decoThreads.size(), procThreads.size(), !writerThread.isDone());
        String s2 = String.format(" queues(d/p/w)=(%d/%d/%d)",
                decoQueue.size(), procQueue.size(), writeQueue.size());
        String s3 = String.format(" events(r/w/t/f)=(%d/%d/%d/%d)",
                readEvents, writeEvents, taggedEvents.get(), failEvents);
        System.out.println("recon-mutil::  "+s1+" "+s2+" "+s3);
    }

    void close() {
        System.out.println(Benchmark.getInstance());
        System.out.println(String.format("recon-mutil :: read/write/tagged/diff = %d/%d/%d/%d",
                readEvents, writeEvents, taggedEvents.get(), writeEvents-readEvents-taggedEvents.get()));
    }
    
}
