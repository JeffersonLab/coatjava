package org.jlab.clas.reco;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.utils.benchmark.Benchmark;
import org.jlab.utils.benchmark.ProgressPrintout;

/**
 * Parallel orchestrator.
 * 
 */
public abstract class Porch {
    
    static final String[] BENCHMARK_NAMES = new String[]{"evio","deco","serial","post","write"};
    
    // Performance parameters:
    final int BENCH_SECONDS = 30;
    final int EVENTS_PER_CHUNK = 50;
    
    // Static parameters:
    int maxEvents;
    int skipEvents;

    // Threads:
    CompletableFuture readerThread;
    CompletableFuture writerThread;
    ConcurrentLinkedQueue<CompletableFuture> decoThreads = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<CompletableFuture> procThreads = new ConcurrentLinkedQueue<>();

    // Queues:
    ConcurrentLinkedQueue<List<Object>> decoQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<List<HipoDataEvent>> procQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<List<HipoDataEvent>> writeQueue = new ConcurrentLinkedQueue<>();

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

    // Event reader/source:
    Object reader;
    
    /**
     * The thread launcher and collector.
     * @param threads number of threads
     * @param input names of input files to read
     */
    public void launch(int[] threads, String... input) {

        reset();

        System.out.println(String.format("recon-mutil::  spawning 2*%d+2 threads",threads[0]));
        
        // start a period status printout:
        ReconUtil.runPeriodic(10, statsShow);
        
        // spawn all the threads:
        readerThread = CompletableFuture.runAsync(() -> { reader(threads[0], input); });
        for (int i=0; i<Math.max(8,threads[0]); i++) {
            final int j = i;
            ReconUtil.addAndRemove(decoThreads, CompletableFuture.runAsync(() -> { decoder(j); }));
        }
        ReconUtil.sleep(1000);
        for (int i=0; i<threads[0]; i++) {
            final int j = i;
            ReconUtil.addAndRemove(procThreads, CompletableFuture.runAsync(() -> { processor(j); }));
        }
        writerThread = CompletableFuture.runAsync(() -> { writer(); });
       
        // perform scaling test:
        if (threads.length > 1)
            CompletableFuture.runAsync(() -> { rethreader(BENCH_SECONDS,threads); }).join();

        // wait for finish:
        writerThread.join();
    }

    /**
     * The reader thread.
     * @param threads
     * @param input input filenames 
     */
    final void reader(int threads, String... input) {
        List<String> inputs = new ArrayList<>(Arrays.asList(input));
        List<Object> output = new ArrayList<>(EVENTS_PER_CHUNK);
        while ( (maxEvents < 1 || writeEvents < maxEvents+taggedEvents.get()) &&
                (maxFileEvents < 1 || fileEvents < maxFileEvents) ) {
            if (reader != null) {
                // sleep instead of overfilling the read queue (100K events, ~2GB):
                if (readEvents > 1e5) ReconUtil.sleep(1000);
                else {
                    Object o = read();
                    if (o != null && (skipEvents < 1 || readEvents > skipEvents)) {
                        output.add(o);
                        if (output.size() >= EVENTS_PER_CHUNK) {
                            decoQueue.offer(output);
                            readEvents += output.size();
                            output = new ArrayList<>(EVENTS_PER_CHUNK);
                        }
                    }
                }
            }
            else if (inputs.isEmpty()) break;
            else reader = open(inputs.removeFirst());
        }
        // write leftover, partial chunk:
        if (!output.isEmpty()) {
            readEvents += output.size();
            decoQueue.offer(output);
        }
        readerExit();
    }

    /**
     * The decoder thread.
     * @param thread thread number
     */
    final void decoder(int thread) {
        while (true) {
            List<Object> input = decoQueue.poll();
            if (input == null) {
                if (decoQueue.isEmpty() && readerThread.isDone() && decoQueue.isEmpty())
                    break;
                ReconUtil.sleep(100);
            }
            else {
                List<HipoDataEvent> output = new ArrayList<>(input.size());
                for (int i=0; i<input.size(); i++)
                    Collections.addAll(output, decode(thread, input.get(i)));
                procQueue.offer(output);
            }
        }
        decoderExit(thread);
    }

    /**
     * The data processor thread.
     * @param thread thread number 
     */
    final void processor(int thread) {
        while (true) {
            if (maxEvents > 0 && writeEvents > maxEvents+taggedEvents.get()) {
                stop();
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
                List<HipoDataEvent> output = new ArrayList<>(input.size());
                for (int i=0; i<input.size(); i++) {
                    if (input.get(i).getHipoEvent().getEventTag() == 0)
                        process(thread, input.get(i));
                    output.add(input.get(i));
                }
                writeQueue.offer(output);
            }
        }
    }
    
    /**
     * The writer thread.
     */
    final void writer() {
        while (true) {
            List<HipoDataEvent> e = writeQueue.poll();
            if (e == null) {
                if (decoThreads.isEmpty() && procThreads.isEmpty() && procQueue.isEmpty() && writeQueue.isEmpty())
                    break;
                ReconUtil.sleep(1000);
            }
            else {
                for (int i=0; i<e.size(); i++) {
                    write(e.get(i));
                    writeEvents++;
                    progress.updateStatus();
                }
            }
        }
        writerExit();
        stop();
    }

    /**
     * The rethreader thread.
     * @param seconds delay before switching to next thread count
     * @param threads thread counts to use 
     */
    void rethreader(int seconds, int... threads) {
        System.out.println("recon-mutil::  ~~~~~~~~~ rethreading initiated ~~~~~~~~~");
        Map<Integer,Benchmark> benches = new LinkedHashMap<>();
        Map<Integer,ProgressPrintout> progs = new LinkedHashMap<>();
        while (writeEvents < 100 || !ReconUtil.isDone(decoThreads))
            ReconUtil.sleep(1000);
        for (CompletableFuture cf : procThreads) cf.cancel(true);
        System.out.println("recon-mutil::  ~~~~~~~~~ rethreading primed ~~~~~~~~~");
        for (int thread : threads) {
            benchmark = null;
            ReconUtil.taskset(0, thread);
            for (int j=0; j<thread; j++) {
                final int k = j;
                ReconUtil.addAndRemove(procThreads, CompletableFuture.runAsync(() -> { processor(k); }));
            }
            ReconUtil.sleep(10000);
            benchmark = new Benchmark(thread+" Threads Scaling ",BENCHMARK_NAMES);
            ReconUtil.sleep(seconds*1000);
            System.out.println(String.format("\nrecon-mutil:: ~~~~~~~~~ rethreading count %d ~~~~~~~~~\n",thread));
            System.out.println(progress.getUpdateString());
            System.out.println(benchmark);
            benches.put(thread, benchmark);
            progs.put(thread, progress);
            for (CompletableFuture cf : procThreads) cf.cancel(true);
        }
        String csv = ReconUtil.toCSV(progs, benches);
        System.out.println(csv);
        ReconUtil.writeFile("scaling-mutil.txt", csv);
        ReconUtil.gnuplotScaling("scaling-mutil.txt","scaling-mutil.svg");
        stop();
    }
    
    /**
     * Print the thread, queue, and event states.
     */
    void show() {
        String s1 = String.format("threads(r/d/p/w)=(%b/%b:%d/%b:%d/%b)",
                readerThread != null ? !readerThread.isDone() : false, 
                !ReconUtil.isDone(decoThreads),
                decoThreads.size(),
                !ReconUtil.isDone(procThreads),
                procThreads.size(),
                writerThread != null ? !writerThread.isDone() : false);
        String s2 = String.format(" queues(d/p/w)=(%d/%d/%d)",
                decoQueue.size()*EVENTS_PER_CHUNK,
                procQueue.size()*EVENTS_PER_CHUNK,
                writeQueue.size()*EVENTS_PER_CHUNK);
        String s3 = String.format(" events(r/w/t/f)=(%d/%d/%d/%d)",
                readEvents, writeEvents, taggedEvents.get(), failEvents);
        Logger.getLogger(ReconMutil.class.getName()).log(Level.CONFIG, () -> s1+" "+s2+" "+s3);
    }
  
    /**
     * Cancel threads, empty queues, and reset counters. 
     */
    void reset() {
        //serialPause.set(true);
        stop();
        if (writerThread != null) {
            //if (writer != null) writer.close();
            writerThread.cancel(true);
            //if (!parser.getOption("-t").stringValue().contains(","))
            //    if (benchmark != null) System.out.println(benchmark);
        }
        readEvents = 0;
        writeEvents = 0;
        failEvents = 0;
        taggedEvents.set(0);
    }

    /**
     * Cancel threads and empty the queues.
     */
    void stop() {
        if (readerThread != null) readerThread.cancel(true);
        for (CompletableFuture cf : decoThreads) cf.cancel(true);
        for (CompletableFuture cf : procThreads) cf.cancel(true);
        decoQueue.clear();
        procQueue.clear();
        writeQueue.clear();
    }
    
    abstract Object open(String filename);
    abstract Object read();
    abstract HipoDataEvent[] decode(int thread, Object event);
    abstract void process(int thread, HipoDataEvent event);
    abstract void write(HipoDataEvent event);
    abstract void readerExit(); 
    abstract void decoderExit(int thread);
    abstract void writerExit();

}
