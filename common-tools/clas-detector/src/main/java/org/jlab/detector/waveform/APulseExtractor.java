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
        if (src.getRows() > 0) {
            List<Pulse> pulses = getPulses(src);
            dest.reset();
            dest.setRows(pulses.size());
            for (int i=0; i<pulses.size(); ++i) {
                copyIndices(src, dest, pulses.get(i).index(), i);
                dest.putFloat("ADC", i, pulses.get(i).integral());
                dest.putFloat("time", i, pulses.get(i).time());
            }
        }
    }

    @Override
    public final void extract(DataBank src, DataBank dest) {
        if (src.rows() > 0) {
            List<Pulse> pulses = getPulses(src);
            dest.reset();
            dest.allocate(pulses.size());
            for (int i=0; i<pulses.size(); ++i) {
                copyIndices(src, dest, pulses.get(i).index(), i);
                dest.setFloat("ADC", i, pulses.get(i).integral());
                dest.setFloat("time", i, pulses.get(i).time());
            }
        }
    }
}
