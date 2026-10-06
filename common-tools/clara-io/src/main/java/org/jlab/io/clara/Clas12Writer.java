package org.jlab.io.clara;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.jlab.clara.std.services.EventWriterException;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.helicity.HelicitySequenceDelayed;
import org.jlab.detector.serial.SerialHoncho;
import org.jlab.detector.serial.PostProcessor;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.jnp.utils.file.FileUtils;
import org.json.JSONObject;

/**
 * 
 * 1. Copies certain banks on-the-fly to new tag-1 events
 * 2. Caches helicity states, scaler readouts, and unix time
 * 3. Writes HEL::flip, RUN/HEL::scaler, and RUN::unix to new tag-1 events
 * 4. Runs post-processing, writing tag-1 information to all events 
 * 5. Adds .hipo to the output filename, if necessary
 * 6. Outputs a parallel file of select trigger bits with a raw schema
 * @author baltzell
 */
public class Clas12Writer extends HipoToHipoWriter {

    SerialHoncho serial;
    Bank runConfig;
    ConstantsManager conman;
    SchemaFactory fullSchema;
    boolean postprocess;

    HipoWriterSorted paraWriter;
    long paraCount;
    long paraTriggerMask;
    int paraTriggerPrescale;

    private void init(JSONObject opts) {
        fullSchema = new SchemaFactory();
        fullSchema.initFromDirectory(FileUtils.getEnvironmentPath("CLAS12DIR","etc/bankdefs/hipo4"));
        serial = new SerialHoncho(fullSchema);
        runConfig = new Bank(fullSchema.getSchema("RUN::config"));
        conman = new ConstantsManager();
        conman.init("/runcontrol/hwp","/runcontrol/helicity");
        postprocess = opts.optBoolean("postprocess", false);
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
        serial.finish(writer);
        super.closeWriter();
        if (postprocess) postprocess();
        serial.clear();
        if (paraTriggerMask > 0) paraWriter.close();
    }
 
    /**
     * Get the first valid run number from a RUN::config bank.
     * @return run
     */
    private int getRunNumber() {
        Event e = new Event();
        HipoReader r = new HipoReader();
        r.open(filename);
        while (r.hasNext()) {
            r.nextEvent(e);
            e.read(runConfig);
            if (runConfig.getRows()>0 && runConfig.getInt("run",0)>0)
                return runConfig.getInt("run",0);
        }
        return 0;
    }

    /**
     * Copy helicity/charge tag-1 information to all events.
     */
    private void postprocess() {
        int d = conman.getConstants(getRunNumber(), "/runcontrol/helicity").getIntValue("delay",0,0,0);
        HelicitySequenceDelayed helicity = new HelicitySequenceDelayed(d);
        helicity.addStream(serial.getHelicities());
        PostProcessor p = new PostProcessor(List.of(filename), fullSchema, helicity, serial.getScalers());
        HipoReader r = new HipoReader();
        r.open(filename);
        Event e = new Event();
        writer.open("pp_"+filename);
        while (r.hasNext()) {
            r.nextEvent(e);
            p.processEvent(e);
            HipoToHipoWriter.writeEvent(writer, e, schemaBankList);
        }
        writer.close();
        new File(filename).delete();
        new File("pp_"+filename).renameTo(new File(filename));
    }

}
