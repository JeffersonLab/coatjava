package org.jlab.utils;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.coda.et.EtSystem;
import org.jlab.coda.et.EtSystemOpenConfig;
import org.jlab.coda.et.exception.EtException;
import org.jlab.coda.et.exception.EtTooManyException;

/**
 *
 * @author gavalian
 */
public class EtProducer {
    public static void main(String[] args){
        try {
            
            String etFile = args[0];
            String etHost = args[1];
            Integer etPort = 11111;
            
            EtSystemOpenConfig config = new EtSystemOpenConfig( etFile,etHost,etPort);
            EtSystem sys = new EtSystem(config);
            sys.open();
            
        } catch (EtException ex) {
            Logger.getLogger(EtProducer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(EtProducer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (EtTooManyException ex) {
            Logger.getLogger(EtProducer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
