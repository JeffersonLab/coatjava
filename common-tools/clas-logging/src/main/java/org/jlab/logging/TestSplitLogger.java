package org.jlab.logging;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A simple class demonstrating how to use {@link SplitLogger}.
 * <p>
 * The {@link SplitLogger} class will send {@code SEVERE} and {@code WARNING} log messages
 * to {@code stderr}, and all lower levels to {@code stdout}.
 * <p>
 * <b>How to set the logging level:</b>
 * <p>
 * A {@code .properties} file is necessary, which has the class name and desired log level; for example,
 * <pre>
 * org.jlab.logging.TestSplitLogger.level = FINE
 * </pre>
 * From high to low, the levels are: {@code SEVERE, WARNING, INFO, CONFIG, FINE, FINER, FINEST}.
 * <p>
 * There are some example {@code .properties} files you can use with this class, like so:
 * <pre>
 * java \
 *   -Djava.util.logging.config.file=common-tools/clas-logging/src/main/resources/org/jlab/logging/TestSplitLogger.finest.properties \
 *   -cp ... \
 *   org.jlab.logging.TestSplitLogger
 * </pre>
 * You may write your own {@code .properties} file, to control the logging level of all classes which use logging.
 * @see SplitLogger {@code SplitLogger}: for implementation details
 * @author dilks
 */
public class TestSplitLogger {

  /** logger instance for this class */
  protected static final Logger LOGGER = SplitLogger.create(TestSplitLogger.class.getName());

  /** constructor: prints some messages to {@code stdout}, but not using the logger yet */
  public TestSplitLogger() {
    System.out.println("Created 'TestSplitLogger' instance");
    System.out.println("Log level = " + LOGGER.getLevel());
  }

  /** test sending string literals */
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

  /** test sending a format string with parameters */
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

  /** test exception handling */
  public void test3() {
    System.out.println("=== TEST 3 ===");
    try {
      throwRuntimeException();
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, "caught test 3 exception", ex);
    }
  }

  /** test exception handling */
  public void test4() {
    System.out.println("=== TEST 4 ===");
    try {
      catchAndThrowException();
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, "caught test 4 exception", ex);
    }
  }

  /**
   * test lazy evaluation.
   * the slow function {@code nap()} will only run if the log level is {@code FINEST}.
   */
  public void test5() {
    System.out.println("=== TEST 5 ===");
    LOGGER.finest(() -> String.format("status: %s", nap()));
  }

  /** throw a runtime exception */
  private static void throwRuntimeException() throws RuntimeException {
    throw new RuntimeException("this is a test runtime exception");
  }

  /** catch and throw an exception */
  private static void catchAndThrowException() throws Exception {
    try {
      throwRuntimeException();
    } catch(RuntimeException ex) {
      throw new Exception("this is a test caught and thrown exception", ex);
    }
  }

  /** sleep for 5 seconds */
  private static String nap() {
    try {
      LOGGER.warning("sleeping....");
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return "done sleeping";
  }

  /**
   * run the tests
   * @param args unused
   */
  public static void main(String[] args) {
    var potato = new TestSplitLogger();
    potato.test1();
    potato.test2();
    potato.test3();
    potato.test4();
    potato.test5();
  }

}
