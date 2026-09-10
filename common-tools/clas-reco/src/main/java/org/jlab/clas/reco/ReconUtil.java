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
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.utils.benchmark.Benchmark;
import org.json.JSONObject;

/**
 *
 * @author baltzell
 */
public class ReconUtil {
    
    /**
     * Catch interruptions in sleep.
     * @param milliseconds 
     */
    static void sleep(int milliseconds) {
        try { Thread.sleep(milliseconds); }
        catch (InterruptedException ex) {}
    }
   
    /**
     * Decode an event.
     * @param decoders decoder pool
     * @param bytes the EVIO byte buffer
     * @return resulting decoded event
     */
    static HipoDataEvent decode(int thread, CLASDecoderPool decoders, ByteBuffer bytes) {
        Benchmark.getInstance().resume(thread,"evio");
        EvioDataEvent evio = new EvioDataEvent(bytes.array(), ByteOrder.LITTLE_ENDIAN);
        Benchmark.getInstance().pause(thread,"evio");
        Benchmark.getInstance().resume(thread,"deco");
        CLASDecoder d = decoders.take();
        HipoDataEvent hipo = d.getDecodedDataEvenet(evio);
        decoders.put(d);
        Benchmark.getInstance().pause(thread,"deco");
        return hipo;
    }
  
    /**
     * Create and initialize a new engine.
     * @param label display name
     * @param clazz full class name
     * @param cfg engine configuration
     * @return the initialized engine
     */
    static ReconstructionEngine initEngine(String label, String clazz, JSONObject cfg) {
        ReconstructionEngine e = null;
        try {
            Class c = Class.forName(clazz);
            if (ReconstructionEngine.class.isAssignableFrom(c)==true){
                e = (ReconstructionEngine) c.newInstance();
                if (cfg != null && !cfg.toString().equals("null")) {
                    EngineData input = new EngineData();
                    input.setData(EngineDataType.JSON.mimeType(), cfg.toString());
                    e.configure(input);
                }
                else e.init();
            }
            else Logger.getLogger(ReconMutil.class.getPackage().getName())
                    .log(clazz.contains("DecoderEngine") ? Level.INFO : Level.SEVERE,
                    "Class is not a reconstruction engine : {0}", clazz);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
            Logger.getLogger(ReconMutil.class.getPackage().getName()).log(Level.SEVERE, null, ex);
        }
        return e;
    }

    static void setSchema(HipoWriterSorted writer, JSONObject json, String schemaDir) {
        SchemaFactory s = new SchemaFactory();
        s.initFromDirectory(schemaDir);
        if (json != null) {
            if (json.has("wildcard")) {
                SchemaFactory s2 = s.reduce(json.getString("wildcard"));
                writer.getSchemaFactory().copy(s2);
            }
        }
        else writer.getSchemaFactory().copy(s);
    }

    static List<Bank> getSchema(HipoWriterSorted writer, JSONObject json) {
        List<Bank> banks = new ArrayList<>();
        if (json != null && json.has("wildcard")) {
            if (json.optBoolean("schema_filter",true)) {
                int schemaSize = writer.getSchemaFactory().getSchemaList().size();
                for (int i=0; i<schemaSize; i++) {
                    Bank dataBank = new Bank(writer.getSchemaFactory().getSchemaList().get(i));
                    banks.add(dataBank);
                }
            }
        }
        return banks;
    }

    static List<String> readResourceLines(String resource) {
        List<String> ret = new ArrayList<>();
        InputStream is = ReconUtil.class.getClassLoader().getResourceAsStream("org/jlab/clas/reco/"+resource);
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        try {
            for (String line; (line=br.readLine()) != null;)
                ret.add(line);
        } catch (IOException ex) {
            System.getLogger(ReconUtil.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        return ret;
    }
}
