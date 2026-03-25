package org.jlab.logging;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogManager;

/**
 * {@code LogManager} that sends errors to {@code stderr} and everything else to {@code stdout}
 * @author dilks
 */
public class SplitLogManager extends LogManager {

  /** whether or not this manager is managing */
  private static final boolean isManaging = SplitLogManager.class.getName().equals(System.getProperty("java.util.logging.manager"));

  /**
   * create a new {@link Logger} instance
   * @param name the name of the logger
   * @return a new {@link Logger} instance
   */
  @Override
  public Logger getLogger(String name) {
    warnIfNotManaging("getLogger");
    Logger logger = super.getLogger(name);
    if(logger != null)
      configureHandlers(logger, true);
    return logger;
  }

  /**
   * add a new {@link Logger} instance
   * @param name the name of the logger
   * @return {@code true} if the argument logger was registered successfully, {@code false} if a logger of that name already exists
   */
  @Override
  public synchronized boolean addLogger(Logger logger) {
    warnIfNotManaging("addLogger");
    boolean added = super.addLogger(logger);
    if(added)
      configureHandlers(logger, true);
    return added;
  }

  /**
   * Configure a logger handlers such that errors go to {@code stderr} and everything else to {@code stdout}
   * @param logger the {@code Logger} instance to configure
   * @param includePrefix whether or not to include a prefix in the formatting
   */
  public static void configureHandlers(Logger logger, boolean includePrefix) {

    // do nothing, if `SplitLogManager` is not the log manager
    if(!isManaging)
      return;

    // clear handlers
    logger.setUseParentHandlers(false);
    for(var handler : logger.getHandlers())
      logger.removeHandler(handler);

    // log message formatting
    if(includePrefix)
      // "[source] level: message throwable_backtrace\n"
      System.setProperty(
          "java.util.logging.SimpleFormatter.format",
          "%4$s: [" + logger.getName().replaceAll(".*\\.","") + "] %5$s%6$s%n");
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
    Level thisLevel = null;
    // if a system property named '<ClassName>.level' is set, use that
    String userLevelProperty = System.getProperty(logger.getName() + ".level");
    if(userLevelProperty != null)
      thisLevel = Level.parse(userLevelProperty);
    // else if the `SplitLogManagerConfig` default level was set, use that level
    else if(SplitLogManagerConfig.INSTANCE.defaultLevelWasSet())
      thisLevel = SplitLogManagerConfig.INSTANCE.getDefaultLevel();
    // else fallback to the level of `logger` itself
    else
      thisLevel = logger.getLevel();
    // if all else fails, try to use the parent's level
    if(thisLevel==null) {
      try {
        thisLevel = logger.getParent().getLevel();
      }
      catch(NullPointerException e) {
        System.err.println("WARNING: 'getParent()' of logger '" + logger.getName() + "' failed");
      }
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
    if(!isManaging)
      return;
    warnIfNotManaging("configureLevel");
    logger.setLevel(level);
    for(var handler : logger.getHandlers())
      handler.setLevel(level);
  }

  /**
   * warn, if this log manager is not the manager
   * @param src the source of the warning, such as a function name
   */
  private static void warnIfNotManaging(String src) {
    if(!isManaging)
      System.err.println("WARNING: SplitLogManager is not the LogManager, but its '" + src + "' was called");
  }

}
