package org.jlab.service.alert;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import org.jlab.io.base.DataEvent;
import org.jlab.detector.base.DetectorType;
import org.jlab.analysis.physics.TestEvent;
import org.jlab.service.ahdc.AHDCEngine;
import org.jlab.rec.ahdc.Mode;

/**
 *
 * @author baltzell
 * @author ftouchte
 */
public class AHDCTest {
	
  @Test
  public void run() {
    System.setProperty("CLAS12DIR", "../../");
    
    DataEvent event = TestEvent.get(DetectorType.AHDC);
    
    AHDCEngine engine = new AHDCEngine();
    engine.init(Mode.AI_Track_Finding);
    engine.processDataEvent(event);

    event.show();
    event.getBank("AHDC::hits").show();
    event.getBank("AHDC::clusters").show();
    
    assertEquals(event.hasBank("FAKE::Bank"), false);
    assertEquals(event.hasBank("AHDC::wf"), true);
    assertEquals(event.getBank("AHDC::hits").rows(), 25);    
  }

  public static void main(String[] args) {
      AHDCTest t = new AHDCTest();
      t.run();
  }

}
