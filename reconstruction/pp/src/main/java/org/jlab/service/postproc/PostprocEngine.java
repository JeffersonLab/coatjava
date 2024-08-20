package org.jlab.service.postproc;

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
            if (getEngineConfigString(CONF_PRELOAD_DIR) != null) {
            }
        }
        if (getEngineConfigString(CONF_PRELOAD_DIR) != null) {
            processor = new Processor(
                getEngineConfigString(CONF_PRELOAD_DIR),
                getEngineConfigString(CONF_PRELOAD_GLOB, Processor.DEF_PRELOAD_GLOB),
                Boolean.parseBoolean(getEngineConfigString(CONF_RESTREAM_HELICITY,"false")));
        }
        if (null != getEngineConfigString(CONF_REBUILD_SCALERS)) {
            if (Boolean.getBoolean(getEngineConfigString(CONF_REBUILD_SCALERS))) {
                //processor.rebuildAndReplace();
            }
        }
        return true;
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        if (processor != null) processor.processEvent(event);
        return true;
    }

}
