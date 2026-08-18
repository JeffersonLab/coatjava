package org.jlab.clas.reco;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import org.json.JSONObject;
import org.jlab.coda.jevio.EvioException;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataSource;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;
import org.jlab.utils.ClaraYaml;
import org.jlab.utils.options.OptionParser;

/**
 *
 * @author baltzell
 */
public class EngineMultiProcessor extends EngineProcessor {

    DataSource reader;
    HipoDataSync writer;
    CompletableFuture readerThread;

    int threads = 4;
    int maxEvents = 0;
    int skipEvents = 0;
    int readEvents = 0;
    ArrayList<String> inputs = new ArrayList<>();
    ConcurrentLinkedQueue<DataEvent> readQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<DataEvent> writeQueue = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<DataEvent> procQueue = new ConcurrentLinkedQueue<>();

    public EngineMultiProcessor(int threads) {
        super();
    }

    public EngineMultiProcessor(int threads, int events, int skip) {
        super();
        this.maxEvents = events;
        this.skipEvents = skip;
    }

    void open() {
        if (inputs.get(0).endsWith(".hipo")) reader = new HipoDataSource();
        else reader = new EvioSource();
        reader.open(inputs.remove(0));
        updateDictionary((HipoDataSource)reader, writer);
    }

    void read() throws InterruptedException, EvioException {
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
            else open();
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

    void write() throws InterruptedException {
        while (true) {
            if (writeQueue.isEmpty()) {
                if (readerThread.isDone()) {
                    if (readQueue.isEmpty() && procQueue.isEmpty()) {
                        break;
                    }
                }
                Thread.sleep(100);
            }
            else writer.writeEvent(writeQueue.poll());
        }
    }

    public void processFiles(String output, String... input) {

        // create new reader and writer:
        for (int i=0; i<input.length; i++) inputs.add(input[i]);
        writer = new HipoDataSync();
        writer.setCompressionType(2);
        writer.open(output);

        // start reader thread:
        readerThread = CompletableFuture.supplyAsync(() -> {
            try { read(); } catch (InterruptedException | EvioException ex) {}
            return true;
        });

        // start writer thread:
        CompletableFuture writerThread = CompletableFuture.supplyAsync(() -> {
            try { write(); } catch (InterruptedException ex) {}
            return true;
        });

        // prime the queue:
        while (readQueue.size() < threads) {}

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

        // close shop:
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
