package org.jlab.service.postproc;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.clara.std.services.EventReaderException;
import org.jlab.io.clara.HipoToHipoReader;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.data.Event;
import org.json.JSONObject;
import org.jlab.analysis.postprocess.Processor;

/**
 * Add postprocessing to HipoToHipoReader
 * @author baltzell
 */
public class HipoToHipoPostprocReader extends HipoToHipoReader {

    static final Logger logger = Logger.getLogger(HipoToHipoPostprocReader.class.getName());

    public static final String CONF_PRELOAD_DIR = "preloadDir";
    public static final String CONF_PRELOAD_GLOB = "preloadGlob";
    public static final String CONF_RESTREAM_HELICITY = "restream";
    public static final String CONF_REBUILD_SCALERS = "rebuild";

    private static boolean configured = false; 
    private static Processor processor = null;

    @Override
    protected HipoReader createReader(Path file, JSONObject opts)
            throws EventReaderException {
        configure(opts);
        return super.createReader(file, opts);
    }

    synchronized void configure(JSONObject opts) {
        if (!configured) {
            configured = true;
            if (opts.has(CONF_PRELOAD_DIR)) {
                String preloadDir = opts.getString(CONF_PRELOAD_DIR);
                String preloadGlob = opts.optString(CONF_PRELOAD_GLOB, Processor.DEF_PRELOAD_GLOB);
                boolean restream = opts.optBoolean(CONF_RESTREAM_HELICITY, false);
                boolean rebuild = opts.optBoolean(CONF_REBUILD_SCALERS, false);
                logger.log(Level.INFO, "Requested preloadDir={0}, postprocessing enabled.", preloadDir);
                logger.log(Level.INFO, "Restreaming helicity {0}abled.", restream?"en":"dis");
                logger.log(Level.INFO, "Rebuilding scalers {0}abled.", rebuild?"en":"dis");
                processor = new Processor(preloadDir, preloadGlob, restream);
                //if (rebuild) processor.rebuildAndReplace();
            }
        }
    }

    @Override
    public Object readEvent(int eventNumber) throws EventReaderException {
        try {
            Event event = new Event();
            event = reader.getEvent(event,eventNumber);
            if (processor != null) processor.processEvent(event);
            return event;
        } catch (Exception e) {
            throw new EventReaderException(e);
        }
    }

}
