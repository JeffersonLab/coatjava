package org.jlab.logging;

import java.util.logging.Level;

/** Configuration singleton for {@code SplitLogManager} */
public enum SplitLogManagerConfig {

  /** singleton instance */
  INSTANCE;

  private volatile Level defaultLevel            = Level.INFO;
  private volatile boolean calledSetDefaultLevel = false;

  /**
   * Set the default {@code logging.Level} for all new {@code SplitLogManager}'s {@code Logger} instances.
   * Note: see {@code SplitLogManager} details to check if other ways to set logging levels will take priority over this.
   * @param level the log level
   */
  public synchronized void setDefaultLevel(Level level) {
    this.defaultLevel          = level;
    this.calledSetDefaultLevel = true;
  }

  /** @return the default {@code logging.Level} for all new {@code SplitLogManager}'s {@code Logger} instances.
   * Note: see {@code SplitLogManager} details to check if other ways to set logging levels will take priority over this.
   */
  public Level getDefaultLevel() {
    return this.defaultLevel;
  }

  /** @return true if {@code setDefaultLevel} was called */
  public boolean defaultLevelWasSet() {
    return this.calledSetDefaultLevel;
  }

}
