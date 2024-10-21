
package org.jlab.detector.pulse;

import java.util.ArrayList;
import java.util.List;
import org.jlab.io.base.DataBank;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.utils.groups.NamedEntry;

public abstract class HipoExtractor implements IExtractor {

    public static final void copyIndices(Bank src, Bank dest, int isrc, int idest) {
        dest.putShort("sector", idest, src.getShort("sector",isrc));
        dest.putShort("layer", idest, src.getShort("layer",isrc));
        dest.putShort("component", idest, src.getShort("component",isrc));
        dest.putShort("order", idest, src.getShort("order",isrc));
        dest.putShort("index", idest, (short)isrc);
    }

    public static final void copyIndices(DataBank src, DataBank dest, int isrc, int idest) {
        dest.setShort("sector", idest, src.getShort("sector",isrc));
        dest.setShort("layer", idest, src.getShort("layer",isrc));
        dest.setShort("component", idest, src.getShort("component",isrc));
        dest.setShort("order", idest, src.getShort("order",isrc));
        dest.setShort("index", idest, (short)isrc);
    }

    public final List<Pulse> getPulses(NamedEntry pars, DataBank bank) {
        List<Pulse> pulses = new ArrayList<>();
        for (int i=0; i<bank.rows(); ++i) {
            short[] samples = new short[12];
            for (int j=0; j<12; ++j)
                samples[j] = bank.getShort(String.format("s%d",j), j);
            pulses.addAll(extract(pars, i, samples));
        }
        return pulses;
    }

    public final List<Pulse> getPulses(NamedEntry pars, Bank bank) {
        List<Pulse> pulses = new ArrayList<>();
        for (int i=0; i<bank.getRows(); ++i) {
            short[] samples = new short[12];
            for (int j=0; j<12; ++j)
                samples[j] = bank.getShort(String.format("s%d",j), j);
            pulses.addAll(extract(pars, i, samples));
        }
        return pulses;
    }

    public final void update(NamedEntry pars, Bank src, Bank dest) {
        if (src.getRows() > 0) {
            List<Pulse> pulses = getPulses(pars, src);
            dest.reset();
            dest.setRows(pulses.size());
            for (int i=0; i<pulses.size(); ++i) {
                copyIndices(src, dest, pulses.get(i).id, i);
                dest.putFloat("ADC", i, pulses.get(i).integral);
                dest.putFloat("time", i, pulses.get(i).time);
            }
        }
    }

    public final void update(NamedEntry pars, DataBank src, DataBank dest) {
        if (src.rows() > 0) {
            List<Pulse> pulses = getPulses(pars, src);
            dest.reset();
            dest.allocate(pulses.size());
            for (int i=0; i<pulses.size(); ++i) {
                copyIndices(src, dest, pulses.get(i).id, i);
                dest.setFloat("ADC", i, pulses.get(i).integral);
                dest.setFloat("time", i, pulses.get(i).time);
            }
        }
    }
/*
    public final void update(NamedEntry pars, Event event, SchemaFactory schema) {
        Bank wf = new Bank(schema.getSchema(pars.wfBankName));
        event.read(wf);
        if (wf.getRows() > 0) {
            Bank adc = new Bank(schema.getSchema(pars.adcBankName));
            update(pars, wf, adc);
            event.remove(schema.getSchema(pars.adcBankName));
            if (adc.getRows() > 0) event.write(adc);
        }
    } 

    public final void update(NamedEntry pars, DataEvent event) {
        DataBank wf = event.getBank(pars.wfBankName);
        if (wf.rows() > 0) {
            DataBank adc = event.getBank(pars.adcBankName);
            update(pars, wf, adc);
            event.removeBank(pars.adcBankName);
            if (adc.rows() > 0) event.appendBank(adc);
        }
    }
*/
}