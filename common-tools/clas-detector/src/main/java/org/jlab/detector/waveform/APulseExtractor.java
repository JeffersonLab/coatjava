package org.jlab.detector.waveform;

import java.util.ArrayList;
import java.util.List;
import org.jlab.io.base.DataBank;
import org.jlab.jnp.hipo4.data.Bank;

/**
 *
 * @author baltzell
 */
public abstract class APulseExtractor implements IPulseExtractor {

    public static void copyIndices(Bank src, Bank dest, int isrc, int idest) {
        dest.putShort("sector", idest, src.getShort("sector",isrc));
        dest.putShort("layer", idest, src.getShort("layer",isrc));
        dest.putShort("component", idest, src.getShort("component",isrc));
        dest.putShort("order", idest, src.getShort("order",isrc));
        dest.putShort("index", idest, (short)isrc);
    }

    public static void copyIndices(DataBank src, DataBank dest, int isrc, int idest) {
        dest.setShort("sector", idest, src.getShort("sector",isrc));
        dest.setShort("layer", idest, src.getShort("layer",isrc));
        dest.setShort("component", idest, src.getShort("component",isrc));
        dest.setShort("order", idest, src.getShort("order",isrc));
        dest.setShort("index", idest, (short)isrc);
    }

    public final List<Pulse> getPulses(DataBank bank) {
        List<Pulse> pulses = new ArrayList<>();
        for (int i=0; i<bank.rows(); ++i) {
            short[] samples = new short[12];
            for (int j=0; j<12; ++j)
                samples[j] = bank.getShort("wf", j);
            pulses.addAll(extract(i, samples));
        }
        return pulses;
    }

    public final List<Pulse> getPulses(Bank bank) {
        List<Pulse> pulses = new ArrayList<>();
        for (int i=0; i<bank.getRows(); ++i) {
            short[] samples = new short[12];
            for (int j=0; j<12; ++j)
                samples[j] = bank.getShort("wf", j);
            pulses.addAll(extract(i, samples));
        }
        return pulses;
    }

    @Override
    public final void extract(Bank src, Bank dest) {
        dest.reset();
        if (src.getRows() > 0) {
            int iadc=0;
            for (Pulse p : getPulses(src)) {
                copyIndices(src, dest, p.index(), iadc);
                dest.putFloat("ADC", iadc, p.integral());
                dest.putFloat("time", iadc, p.time());
                iadc++;
            }
        }
    }

    @Override
    public final void extract(DataBank src, DataBank dest) {
        dest.reset();
        if (src.rows() > 0) {
            int iadc=0;
            for (Pulse p : getPulses(src)) {
                copyIndices(src, dest, p.index(), iadc);
                dest.setFloat("ADC", iadc, p.integral());
                dest.setFloat("time", iadc, p.time());
                iadc++;
            }
        }
    }
}
