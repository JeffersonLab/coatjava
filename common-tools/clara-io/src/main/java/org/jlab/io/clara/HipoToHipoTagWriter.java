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
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.json.JSONObject;

/**
 * This is just a wrap of the standard COATJAVA "decoder" in a CLARA I/O service
 * 
 * @author baltzell
 */
public class HipoToHipoTagWriter extends HipoToHipoWriter {

    // defaults:
    int tag = 1;
    String[] bankNames = {
        "RUN::scaler",
        "HEL::scaler",
        "RAW::scaler",
        "RAW::epics",
        "HEL::flip"
    };

    Bank[] banks;
    Bank runConfig;
    Bank helicityAdc;
    ConstantsManager conman;
    TreeSet<HelicityState> helicityReadings;

    @Override
    protected HipoWriterSorted createWriter(Path file, JSONObject opts) throws EventWriterException {
        helicityReadings = new TreeSet<>();
        conman = new ConstantsManager();
        conman.init("/runcontrol/hwp");
        if (opts.has("variation")) conman.setVariation(opts.getString("variation"));
        if (opts.has("timestamp")) conman.setTimeStamp(opts.getString("timestamp"));
        if (opts.has("tag")) tag = opts.getInt("tag");
        if (opts.has("banks")) bankNames = opts.getString("banks").split(",");
        HipoWriterSorted w = new HipoWriterSorted();
        super.configure(w, opts);
        w.open(file.toString());
        runConfig = new Bank(w.getSchemaFactory().getSchema("RUN::config"));
        helicityAdc = new Bank(w.getSchemaFactory().getSchema("HEL::adc"));
        banks = new Bank[bankNames.length];
        for (int i=0; i<banks.length; ++i)
            banks[i] = new Bank(w.getSchemaFactory().getSchema(bankNames[i]));
        return w;
    }

    @Override
    protected void writeEvent(Object event) throws EventWriterException {
        Event t = CLASDecoder4.createTaggedEvent(writer.getSchemaFactory(), 
            (Event)event, "RUN::scaler","HEL::scaler","RAW::scaler","RAW::epics","HEL::flip");
        if (!t.isEmpty()) writer.addEvent(t, tag);
        ((Event)event).read(runConfig);
        ((Event)event).read(helicityAdc);
        helicityReadings.add(HelicityState.createFromFadcBank(helicityAdc, runConfig, conman));
        super.writeEvent(event);
    }

    @Override
    protected void closeWriter() {
        HelicitySequence.writeFlips(writer, helicityReadings); 
        super.closeWriter();
    }

}
