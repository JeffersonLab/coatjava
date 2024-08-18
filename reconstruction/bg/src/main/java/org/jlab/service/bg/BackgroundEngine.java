package org.jlab.service.bg;

import java.io.File;
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

    static final Logger logger = Logger.getLogger(BackgroundEngine.class.getName());

    EventMerger merger = null;
    HipoDataSource reader = null;
    LinkedList<String> filenames = new LinkedList<>();

    public BackgroundEngine() {
        super("BG", "baltzell", "1.0");
    }

    @Override
    public boolean init() {
        return init(getEngineConfigString(CONF_FILENAME));
    }

    public boolean init(String filename) {
        if (filename != null) {
            File f = new File(filename);
            if (!f.exists() || !f.isFile() || !f.canRead()) {
                logger.log(Level.SEVERE,"BackgroundEngine:: filename {} invalid:  ",filename);
                return false;
            }
            filenames.add(filename);
            String detectors = getEngineConfigString(CONF_DETECTORS,"DC,FTOF");
            String orders = getEngineConfigString(CONF_ORDERS,"NOMINAL");
            Boolean suppressDoubles = Boolean.getBoolean(getEngineConfigString(CONF_SUPPRESS_DOUBLES,"true"));
            Boolean preserveOrder = Boolean.getBoolean(getEngineConfigString(CONF_PRESERVE_ORDER,"true"));
            logger.log(Level.INFO,"BackgroundEngine::  reading {0}",filename);
            merger = new EventMerger(detectors.split(","), orders.split(","), suppressDoubles, preserveOrder);
            reader = new HipoDataSource();
            reader.open(getEngineConfigString(CONF_FILENAME));
        }
        return true;
    }

    synchronized public DataEvent getBackgroundEvent() {
        if (reader.hasEvent()) return reader.getNextEvent();
        String filename = filenames.remove();
        filenames.add(filename);
        reader = new HipoDataSource();
        reader.open(filename);
        return reader.getNextEvent();
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        if (filenames.size() > 0) {
            DataEvent a = getBackgroundEvent();
            DataEvent b = getBackgroundEvent();
            merger.mergeEvents(event, a, b);
        }
        return true;
    }

}
