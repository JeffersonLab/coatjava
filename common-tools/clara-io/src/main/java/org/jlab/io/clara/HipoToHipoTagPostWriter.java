package org.jlab.io.clara;

import java.io.File;
import org.jlab.analysis.postprocess.Processor;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;

/**
 *
 * @author baltzell
 */
public class HipoToHipoTagPostWriter extends HipoToHipoTagWriter {
   
    final boolean rebuildScalers = false;
    final boolean restreamHelicity = false;

    @Override
    protected void closeWriter() {
        super.closeWriter();
        Processor p = new Processor(new File(filename),restreamHelicity,rebuildScalers);
        HipoReader r = new HipoReader();
        HipoWriterSorted w = new HipoWriterSorted();
        w.open(filename+".2");
        r.open(filename);
        Event e = new Event();
        while (r.hasNext()) {
            r.nextEvent(e);
            p.processEvent(e);
            w.addEvent(e);
        }
        w.close();
    }

}
