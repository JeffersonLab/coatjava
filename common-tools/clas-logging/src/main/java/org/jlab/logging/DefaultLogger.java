package org.jlab.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Read a logging configuration and load it into the global log manager.
 *
 * @author Nick Tyler, UofSC
 *
 * Modified from hps-java logging
 * @author Jeremy McCormick, SLAC
 */
public class DefaultLogger {

    private static void init(String resource) {
        // FIXME:  document why this check is here:
        if (System.getProperty("java.util.logging.config.file") == null) {
            InputStream inputStream = DefaultLogger.class.getResourceAsStream(resource);
            try {
                LogManager.getLogManager().readConfiguration(inputStream);
                Logger.getLogger(DefaultLogger.class.getName()).log(Level.INFO, "Reading logging properties from org/jlab/logging/{0}", resource);
            } catch (SecurityException | IOException e) {
                throw new RuntimeException("Initialization of default logging configuration failed.", e);
            }
        }
    }

    private static void init(Level level){
        init(level.toString().toLowerCase()+".properties");
    }

    public static void initialize() {
        init(Level.FINE);
    }

    public static void initialize(Level level) {
        init(level);
    }

    public static void debug() {
        init(Level.FINE);
    }

    public static void silent() {
        init("silent.properties");
    }

    public static void main(String[] args) {
        DefaultLogger.initialize();
        Logger.getLogger(DefaultLogger.class.getName()).log(Level.INFO,"Info");
        Logger.getLogger(DefaultLogger.class.getName()).log(Level.WARNING,"Warning");
        Logger.getLogger(DefaultLogger.class.getName()).log(Level.SEVERE,"Severe");
    }
}