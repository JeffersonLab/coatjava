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
    public static final String CONF_REUSE_EVENTS = "reuseEvents";

    static final Logger logger = Logger.getLogger(BackgroundEngine.class.getName());

    int filesUsed = 0;
    boolean reuseEvents = true;
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

    @Override
    public void detectorChanged(int run){}

    public boolean init(String... filenames) {
        bgfilenames.clear();
        String detectors = getEngineConfigString(CONF_DETECTORS,"DC,FTOF");
        String orders = getEngineConfigString(CONF_ORDERS,"NOMINAL");
        boolean suppressDoubles = Boolean.valueOf(getEngineConfigString(CONF_SUPPRESS_DOUBLES,"true"));
        boolean preserveOrder = Boolean.valueOf(getEngineConfigString(CONF_PRESERVE_ORDER,"true"));
        boolean reuseEvents = Boolean.valueOf(getEngineConfigString(CONF_REUSE_EVENTS,"false"));
        for (String filename : filenames) {
            File f = new File(filename);
            if (!f.exists() || !f.isFile() || !f.canRead()) {
                logger.log(Level.SEVERE,"BackgroundEngine:: filename {0} invalid.",filename);
                return false;
            }
            logger.log(Level.INFO,"BackgroundEngine::  reading {0}",filename);
            bgfilenames.add(filename);
        }
        bgmerger = new EventMerger(detectors.split(","), orders.split(","), suppressDoubles, preserveOrder);
        openNextFile();
        return true;
    }

    private void openNextFile() {
        if (filesUsed>0 && filesUsed%bgfilenames.size()==0) {
            if (reuseEvents) logger.info("BackgroundEngine::  Reopening previously used file.");
            else throw new RuntimeException("BackgroundEngine::  Ran out of events.");
        }
        String filename = bgfilenames.remove();
        bgfilenames.add(filename);
        bgreader = new HipoDataSource();
        bgreader.open(filename);
        filesUsed++;
    }

    synchronized public DataEvent getBackgroundEvent() {
        if (!bgreader.hasEvent()) openNextFile();
        return bgreader.getNextEvent();
    }

    @Override
    public boolean processDataEventUser(DataEvent event) {
        if (!bgfilenames.isEmpty()) {
            DataEvent a = getBackgroundEvent();
            DataEvent b = getBackgroundEvent();
            bgmerger.mergeEvents(event, a, b);
        }
        return true;
    }

}
