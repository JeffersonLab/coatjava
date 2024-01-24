package org.jlab.detector.helicity;

import java.util.ArrayList;
import java.util.List;
import org.jlab.detector.decode.CLASDecoder4;
import org.jlab.detector.decode.EvioSortedSource;
import org.jlab.io.base.DataEvent;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;

/**
 *
 * @author baltzell
 */
public class HelicityUtil {
    
    /**
     * This is the helicity flip detection extracted from the standard EVIO-HIPO
     * decoder, CLASDecoder4, to facilitate decoding of data with EVIO event
     * misordering without changing their original order.
     * 
     * The original approach added helicity flip information to events on-the-fly
     * as the flips were detected.  Here, instead all events are first read and
     * sorted before flip detection.  This does imply reading all the events
     * twice, once here to get the ordering, and then again later to do the data
     * format conversion and write to a file.
     *
     * As is currently, this is very inefficient, due to reading far more raw
     * detector data than necessary for flip detection.  A first look suggests
     * that fixing that, without copy-pasting low-level code around, while 
     * still supporting all the FADC readout modes, would be a significant project
     * and time would be better spent on a larger rewrite, which is already in
     * progress but not ready to support the entire CLAS12 data.
     * 
     * Anyway, in the big picture the CPU time required is still very small
     * relative to reconstruction.
     * 
     * Alternatively, flip detection could operate as a post-processor on a HIPO
     * file, but it looks like the HIPO reader used in the decoder does not
     * support direct, non-sequential event access.
     * 
     * @return list of helicity flips' corresponding HEL::flip and RUN::config banks
     */
    public static List<Event> getFlips(EvioSortedSource s) {
        List<Event> flips = new ArrayList<>();
        CLASDecoder4 d = new CLASDecoder4();
        Bank adc = new Bank(d.getSchemaCopy("HEL::adc"));
        Bank cfg = new Bank(d.getSchemaCopy("RUN::config"));
        HelicityState prevHelicity = new HelicityState();
        while (s.hasEvent()) {
            DataEvent evio = s.getNextEvent();
            Event hipo = d.getDataEvent(evio);
            hipo.read(adc);
            if (adc.getRows()>0) {
                HelicityState thisHelicity = HelicityState.createFromFadcBank(adc);
                if (!thisHelicity.isValid() || !thisHelicity.equals(prevHelicity)) {
                    Bank flip = d.createHelicityFlipBank(hipo,thisHelicity);
                    Event e = new Event();
                    e.write(cfg);
                    e.write(flip);
                    flips.add(e);
                    prevHelicity = thisHelicity;
                }
            }
                
        }
        s.close();
        return flips;
    }

}
