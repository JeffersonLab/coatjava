package org.jlab.io.clara;

import java.io.File;
import org.jlab.analysis.postprocess.Processor;
import org.jlab.detector.helicity.HelicitySequence;
import org.jlab.detector.helicity.HelicitySequenceDelayed;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;

/**
 * Adds "postprocessing", i.e., copying tag-1 data to physics events. 
 * 
 * @author baltzell
 */
public class HipoToHipoPostWriter extends HipoToHipoTagWriter {
 
    protected void update() {
        HelicitySequenceDelayed h = new HelicitySequenceDelayed(8);
        h.addStream(helicityReadings);
        Processor p = new Processor(fullSchema, h, daqScalers);
        HipoReader r = new HipoReader();
        HipoWriterSorted w = new HipoWriterSorted();
        w.open("pp_"+filename);
        r.open(filename);
        Event e = new Event();
        while (r.hasNext()) {
            r.nextEvent(e);
            p.processEvent(e);
            w.addEvent(e);
        }
        w.close();
        new File(filename).delete();
        new File("pp_"+filename).renameTo(new File(filename));
    }
    
    @Override
    protected void closeWriter() {
        HelicitySequence.writeFlips(fullSchema, writer, helicityReadings);
        super.closeRawWriter();
        update();
        helicityReadings.clear();
        daqScalers.clear();
    }

}
