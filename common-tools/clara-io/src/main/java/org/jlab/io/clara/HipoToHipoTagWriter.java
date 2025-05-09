package org.jlab.io.clara;

import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;
import org.jlab.clara.std.services.EventWriterException;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.json.JSONObject;

/**
 *
 * @author baltzell
 */
public class HipoToHipoTagWriter extends HipoToHipoWriter {

    static final int tag = 1;
    List<Bank> banks;
    List<String> bankNames = Arrays.asList(new String[]{
        "RUN::scaler",
        "HEL::scaler",
        "RUN::epics",
        "HEL::flip"});
    Bank runConfig;

    @Override
    protected HipoWriterSorted createWriter(Path file, JSONObject opts) throws EventWriterException {
        try {
            HipoWriterSorted w = new HipoWriterSorted();
            super.configure(w, opts);
            w.open(file.toString());
            for (String b : bankNames)
                banks.add(new Bank(w.getSchemaFactory().getSchema(b)));
            runConfig = new Bank(w.getSchemaFactory().getSchema("RUN::config"));
            return w;
        } catch (Exception e) {
            throw new EventWriterException(e);
        }
    }

    @Override
    protected void writeEvent(Object event) throws EventWriterException {
        Event e = new Event();
        e.read(runConfig);
        for (Bank b : banks) {
            ((Event)event).read(b);
            if (b.getRows() > 0) e.write(b);
        }
        if (!e.isEmpty()) {
            e.write(runConfig);
            writer.addEvent(e, tag);
        }
        super.writeEvent(event); 
    }

}
