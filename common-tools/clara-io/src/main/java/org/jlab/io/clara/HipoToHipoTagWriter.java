package org.jlab.io.clara;

import java.nio.file.Path;
import java.util.TreeSet;
import org.jlab.clara.std.services.EventWriterException;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.decode.CLASDecoder4;
import org.jlab.detector.helicity.HelicitySequence;
import org.jlab.detector.helicity.HelicityState;
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

    Bank runConfig;
    Bank helicityAdc;
    ConstantsManager conman;
    TreeSet<HelicityState> helicityReadings;
    SchemaFactory fullSchema;

    protected void init(JSONObject opts) {
        fullSchema = new SchemaFactory();
        fullSchema.initFromDirectory(FileUtils.getEnvironmentPath("CLAS12DIR","etc/bankdefs/hipo4"));
        runConfig = new Bank(fullSchema.getSchema("RUN::config"));
        helicityAdc = new Bank(fullSchema.getSchema("HEL::adc"));
        helicityReadings = new TreeSet<>();
        conman = new ConstantsManager();
        conman.init("/runcontrol/hwp");
        if (opts.has("variation")) conman.setVariation(opts.getString("variation"));
        if (opts.has("timestamp")) conman.setTimeStamp(opts.getString("timestamp"));
        if (opts.has("tag")) tag = opts.getInt("tag");
        if (opts.has("banks")) bankNames = opts.getString("banks").split(",");
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
        Event t = CLASDecoder4.createTaggedEvent(fullSchema, (Event)event, bankNames);
        if (!t.isEmpty()) writer.addEvent(t, tag);
        ((Event)event).read(runConfig);
        ((Event)event).read(helicityAdc);
        helicityReadings.add(HelicityState.createFromFadcBank(helicityAdc, runConfig, conman));
        super.writeEvent(event);
    }

    @Override
    protected void closeWriter() {
        HelicitySequence.writeFlips(fullSchema, writer, helicityReadings);
        helicityReadings.clear();
        super.closeWriter();
    }

}
