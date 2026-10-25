package org.jlab.io.clara;

import java.nio.file.Path;
import org.jlab.clara.std.services.EventWriterException;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.serial.Occupancer;
import org.jlab.clas.reco.ParaTrigWriter;
import org.jlab.detector.serial.SerialHoncho;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.utils.CLASResources;
import org.json.JSONObject;

/**
 *
 * @author baltzell
 */
public class Clas12Writer extends HipoToHipoWriter {

    Occupancer occupancer;
    SerialHoncho serial;
    ConstantsManager conman;
    ParaTrigWriter paraWriter;
    long paraTriggerMask;
    int paraTriggerPrescale;

    private void init(JSONObject opts) {
        occupancer = new Occupancer(opts.optLong("occTriggerMask",(long)1<<39),opts.optInt("occEvents",100));
        serial = new SerialHoncho(CLASResources.getFullSchema());
        conman = new ConstantsManager();
        conman.init("/runcontrol/hwp","/runcontrol/helicity");
        paraTriggerMask = opts.optLong("paraTriggerMask", 0);
        paraTriggerPrescale = opts.optInt("paraTriggerPrescale", 0);
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
                paraWriter = new ParaTrigWriter(paraTriggerMask,paraTriggerPrescale);
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
        occupancer.process(((Event)event));
        super.writeEvent(event);
        paraWriter.addEvent((Event)event);
        if (!t.isEmpty()) paraWriter.addEvent(t,1);
    }

    @Override
    protected void closeWriter() {
        serial.closure(writer);
        super.closeWriter();
        serial.clear();
        if (paraTriggerMask > 0) paraWriter.close();
    }
 
}
