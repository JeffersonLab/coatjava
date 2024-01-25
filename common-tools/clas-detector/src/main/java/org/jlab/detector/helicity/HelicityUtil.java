package org.jlab.detector.helicity;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.detector.decode.CLASDecoder4;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.evio.EvioSource;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
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
    public static List<Bank> getFlips(HipoDataSource r, byte hwp) {
        Logger.getLogger(HelicityUtil.class.getName()).info("Initializing Helicity Flips");
        List<Bank> flips = new ArrayList<>();
        HelicityState prevHelicity = new HelicityState();
        while (r.hasEvent()) {
            DataEvent e = r.getNextEvent();
            if (e.hasBank("HEL::adc")) {
                DataBank b = e.getBank("HEL::adc");
                if (b.rows() > 0) {
                    HelicityState thisHelicity = HelicityState.createFromFadcBank(b);
                    thisHelicity.setHalfWavePlate(hwp);
                    System.out.println("prev:  "+prevHelicity);
                    System.out.println("this:  "+thisHelicity);
                    if (!thisHelicity.isValid() || !thisHelicity.equals(prevHelicity)) {
                        flips.add(thisHelicity.getFlipBank(r.getReader().getSchemaFactory(), e));
                        prevHelicity = thisHelicity;
                    }

                }
            }
        }
        Logger.getLogger(HelicityUtil.class.getName()).log(Level.INFO, "Found {0} helicity flips", flips.size());
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
    public static List<Bank> getFlips(EvioSource s) {
        List<Bank> flips = new ArrayList<>();
        ProgressPrintout progress = new ProgressPrintout();
        Logger.getLogger(HelicityUtil.class.getName()).info("Initializing Helicity Flips");
        CLASDecoder4 d = new CLASDecoder4();
        Bank adc = new Bank(d.getSchemaCopy("HEL::adc"));
        HelicityState prevHelicity = new HelicityState();
        while (s.hasEvent()) {
            DataEvent evio = s.getNextEvent();
            Event hipo = d.getDataEvent(evio);
            hipo.read(adc);
            if (adc.getRows()>0) {
                HelicityState thisHelicity = HelicityState.createFromFadcBank(adc);
                if (!thisHelicity.isValid() || !thisHelicity.equals(prevHelicity)) {
                    flips.add(d.createHelicityFlipBank(hipo,thisHelicity));
                    prevHelicity = thisHelicity;
                }
                progress.updateStatus();
            }
                
        }
        s.close();
        return flips;
    }

}
