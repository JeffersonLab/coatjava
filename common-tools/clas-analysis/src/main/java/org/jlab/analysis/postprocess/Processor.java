package org.jlab.analysis.postprocess;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataBank;

import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.scalers.DaqScalers;
import org.jlab.detector.scalers.DaqScalersSequence;
import org.jlab.detector.helicity.HelicityBit;
import org.jlab.detector.helicity.HelicitySequenceDelayed;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.system.ClasUtilsFile;

/**
 *
 * @author baltzell
 */
public class Processor {

    public static final String CCDB_TABLES[] = {"/runcontrol/fcup","/runcontrol/slm",
        "/runcontrol/helicity","/daq/config/scalers/dsc1","/runcontrol/hwp"};
    public static final String DEF_PRELOAD_GLOB = "*.{hipo,h5}";

    private final String outputPrefix = "tmp_";

    private Bank runConfig = null;
    private Bank recEvent = null;
    private ConstantsManager conman = null;
    private SchemaFactory schemaFactory = null;
    private DaqScalersSequence chargeSequence = null;
    private HelicitySequenceDelayed helicitySequence = null;
    private TreeMap<Integer,Integer> eventUnix = null;

    public Processor(File file, boolean restream, boolean rebuild) {
        configure(Arrays.asList(file.getAbsolutePath()), restream, rebuild);
    }
    
    public Processor(String dir, boolean restream, boolean rebuild) {
        configure(findPreloadFiles(dir,DEF_PRELOAD_GLOB), restream, rebuild);
    }

    public Processor(String dir, String glob, boolean restream, boolean rebuild) {
        configure(findPreloadFiles(dir,glob), restream, rebuild);
    }

    public Processor(List<String> files, boolean restream, boolean rebuild) {
        configure(files, restream, rebuild);
    }

    private void configure(List<String> preloadFiles, boolean restream, boolean rebuild) {
        if (!preloadFiles.isEmpty()) {
            HipoReader r = new HipoReader();
            r.open(preloadFiles.get(0));
            schemaFactory = r.getSchemaFactory();
            r.close();
            runConfig = new Bank(schemaFactory.getSchema("RUN::config"));
            recEvent = new Bank(schemaFactory.getSchema("REC::Event"));
            conman = new ConstantsManager();
            conman.init(CCDB_TABLES);
            helicitySequence = Util.getHelicity(preloadFiles, schemaFactory, restream, conman);
            if (rebuild) chargeSequence = DaqScalersSequence.rebuildSequence(1, conman, preloadFiles);
            else chargeSequence = DaqScalersSequence.readSequence(preloadFiles);
            eventUnix = getEventUnixMap(schemaFactory, preloadFiles); 
        }
    }

    /**
     * Get a list of files to preload, from one directory and a glob.
     * @param dir
     * @param glob
     * @return list of preload files 
     */
    private static List<String> findPreloadFiles(String dir, String glob) {
        List<String> ret = new ArrayList<>();
        if (dir != null) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:"+dir+"/"+glob);
            for (File f : (new File(dir)).listFiles()) {
                if (matcher.matches(f.toPath()))
                    ret.add(f.getPath());
            }
        }
        return ret;
    }

    /**
     * Load the mapping from event number to unix time
     * @param schema
     * @param files
     * @return map 
     */
    public static TreeMap<Integer,Integer> getEventUnixMap(SchemaFactory schema, List<String> files) {
        Bank unix = new Bank(schema.getSchema("RUN::unix"));
        TreeMap<Integer,Integer> m = new TreeMap<>();
        Event e = new Event();
        for (String f : files) {
            HipoReader r = new HipoReader();
            r.setTags(1);
            r.open(f);
            while (r.hasNext()) {
                r.nextEvent(e);
                e.read(unix);
                int size = unix.getRows();
                for (int i=0; i<size; i++) {
                    m.put(unix.getInt("event",i), unix.getInt("unixtime",i));
                }
            }
            r.close();
        }
        return m;
    }

    /**
     * Modify REC::Event/HEL::scaler with the delay-corrected helicity
     * @param event
     * @param runcfg
     * @param recevt 
     */
    private void processEventHelicity(DataEvent event, DataBank runcfg, DataBank recevt) {
        HelicityBit hb = helicitySequence.search(runcfg.getLong("timestamp", 0));
        HelicityBit hbraw = helicitySequence.getHalfWavePlate() ? HelicityBit.getFlipped(hb) : hb;
        recevt.setByte("helicity",0,hb.value());
        recevt.setByte("helicityRaw",0,hbraw.value());
        DataBank helScaler = event.getBank("HEL::scaler");
        if (helScaler.rows()>0) {
            event.removeBank("HEL::scaler");
            Util.assignScalerHelicity(runcfg.getLong("timestamp",0), ((HipoDataBank)helScaler).getBank(), helicitySequence);
            event.appendBank(helScaler);
        }
    }

    /**
     * Modify REC::Event/HEL::scaler with the delay-corrected helicity
     * @param event
     * @param runcfg
     * @param recevt 
     */
    private void processEventHelicity(Event event, Bank runcfg, Bank recevt) {
        HelicityBit hb = helicitySequence.search(runcfg.getLong("timestamp", 0));
        HelicityBit hbraw = helicitySequence.getHalfWavePlate() ? HelicityBit.getFlipped(hb) : hb;
        recevt.setByte("helicity",0,hb.value());
        recevt.setByte("helicityRaw",0,hbraw.value());
        Bank helScaler = new Bank(schemaFactory.getSchema("HEL::scaler"));
        event.read(helScaler);
        if (helScaler.getRows()>0) {
            event.remove(schemaFactory.getSchema("HEL::scaler"));
            Util.assignScalerHelicity(runcfg.getLong("timestamp",0), helScaler, helicitySequence);
            event.write(helScaler);
        }
    }

    /**
     * Modify REC::Event for beam charge and livetime
     * @param runcfg
     * @param recevt 
     */
    private void processEventScalers(DataBank runcfg, DataBank recevt) {
        DaqScalers ds = chargeSequence.get(runcfg.getLong("timestamp", 0));
        if (ds != null) {
            recevt.setFloat("beamCharge",0, (float) ds.dsc2.getBeamChargeGated());
            recevt.setDouble("liveTime",0,ds.dsc2.getLivetime());
        }
    }

    /**
     * Modify REC::Event for beam charge and livetime
     * @param runcfg
     * @param recevt 
     */
    private void processEventScalers(Bank runcfg, Bank recevt) {
        DaqScalers ds = chargeSequence.get(runcfg.getLong("timestamp", 0));
        if (ds != null) {
            recevt.putFloat("beamCharge",0, (float) ds.dsc2.getBeamChargeGated());
            recevt.putDouble("liveTime",0,ds.dsc2.getLivetime());
        }
    }

    /**
     * Modify RUN::config's unixtime and update the event
     * @param event
     * @param runcfg 
     */
    private void processEventUnix(Event event, Bank runcfg) {
        if (runcfg.getRows() > 0) {
            Integer key =  eventUnix.floorKey(runcfg.getInt("event",0));
            if (key != null) {
                Integer unix = eventUnix.get(key);
                if (unix != null) {
                    event.remove(runcfg.getSchema());
                    runcfg.putInt("unixtime", 0, unix);
                    event.write(runcfg);
                }
            }
        }
    }

    /**
     * Modify RUN::config's unixtime and update the event
     * @param event
     * @param runcfg 
     */
    private void processEventUnix(DataEvent event, DataBank runcfg) {
        if (runcfg.rows() > 0) {
            Integer key = eventUnix.floorKey(runcfg.getInt("event",0));
            if (key != null) {
                Integer unix = eventUnix.get(key);
                if (unix != null) {
                    event.removeBank(runcfg.getDescriptor().getName());
                    runcfg.setInt("unixtime", 0, unix);
                    event.appendBank(runcfg);
                }
            }
        }
    }

    /**
     * Postprocess one event
     * @param event 
     */
    public void processEvent(DataEvent event) {
        if (event.hasBank("RUN::config")) {
            DataBank runcfg = event.getBank("RUN::config");
            if (runcfg.rows() > 0) {
                processEventUnix(event, runcfg);
                if (event.hasBank("REC::Event")) {
                    DataBank recevt = event.getBank("REC::Event");
                    if (recevt.rows() > 0) {
                        event.removeBank("REC::Event");
                        if (helicitySequence != null) processEventHelicity(event, runcfg, recevt);
                        if (chargeSequence != null) processEventScalers(runcfg, recevt);
                        event.appendBank(recevt);
                    }
                }
            }
        }
    }

    /**
     * Postprocess one event
     * @param event 
     */
    public void processEvent(Event event) {
        event.read(runConfig);
        event.read(recEvent);
        if (runConfig.getRows() > 0) {
            processEventUnix(event, runConfig);
            if (recEvent.getRows() > 0) {
                event.remove(recEvent.getSchema());
                if (helicitySequence != null) processEventHelicity(event, runConfig, recEvent);
                if (chargeSequence != null) processEventScalers(runConfig, recEvent);
                event.write(recEvent);
            }
        }
    }

    /**
     * Create rebuilt files from preload files.
     * @param files
     * @return map of rebuilt:preload files 
     */
    private Map<String,String> rebuild(String dir, List<String> files) {
        File d = new File(dir);
        if (!d.canWrite()) {
            throw new RuntimeException("No write permissions on "+dir);
        }
        Map<String,String> rebuiltFiles = new HashMap<>();
        for (String preloadFile : files) {
            String rebuiltFile = dir+"/"+outputPrefix+preloadFile.replace(dir+"/","");
            Util.rebuildScalers(conman, preloadFile, rebuiltFile);
            rebuiltFiles.put(rebuiltFile,preloadFile);
        }
        return rebuiltFiles;
    }

    /**
     * Replace files with new ones.
     * @param files map of new:old filenames
     */
    private static void replace(Map<String,String> files) {
        for (String rebuiltFile : files.keySet()) {
            new File(files.get(rebuiltFile)).delete();
            new File(rebuiltFile).renameTo(new File(files.get(rebuiltFile)));
        }
    }

    /**
     * The "postprocess" program.
     * @param args
     */
    public static void main(String args[]) {

        OptionParser o = new OptionParser("postprocess");
        o.addOption("-f","0","reflip:  rebuild the HEL::flip bank");
        o.addOption("-c","0","recharge:  rebuild the RUN/HEL::scaler banks");
        o.addOption("-o",null,"merged output file path");
        o.setRequiresInputList(true);
        o.parse(args);

        boolean restream = !o.getOption("-f").isDefault();
        boolean rebuild = !o.getOption("-c").isDefault();

        Processor post = new Processor(o.getInputList(), restream, rebuild);
        
        HipoWriterSorted writer = null;

        if (!o.getOption("-o").isDefault()) {
            writer = new HipoWriterSorted();
            SchemaFactory schema = writer.getSchemaFactory();
            schema.initFromDirectory(ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4"));
            writer.setCompressionType(2);
            writer.open(o.getOption("-o").stringValue());
        }

        for (String f : o.getInputList()) {
            HipoReader reader = new HipoReader();
            reader.setTags(0);
            reader.open(f);
            Event event = new Event();
            while (reader.hasNext()) {
                reader.nextEvent(event);
                post.processEvent(event);
                if (writer != null) writer.addEvent(event);
            }
            reader.close();
        }

        if (writer != null) writer.close();
    }

}
