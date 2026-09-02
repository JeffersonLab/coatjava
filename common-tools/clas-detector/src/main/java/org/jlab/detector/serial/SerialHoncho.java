package org.jlab.detector.serial;

import java.util.TreeMap;
import java.util.TreeSet;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.decode.CLASDecoder;
import org.jlab.detector.helicity.HelicitySequence;
import org.jlab.detector.helicity.HelicityState;
import org.jlab.detector.scalers.DaqScalersSequence;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.utils.system.ClasUtilsFile;

/**
 *
 * @author baltzell
 */
public class SerialHoncho {
    
    static final String[] TAG1BANKS = {"RUN::scaler","HEL::scaler","RAW::scaler","RAW::epics","HEL::flip","COAT::config"};
   
    SchemaFactory schema;
    Bank[] tag1banks;
    Bank runConfig;
    Bank helicityAdc;
    ConstantsManager conman;
    TreeMap<Integer,Integer> eventUnix;
    TreeSet<HelicityState> helicities;
    DaqScalersSequence scalers;
   
    public SerialHoncho() {
        schema = new SchemaFactory();
        schema.initFromDirectory(ClasUtilsFile.getResourceDir("CLAS12DIR","etc/bankdefs/hipo4"));
        runConfig = new Bank(schema.getSchema("RUN::config"));
        helicityAdc = new Bank(schema.getSchema("HEL::adc"));
        helicities = new TreeSet<>();
        scalers = new DaqScalersSequence(schema);
        conman = new ConstantsManager();
        eventUnix = new TreeMap<>();
        conman.init("/runcontrol/hwp","/runcontrol/helicity");
        tag1banks = new Bank[TAG1BANKS.length];
        for (int i=0; i<tag1banks.length; ++i)
            tag1banks[i] = new Bank(schema.getSchema(TAG1BANKS[i]));
    }

    public Event read(DataEvent event) {
        event.getBankList();
        //scalers.add(event);
        DataBank runConfig = event.getBank("RUN::config");
        if (runConfig.rows() > 0) {
            DataBank helicityAdc = event.getBank("HEL::adc");
            helicities.add(HelicityState.createFromFadcBank(helicityAdc, runConfig, conman));
            int unix = runConfig.getInt("unixtime",0);
            int evno = runConfig.getInt("event",0);
            if (unix > 0 && evno > 0) eventUnix.put(evno, unix);
        }
        return null;//CLASDecoder.createTaggedEvent((Event)event, runConfig, tag1banks);
    }

    public synchronized Event read(Event event) {
        scalers.add(event);
        event.read(runConfig);
        event.read(helicityAdc);
        if (runConfig.getRows() > 0) {
            int unix = runConfig.getInt("unixtime",0);
            int evno = runConfig.getInt("event",0);
            if (unix > 0 && evno > 0) eventUnix.put(evno, unix);
        }
        helicities.add(HelicityState.createFromFadcBank(helicityAdc, runConfig, conman));
        return CLASDecoder.createTaggedEvent(event, runConfig, tag1banks);
    }

    public void finish(HipoWriterSorted writer) {
        writer.addEvent(getUnixEvent(runConfig),1);
        HelicitySequence.writeFlips(schema, writer, helicities);
    }
    
    Event getUnixEvent(Bank config) {
        Bank unix = new Bank(schema.getSchema("RUN::unix"));
        unix.setRows(eventUnix.size());
        int row = 0;
        for (int evno : eventUnix.keySet()) {
            unix.putInt("event", row, evno);
            unix.putInt("unixtime",row, eventUnix.get(evno));
            row++;
        }
        Event e = new Event();
        e.write(config);
        e.write(unix);
        return e;
    }
}
