package org.jlab.io.clara;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.text.StringSubstitutor;

import org.jlab.clara.engine.EngineDataType;
import org.jlab.clara.std.services.AbstractEventWriterService;
import org.jlab.clara.std.services.EventWriterException;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoWriter;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.jnp.utils.file.FileUtils;
import org.json.JSONObject;

/**
 * Service that converts HIPO transient data to HIPO persistent data
 * (i.e. writes HIPO events to an output file).
 */
public class HipoToHipoWriter extends AbstractEventWriterService<HipoWriterSorted> {

    private static final String CONF_COMPRESSION = "compression";
    private static final String CONF_SCHEMA_DIR = "schema_dir";
    private static final String CONF_SCHEMA_FILTER = "schema_filter";
    private static final String CONF_SCHEMA_WILDCARD = "wildcard";
    
    protected final List<Bank> schemaBankList = new ArrayList<Bank>();
    private final StringSubstitutor envSubstitutor = new StringSubstitutor(System.getenv());

    private int compression = 2;
    protected String filename;

    @Override
    protected HipoWriterSorted createWriter(Path file, JSONObject opts) throws EventWriterException {
        try {
            filename = file.toString();
            HipoWriterSorted writer = new HipoWriterSorted();
            configure(writer, opts);
            writer.open(filename);
            return writer;
        } catch (Exception e) {
            throw new EventWriterException(e);
        }
    }

    
    protected void configure(HipoWriterSorted writer, JSONObject opts) {
        schemaBankList.clear();
        if (opts.has(CONF_COMPRESSION)) {
            compression = opts.getInt(CONF_COMPRESSION);
            System.out.printf("%s service: compression level = %d%n", getName(), compression);
        }
        writer.setCompressionType(compression);

        String schemaDir = FileUtils.getEnvironmentPath("CLAS12DIR", "etc/bankdefs/hipo4");
        if (opts.has(CONF_SCHEMA_DIR)) {
            schemaDir = opts.getString(CONF_SCHEMA_DIR);
            schemaDir = envSubstitutor.replace(schemaDir);
            System.out.printf("%s service: schema directory = %s%n", getName(), schemaDir);
        }

        SchemaFactory factory = new SchemaFactory();
        factory.initFromDirectory(schemaDir);

        if(opts.has(CONF_SCHEMA_WILDCARD)==true){
            String wildcard = opts.getString("wildcard");
            SchemaFactory f2 = factory.reduce(wildcard);
            writer.getSchemaFactory().copy(f2);
        } else {
            writer.getSchemaFactory().copy(factory);
        }
        
        if (opts.has(CONF_SCHEMA_DIR)==true||opts.has(CONF_SCHEMA_WILDCARD)==true) {
            boolean useFilter = opts.optBoolean(CONF_SCHEMA_FILTER, true);
            System.out.printf("%s service: schema filter = %b%n", getName(), useFilter);
            if(useFilter==true){
                int schemaSize = writer.getSchemaFactory().getSchemaList().size();
                for(int i = 0; i < schemaSize; i++){
                    Bank dataBank = new Bank(writer.getSchemaFactory().getSchemaList().get(i));
                    schemaBankList.add(dataBank);
                }
            }
        }

        System.out.printf("SERVICE WRITER :: [filter] %s\n",opts.has(HipoToHipoWriter.CONF_SCHEMA_FILTER));
        System.out.printf("SERVICE WRITER :: [dir] %s\n",opts.has(HipoToHipoWriter.CONF_SCHEMA_DIR));
        System.out.printf("SERVICE WRITER :: [wildcard] %s\n",opts.has(HipoToHipoWriter.CONF_SCHEMA_WILDCARD));
    }

    private Method getSchemaFilterSetter() throws NoSuchMethodException, SecurityException {
        return HipoWriter.class.getMethod("setSchemaFilter", boolean.class);
    }

    @Override
    protected void closeWriter() {
        writer.close();
        schemaBankList.clear();
    }

    public static void writeEvent(HipoWriterSorted w, Event e, List<Bank> schema) {
        int tag = e.getEventTag();
        if (tag==1 || schema.isEmpty()) {
            w.addEvent(e,tag);
        }
        else {
            w.addEvent(e.reduceEvent(schema),tag);
        }
    }

    @Override
    protected void writeEvent(Object event) throws EventWriterException {
        try {
            writeEvent(writer, (Event)event, schemaBankList);
        } catch (Exception e) {
            throw new EventWriterException(e);
        }
    }

    @Override
    protected EngineDataType getDataType() {
        return Clas12Types.HIPO;
    }
}
