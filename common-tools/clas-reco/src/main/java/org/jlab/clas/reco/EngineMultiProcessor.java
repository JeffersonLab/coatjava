package org.jlab.clas.reco;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;
import org.jlab.coda.jevio.EvioException;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataSource;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;
import org.jlab.utils.ClaraYaml;
import org.jlab.utils.options.OptionParser;
import org.json.JSONObject;

/**
 *
 * @author baltzell
 */
public class EngineMultiProcessor extends EngineProcessor {

    int maxEvents = 0;
    int skipEvents = 0;
    int readEvents = 0;
    ArrayList<String> inputs = new ArrayList<>();
    DataSource reader;
    HipoDataSync writer;
    ThreadPoolExecutor executor;
    ConcurrentLinkedQueue<DataEvent> readQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<DataEvent> writeQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<DataEvent> procQueue = new ConcurrentLinkedQueue<>();

    public EngineMultiProcessor(int threads) {
        super();
        executor = (ThreadPoolExecutor)Executors.newFixedThreadPool(threads + 2);
    }
    
    public EngineMultiProcessor(int threads, int events, int skip) {
        super();
        this.maxEvents = events;
        this.skipEvents = skip;
        executor = (ThreadPoolExecutor)Executors.newFixedThreadPool(threads + 2);
    }

    void open(String output, String... input) {
        for (int i=0; i<input.length; i++) inputs.add(input[i]);
        writer = new HipoDataSync();
        writer.setCompressionType(2);
        writer.open(output);
        open(inputs.remove(0));
    }

    void open(String input) {
        if (input.endsWith(".hipo")) reader = new HipoDataSource();
        else reader = new EvioSource();
        reader.open(input);
        updateDictionary((HipoDataSource)reader, writer);
    }

    void read() throws InterruptedException, EvioException {
        while (true) {
            if (readEvents > 0 && maxEvents > readEvents) break;
            if (reader.hasEvent()) {
                if (readQueue.size() > 10*executor.getMaximumPoolSize()) Thread.sleep(100);
                else {
                    readEvents++;
                    DataEvent event = reader.getNextEvent();
                    if (skipEvents < 0 || readEvents > skipEvents) readQueue.offer(event);
                }
            }
            else if (inputs.isEmpty()) break;
            else open(inputs.remove(0));
        }
    }

    void process() {
        while (!readQueue.isEmpty()) {
            DataEvent event = readQueue.poll();
            procQueue.offer(event);
            processEvent(event);
            procQueue.remove(event);
            writeQueue.offer(event);
        }
    }

    void write() throws InterruptedException {
        while (true) {
            if (writeQueue.isEmpty()) Thread.sleep(100);
            else writer.writeEvent(writeQueue.poll());
        }
    }

    public void processFiles(String output, String... input) {

        // create new reader and writer:
        open(output, input);

        // start reader thread:
        CompletableFuture.supplyAsync(() -> {
            try { read(); } catch (InterruptedException | EvioException ex) {}
            return true;
        }, executor);
        
        // start writer thread:
        CompletableFuture.supplyAsync(() -> {
            try { write(); } catch (InterruptedException ex) {}
            return true;
        }, executor);

        // prime the queue:
        while (readQueue.size() < executor.getMaximumPoolSize()) {}

        // start processor threads:
        for (int i=0; i<executor.getMaximumPoolSize(); i++) {
            CompletableFuture.supplyAsync(() -> {
                process();
                return true;
            }, executor);
        }

        // wait for queues to be empty:
        while (!readQueue.isEmpty() || !writeQueue.isEmpty() || !procQueue.isEmpty()) {
            try { Thread.sleep(1000); } catch (InterruptedException ex) {}
        }

        // close shop:
        executor.shutdownNow();
        writer.close();
        reader.close();
    }
    
    public static void main(String[] args) {

        OptionParser parser = new OptionParser("recon-util");
        parser.addRequired("-o","output.hipo");
        parser.addRequired("-i","input.evio/hipo");
        parser.addOption("-c","0","use default configuration [0 - no, 1 - yes/default, 2 - all services] ");
        parser.addOption("-s","-1","number of events to skip");
        parser.addOption("-n","-1","number of events to process");
        parser.addOption("-t","1","number of threads");
        parser.addOption("-y","0","yaml file");
        parser.addOption("-u","true","update dictionary from writer ? ");
        parser.addOption("-S",null,"schema directory");
        parser.addOption("-B",null,"background file");
        parser.addOption("-P",null,"preload file for post-processing");
        parser.addOption("-R","0","rebuild scalers");
        parser.addOption("-H","0","restream helicity");
        parser.setRequiresInputList(false);
        parser.parse(args);
        parser.syncLogLevel(Logger.getLogger(EngineProcessor.class.getPackage().getName()));

        EngineMultiProcessor proc = new EngineMultiProcessor(parser.getOption("-t").intValue());

        if (parser.getOption("-u").stringValue().contains("false")) {
            proc.updateDictionary = false;
        }

        // services from YAML:
        if (!parser.getOption("-y").stringValue().equals("0")) {
            ClaraYaml yaml = new ClaraYaml(parser.getOption("-y").stringValue());
            if (yaml.schemaDirectory() != null) {
                proc.setBanksToKeep(yaml.schemaDirectory());
            }
            for (JSONObject service : yaml.services()) {
                JSONObject cfg = yaml.filter(service.getString("name"));
                if (cfg.length() > 0) {
                    proc.addEngine(service.getString("name"),service.getString("class"),cfg.toString());
                } else {
                    proc.addEngine(service.getString("name"),service.getString("class"));
                }
            }
        }
        // services from builtin configurations:
        else if (parser.getOption("-c").intValue() > 0){
            if(parser.getOption("-c").intValue() > 2){
                proc.initCaloDebug();
            } else if(parser.getOption("-c").intValue() == 2){
                proc.initAll();
            } else {
                proc.initDefault();
            }
        }
        // user-defined services:
        else {
            for(String engine : parser.getInputList()){
                System.out.println("Adding reconstruction engine " + engine);
                proc.addEngine(engine);
            }
        }

        // command-line schema overrides YAML:
        if (parser.getOption("-S").stringValue() != null)
            proc.setBanksToKeep(parser.getOption("-S").stringValue());

        // command-line filename for background merging overrides YAML:
        if (parser.getOption("-B").stringValue() != null)
            proc.setBackgroundFiles(parser.getOption("-B").stringValue());
        
        // command-line filename for post-processing overrides YAML:
        if (parser.getOption("-P").stringValue() != null) {
            proc.setPreloadFiles(parser.getOption("-P").stringValue(),
                parser.getOption("-H").intValue()!=0,
                parser.getOption("-R").intValue()!=0);
        }

        proc.processFiles(parser.getOption("-o").stringValue(),
                          parser.getOption("-i").stringValue());
    }
}
