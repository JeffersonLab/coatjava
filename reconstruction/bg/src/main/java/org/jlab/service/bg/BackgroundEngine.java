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

    EventMerger bgmerger = null;
    HipoDataSource bgreader = null;
    LinkedList<String> bgfilenames = new LinkedList<>();

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
        bgfilenames.clear();
        for (String filename : filenames) {
            File f = new File(filename);
            if (!f.exists() || !f.isFile() || !f.canRead()) {
                logger.log(Level.SEVERE,"BackgroundEngine:: filename {0} invalid.",filename);
                return false;
            }
            logger.log(Level.INFO,"BackgroundEngine::  reading {0}",filename);
            bgfilenames.add(filename);
        }
        String detectors = getEngineConfigString(CONF_DETECTORS,"DC,FTOF");
        String orders = getEngineConfigString(CONF_ORDERS,"NOMINAL");
        Boolean suppressDoubles = Boolean.valueOf(getEngineConfigString(CONF_SUPPRESS_DOUBLES,"true"));
        Boolean preserveOrder = Boolean.valueOf(getEngineConfigString(CONF_PRESERVE_ORDER,"true"));
        bgmerger = new EventMerger(detectors.split(","), orders.split(","), suppressDoubles, preserveOrder);
        openNextFile();
        return true;
    }

    private void openNextFile() {
        String filename = bgfilenames.remove();
        bgfilenames.add(filename);
        bgreader = new HipoDataSource();
        bgreader.open(filename);
    }

    synchronized public DataEvent getBackgroundEvent() {
        if (!bgreader.hasEvent()) openNextFile();
        return bgreader.getNextEvent();
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        if (!bgfilenames.isEmpty()) {
            DataEvent a = getBackgroundEvent();
            DataEvent b = getBackgroundEvent();
            bgmerger.mergeEvents(event, a, b);
        }
        return true;
    }

}
