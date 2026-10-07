package org.jlab.io.clara;

import java.nio.file.Path;
import org.jlab.clara.std.services.EventWriterException;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.serial.SerialHoncho;
import org.jlab.detector.serial.Occupancer;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.jnp.utils.file.FileUtils;
import org.json.JSONObject;

/**
 * 
 * 1. Copies certain banks on-the-fly to new tag-1 events
 * 2. Caches helicity states, scaler readouts, and unix time
 * 3. Writes HEL::flip, RUN/HEL::scaler, and RUN::unix to new tag-1 events
 * 5. Adds .hipo to the output filename, if necessary
 * 6. Outputs a parallel file of select trigger bits with a raw schema
 * @author baltzell
 */
public class Clas12Writer extends HipoToHipoWriter {

    Occupancer occupancer;
    SerialHoncho serial;
    ConstantsManager conman;
    SchemaFactory fullSchema;
    Bank runConfig;

    HipoWriterSorted paraWriter;
    long paraCount;
    long paraTriggerMask;
    int paraTriggerPrescale;

    private void init(JSONObject opts) {
        occupancer = new Occupancer();
        fullSchema = new SchemaFactory();
        fullSchema.initFromDirectory(FileUtils.getEnvironmentPath("CLAS12DIR","etc/bankdefs/hipo4"));
        serial = new SerialHoncho(fullSchema);
        conman = new ConstantsManager();
        runConfig = new Bank(fullSchema.getSchema("RUN::config"));
        conman.init("/runcontrol/hwp","/runcontrol/helicity");
        paraTriggerMask = opts.optLong("paraTriggerMask", 0);
        paraTriggerPrescale = opts.optInt("paraTriggerPrescale", 0);
        if (opts.has("variation")) conman.setVariation(opts.getString("variation"));
        if (opts.has("timestamp")) conman.setTimeStamp(opts.getString("timestamp"));
    }

    @Override
    protected HipoWriterSorted createWriter(Path file, JSONObject opts) throws EventWriterException {
        try {
            init(opts);
            HipoWriterSorted w = new HipoWriterSorted();
            super.configure(w, opts);
            String dirname = file.getParent().toString();
            String basename = file.getFileName().toString();
            if (!basename.endsWith(".hipo")) basename += ".hipo";
            if (paraTriggerMask > 0) {
                paraWriter = new HipoWriterSorted();
                paraWriter.getSchemaFactory().copy(fullSchema);
                paraWriter.open(dirname + "/tb" + basename.substring(basename.indexOf("_")));
            }
            w.open(dirname + "/" + basename);
            return w;
        } catch (Exception e) {
            throw new EventWriterException(e);
        }
    }

    @Override
    protected void writeEvent(Object event) throws EventWriterException {
        Event t = serial.read((Event)event);
        if (!t.isEmpty()) writer.addEvent(t, 1);
        occupancer.process(((Event)event));
        super.writeEvent(event);
        writeRaw((Event)event, t);
    }

    void writeRaw(Event physics, Event tagged) {
        if (paraTriggerMask > 0) {
            physics.read(runConfig);
            if (runConfig.getRows()>0 && (runConfig.getLong("trigger",0) & paraTriggerMask) != 0)
                if (paraTriggerPrescale<=0 || (++paraCount % paraTriggerPrescale) == 0)
                    paraWriter.addEvent(physics);
            if (!tagged.isEmpty()) paraWriter.addEvent(tagged, tagged.getEventTag());
        }
    }

    @Override
    protected void closeWriter() {
        serial.closure(writer);
        super.closeWriter();
        serial.clear();
        if (paraTriggerMask > 0) paraWriter.close();
    }

}
