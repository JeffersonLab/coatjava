package org.jlab.service.bg;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.analysis.eventmerger.EventMerger;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;

/**
 *
 * @author baltzell
 */
public class BackgroundEngine extends ReconstructionEngine {

    public static final String CONF_FILENAME = "filename";
    public static final String CONF_DETECTORS = "detectors";
    public static final String CONF_ORDERS = "orders";
    public static final String CONF_SUPPRESS_DOUBLES = "suppressDoubles";
    public static final String CONF_PRESERVE_ORDER = "preserveOrder";
    public static final String CONF_REUSE_EVENTS = "reuseEvents";
    public static final String CONF_BG_SCALE = "bgScale";

    static final Logger logger = Logger.getLogger(BackgroundEngine.class.getName());

    EventMerger bgmerger = null;
//    LinkedList<String> bgfilenames = new LinkedList<>();

    public BackgroundEngine() {
        super("BG", "baltzell", "1.0");
    }

    @Override
    public boolean init() {
        if (getEngineConfigString(CONF_FILENAME) != null)
            return init(getEngineConfigString(CONF_FILENAME).split(","));
        return true;
    }

    public boolean init(String... filenames) {
        String detectors = getEngineConfigString(CONF_DETECTORS,"DC,FTOF");
        String orders = getEngineConfigString(CONF_ORDERS,"NOMINAL");
        boolean suppressDoubles = Boolean.valueOf(getEngineConfigString(CONF_SUPPRESS_DOUBLES,"true"));
        boolean preserveOrder = Boolean.valueOf(getEngineConfigString(CONF_PRESERVE_ORDER,"true"));
        boolean reuseEvents = Boolean.valueOf(getEngineConfigString(CONF_REUSE_EVENTS,"false"));
        int     bgScale = Integer.valueOf(getEngineConfigString(CONF_BG_SCALE,"1"));
        bgmerger = new EventMerger(detectors.split(","), orders.split(","), suppressDoubles, preserveOrder);
        return bgmerger.setBgFiles(Arrays.asList(filenames), bgScale, reuseEvents);
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        return bgmerger.mergeEvents(event);
    }

}
