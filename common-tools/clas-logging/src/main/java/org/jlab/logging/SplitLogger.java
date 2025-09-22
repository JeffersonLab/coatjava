package org.jlab.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper methods to create a `Logger` that sends errors to `stderr` and everything else to `stdout`
 * @author dilks
 */
public class SplitLogger {

  /**
   * @return a new `SplitLogger` instance
   * @param the name of the logger
   */
  public static Logger create(String name) {
    Logger logger = Logger.getLogger(name);
    configureLogger(logger);
    return logger;
  }

  /**
   * Configure a logger such that errors go to `stderr` and everything else to `stdout`
   * @param logger the `Logger` instance to configure
   */
  public static void configureLogger(Logger logger) {

    // clear handlers
    logger.setUseParentHandlers(false);

    // log message formatting
    java.util.logging.Formatter formatter = new java.util.logging.SimpleFormatter() {
      private static final String format = "%5$s%6$s%n";
      @Override
      public synchronized String format(java.util.logging.LogRecord lr) {
        return String.format(format,
            lr.getSourceClassName(),
            lr.getSourceMethodName(),
            lr.getLoggerName(),
            lr.getLevel().getLocalizedName(),
            lr.getMessage(),
            lr.getThrown() == null ? "" : lr.getThrown());
      }
    };

    // stdout handler
    java.util.logging.Handler infoHandler = new java.util.logging.StreamHandler(System.out, formatter) {
      @Override
      public synchronized void publish(java.util.logging.LogRecord record) {
        if(isLoggable(record) && record.getLevel().intValue() <= Level.INFO.intValue()) {
          super.publish(record);
          flush();
        }
      }
    };

    // stderr handler
    java.util.logging.Handler errorHandler = new java.util.logging.StreamHandler(System.err, formatter) {
      @Override
      public synchronized void publish(java.util.logging.LogRecord record) {
        if(isLoggable(record) && record.getLevel().intValue() >= Level.WARNING.intValue()) {
          super.publish(record);
          flush();
        }
      }
    };

    // tell the handlers which log level
    Level thisLevel = logger.getLevel();
    if(thisLevel==null) { // caller did not set log level, use parent
      thisLevel = logger.getParent().getLevel();
      if(thisLevel==null) { // should never happen, but just in case
        thisLevel = Level.INFO;
        System.err.println("WARNING: trouble setting level of logger '" + logger.getName() + "'; defaulting to level '" + thisLevel + "'");
      }
      logger.setLevel(thisLevel);
    }
    infoHandler.setLevel(thisLevel);
    errorHandler.setLevel(thisLevel);
    logger.addHandler(infoHandler);
    logger.addHandler(errorHandler);
  }

}
