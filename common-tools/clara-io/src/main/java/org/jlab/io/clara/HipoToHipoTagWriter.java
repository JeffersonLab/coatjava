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
 * This is just a wrap of the standard COATJAVA "decoder" in a CLARA I/O service
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
    TreeSet<HelicityState> helicityReadings;
    DaqScalersSequence daqScalers;
    SchemaFactory fullSchema;

    protected void init(JSONObject opts) {
        fullSchema = new SchemaFactory();
        fullSchema.initFromDirectory(FileUtils.getEnvironmentPath("CLAS12DIR","etc/bankdefs/hipo4"));
        runConfig = new Bank(fullSchema.getSchema("RUN::config"));
        helicityAdc = new Bank(fullSchema.getSchema("HEL::adc"));
        helicityReadings = new TreeSet<>();
        daqScalers = new DaqScalersSequence(fullSchema);
        conman = new ConstantsManager();
        conman.init("/runcontrol/hwp");
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
        daqScalers.add((Event)event);
        ((Event)event).read(runConfig);
        ((Event)event).read(helicityAdc);
        helicityReadings.add(HelicityState.createFromFadcBank(helicityAdc, runConfig, conman));
        Event t = CLASDecoder4.createTaggedEvent((Event)event, runConfig, banks);
        if (!t.isEmpty()) writer.addEvent(t, tag);
        super.writeEvent(event);
    }

    @Override
    protected void closeWriter() {
        HelicitySequence.writeFlips(fullSchema, writer, helicityReadings);
        helicityReadings.clear();
        daqScalers.clear();
        super.closeWriter();
    }

    protected void closeRawWriter() {
        super.closeWriter();
    }

}
