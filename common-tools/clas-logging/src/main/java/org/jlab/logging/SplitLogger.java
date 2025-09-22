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
    configureLogger(logger);
    return logger;
  }

  /**
   * Configure a logger such that errors go to {@code stderr} and everything else to {@code stdout}
   * @param logger the {@code Logger} instance to configure
   */
  public static void configureLogger(Logger logger) {

    // clear handlers
    logger.setUseParentHandlers(false);

    // terse logger name
    String terseName = logger.getName().replaceAll(".*\\.","");

    // log message formatting
    java.util.logging.Formatter formatter = new java.util.logging.SimpleFormatter() {
      @Override
      public synchronized String format(java.util.logging.LogRecord lr) {
        String methodName = lr.getSourceMethodName();
        String msg = (lr.getParameters() != null && lr.getParameters().length > 0)
          ? java.text.MessageFormat.format(lr.getMessage(), lr.getParameters())
          : lr.getMessage();
        String throwable = (lr.getThrown() == null) ? "" : lr.getThrown().toString();
        return String.format("[%s.%s] %s %s",
            terseName,
            methodName==null ? "" : methodName,
            msg,
            throwable.isEmpty() ? "" : " " + throwable
            ) + System.lineSeparator();
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
