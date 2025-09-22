package org.jlab.logging;

import java.util.logging.Logger;
import java.util.logging.Level;

public class TestSplitLogger {

  protected Logger LOGGER = SplitLogger.create(TestSplitLogger.class.getName());
  // static final Logger LOGGER = Logger.getLogger(TestSplitLogger.class.getName());

  public TestSplitLogger() {
    System.out.println("Created 'TestSplitLogger' instance");
    System.out.println("Log level = " + LOGGER.getLevel());
  }

  public void test1() {
    System.out.println("=== TEST 1 ===");
    LOGGER.severe("SEVERE MESSAGE - should go to stderr" );
    LOGGER.warning("WARNING MESSAGE - should go to stderr" );
    LOGGER.info("INFO MESSAGE - should go to stdout" );
    LOGGER.config("CONFIG MESSAGE - should go to stdout" );
    LOGGER.fine("FINE MESSAGE - should go to stdout" );
    LOGGER.finer("FINER MESSAGE - should go to stdout" );
    LOGGER.finest("FINEST MESSAGE - should go to stdout" );
  }

  public void test2() {
    System.out.println("=== TEST 2 ===");
    LOGGER.log(Level.SEVERE, "{0} MESSAGE - should go to stderr", Level.SEVERE);
    LOGGER.log(Level.WARNING, "{0} MESSAGE - should go to stderr", Level.WARNING);
    LOGGER.log(Level.INFO, "{0} MESSAGE - should go to stdout", Level.INFO);
    LOGGER.log(Level.CONFIG, "{0} MESSAGE - should go to stdout", Level.CONFIG);
    LOGGER.log(Level.FINE, "{0} MESSAGE - should go to stdout", Level.FINE);
    LOGGER.log(Level.FINER, "{0} MESSAGE - should go to stdout", Level.FINER);
    LOGGER.log(Level.FINEST, "{0} MESSAGE - should go to stdout", Level.FINEST);
  }

  public static void main(String[] args) {
    var potato = new TestSplitLogger();
    potato.test1();
    potato.test2();
  }

}
