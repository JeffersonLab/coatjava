package org.jlab.service.postproc;

import java.io.File;
import java.util.logging.Logger;
import org.jlab.analysis.postprocess.Processor;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataEvent;

/**
 * Post-processing as an engine.
 * @author baltzell
 */
public class PostprocEngine extends ReconstructionEngine {

    public static final String CONF_PRELOAD_FILE = "preloadFile";
    public static final String CONF_PRELOAD_DIR = "preloadDir";
    public static final String CONF_PRELOAD_GLOB = "preloadGlob";
    public static final String CONF_RESTREAM_HELICITY = "restream";
    public static final String CONF_REBUILD_SCALERS = "rebuild";

    static final Logger logger = Logger.getLogger(PostprocEngine.class.getName());

    Processor processor = null;

    public PostprocEngine() {
        super("PP", "baltzell", "1.0");
    }

    @Override
    public boolean init() {
        requireConstants(Processor.CCDB_TABLES);
        if (getEngineConfigString(CONF_PRELOAD_FILE) != null) {
            if (getEngineConfigString(CONF_PRELOAD_DIR) != null)
                logger.warning("PostprocEngine::  Ignoring preloadDir, using preloadFile.");
            processor = new Processor(new File(getEngineConfigString(CONF_PRELOAD_FILE)),
                Boolean.parseBoolean(getEngineConfigString(CONF_RESTREAM_HELICITY,"false")),
                Boolean.parseBoolean(getEngineConfigString(CONF_REBUILD_SCALERS,"false")));
        }
        else if (getEngineConfigString(CONF_PRELOAD_DIR) != null) {
            processor = new Processor(
                getEngineConfigString(CONF_PRELOAD_DIR),
                getEngineConfigString(CONF_PRELOAD_GLOB, Processor.DEF_PRELOAD_GLOB),
                Boolean.parseBoolean(getEngineConfigString(CONF_RESTREAM_HELICITY,"false")),
                Boolean.parseBoolean(getEngineConfigString(CONF_REBUILD_SCALERS,"false")));
        }
        return true;
    }

    @Override
    public void detectorChanged(int run) {}

    @Override
    public boolean processDataEvent(DataEvent event) {
        processor.processEvent(event);
        return true;
    }

}
