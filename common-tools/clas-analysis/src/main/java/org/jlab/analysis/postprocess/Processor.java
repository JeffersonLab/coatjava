package org.jlab.analysis.postprocess;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.jlab.logging.DefaultLogger;

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

/**
 *
 * @author baltzell
 */
public class Processor {

    static final Logger logger = Logger.getLogger(Util.class.getName());

    public static final String CCDB_TABLES[] = {"/runcontrol/fcup","/runcontrol/slm",
        "/runcontrol/helicity","/daq/config/scalers/dsc1","/runcontrol/hwp"};
    public static final String DEF_PRELOAD_GLOB = "*.{hipo,h5}";

    private final String outputPrefix = "tmp_";

    private boolean initialized;
    private ConstantsManager conman = null;
    private SchemaFactory schemaFactory = null;
    private DaqScalersSequence chargeSequence = null;
    private HelicitySequenceDelayed helicitySequence = null;

    public Processor(File file, boolean restream) {
        configure(restream, Arrays.asList(file.getAbsolutePath()));
    }
    
    public Processor(String dir, boolean restream) {
        configure(restream, findPreloadFiles(dir,DEF_PRELOAD_GLOB));
    }

    public Processor(String dir, String glob, boolean restream) {
        configure(restream, findPreloadFiles(dir,glob));
    }

    private void configure(boolean restream, List<String> preloadFiles) {
        if (preloadFiles.isEmpty()) {
            logger.warning("<<<< No preload files found, postprocessing disabled.");
            initialized = false;
        } else {
            HipoReader r = new HipoReader();
            r.open(preloadFiles.get(0));
            conman = new ConstantsManager();
            conman.init(CCDB_TABLES);
            schemaFactory = r.getSchemaFactory();
            helicitySequence = Util.getHelicity(preloadFiles, schemaFactory, restream, conman);
            chargeSequence = DaqScalersSequence.readSequence(preloadFiles);
            r.close();
            initialized = true;
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
     * Modify REC::Event/HEL::scaler with the delay-corrected helicity
     * @param event
     * @param runConfig
     * @param recEvent 
     */
    private void processEventHelicity(DataEvent event, DataBank runConfig, DataBank recEvent) {
        HelicityBit hb = helicitySequence.search(runConfig.getLong("timestamp", 0));
        HelicityBit hbraw = helicitySequence.getHalfWavePlate() ? HelicityBit.getFlipped(hb) : hb;
        recEvent.setByte("helicity",0,hb.value());
        recEvent.setByte("helicityRaw",0,hbraw.value());
        DataBank helScaler = event.getBank("HEL::scaler");
        if (helScaler.rows()>0) {
            event.removeBank("HEL::scaler");
            Util.assignScalerHelicity(runConfig.getLong("timestamp",0), ((HipoDataBank)helScaler).getBank(), helicitySequence);
            event.appendBank(helScaler);
        }
    }

    /**
     * Modify REC::Event/HEL::scaler with the delay-corrected helicity
     * @param event
     * @param runConfig
     * @param recEvent 
     */
    private void processEventHelicity(Event event, Bank runConfig, Bank recEvent) {
        HelicityBit hb = helicitySequence.search(runConfig.getLong("timestamp", 0));
        HelicityBit hbraw = helicitySequence.getHalfWavePlate() ? HelicityBit.getFlipped(hb) : hb;
        recEvent.setByte("helicity",0,hb.value());
        recEvent.setByte("helicityRaw",0,hbraw.value());
        Bank helScaler = new Bank(schemaFactory.getSchema("HEL::scaler"));
        event.read(helScaler);
        if (helScaler.getRows()>0) {
            event.remove(schemaFactory.getSchema("HEL::scaler"));
            Util.assignScalerHelicity(runConfig.getLong("timestamp",0), helScaler, helicitySequence);
            event.write(helScaler);
        }
    }

    /**
     * Modify REC::Event for beam charge and livetime
     * @param runConfig
     * @param recEvent 
     */
    private void processEventScalers(DataBank runConfig, DataBank recEvent) {
        DaqScalers ds = chargeSequence.get(runConfig.getLong("timestamp", 0));
        if (ds != null) {
            recEvent.setFloat("beamCharge",0, (float) ds.dsc2.getBeamChargeGated());
            recEvent.setDouble("liveTime",0,ds.dsc2.getLivetime());
        }
    }

    /**
     * Modify REC::Event for beam charge and livetime
     * @param runConfig
     * @param recEvent 
     */
    private void processEventScalers(Bank runConfig, Bank recEvent) {
        DaqScalers ds = chargeSequence.get(runConfig.getLong("timestamp", 0));
        if (ds != null) {
            recEvent.putFloat("beamCharge",0, (float) ds.dsc2.getBeamChargeGated());
            recEvent.putDouble("liveTime",0,ds.dsc2.getLivetime());
        }
    }

    /**
     * Postprocess one event
     * @param e 
     */
    public void processEvent(DataEvent e) {
        if (!initialized) return;
        if (!e.hasBank("RUN::config")) return;
        if (!e.hasBank("REC::Event")) return;
        DataBank runConfig = e.getBank("RUN::config");
        DataBank recEvent = e.getBank("REC::Event");
        if (runConfig.rows()<1 || recEvent.rows()<1) return;
        e.removeBank("REC::Event");
        if (helicitySequence != null) processEventHelicity(e, runConfig, recEvent);
        if (chargeSequence != null) processEventScalers(runConfig, recEvent);
        e.appendBank(recEvent);
    }

    /**
     * Postprocess one event
     * @param e 
     */
    public void processEvent(Event e) {
        if (!initialized) return;
        if (!e.hasBank(schemaFactory.getSchema("RUN::config"))) return;
        if (!e.hasBank(schemaFactory.getSchema("REC::Event"))) return;
        Bank runConfig = new Bank(schemaFactory.getSchema("RUN::config"));
        Bank recEvent = new Bank(schemaFactory.getSchema("REC::Event"));
        e.read(runConfig);
        e.read(recEvent);
        if (runConfig.getRows()<1 || recEvent.getRows()<1) return;
        e.remove(schemaFactory.getSchema("REC::Event"));
        if (helicitySequence != null) processEventHelicity(e, runConfig, recEvent);
        if (chargeSequence != null) processEventScalers(runConfig, recEvent);
        e.write(recEvent);
    }

    /**
     * Create rebuilt files from preload files.
     * @param preloadFiles
     * @return map of rebuilt:preload files 
     */
    private Map<String,String> rebuild(String dir, List<String> preloadFiles) {
        File d = new File(dir);
        if (!d.canWrite()) {
            throw new RuntimeException("No write permissions on "+dir);
        }
        Map<String,String> rebuiltFiles = new HashMap<>();
        for (String preloadFile : preloadFiles) {
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
     * Replace preload files with rebuilt ones. 
     */
    private void rebuildAndReplace(List<String> preloadFiles) {
        replace(rebuild(".",preloadFiles));
    }

    public static void main(String args[]) {
        DefaultLogger.debug();
        Processor p = new Processor(System.getenv("HOME")+"/tmp","r*.hipo",false);
        //p.rebuildAndReplace();
    }

}
