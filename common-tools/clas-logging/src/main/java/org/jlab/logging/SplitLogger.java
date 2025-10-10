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
   * create a new {@link SplitLogger} instance, with formatted messages
   * @return a new {@link SplitLogger} instance
   * @param name the name of the logger
   */
  public static Logger create(String name) {
    return create(name, true);
  }

  /**
   * create a new {@link SplitLogger} instance, with an optional message formatting including a prefix
   * @return a new {@link SplitLogger} instance
   * @param name the name of the logger
   * @param includePrefix whether or not to include a prefix in the formatting
   */
  public static Logger create(String name, boolean includePrefix) {
    Logger logger = Logger.getLogger(name);
    configureHandlers(logger, includePrefix);
    return logger;
  }

  /**
   * Configure a logger handlers such that errors go to {@code stderr} and everything else to {@code stdout}
   * @param logger the {@code Logger} instance to configure
   * @param includePrefix whether or not to include a prefix in the formatting
   */
  public static void configureHandlers(Logger logger, boolean includePrefix) {

    // clear handlers
    logger.setUseParentHandlers(false);

    // log message formatting
    if(includePrefix)
      // "[source] level: message throwable_backtrace\n"
      System.setProperty(
          "java.util.logging.SimpleFormatter.format",
          "[" + logger.getName().replaceAll(".*\\.","") + "] %4$s: %5$s%6$s%n");
    else
      // "level: message throwable_backtrace\n"
      System.setProperty(
          "java.util.logging.SimpleFormatter.format",
          "%4$s: %5$s%6$s%n");
    java.util.logging.Formatter formatter = new java.util.logging.SimpleFormatter();

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
