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
import org.jlab.utils.ClaraYaml;
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
    private static final String CONF_SCHEMA_PRESCALE = "schema_prescale";
    
    protected final List<Bank> schemaBankList = new ArrayList<Bank>();
    private final StringSubstitutor envSubstitutor = new StringSubstitutor(System.getenv());

    private int compression = 2;
    protected String filename;

    private long schemaPrescaleEvents = 0;
    private int schemaPrescale = 0;
    
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

        // set prescale:
        if (opts.has(CONF_SCHEMA_PRESCALE)) {
            schemaPrescale = opts.getInt(CONF_SCHEMA_PRESCALE);
        }

        // set compression:
        if (opts.has(CONF_COMPRESSION)) {
            compression = opts.getInt(CONF_COMPRESSION);
        }
        writer.setCompressionType(compression);

        // create full schema:
        SchemaFactory fullSchema = new SchemaFactory();
        fullSchema.initFromDirectory(FileUtils.getEnvironmentPath("CLAS12DIR","etc/bankdefs/hipo4"));
       
        // choose user schema directory:
        String schemaDir = FileUtils.getEnvironmentPath("CLAS12DIR", "etc/bankdefs/hipo4");
        if (opts.has(CONF_SCHEMA_DIR)) {
            // Run YAML values throuh env-substitor: 
            schemaDir = opts.getString(CONF_SCHEMA_DIR).trim();
            schemaDir = envSubstitutor.replace(schemaDir);
            // If it's not already an absolute path, assume it's the name of a
            // stock schema that comes with COATJAVA and get the full path to it:
            if (!schemaDir.startsWith("/")) schemaDir = ClaraYaml.getStockSchemaDirectory(schemaDir);
            System.out.printf("%s service: schema directory = %s%n", getName(), schemaDir);
        }

        // create user schemaa:
        SchemaFactory factory = new SchemaFactory();
        factory.initFromDirectory(schemaDir);

        // set the writer's schema factory:
        if(opts.has(CONF_SCHEMA_WILDCARD)==true){
            // apply a wildcard reduction on the user's schema:
            String wildcard = opts.getString("wildcard");
            SchemaFactory f2 = factory.reduce(wildcard);
            writer.getSchemaFactory().copy(f2);
        } else {
            writer.getSchemaFactory().copy(factory);
        }
       
        // set the bank list for filtering: 
        schemaBankList.clear();
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

        // set the writer's schema factory:
        writer.getSchemaFactory().copy(fullSchema);

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
        if (tag==1 || schema == null || schema.isEmpty()) {
            w.addEvent(e,tag);
        }
        else {
            w.addEvent(e.reduceEvent(schema),tag);
        }
    }

    @Override
    protected void writeEvent(Object event) throws EventWriterException {
        try {
            if (schemaPrescale <= 0 || (++schemaPrescaleEvents % schemaPrescale) != 0)
                writeEvent(writer, (Event)event, schemaBankList);
            else
                writeEvent(writer, (Event)event, null);
        } catch (Exception e) {
            throw new EventWriterException(e);
        }
    }

    @Override
    protected EngineDataType getDataType() {
        return Clas12Types.HIPO;
    }
}
