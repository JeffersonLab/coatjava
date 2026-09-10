package org.jlab.io.clara;

import java.nio.file.Path;
import org.jlab.clara.std.services.EventWriterException;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.serial.SerialHoncho;
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
 *
 * @author baltzell
 */
public class Clas12Writer extends HipoToHipoWriter {

    SerialHoncho serial;
    ConstantsManager conman;
    SchemaFactory fullSchema;

    private void init(JSONObject opts) {
        fullSchema = new SchemaFactory();
        fullSchema.initFromDirectory(FileUtils.getEnvironmentPath("CLAS12DIR","etc/bankdefs/hipo4"));
        serial = new SerialHoncho(fullSchema);
        conman = new ConstantsManager();
        conman.init("/runcontrol/hwp","/runcontrol/helicity");
        if (opts.has("variation")) conman.setVariation(opts.getString("variation"));
        if (opts.has("timestamp")) conman.setTimeStamp(opts.getString("timestamp"));
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

    @Override
    protected void writeEvent(Object event) throws EventWriterException {
        Event t = serial.read((Event)event);
        if (!t.isEmpty()) writer.addEvent(t, 1);
        super.writeEvent(event);
    }

    @Override
    protected void closeWriter() {
        serial.finish(writer);
        super.closeWriter();
        serial.clear();
    }
}
