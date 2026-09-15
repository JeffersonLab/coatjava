package org.jlab.detector.serial;

import java.util.Arrays;
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
    volatile TreeMap<Integer,Integer> eventUnix;
    volatile HelicitySequence helicitySequence;
    volatile TreeSet<HelicityState> helicities;
    volatile DaqScalersSequence scalers;
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

    /**
     * Register an event's serial data and return a (possibly empty) tag-1 event.
     * @param event
     * @return new tag-1 event 
     */
    public Event read(Event event) {
        Bank cfg = new Bank(runConfig);
        Bank hel = new Bank(helicityAdc);
        event.read(cfg);
        event.read(hel);
        read(event, cfg, hel);
        return CLASDecoder.createTaggedEvent(event, cfg, createTaggedBanks(tag1banks));
    }

    /**
     * Modify a physics event's helicity and charge information. 
     * @param event 
     */
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
                processScalers(scalers.get(cfg.getLong("timestamp",0)), evt);
                event.write(evt);
            }
        }
    }

    /**
     * Add helicity and unixtime sequence banks.
     * @param writer
     */
    public void closure(HipoWriterSorted writer) {
        Bank cfg = new Bank(runConfig, 1);
        cfg.putInt("run",0,run); 
        writer.addEvent(getUnixEvent(cfg),1);
        helicitySequence.writeFlips(writer, 1);
    }

    /**
     * Zero all the sequences.
     */
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
  
    /**
     * Recreate the HelicitySequence from the TreeSet of helicity states.
     */
    public void updateHelicitySequence() {
        helicitySequence = new HelicitySequenceDelayed(
            conman.getConstants(run, "/runcontrol/helicity").getIntValue("delay",0,0,0));
        helicitySequence.addStream(helicities);
    }
   
    /**
     * 
     * @param event
     * @param runConfig
     * @param helicityAdc 
     */
    void read(Event event, Bank runConfig, Bank helicityAdc) {
        scalers.add(event);
        helicities.add(HelicityState.createFromFadcBank(helicityAdc, runConfig, conman));
        prune();
        if (runConfig.getRows() > 0) {
            int r = runConfig.getInt("run", 0);
            if (r > 0) {
                if (r != run) {
                    clear();
                    run = r;
                }
            }
            if (run > 0) {
                int unix = runConfig.getInt("unixtime",0);
                int evno = runConfig.getInt("event",0);
                if (unix > 0 && evno > 0) eventUnix.put(evno, unix);
            }
        }
    }

    /**
     * Trim down the helicity and scaler FIFOs to reasonable values.
     */
    void prune() {
        // Estimated size of HelicityState is ~22 bytes.
        // 1 million states, ~22 MB, 1 minute at 10 kHz trigger.
        if (helicities.size() > 2e6) 
            pruneHelicities(helicities, (int)1e6); 
        // Assuming scalers are 50x larger.
        // 10,000 events is 2.7 hours at 1 Hz.
        if (scalers.size() > 2e4)
            scalers.clear((int)1e4);
    }

    /**
     * Get a new event with RUN::unix and RUN::config banks.
     * @param runConfig
     * @return 
     */
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

    /**
     * Get the unix time for a given RUN::config bank.
     * @return unix time
     */
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

    /**
     * Update RUN::config's unixtime, and update the event.
     */
    void processEventUnix(Event event, Bank runConfig) {
        int ut = getUnixTime(runConfig);
        event.remove(runConfig.getSchema());
        runConfig.putInt("unixtime", 0, ut);
        event.write(runConfig);
    }

    /**
     * Update an event's helicity information, in REC::Event and HEL::scaler.
     * @param event
     * @param runConfig
     * @param recEvent 
     */
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

    /**
     * Update a REC::EVent's bank beam-charge information.  
     */
    static void processScalers(DaqScalers ds, Bank recEvent) {
        if (ds != null) {
            recEvent.putFloat("beamCharge",0, (float) ds.dsc2.getBeamChargeGated());
            recEvent.putDouble("liveTime",0,ds.dsc2.getLivetime());
        }
    }

    /**
     * For all states deeper than depth, remove consecutive helicity states that
     * differ only by timestamp.
     * @param helicities
     * @param depth 
     */
    static void pruneHelicities(TreeSet<HelicityState> helicities, int depth) {
        HelicityState prev = null;
        ListIterator<HelicityState> iter = (ListIterator)helicities.iterator();
        final int size = helicities.size();
        while (iter.hasNext() && iter.nextIndex() < size-depth) {
            HelicityState next = iter.next();
            if (prev != null && prev == next)
                helicities.remove(next);
        }
    }

    /**
     * FIXME:  Switch to lists and delete this.
     * @param tag1banks
     * @return 
     */
    static Bank[] createTaggedBanks(Schema[] tag1banks) {
        List<Bank> lbank = Arrays.asList(tag1banks).stream().map(s -> new Bank(s)).collect(Collectors.toList());
        ListIterator<Bank> ibank = lbank.listIterator();
        Bank[] banks = new Bank[lbank.size()];
        while (ibank.hasNext())
            banks[ibank.nextIndex()] = ibank.next();
        return banks;
    }
   
}
