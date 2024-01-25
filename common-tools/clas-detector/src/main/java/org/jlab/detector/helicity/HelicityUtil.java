package org.jlab.detector.helicity;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.jlab.detector.decode.CLASDecoder4;
import org.jlab.detector.decode.EvioSortedSource;
import org.jlab.io.base.DataEvent;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.utils.benchmark.ProgressPrintout;

/**
 *
 * @author baltzell
 */
public class HelicityUtil {

    /**
     * This is just the helicity flip detection extracted from the standard
     * decoder, CLASDecoder4, to facilitate using that same decoder on misordered
     * EVIO events, without changing their original order in the output HIPO file.
     * 
     * The original approach added helicity flip information to events on-the-fly
     * as the flips were detected.  Here, instead all events are first read and
     * sorted, which isn't too much overhead because that only requires reading
     * event headers.  All events are then read again for helicity flip detection,
     * which is currently very inefficient due to reading far more raw detector
     * data than necessary for flip detection, due to reusing functionality from
     * the decoder.  A first look suggests that fixing that, without copy-pasting
     * low-level code around, while still supporting all the FADC readout modes,
     * would be a significant project and time would be better spent on a larger
     * rewrite, which is already in independently progress but not ready to
     * support the entire CLAS12 data.
     * 
     * Alternatively, flip detection could operate as a post-processor on a HIPO
     * file, which should be somewhat more efficient, but it looks like the HIPO
     * reader used in the decoder does not support direct, non-sequential event
     * access.
     * 
     * Meanwhile, in the big picture, this essentially doubles the CPU time
     * required for decoding, which is still a ~1 % effect on the total.
     * 
     * @param s
     * @return list of helicity flips' corresponding HEL::flip and RUN::config banks
     */
    public static List<Event> getFlips(EvioSortedSource s) {
        ProgressPrintout progress = new ProgressPrintout();
        Logger.getLogger(HelicityUtil.class.getName()).info("Initializing Helicity Flips");
        List<Event> flips = new ArrayList<>();
        CLASDecoder4 d = new CLASDecoder4();
        Bank adc = new Bank(d.getSchemaCopy("HEL::adc"));
        Bank cfg = new Bank(d.getSchemaCopy("RUN::config"));
        HelicityState prevHelicity = new HelicityState();
        while (s.hasEvent()) {
            DataEvent evio = s.getNextEvent();
            Event hipo = d.getDataEvent(evio);
            hipo.read(adc);
            hipo.read(cfg);
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
                progress.updateStatus();
            }
                
        }
        s.close();
        return flips;
    }

}
