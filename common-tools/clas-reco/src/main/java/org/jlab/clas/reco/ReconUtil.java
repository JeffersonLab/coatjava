package org.jlab.clas.reco;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.clara.engine.EngineData;
import org.jlab.clara.engine.EngineDataType;
import org.jlab.detector.decode.CLASDecoder;
import org.jlab.detector.decode.CLASDecoderPool;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.utils.ClaraYaml;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.system.ClasUtilsFile;
import org.json.JSONObject;

public class ReconUtil {

    /**
     * Get a new schema factory, potentially filtered based on yaml.
     * @param parser
     * @param yaml
     * @return
     */
    static SchemaFactory getSchemaFactory(OptionParser parser, ClaraYaml yaml) {
        SchemaFactory ret = new SchemaFactory();
        SchemaFactory stock = new SchemaFactory();
        stock.initFromDirectory(getSchemaDirectory(parser, yaml));
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
    static String getSchemaDirectory(OptionParser parser, ClaraYaml yaml) {
        String d = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4");
        if (yaml != null && yaml.getSchemaDirectory() != null)
            d = yaml.getSchemaDirectory();
        if (!parser.getOption("-S").isDefault())
            d = parser.getOption("-S").stringValue();
        return d;
    }

    /**
     * Get a list of banks in the schema for filtering.
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

    /** 
     * Just get the ascii contents of a resource file.
     * @param resource
     * @return files lines
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
     * Decode an event.
     * @param bytes an EVIO event byte buffer
     * @return decoded HIPO event
     */
    public static HipoDataEvent decode(ByteBuffer bytes) {
        EvioDataEvent evio = new EvioDataEvent(bytes.array(), ByteOrder.LITTLE_ENDIAN);
        CLASDecoder d = CLASDecoderPool.getInstance().take();
        HipoDataEvent hipo = d.getDecodedDataEvent(evio);
        CLASDecoderPool.getInstance().put(d);
        return hipo;
    }

}
