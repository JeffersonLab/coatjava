package org.jlab.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper methods to create a {@code Logger} that sends errors to {@code stderr} and everything else to {@code stdout}
 * @see TestSplitLogger {@code TestSplitLogger}: for guidance on how to use this class
 * @author dilks
 */
public class SplitLogger {

  /**
   * create a new {@link SplitLogger} instance
   * @return a new {@link SplitLogger} instance
   * @param the name of the logger
   */
  public static Logger create(String name) {
    Logger logger = Logger.getLogger(name);
    configureHandlers(logger);
    return logger;
  }

  /**
   * Configure a logger handlers such that errors go to {@code stderr} and everything else to {@code stdout}
   * @param logger the {@code Logger} instance to configure
   */
  public static void configureHandlers(Logger logger) {

    // set the log level, since the handlers need to know it too
    Level thisLevel = logger.getLevel();
    if(thisLevel==null) { // caller did not set log level, use parent
      thisLevel = logger.getParent().getLevel();
      if(thisLevel==null) { // should never happen, but just in case, fall back to default and complain directly to `stderr`
        thisLevel = Level.INFO;
        System.err.println("WARNING: trouble setting level of logger '" + logger.getName() + "'; defaulting to level '" + thisLevel + "'");
      }
    }
    configureLevel(logger, thisLevel);
  }

  /**
   * set the log level of a logger and its handlers
   * @param logger the {@code Logger} instance to configure
   * @param level the {@code Level} to apply
   */
  public static void configureLevel(Logger logger, Level level) {
    logger.setLevel(level);
    for(var handler : logger.getHandlers())
      handler.setLevel(level);
  }

}
