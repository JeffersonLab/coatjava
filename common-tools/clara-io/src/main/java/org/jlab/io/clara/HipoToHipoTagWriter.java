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

    static final int TAG = 1;
    static final String[] bankNames = {
        "RUN::scaler",
        "HEL::scaler",
        "RAW::scaler",
        "RAW::epics",
        "HEL::flip"
    };

    Bank[] banks;
    Bank runConfig;
    Bank helicityAdc;

    ConstantsManager conman = new ConstantsManager();
    TreeSet<HelicityState> helicityReadings = new TreeSet<>();

    @Override
    protected HipoWriterSorted createWriter(Path file, JSONObject opts) throws EventWriterException {
        try {
            HipoWriterSorted w = new HipoWriterSorted();
            super.configure(w, opts);
            w.open(file.toString());
            runConfig = new Bank(w.getSchemaFactory().getSchema("RUN::config"));
            helicityAdc = new Bank(w.getSchemaFactory().getSchema("HEL::adc"));
            banks = new Bank[bankNames.length];
            for (int i=0; i<banks.length; ++i)
                banks[i] = new Bank(w.getSchemaFactory().getSchema(bankNames[i]));
            conman.init("/runcontrol/hwp");
            return w;
        } catch (Exception e) {
            throw new EventWriterException(e);
        }
    }

    @Override
    protected void writeEvent(Object event) throws EventWriterException {
        Event tag = CLASDecoder4.createTaggedEvent(writer.getSchemaFactory(), 
            (Event)event, "RUN::scaler","HEL::scaler","RAW::scaler","RAW::epics","HEL::flip");
        if (!tag.isEmpty()) writer.addEvent(tag, TAG);
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
