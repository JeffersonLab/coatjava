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
   
    public SerialHoncho(SchemaFactory schema) {
        this.schema = schema;
        conman = new ConstantsManager();
        conman.init("/runcontrol/hwp","/runcontrol/helicity");
        runConfig = new Bank(schema.getSchema("RUN::config"));
        helicityAdc = new Bank(schema.getSchema("HEL::adc"));
        helicities = new TreeSet<>();
        scalers = new DaqScalersSequence(schema);
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
            int unix = runConfig.getInt("unixtime",0);
            int evno = runConfig.getInt("event",0);
            if (unix > 0 && evno > 0) eventUnix.put(evno, unix);
        }
        helicities.add(HelicityState.createFromFadcBank(helicityAdc, runConfig, conman));
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
        HelicitySequence.writeFlips(schema, writer, helicities);
    }

    public void clear() {
        while (helicities.size() > 100) helicities.pollFirst();
        scalers.clear(100);
    }
    
    public DaqScalersSequence getScalers() {
        return scalers;
    }
    
    public TreeSet<HelicityState> getHelicities() {
        return helicities;
    }

    public HelicitySequenceDelayed getHelicitySequence() {
        // FIXME:  autogenerate if necessary
        HelicitySequenceDelayed h = new HelicitySequenceDelayed(
                conman.getConstants(4013, "/runcontrol/helicity").getIntValue("delay",0,0,0));
        h.addStream(helicities);
        return h;
    }

    public ConstantsManager getConstantsManager() {
        return conman;
    }

    public SchemaFactory getSchemaFactory() {
        return schema;
    }

    SchemaFactory schema;
    Bank[] tag1banks;
    // FIXME: store Schema for banks;
    Bank runConfig;
    Bank helicityAdc;
    ConstantsManager conman;
    TreeMap<Integer,Integer> eventUnix;
    TreeSet<HelicityState> helicities;
    DaqScalersSequence scalers;
  
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

    void processHelicity(Event event, Bank runcfg, Bank recevt) {
        HelicityBit hb = getHelicitySequence().search(runcfg.getLong("timestamp", 0));
        HelicityBit hbraw = getHelicitySequence().getHalfWavePlate() ? HelicityBit.getFlipped(hb) : hb;
        recevt.putByte("helicity",0,hb.value());
        recevt.putByte("helicityRaw",0,hbraw.value());
        Bank helScaler = new Bank(schema.getSchema("HEL::scaler"));
        event.read(helScaler);
        if (helScaler.getRows()>0) {
            event.remove(schema.getSchema("HEL::scaler"));
            SerialUtil.assignScalerHelicity(runcfg.getLong("timestamp",0), helScaler, getHelicitySequence());
            event.write(helScaler);
        }
    }

}
