package org.jlab.service.alert;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import org.jlab.io.base.DataEvent;
import org.jlab.detector.base.DetectorType;
import org.jlab.analysis.physics.TestEvent;
import org.jlab.service.atof.ATOFEngine;

/**
 * @N-Plx
 */
public class ATOFTest {
	
  @Test
  public void run() {
    System.setProperty("CLAS12DIR", "../../");
    
    DataEvent event = TestEvent.get(DetectorType.ATOF);
    
    ATOFEngine engine = new ATOFEngine();
    engine.init();
    engine.processDataEvent(event);

    event.show();
    event.getBank("ATOF::hits").show();
    event.getBank("ATOF::clusters").show();
    
    assertEquals(event.hasBank("FAKE::Bank"), false);
    assertEquals(event.hasBank("ATOF::tdc"), true);
    assertEquals(event.getBank("ATOF::hits").rows(), 8);    
  }

  public static void main(String[] args) {
      ATOFTest t = new ATOFTest();
      t.run();
  }
}
