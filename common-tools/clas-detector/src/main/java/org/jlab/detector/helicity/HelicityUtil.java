package org.jlab.detector.helicity;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.jlab.detector.decode.CLASDecoder4;
import org.jlab.io.base.DataEvent;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.Schema;
import org.jlab.utils.benchmark.ProgressPrintout;

/**
 *
 * @author baltzell
 */
public class HelicityUtil {

    /**
     * Get all helicity flips as seen by the FADC board.
     * 
     * Note, this assumes events are ordered by time, so normally you'd want to
     * pass in a HipoSortedDataSource.
     * 
     * @param r
     * @param hwp half-wave plate status (-1/0/1 = IN/UDF/OUT)
     * @return list of helicity flips' corresponding HEL::flip and RUN::config banks
     */
    public static List<Event> getFlips(HipoDataSource r, byte hwp) {
        List<Event> flips = new ArrayList<>();
        Schema s = r.getReader().getSchemaFactory().getSchema("HEL::adc");
        Bank adc = new Bank(r.getReader().getSchemaFactory().getSchema("HEL::adc"));
        Bank cfg = new Bank(r.getReader().getSchemaFactory().getSchema("RUN::config"));
        HelicityState prevHelicity = new HelicityState();
        while (r.hasEvent()) {
            Event e = (Event)r.getNextEvent();
            if (e.hasBank(s)) {
                e.read(adc);
                e.read(cfg);
                if (adc.getRows()>0) {
                    HelicityState thisHelicity = HelicityState.createFromFadcBank(adc);
                    thisHelicity.setHalfWavePlate(hwp);
                    if (!thisHelicity.isValid() || !thisHelicity.equals(prevHelicity)) {
                        Bank flip = thisHelicity.getFlipBank(r.getReader().getSchemaFactory(), e);
                        Event x = new Event();
                        x.write(cfg);
                        x.write(flip);
                        flips.add(x);
                        prevHelicity = thisHelicity;
                    }

                }
            }
        }
        return flips;
    }

    /**
     * Get all helicity flips as seen by the FADC board.
     * 
     * Note, this assumes events are ordered by time, so normally you'd want to
     * pass in an EvioSortedSource.
     * 
     * This is just the helicity flip detection extracted from the standard
     * decoder, CLASDecoder4, to facilitate using that same decoder on misordered
     * EVIO events, without changing their original order in the output HIPO file.
     * 
     * The original approach added helicity flip information to events on-the-fly
     * as the flips were detected.  Here, instead all events are first read for
     * helicity flip detection, which is currently very inefficient due to reading
     * far more raw detector data than necessary for flip detection, due to
     * reusing functionality from the decoder.  A first look suggests that fixing
     * that, without copy-pasting low-level code around, while still supporting
     * all the FADC readout modes, would be a significant project and time would
     * be better spent on a larger rewrite, which is already in independently
     * progress but not ready to support the entire CLAS12 data.
     * 
     * In the big picture, this essentially doubles the CPU time required for
     * decoding, which is only a ~1 % effect on the total.
     * 
     * @param s
     * @return list of helicity flips' corresponding HEL::flip and RUN::config banks
     */
    public static List<Event> getFlips(EvioSource s) {
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
