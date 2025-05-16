package org.jlab.io.clara;

import java.nio.file.Path;
import java.util.TreeSet;
import org.jlab.clara.std.services.EventWriterException;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.decode.CLASDecoder4;
import org.jlab.detector.helicity.HelicitySequence;
import org.jlab.detector.helicity.HelicityState;
import org.jlab.detector.scalers.DaqScalersSequence;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.jnp.utils.file.FileUtils;
import org.json.JSONObject;

/**
 * A port of the standard "decoder" to a CLARA I/O service.
 *
 * 1. Converts EVIO to HIPO, translation tables, pulse extraction
 * 2. Copies special banks on-the-fly to new tag-1 events
 * 3. Caches helicity states and scaler readouts, for later use in post-processing.
 * 4. Upon close, writes the helicity sequence to HEL::flip banks in new tag-1 events.
 * 
 * @author baltzell
 */
public class HipoToHipoTagWriter extends HipoToHipoWriter {

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
        helicities.clear();
        scalers.clear();
        super.closeWriter();
    }

    protected void closeRawWriter() {
        super.closeWriter();
    }

}
