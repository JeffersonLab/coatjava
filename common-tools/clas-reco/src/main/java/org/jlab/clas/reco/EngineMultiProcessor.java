package org.jlab.clas.reco;

import java.util.logging.Logger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import org.json.JSONObject;
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
public class EngineMultiProcessor {

    int threads;
    DataSource reader;
    HipoDataSync writer;
    ConcurrentLinkedQueue<DataEvent> readQueue;
    ConcurrentLinkedQueue<DataEvent> writeQueue;
    ConcurrentLinkedQueue<DataEvent> procQueue;
    EngineProcessor engines;
    ExecutorService executor;

    public EngineMultiProcessor(int threads) {
        this.threads = threads;
        engines = new EngineProcessor();
        readQueue = new ConcurrentLinkedQueue<>();
        writeQueue = new ConcurrentLinkedQueue<>();
        procQueue = new ConcurrentLinkedQueue<>();
        executor = Executors.newFixedThreadPool(threads);
    }
    void read() throws InterruptedException {
        while (reader.hasEvent()) {
            if (readQueue.size() > 10*threads) Thread.sleep(100);
            else readQueue.offer(reader.getNextEvent());
        }
    }
    void process() {
        while (!readQueue.isEmpty()) {
            DataEvent event = readQueue.poll();
            procQueue.offer(event);
            engines.processEvent(event);
            procQueue.remove(event);
            writeQueue.offer(event);
        }
    }
    void write() throws InterruptedException {
        while (true) {
            if (!writeQueue.isEmpty())
                writer.writeEvent(writeQueue.poll());
            else Thread.sleep(1000);
        }
    }
    public void open(String input, String output, int events, int skip) {
        writer = new HipoDataSync();
        writer.setCompressionType(2);
        if (input.endsWith(".hipo")) reader = new HipoDataSource();
        else reader = new EvioSource();
        reader.open(input);
        writer.open(output);
        engines.updateDictionary((HipoDataSource)reader, writer);
        CompletableFuture.supplyAsync(() -> {
            try { read(); } catch (InterruptedException ex) {}
            return true;
        }, executor);
        CompletableFuture.supplyAsync(() -> {
            try { write(); } catch (InterruptedException ex) {}
            return true;
        }, executor);
        try { Thread.sleep(5000); } catch (InterruptedException ex) {}
        for (int i=0; i<threads; i++){
            CompletableFuture.supplyAsync(() -> {
                process();
                return true;
            }, executor);
        }
        while (!readQueue.isEmpty() || !writeQueue.isEmpty() || !procQueue.isEmpty()) {
            try { Thread.sleep(1000); } catch (InterruptedException ex) {}
        }
        executor.shutdownNow();
        writer.close();
        reader.close();
    }

    public static void main(String[] args) {

        OptionParser parser = new OptionParser("recon-util");
        parser.addRequired("-o","output.hipo");
        parser.addRequired("-i","input.evio/hipo");
        parser.setRequiresInputList(false);
        parser.addOption("-c","0","use default configuration [0 - no, 1 - yes/default, 2 - all services] ");
        parser.addOption("-s","-1","number of events to skip");
        parser.addOption("-n","-1","number of events to process");
        parser.addOption("-y","0","yaml file");
        parser.addOption("-u","true","update dictionary from writer ? ");
        parser.addOption("-S",null,"schema directory");
        parser.addOption("-B",null,"background file");
        parser.addOption("-P",null,"preload file for post-processing");
        parser.addOption("-R","0","rebuild scalers");
        parser.addOption("-H","0","restream helicity");
        parser.addOption("-t","1","threads");
        parser.parse(args);
        parser.syncLogLevel(Logger.getLogger(EngineMultiProcessor.class.getPackage().getName()));

        EngineMultiProcessor multi = new EngineMultiProcessor(parser.getOption("-t").intValue());

        if (parser.getOption("-u").stringValue().contains("false"))
            multi.engines.updateDictionary = false;

        // service list from YAML:
        if(!parser.getOption("-y").stringValue().equals("0")) {
            ClaraYaml yaml = new ClaraYaml(parser.getOption("-y").stringValue());
            if (yaml.schemaDirectory() != null) {
                multi.engines.setBanksToKeep(yaml.schemaDirectory());
            }
            for (JSONObject service : yaml.services()) {
                JSONObject cfg = yaml.filter(service.getString("name"));
                if (cfg.length() > 0) {
                    multi.engines.addEngine(service.getString("name"),service.getString("class"),cfg.toString());
                } else {
                    multi.engines.addEngine(service.getString("name"),service.getString("class"));
                }
            }
        }
        // built-in service list:
        else if (parser.getOption("-c").intValue() > 0) {
            if (parser.getOption("-c").intValue() > 2) {
                multi.engines.initCaloDebug();
            } else if(parser.getOption("-c").intValue() == 2) {
                multi.engines.initAll();
            } else {
                multi.engines.initDefault();
            }
        }
        // user-defined service list:
        else {
            for(String engine : parser.getInputList()){
                System.out.println("Adding reconstruction engine " + engine);
                multi.engines.addEngine(engine);
            }
        }

        // command-line schema overrides YAML:
        if (parser.getOption("-S").stringValue() != null)
            multi.engines.setBanksToKeep(parser.getOption("-S").stringValue());

        // command-line filename for background merging overrides YAML:
        if (parser.getOption("-B").stringValue() != null)
            multi.engines.setBackgroundFiles(parser.getOption("-B").stringValue());

        // command-line filename for post-processing overrides YAML:
        if (parser.getOption("-P").stringValue() != null) {
            multi.engines.setPreloadFiles(parser.getOption("-P").stringValue(),
                parser.getOption("-H").intValue()!=0,
                parser.getOption("-R").intValue()!=0);
        }

        multi.open(parser.getOption("-i").stringValue(),
            parser.getOption("-o").stringValue(),
            parser.getOption("-s").intValue(),
            parser.getOption("-n").intValue());
    }

}
