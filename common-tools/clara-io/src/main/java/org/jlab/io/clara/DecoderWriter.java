package org.jlab.io.clara;

import java.io.File;
import java.nio.file.Path;
import java.util.TreeSet;
import org.jlab.analysis.postprocess.Processor;
import org.jlab.clara.std.services.EventWriterException;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.decode.CLASDecoder4;
import org.jlab.detector.helicity.HelicitySequence;
import org.jlab.detector.helicity.HelicitySequenceDelayed;
import org.jlab.detector.helicity.HelicityState;
import org.jlab.detector.scalers.DaqScalersSequence;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.jnp.utils.file.FileUtils;
import org.json.JSONObject;

/**
 * Combined with DecoderReader, a port of the standard "decoder" to CLARA.
 *
 * 1. Copies certain banks on-the-fly to new tag-1 events
 * 2. Caches helicity states and scaler readouts, for later use in post-processing
 * 3. Writes the helicity sequence to HEL::flip banks in new tag-1 events
 * 4. Adds .hipo to the output filename, if necessary
 * 5. Runs post-processing, writing tag-1 information to all events 
 *
 * @author baltzell
 */
public class DecoderWriter extends HipoToHipoWriter {

    static final String[] TAG1BANKS = {"RUN::scaler","HEL::scaler","RAW::scaler","RAW::epics","HEL::flip","COAT::config"};

    Bank[] tag1banks;
    Bank runConfig;
    Bank helicityAdc;
    ConstantsManager conman;
    TreeSet<HelicityState> helicities;
    DaqScalersSequence scalers;
    SchemaFactory fullSchema;
    boolean postprocess;

    private void init(JSONObject opts) {
        fullSchema = new SchemaFactory();
        fullSchema.initFromDirectory(FileUtils.getEnvironmentPath("CLAS12DIR","etc/bankdefs/hipo4"));
        runConfig = new Bank(fullSchema.getSchema("RUN::config"));
        helicityAdc = new Bank(fullSchema.getSchema("HEL::adc"));
        helicities = new TreeSet<>();
        scalers = new DaqScalersSequence(fullSchema);
        conman = new ConstantsManager();
        conman.init("/runcontrol/hwp","/runcontrol/helicity");
        postprocess = opts.optBoolean("postprocess", false);
        if (opts.has("variation")) conman.setVariation(opts.getString("variation"));
        if (opts.has("timestamp")) conman.setTimeStamp(opts.getString("timestamp"));
        tag1banks = new Bank[TAG1BANKS.length];
        for (int i=0; i<tag1banks.length; ++i)
            tag1banks[i] = new Bank(fullSchema.getSchema(TAG1BANKS[i]));
    }

    @Override
    protected HipoWriterSorted createWriter(Path file, JSONObject opts) throws EventWriterException {
        try {
            init(opts);
            HipoWriterSorted w = new HipoWriterSorted();
            super.configure(w, opts);
            w.open(file.toString().endsWith(".hipo") ? file.toString() : file.toString()+".hipo");
            return w;
        } catch (Exception e) {
            throw new EventWriterException(e);
        }
    }

    /**
     * In addition to writing the incoming event, copies all tag-1 banks to new
     * tag-1 events and writes them, and stores helicity/scaler readings for later.
     * @param event
     * @throws EventWriterException 
     */
    @Override
    protected void writeEvent(Object event) throws EventWriterException {
        scalers.add((Event)event);
        ((Event)event).read(runConfig);
        ((Event)event).read(helicityAdc);
        helicities.add(HelicityState.createFromFadcBank(helicityAdc, runConfig, conman));
        Event t = CLASDecoder4.createTaggedEvent((Event)event, runConfig, tag1banks);
        if (!t.isEmpty()) writer.addEvent(t, 1);
        super.writeEvent(event);
    }

    /**
     * In addition to closing the writer, creates and writes tag-1 events with
     * HEL::flip bnks and clears old scaler/helicity readings.
     */
    @Override
    protected void closeWriter() {
        HelicitySequence.writeFlips(fullSchema, writer, helicities);
        super.closeWriter();
        if (postprocess) postprocess();
        // keep the latest helicity/scaler reading for the next file:
        while (helicities.size() > 60) helicities.pollFirst();
        scalers.clear(10);
    }
  
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
        HelicitySequenceDelayed h = new HelicitySequenceDelayed(d);
        h.addStream(helicities);
        Processor p = new Processor(fullSchema, h, scalers);
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
