package org.jlab.logging;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Kluge around certain upstream function calls that alter log levels against our will.
 * @author dilks
 */
public class LogLevelGuard {

  private final List<String> loggerNames;
  private final Map<String, Level> savedLevels = new HashMap<>();

  /**
   * constructor
   * @param loggerNames the names of the Loggers to guard
   */
  public LogLevelGuard(String... loggerNames) {
    this.loggerNames = List.of(loggerNames);
  }

  /** save the log levels */
  public void save() {
    for(var name : loggerNames)
      savedLevels.put(name, Logger.getLogger(name).getLevel());
  }

  /** restore the log levels */
  public void restore() {
    for(var entry : savedLevels.entrySet())
      if(entry.getValue() != null)
        Logger.getLogger(entry.getKey()).setLevel(entry.getValue());
  }

}
