package org.jlab.clas.reco;

import java.util.TreeMap;
import java.util.TreeSet;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.decode.CLASDecoder;
import org.jlab.detector.helicity.HelicityState;
import org.jlab.detector.scalers.DaqScalersSequence;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;

/**
 *
 * @author baltzell
 */
public class SerialHoncho {
    
    Bank[] tag1banks;
    Bank runConfig;
    Bank helicityAdc;
    ConstantsManager conman;
    TreeMap<Integer,Integer> eventUnix;
    TreeSet<HelicityState> helicities;
    DaqScalersSequence scalers;
   
    public SerialHoncho(){}

    public Event[] doggy(Event event) {
        scalers.add(event);
        event.read(runConfig);
        event.read(helicityAdc);
        if (runConfig.getRows() > 0) {
            int unix = runConfig.getInt("unixtime",0);
            int evno = runConfig.getInt("event",0);
            if (unix > 0 && evno > 0) eventUnix.put(evno, unix);
        }
        helicities.add(HelicityState.createFromFadcBank(helicityAdc, runConfig, conman));
        return new Event[]{CLASDecoder.createTaggedEvent(event, runConfig, tag1banks)};
    }
}
