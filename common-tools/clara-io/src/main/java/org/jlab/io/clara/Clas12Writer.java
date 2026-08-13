package org.jlab.io.clara;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jlab.analysis.postprocess.Processor;
import org.jlab.clara.std.services.EventWriterException;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.calib.utils.OccupancyTable;
import org.jlab.detector.decode.CLASDecoder4;
import org.jlab.detector.helicity.HelicitySequence;
import org.jlab.detector.helicity.HelicitySequenceDelayed;
import org.jlab.detector.helicity.HelicityState;
import org.jlab.detector.scalers.DaqScalersSequence;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.jnp.utils.file.FileUtils;
import org.json.JSONObject;

/**
 * 
 * 1. Copies certain banks on-the-fly to new tag-1 events
 * 2. Caches helicity states, scaler readouts, and unix time
 * 3. Writes HEL::flip, RUN/HEL::scaler, and RUN::unix to new tag-1 events
 * 4. Runs post-processing, writing tag-1 information to all events 
 * 5. Adds .hipo to the output filename, if necessary
 *
 * @author baltzell
 */
public class Clas12Writer extends HipoToHipoWriter {

    static final String[] TAG1BANKS = {"RUN::scaler","HEL::scaler","RAW::scaler","RAW::epics","HEL::flip","COAT::config"};

    long occupancyEvents;
    OccupancyTable[] occupancyTables;
    Bank[] occupancyBanks;
    Bank[] tag1banks;
    Bank runConfig;
    Bank helicityAdc;
    ConstantsManager conman;
    TreeMap<Integer,Integer> eventUnix;
    TreeSet<HelicityState> helicities;
    DaqScalersSequence scalers;
    SchemaFactory fullSchema;
    boolean postprocess;

    private void init(JSONObject opts) {
        occupancyEvents = 0;
        fullSchema = new SchemaFactory();
        fullSchema.initFromDirectory(FileUtils.getEnvironmentPath("CLAS12DIR","etc/bankdefs/hipo4"));
        runConfig = new Bank(fullSchema.getSchema("RUN::config"));
        helicityAdc = new Bank(fullSchema.getSchema("HEL::adc"));
        helicities = new TreeSet<>();
        scalers = new DaqScalersSequence(fullSchema);
        conman = new ConstantsManager();
        eventUnix = new TreeMap<>();
        conman.init("/runcontrol/hwp","/runcontrol/helicity");
        postprocess = opts.optBoolean("postprocess", false);
        if (opts.has("variation")) conman.setVariation(opts.getString("variation"));
        if (opts.has("timestamp")) conman.setTimeStamp(opts.getString("timestamp"));
        tag1banks = new Bank[TAG1BANKS.length];
        for (int i=0; i<tag1banks.length; ++i)
            tag1banks[i] = new Bank(fullSchema.getSchema(TAG1BANKS[i]));
        occupancyBanks = new Bank[1];
        occupancyBanks[0] = new Bank(fullSchema.getSchema("DC::occ"));
        occupancyTables = new OccupancyTable[1];
        occupancyTables[0] = new OccupancyTable.DC();
    }

    @Override
    protected HipoWriterSorted createWriter(Path file, JSONObject opts) throws EventWriterException {
        try {
            init(opts);
            HipoWriterSorted w = new HipoWriterSorted();
            super.configure(w, opts);
            w.open(file.toString().endsWith(".hipo") ? file.toString() : file.toString()+".hipo");
            return w;
        } catch (Exception e) {
            throw new EventWriterException(e);
        }
    }

    private void processOccupancy(Event event) {
        for (int i=0; i<occupancyBanks.length; i++) {
            event.read(occupancyBanks[i]);
            event.remove(occupancyBanks[i].getSchema());
            occupancyTables[i].fill(occupancyBanks[i], true);
        }
        if (occupancyEvents++ % 1000 == 0) {
            for (int i=0; i<occupancyBanks.length; i++) {
                occupancyTables[i].create(occupancyEvents, occupancyBanks[i]);
                event.write(occupancyBanks[i]);
                occupancyTables[i].reset();
            }
            occupancyEvents = 0;
        }
    }

    @Override
    protected void writeEvent(Object event) throws EventWriterException {
        scalers.add((Event)event);
        ((Event)event).read(runConfig);
        ((Event)event).read(helicityAdc);
        if (runConfig.getRows() > 0) {
            int unix = runConfig.getInt("unixtime",0);
            int evno = runConfig.getInt("event",0);
            if (unix > 0 && evno > 0) eventUnix.put(evno, unix);
        }
        processOccupancy((Event)event);
        helicities.add(HelicityState.createFromFadcBank(helicityAdc, runConfig, conman));
        Event t = CLASDecoder4.createTaggedEvent((Event)event, runConfig, tag1banks);
        if (!t.isEmpty()) writer.addEvent(t, 1);
        super.writeEvent(event);
    }

    @Override
    protected void closeWriter() {
        HelicitySequence.writeFlips(fullSchema, writer, helicities);
        writer.addEvent(getUnixEvent(runConfig),1);
        super.closeWriter();
        if (postprocess) postprocess();
        // keep the latest helicity/scaler reading for the next file:
        while (helicities.size() > 60) helicities.pollFirst();
        scalers.clear(10);
    }
 
    /**
     * Get the first valid run number from a RUN::config bank.
     * @return run
     */
    private int getRunNumber() {
        Event e = new Event();
        HipoReader r = new HipoReader();
        r.open(filename);
        while (r.hasNext()) {
            r.nextEvent(e);
            e.read(runConfig);
            if (runConfig.getRows()>0 && runConfig.getInt("run",0)>0)
                return runConfig.getInt("run",0);
        }
        return 0;
    }

    /**
     * Get a new event with a RUN::unix bank containing event-timestamp mapping,
     * and the latest RUN::config bank.
     * @param config
     * @return 
     */
    private Event getUnixEvent(Bank config) {
        Bank unix = new Bank(fullSchema.getSchema("RUN::unix"));
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

    /**
     * Copy helicity/charge tag-1 information to all events.
     */
    private void postprocess() {
        int d = conman.getConstants(getRunNumber(), "/runcontrol/helicity").getIntValue("delay",0,0,0);
        HelicitySequenceDelayed helicity = new HelicitySequenceDelayed(d);
        helicity.addStream(helicities);
        Processor p = new Processor(List.of(filename), fullSchema, helicity, scalers);
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

}
