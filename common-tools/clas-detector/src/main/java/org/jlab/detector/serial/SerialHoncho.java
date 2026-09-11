package org.jlab.detector.serial;

import java.util.TreeMap;
import java.util.TreeSet;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.decode.CLASDecoder;
import org.jlab.detector.helicity.HelicityBit;
import org.jlab.detector.helicity.HelicitySequence;
import org.jlab.detector.helicity.HelicitySequenceDelayed;
import org.jlab.detector.helicity.HelicityState;
import org.jlab.detector.scalers.DaqScalers;
import org.jlab.detector.scalers.DaqScalersSequence;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;

/**
 *
 * @author baltzell
 */
public class SerialHoncho {
    
    static final String[] TAG1BANKS = {"RUN::scaler","HEL::scaler","RAW::scaler","RAW::epics","HEL::flip","COAT::config"};
    SchemaFactory schema;
    Bank[] tag1banks;
    Bank runConfig; // FIXME: store Schema for banks;
    Bank helicityAdc;
    ConstantsManager conman;
    TreeMap<Integer,Integer> eventUnix;
    HelicitySequence helicitySequence;
    TreeSet<HelicityState> helicities;
    DaqScalersSequence scalers;
    int run;
  
    public SerialHoncho(SchemaFactory schema) {
        this.schema = schema;
        conman = new ConstantsManager();
        conman.init("/runcontrol/hwp","/runcontrol/helicity");
        runConfig = new Bank(schema.getSchema("RUN::config"));
        helicityAdc = new Bank(schema.getSchema("HEL::adc"));
        scalers = new DaqScalersSequence(schema);
        helicities = new TreeSet<>();
        eventUnix = new TreeMap<>();
        tag1banks = new Bank[TAG1BANKS.length];
        for (int i=0; i<tag1banks.length; ++i)
            tag1banks[i] = new Bank(schema.getSchema(TAG1BANKS[i]));
    }

    public synchronized Event read(Event event) {
        scalers.add(event);
        event.read(runConfig);
        event.read(helicityAdc);
        if (runConfig.getRows() > 0) {
            if (run <= 0 && runConfig.getInt("run", 0) > 0) {
                run = runConfig.getInt("run",0);
                helicitySequence = new HelicitySequenceDelayed(
                    conman.getConstants(run, "/runcontrol/helicity").getIntValue("delay",0,0,0));
            }
            int unix = runConfig.getInt("unixtime",0);
            int evno = runConfig.getInt("event",0);
            if (unix > 0 && evno > 0) eventUnix.put(evno, unix);
        }
        if (helicitySequence != null) {
            HelicityState state = HelicityState.createFromFadcBank(helicityAdc, runConfig, conman);
            helicities.add(state);
            helicitySequence.addState(state);
        }
        return CLASDecoder.createTaggedEvent(event, runConfig, tag1banks);
    }
   
    public void process(Event event) {
        Bank cfg = new Bank(schema.getSchema("RUN::config"));
        Bank evt = new Bank(schema.getSchema("REC::Event"));
        event.read(cfg);
        event.read(evt);
        if (cfg.getRows() > 0) {
            processEventUnix(event, cfg);
            if (evt.getRows() > 0) {
                event.remove(evt.getSchema());
                processHelicity(event, cfg, evt);
                processScalers(cfg, evt);
                event.write(evt);
            }
        }
    }

    public void finish(HipoWriterSorted writer) {
        writer.addEvent(getUnixEvent(runConfig),1);
        // FIXME:  mark written flips and don't write them again
        helicitySequence.writeFlips(writer, 1);
    }

    public void clear() {
        eventUnix.clear();
        helicities.clear();
        scalers.clear();
    }

    public DaqScalersSequence getScalers() {
        return scalers;
    }
    
    public HelicitySequence getHelicitySequence() {
        return helicitySequence;
    }

    public ConstantsManager getConstantsManager() {
        return conman;
    }

    public SchemaFactory getSchemaFactory() {
        return schema;
    }
   
    public TreeSet<HelicityState> getHelicities() {
        return helicities;
    }

    HelicitySequence createHelicitySequence() {
        HelicitySequence seq = new HelicitySequenceDelayed(
                conman.getConstants(run, "/runcontrol/helicity").getIntValue("delay",0,0,0));
        seq.addStream(helicities);
        return seq;
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

    int getUnixTime(Bank runConfig) {
        if (runConfig.getRows() < 1) {
            Integer key =  eventUnix.floorKey(runConfig.getInt("event",0));
            if (key != null) {
                Integer unix = eventUnix.get(key);
                if (unix != null) return unix;
            }
        }
        return 0;
    }

    void processEventUnix(Event event, Bank runConfig) {
        int ut = getUnixTime(runConfig);
        event.remove(runConfig.getSchema());
        runConfig.putInt("unixtime", 0, ut);
        event.write(runConfig);
    }

    void processScalers(Bank runConfig, Bank recEvent) {
        DaqScalers ds = scalers.get(runConfig.getLong("timestamp", 0));
        if (ds != null) {
            recEvent.putFloat("beamCharge",0, (float) ds.dsc2.getBeamChargeGated());
            recEvent.putDouble("liveTime",0,ds.dsc2.getLivetime());
        }
    }

    void processHelicity(Event event, Bank runConfig, Bank recEvent) {
        HelicityBit hb = helicitySequence.search(runConfig.getLong("timestamp", 0));
        HelicityBit hbraw = helicitySequence.getHalfWavePlate() ? HelicityBit.getFlipped(hb) : hb;
        recEvent.putByte("helicity",0,hb.value());
        recEvent.putByte("helicityRaw",0,hbraw.value());
        Bank helScaler = new Bank(schema.getSchema("HEL::scaler"));
        event.read(helScaler);
        if (helScaler.getRows()>0) {
            event.remove(schema.getSchema("HEL::scaler"));
            SerialUtil.assignScalerHelicity(runConfig.getLong("timestamp",0), helScaler, helicitySequence);
            event.write(helScaler);
        }
    }

}
