package org.jlab.logging;

import java.util.logging.Logger;

public class TestSplitLogger {

  public static final Logger LOGGER = SplitLogger.create(TestSplitLogger.class.getName());

  public TestSplitLogger()
  {
  }

  public void test()
  {
    LOGGER.severe(  "TEST MESSAGE SEVERE  - should go to stderr" );
    LOGGER.warning( "TEST MESSAGE WARNING - should go to stderr" );
    LOGGER.info(    "TEST MESSAGE INFO    - should go to stdout" );
    LOGGER.config(  "TEST MESSAGE CONFIG  - should go to stdout" );
    LOGGER.fine(    "TEST MESSAGE FINE    - should go to stdout" );
    LOGGER.finer(   "TEST MESSAGE FINER   - should go to stdout" );
    LOGGER.finest(  "TEST MESSAGE FINEST  - should go to stdout" );
  }

  public static void main(String[] args)
  {
    var t = new TestSplitLogger();
    t.test();
  }

}
