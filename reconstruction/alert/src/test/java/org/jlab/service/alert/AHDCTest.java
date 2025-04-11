package org.jlab.service.alert;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import org.jlab.io.base.DataEvent;
import org.jlab.detector.base.DetectorType;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.logging.DefaultLogger;
import org.jlab.utils.system.ClasUtilsFile;
import org.jlab.analysis.physics.TestEvent;

/**
 *
 * @author baltzell
 */
public class AHDCTest {
	
  @Test
  public static void run() {
    System.setProperty("CLAS12DIR", "../../");
    DefaultLogger.debug();
    String dir = ClasUtilsFile.getResourceDir("CLAS12DIR", "etc/bankdefs/hipo4");
    SchemaFactory schemaFactory = new SchemaFactory();
    schemaFactory.initFromDirectory(dir);
    
    DataEvent event = TestEvent.get(DetectorType.AHDC);
    
    ALERTEngine engine = new ALERTEngine();
    engine.init();
    engine.processDataEvent(event);

    event.show();
    event.getBank("ECAL::hits").show();
    event.getBank("ECAL::clusters").show();
    
    assertEquals(event.hasBank("FAKE::Bank"), false);
    assertEquals(event.hasBank("AHDC::wf"), true);
    assertEquals(event.getBank("AHDC::hits").rows(), 3);    
  }

  public static void main(String[] args) {
      run();
  }

}
