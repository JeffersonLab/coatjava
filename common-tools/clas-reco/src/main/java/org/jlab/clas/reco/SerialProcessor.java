package org.jlab.clas.reco;

import java.util.TreeMap;
import java.util.TreeSet;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.decode.CLASDecoder;
import org.jlab.detector.helicity.HelicitySequence;
import org.jlab.detector.helicity.HelicityState;
import org.jlab.detector.scalers.DaqScalersSequence;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataBank;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;

/**
 *
 * @author baltzell
 */
public class SerialProcessor {
    
    TreeMap<Integer,Integer> eventUnix;
    TreeSet<HelicityState> helicities;
    DaqScalersSequence scalers;
    Bank[] tag1banks;
    Bank runConfig;
    Bank helicityAdc;
    ConstantsManager conman;
    SchemaFactory schema;

    public void process(Event event) {
        scalers.add(event);
        event.read(runConfig);
        event.read(helicityAdc);
        if (runConfig.getRows() > 0) {
            int unix = runConfig.getInt("unixtime",0);
            int evno = runConfig.getInt("event",0);
            if (unix > 0 && evno > 0) eventUnix.put(evno, unix);
        }
        helicities.add(HelicityState.createFromFadcBank(helicityAdc, runConfig, conman));
        Event t = CLASDecoder.createTaggedEvent((Event)event, runConfig, tag1banks);
    }

    private DataEvent getUnixEvent(DataBank config) {
        Bank unix = new Bank(schema.getSchema("RUN::unix"));
        unix.setRows(eventUnix.size());
        int row = 0;
        for (int evno : eventUnix.keySet()) {
            unix.putInt("event", row, evno);
            unix.putInt("unixtime",row, eventUnix.get(evno));
            row++;
        }
        Event e = new Event();
        HipoDataEvent e2 = new HipoDataEvent(e);
        HipoDataBank b = new HipoDataBank(unix);
        //e.write(config);
        e.write(unix);
        return e2;
    }

    public void closure(HipoWriterSorted writer){
        HelicitySequence.writeFlips(schema, writer, helicities);
        //writer.addEvent(getUnixEvent(runConfig),1);
    }
    
}
