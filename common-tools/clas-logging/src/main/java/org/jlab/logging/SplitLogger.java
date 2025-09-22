package org.jlab.logging;

import java.util.logging.Level;
import java.util.logging.Logger;

public class SplitLogger {

  /**
   * Configure a logger so INFO and below go to stdout, WARNING and above to stderr, with custom formatting.
   */
  public static void configureLogger(Logger logger) {
    logger.setUseParentHandlers(false);
    java.util.logging.Formatter formatter = new java.util.logging.SimpleFormatter() {
      private static final String format = "SPLIT LOGGER: %5$s%6$s%n";
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
    java.util.logging.Handler infoHandler = new java.util.logging.StreamHandler(System.out, formatter) {
      @Override
      public synchronized void publish(java.util.logging.LogRecord record) {
        if (record.getLevel().intValue() <= Level.INFO.intValue()) {
          super.publish(record);
          flush();
        }
      }
    };
    java.util.logging.Handler errorHandler = new java.util.logging.StreamHandler(System.err, formatter) {
      @Override
      public synchronized void publish(java.util.logging.LogRecord record) {
        if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
          super.publish(record);
          flush();
        }
      }
    };
    logger.addHandler(infoHandler);
    logger.addHandler(errorHandler);
  }

  /**
   * create a split logger
   */
  public static Logger create(String name) {
    Logger logger = Logger.getLogger(name);
    configureLogger(logger);
    return logger;
  }

}
