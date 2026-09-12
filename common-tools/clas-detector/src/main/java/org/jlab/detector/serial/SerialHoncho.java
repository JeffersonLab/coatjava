package org.jlab.detector.serial;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
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
import org.jlab.jnp.hipo4.data.Schema;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;

/**
 *
 * @author baltzell
 */
public class SerialHoncho {
    
    static final String[] TAG1BANKS = {"RUN::scaler","HEL::scaler","RAW::scaler","RAW::epics","HEL::flip","COAT::config"};
    SchemaFactory schema;
    Schema[] tag1banks;
    Schema runConfig;
    Schema recEvent;
    Schema helScaler;
    Schema helicityAdc;
    ConstantsManager conman;
    TreeMap<Integer,Integer> eventUnix;
    HelicitySequence helicitySequence;
    TreeSet<HelicityState> helicities;
    DaqScalersSequence scalers;
    int run = 0;
  
    public SerialHoncho(SchemaFactory schema) {
        this.schema = schema;
        conman = new ConstantsManager();
        conman.init("/runcontrol/hwp","/runcontrol/helicity");
        runConfig = schema.getSchema("RUN::config");
        recEvent = schema.getSchema("REC::Event");
        helicityAdc = schema.getSchema("HEL::adc");
        helScaler = schema.getSchema("HEL::scaler");
        scalers = new DaqScalersSequence(schema);
        helicities = new TreeSet<>();
        eventUnix = new TreeMap<>();
        tag1banks = new Schema[TAG1BANKS.length];
        for (int i=0; i<tag1banks.length; ++i)
            tag1banks[i] = schema.getSchema(TAG1BANKS[i]);
    }

    public synchronized Event read(Event event) {
        Bank cfg = new Bank(runConfig);
        Bank hel = new Bank(helicityAdc);
        scalers.add(event);
        event.read(cfg);
        event.read(hel);
        if (cfg.getRows() > 0) {
            if (run <= 0 && cfg.getInt("run", 0) > 0)
                run = cfg.getInt("run",0);
            int unix = cfg.getInt("unixtime",0);
            int evno = cfg.getInt("event",0);
            if (unix > 0 && evno > 0) eventUnix.put(evno, unix);
        }
        helicities.add(HelicityState.createFromFadcBank(hel, cfg, conman));
        return CLASDecoder.createTaggedEvent(event, cfg, createTaggedBanks(tag1banks));
    }

    public void process(Event event) {
        Bank cfg = new Bank(runConfig);
        Bank evt = new Bank(recEvent);
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

    public void prune() {
        // remove identical helicities in the stream:
        HelicityState prev = null;
        Iterator<HelicityState> iter = (ListIterator)helicities.iterator();
        while (iter.hasNext()) {
            HelicityState next = iter.next();
            if (prev != null && prev == next)
                helicities.remove(next);
        }
        scalers.clear((int)1e5);
        // trim helicities to 100 million events, ~1 run, ~1 GB:
        //while (helicities.size() < 1e8) helicities.pollFirst();
    }

    public void finish(HipoWriterSorted writer) {
        Bank cfg = new Bank(runConfig, 1);
        cfg.putInt("run",0,run); 
        writer.addEvent(getUnixEvent(cfg),1);
        helicitySequence.writeFlips(writer, 1);
    }

    public void clear() {
        eventUnix.clear();
        helicities.clear();
        scalers.clear();
        helicitySequence = null;
    }

    public TreeSet<HelicityState> getHelicities() {
        return helicities;
    }

    public DaqScalersSequence getScalers() {
        return scalers;
    }

    public ConstantsManager getConstantsManager() {
        return conman;
    }

    public SchemaFactory getSchemaFactory() {
        return schema;
    }
   
    public void updateHelicitySequence() {
        helicitySequence = new HelicitySequenceDelayed(
            conman.getConstants(run, "/runcontrol/helicity").getIntValue("delay",0,0,0));
        helicitySequence.addStream(helicities);
    }
    
    Event getUnixEvent(Bank runConfig) {
        Bank unix = new Bank(schema.getSchema("RUN::unix"));
        unix.setRows(eventUnix.size());
        int row = 0;
        for (int evno : eventUnix.keySet()) {
            unix.putInt("event", row, evno);
            unix.putInt("unixtime",row, eventUnix.get(evno));
            row++;
        }
        Event e = new Event();
        e.write(runConfig);
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
        Bank scaler = new Bank(helScaler);
        event.read(scaler);
        if (scaler.getRows()>0) {
            event.remove(helScaler);
            SerialUtil.assignScalerHelicity(runConfig.getLong("timestamp",0), scaler, helicitySequence);
            event.write(scaler);
        }
    }

    static Bank[] createTaggedBanks(Schema[] tag1banks) {
        List<Bank> lbank = Arrays.asList(tag1banks).stream().map(s -> new Bank(s)).collect(Collectors.toList());
        ListIterator<Bank> ibank = lbank.listIterator();
        Bank[] banks = new Bank[lbank.size()];
        while (ibank.hasNext())
            banks[ibank.nextIndex()] = ibank.next();
        return banks;
    }
   
}
