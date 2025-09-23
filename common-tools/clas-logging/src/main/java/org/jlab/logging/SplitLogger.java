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

    // clear handlers
    logger.setUseParentHandlers(false);

    // terse logger name
    String terseName = logger.getName().replaceAll(".*\\.","");

    // log message formatting
    java.util.logging.Formatter formatter = new java.util.logging.Formatter() {
      @Override
      public synchronized String format(java.util.logging.LogRecord lr) {
        // prefix
        StringBuilder prefix = new StringBuilder("[" + terseName);
        String methodName = lr.getSourceMethodName();
        if(methodName != null)
          prefix.append("."+methodName);
        prefix.append("] ");
        // level
        if(lr.getLevel().intValue() >= Level.WARNING.intValue())
          prefix.append(lr.getLevel() + ": ");
        // message
        StringBuilder result = new StringBuilder(prefix);
        String msg = (lr.getParameters() != null && lr.getParameters().length > 0)
          ? java.text.MessageFormat.format(lr.getMessage(), lr.getParameters())
          : lr.getMessage();
        result.append(msg);
        // stack trace
        if(lr.getThrown() != null) {
          result.append(System.lineSeparator());
          java.io.StringWriter sw = new java.io.StringWriter();
          java.io.PrintWriter pw = new java.io.PrintWriter(sw);
          lr.getThrown().printStackTrace(pw);
          result.append(sw.toString());
        }
        // final result
        result.append(System.lineSeparator());
        return result.toString();
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

    // add the handlers
    logger.addHandler(infoHandler);
    logger.addHandler(errorHandler);

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
