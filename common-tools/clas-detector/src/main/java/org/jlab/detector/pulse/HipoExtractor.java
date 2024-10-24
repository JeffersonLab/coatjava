
package org.jlab.detector.pulse;

import java.util.ArrayList;
import java.util.List;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.utils.groups.IndexedTable;

/**
 * For now, a place to store standard waveform/pulse HIPO manipulations.
 * No bounds checking for number of samples.
 */
public abstract class HipoExtractor implements IExtractor {

    /**
     * @param n number of samples in readout
     * @param it CCDB table containing extraction initialization parameters
     * @param event the event to modify
     * @param schema bank schema factory
     * @param wfBankName name of the input waveform bank
     * @param adcBankName name of the output ADC bank
     */
    public void update(int n, IndexedTable it, Event event, SchemaFactory schema, String wfBankName, String adcBankName) {
        Bank wf = new Bank(schema.getSchema(wfBankName));
        event.read(wf);
        if (wf.getRows() > 0) {
            Bank adc = new Bank(schema.getSchema(adcBankName));
            update(n, it, wf, adc);
            event.remove(schema.getSchema(adcBankName));
            if (adc.getRows() > 0) event.write(adc);
        }
    }

    /**
     * @param n number of samples in readout
     * @param it CCDB table containing extraction initialization parameters
     * @param event the event to modify
     * @param wfBankName name of the input waveform bank
     * @param adcBankName name of the output ADC bank
     */
    public void update(int n, IndexedTable it, DataEvent event, String wfBankName, String adcBankName) {
        DataBank wf = event.getBank(wfBankName);
        if (wf.rows() > 0) {
            event.removeBank(adcBankName);
            List<Pulse> pulses = getPulses(n, it, wf);
            if (pulses != null && !pulses.isEmpty()) {
                DataBank adc = event.createBank(adcBankName, pulses.size());
                for (int i=0; i<pulses.size(); ++i) {
                    copyIndices(wf, adc, i, i);
                    adc.setInt("ADC", i, (int)pulses.get(i).integral);
                    adc.setFloat("time", i, pulses.get(i).time);
                }
                event.appendBank(adc);
            }
        }
    }

    /**
     * 
     * @param n
     * @param it
     * @param src
     * @param dest 
     */
    protected void update(int n, IndexedTable it, Bank src, Bank dest) {
        if (src.getRows() > 0) {
            List<Pulse> pulses = getPulses(n, it, src);
            dest.reset();
            dest.setRows(pulses!=null ? pulses.size() : 0);
            if (pulses!=null && !pulses.isEmpty()) {
                for (int i=0; i<pulses.size(); ++i) {
                    copyIndices(src, dest, pulses.get(i).id, i);
                    dest.putInt("ADC", i, (int)pulses.get(i).integral);
                    dest.putFloat("time", i, pulses.get(i).time);
                }
            }
        }
    }

    private static void copyIndices(Bank src, Bank dest, int isrc, int idest) {
        dest.putByte("sector", idest, src.getByte("sector",isrc));
        dest.putByte("layer", idest, src.getByte("layer",isrc));
        dest.putShort("component", idest, src.getShort("component",isrc));
        dest.putByte("order", idest, src.getByte("order",isrc));
        dest.putShort("id", idest, (short)isrc);
    }

    private static void copyIndices(DataBank src, DataBank dest, int isrc, int idest) {
        dest.setByte("sector", idest, src.getByte("sector",isrc));
        dest.setByte("layer", idest, src.getByte("layer",isrc));
        dest.setShort("component", idest, src.getShort("component",isrc));
        dest.setByte("order", idest, src.getByte("order",isrc));
        dest.setShort("id", idest, (short)isrc);
    }

    private static int[] getIndices(Bank bank, int row) {
        return new int[] {
            bank.getShort("sector", row),
            bank.getShort("layer", row),
            bank.getShort("component", row),
            bank.getShort("order", row)};
    }

    private static int[] getIndices(DataBank bank, int row) {
        return new int[] {
            bank.getShort("sector", row),
            bank.getShort("layer", row),
            bank.getShort("component", row),
            bank.getShort("order", row)};
    }

    private List<Pulse> getPulses(int n, IndexedTable it, DataBank wfBank) {
        List<Pulse> pulses = null;
        short[] samples = new short[n];
        for (int i=0; i<wfBank.rows(); ++i) {
            for (int j=0; j<n; ++j)
                samples[j] = wfBank.getShort(String.format("s%d",j+1), i);
            List<Pulse> p = it==null ? extract(null, i, samples) :
                extract(it.getNamedEntry(getIndices(wfBank,i)), i, samples);
            if (p!=null && !p.isEmpty()) {
                if (pulses == null) pulses = new ArrayList<>();
                pulses.addAll(p);
            }
        }
        return pulses;
    }

    private List<Pulse> getPulses(int n, IndexedTable it, Bank wfBank) {
        List<Pulse> pulses = null;
        short[] samples = new short[n];
        for (int i=0; i<wfBank.getRows(); ++i) {
            for (int j=0; j<n; ++j)
                samples[j] = wfBank.getShort(String.format("s%d",j+1), i);
                // Can speed this up (but looks like not for DataBank?):
                //samples[j] = wfBank.getShort(String.format(5+j,j+1), i);
            List p = it==null ? extract(null, i, samples) :
                extract(it.getNamedEntry(getIndices(wfBank,i)), i, samples);
            if (p!=null && !p.isEmpty()) {
                if (pulses == null) pulses = new ArrayList<>();
                pulses.addAll(p);
            }
        }
        return pulses;
    }

}