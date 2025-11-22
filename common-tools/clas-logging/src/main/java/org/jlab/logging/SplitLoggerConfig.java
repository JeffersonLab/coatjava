package org.jlab.logging;

import java.util.logging.Level;

/** Configuration singleton for {@code SplitLogger} */
public enum SplitLoggerConfig {

  /** singleton instance */
  INSTANCE;

  private volatile Level defaultLevel            = Level.INFO;
  private volatile boolean calledSetDefaultLevel = false;

  /**
   * Set the default {@code logging.Level} for all new {@code SplitLogger} instances.
   * Note: see {@code SplitLogger} details to check if other ways to set logging levels will take priority over this.
   * @param level the log level
   */
  public synchronized void setDefaultLevel(Level level) {
    this.defaultLevel          = level;
    this.calledSetDefaultLevel = true;
  }

  /** @return the default {@code logging.Level} for all new {@code SplitLogger} instances.
   * Note: see {@code SplitLogger} details to check if other ways to set logging levels will take priority over this.
   */
  public Level getDefaultLevel() {
    return this.defaultLevel;
  }

  /** @return true if {@code setDefaultLevel} was called */
  public boolean defaultLevelWasSet() {
    return this.calledSetDefaultLevel;
  }

}
