package org.jlab.service.ec;

import org.junit.Test;
import static org.junit.Assert.*;

import org.jlab.io.base.DataEvent;

import org.jlab.analysis.physics.TestEvent;
import org.jlab.detector.base.DetectorType;

/**
 *
 * @author naharrison
 */
public class ECReconstructionTest {
	
  @Test
  public void testECReconstruction() {

    System.setProperty("CLAS12DIR", "../../");

    DataEvent testEvent = TestEvent.get(DetectorType.ECAL);
    
    ECEngine engineEC = new ECEngine();
    engineEC.init();
    engineEC.processEvent(testEvent);

    testEvent.show();
    testEvent.getBank("ECAL::hits").show();
    testEvent.getBank("ECAL::clusters").show();
    
    assertEquals(testEvent.hasBank("FAKE::Bank"), false);
    assertEquals(testEvent.hasBank("ECAL::clusters"), true);
    assertEquals(testEvent.getBank("ECAL::clusters").rows(), 3);    
  }

}
