package org.jlab.clas.reco;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.clara.engine.EngineData;
import org.jlab.clara.engine.EngineDataType;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.utils.ClaraYaml;
import org.jlab.utils.benchmark.Benchmark;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.options.OptionValue;
import org.jlab.utils.system.ClasUtilsFile;
import org.json.JSONObject;

/**
 * Static utility methods.
 *
 */
public class ReconUtil {

    static OptionParser getParser() {
        OptionParser parser = new OptionParser("recon-util");
        parser.addRequired("-o","output.hipo");
        parser.addRequired("-i","input.evio/hipo");
        parser.addOption("-c","0","use default configuration [0 - no, 1 - yes/default, 2 - all services] ");
        parser.addOption("-s","0","number of events to skip");
        parser.addOption("-n","0","number of events to process");
        parser.addOption("-y","0","yaml file");
        parser.addOption("-u","true","update dictionary from writer ? ");
        parser.addOption("-S",null,"schema directory");
        parser.addOption("-B",null,"background file");
        parser.addOption("-P",null,"preload file for post-processing");
        parser.addOption("-R","0","rebuild scalers");
        parser.addOption("-H","0","restream helicity");
        parser.setRequiresInputList(false);
        return parser;
    }

    /**
     * Get a new schema factory, potentially filtered based on yaml.
     * @param schema
     * @param yaml
     * @return
     */
    static SchemaFactory getSchemaFactory(OptionValue schema, ClaraYaml yaml) {
        SchemaFactory ret = new SchemaFactory();
        SchemaFactory stock = new SchemaFactory();
        stock.initFromDirectory(getSchemaDirectory(schema, yaml));
        if (yaml == null) {
            ret.copy(stock);
        } else {
            JSONObject json = yaml.filter("writer");
            if (json.has("wildcard")) {
                ret.copy(stock.reduce(json.getString("wildcard")));
            } else {
                ret.copy(stock);
            }
        }
        return ret;
    }

    /**
     * Choose schema directory, prioritizing command-line, yaml, and then default.
     * @param parser
     * @param yaml
     * @return the chosen schema directory
     */
    static String getSchemaDirectory(OptionValue opt, ClaraYaml yaml) {
        String d = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4");
        if (yaml != null && yaml.getSchemaDirectory() != null)
            d = yaml.getSchemaDirectory();
        if (opt.stringValue() != null)
            d = opt.stringValue();
        return d;
    }

    /**
     *
     * @param schema
     * @param yaml
     * @return
     */
    static List<Bank> getBankList(SchemaFactory schema, ClaraYaml yaml) {
        List<Bank> banks = new ArrayList<>();
        if (yaml != null) {
            JSONObject json = yaml.filter("writer");
            if (json.has("wildcard")) {
                if (json.optBoolean("schema_filter", true)) {
                    int schemaSize = schema.getSchemaList().size();
                    for (int i = 0; i < schemaSize; i++) {
                        banks.add(new Bank(schema.getSchemaList().get(i)));
                    }
                }
            }
        }
        return banks;
    }

    /**
     * Add a new engine to the list.
     * @param label display name
     * @param clazz full class name
     * @param cfg engine configuration
     * @return
     */
    static ReconstructionEngine addEngine(Map<String, ReconstructionEngine> engines, String label, String clazz, JSONObject cfg) {
        ReconstructionEngine engine = null;
        try {
            Class c = Class.forName(clazz);
            if (ReconstructionEngine.class.isAssignableFrom(c) == true) {
                engine = (ReconstructionEngine) c.newInstance();
                if (cfg != null && !cfg.toString().equals("null")) {
                    EngineData input = new EngineData();
                    input.setData(EngineDataType.JSON.mimeType(), cfg.toString());
                    engine.configure(input);
                } else {
                    engine.init();
                }
                engines.put(label == null ? engine.getName() : label, engine);
            } else {
                Logger.getLogger(ReconMutil.class.getPackage().getName()).log(clazz.contains("DecoderEngine") ? Level.INFO : Level.SEVERE, "Class is not a reconstruction engine : {0}", clazz);
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            Logger.getLogger(ReconMutil.class.getPackage().getName()).log(Level.SEVERE, null, ex);
        }
        return engine;
    }

    /**
     * Catch interruptions in sleep.
     * @param milliseconds
     */
    static void sleep(int milliseconds) {
        try { Thread.sleep(milliseconds); }
        catch (InterruptedException ex) {}
    }

    static void writeFile(String filename, String content) {
        Path p = Path.of(filename);
        try {
            Files.writeString(p, content);
        } catch (IOException ex) {
            System.getLogger(ReconUtil.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        
    }

    /**
     * Just get the contents of a text file resource.
     * @param resource
     * @return 
     */
    static List<String> readResourceLines(String resource) {
        List<String> lines = new ArrayList<>();
        InputStream is = ReconMutil.class.getClassLoader().getResourceAsStream(resource);
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        try {
            for (String line; (line = br.readLine()) != null;) lines.add(line);
        } catch (IOException ex) {
            System.getLogger(ReconMutil.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        return lines;
    }
  
    /**
     * Check whether any of the given futures are still running.
     * @param queue
     * @return whether any are still running
     */
    static boolean isDone(ConcurrentLinkedQueue<CompletableFuture> queue) {
        return queue.stream().filter(f -> !f.isDone()).toList().isEmpty();
    }

    /**
     * Add a thread to the queue and make it remove itself when done.
     * @param queue
     * @param future 
     */
    static void addAndRemove(ConcurrentLinkedQueue<CompletableFuture> queue, CompletableFuture future) {
        future.whenCompleteAsync((result,exception) -> { queue.remove(future); });
        queue.offer(future);
    }

    static String toCSV(Map<Integer,Benchmark> benches) {
        for (Benchmark b : benches.values()) 
            b.sortHeaders();
        List<String> csv = new ArrayList<>();
        String head = (new ArrayList<>(benches.values())).get(0).toCSV()[0];
        csv.add("threads," + head);
        for (int threads : benches.keySet())
            csv.add(threads+","+benches.get(threads).toCSV()[1]);
        return String.join("\n",csv);
    }
}
