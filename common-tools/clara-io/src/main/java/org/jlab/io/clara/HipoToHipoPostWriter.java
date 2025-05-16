package org.jlab.io.clara;

import java.io.File;
import org.jlab.analysis.postprocess.Processor;
import org.jlab.detector.helicity.HelicitySequence;
import org.jlab.detector.helicity.HelicitySequenceDelayed;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoReader;

/**
 * Adds "postprocessing", i.e., copying tag-1 data to physics events. 
 * 
 * @author baltzell
 */
public class HipoToHipoPostWriter extends HipoToHipoTagWriter {

    private void update() {
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

    @Override
    protected void closeWriter() {
        HelicitySequence.writeFlips(fullSchema, writer, helicities);
        super.closeRawWriter();
        update();
        helicities.clear();
        scalers.clear();
    }

}
