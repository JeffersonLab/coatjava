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
 * Combined with EvioToHipoReader, a port of the standard "decoder" to CLARA.
 *
 * 1. Converts EVIO to HIPO, translation tables, pulse extraction
 * 2. Copies special banks on-the-fly to new tag-1 events
 * 3. Caches helicity states and scaler readouts, for later use in post-processing.
 * 4. Upon close, writes the helicity sequence to HEL::flip banks in new tag-1 events.
 * 
 * @author baltzell
 */
public class DecoderWriter extends HipoToHipoWriter {

    int tag = 1;
    String[] bankNames = {"RUN::scaler","HEL::scaler","RAW::scaler","RAW::epics","HEL::flip","COAT::config"};
    Bank[] banks;
    Bank runConfig;
    Bank helicityAdc;
    ConstantsManager conman;
    TreeSet<HelicityState> helicities;
    DaqScalersSequence scalers;
    SchemaFactory fullSchema;
    int runNumber;
    boolean postprocess = false;

    private void init(JSONObject opts) {
        runNumber = 0;
        fullSchema = new SchemaFactory();
        fullSchema.initFromDirectory(FileUtils.getEnvironmentPath("CLAS12DIR","etc/bankdefs/hipo4"));
        runConfig = new Bank(fullSchema.getSchema("RUN::config"));
        helicityAdc = new Bank(fullSchema.getSchema("HEL::adc"));
        helicities = new TreeSet<>();
        scalers = new DaqScalersSequence(fullSchema);
        conman = new ConstantsManager();
        conman.init("/runcontrol/hwp","/runcontrol/helicity");
        if (opts.has("postprocess")) postprocess = opts.getBoolean("postprocess");
        if (opts.has("variation")) conman.setVariation(opts.getString("variation"));
        if (opts.has("timestamp")) conman.setTimeStamp(opts.getString("timestamp"));
        if (opts.has("tag")) tag = opts.getInt("tag");
        if (opts.has("banks")) bankNames = opts.getString("banks").split(",");
        banks = new Bank[bankNames.length];
        for (int i=0; i<banks.length; ++i)
            banks[i] = new Bank(fullSchema.getSchema(bankNames[i]));
    }

    @Override
    protected HipoWriterSorted createWriter(Path file, JSONObject opts) throws EventWriterException {
        try {
            init(opts);
            HipoWriterSorted w = new HipoWriterSorted();
            super.configure(w, opts);
            w.open(file.toString());
            return w;
        } catch (Exception e) {
            throw new EventWriterException(e);
        }
    }

    @Override
    protected void writeEvent(Object event) throws EventWriterException {
        scalers.add((Event)event);
        ((Event)event).read(runConfig);
        if (runConfig.getRows() > 0) {
            int r = runConfig.getInt("run",0);
            if (r > 0) runNumber = r;
        }
        ((Event)event).read(helicityAdc);
        helicities.add(HelicityState.createFromFadcBank(helicityAdc, runConfig, conman));
        Event t = CLASDecoder4.createTaggedEvent((Event)event, runConfig, banks);
        if (!t.isEmpty()) writer.addEvent(t, tag);
        super.writeEvent(event);
    }

    @Override
    protected void closeWriter() {
        HelicitySequence.writeFlips(fullSchema, writer, helicities);
        super.closeWriter();
        if (postprocess) postprocess();
        // keep the latest helicity/scaler reading for the next file:
        while (helicities.size() > 1) helicities.pollFirst();
        scalers.clear(1);
    }
   
    private void postprocess() {
        int d = conman.getConstants(runNumber, "/runcontrol/helicity").getIntValue("delay",0,0,0);
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
