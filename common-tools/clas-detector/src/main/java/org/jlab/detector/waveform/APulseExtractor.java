package org.jlab.detector.waveform;

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
    }

    public static void copyIndices(DataBank src, DataBank dest, int isrc, int idest) {
        dest.setShort("sector", idest, src.getShort("sector",isrc));
        dest.setShort("layer", idest, src.getShort("layer",isrc));
        dest.setShort("component", idest, src.getShort("component",isrc));
        dest.setShort("order", idest, src.getShort("order",isrc));
    }

    public void extract(Bank src, Bank dest) {
        dest.reset();
        if (src.getRows() > 0) {
            int iadc = 0;
            for (int iwf=0; iwf<src.getRows(); ++iwf) {
                short[] samples = new short[12];
                for (int j=0; j<12; ++j)
                    samples[j] = dest.getShort("wf", j);
                for (Pulse p : extract(samples)) {
                    copyIndices(src, dest, iwf, iadc);
                    dest.putFloat("ADC", iadc, p.integral());
                    dest.putFloat("time", iadc, p.time());
                    iadc++;
                }
            }
        }
    }

    public void extract(DataBank src, DataBank dest) {
        dest.reset();
        if (src.rows() > 0) {
            int iadc = 0;
            for (int iwf=0; iwf<src.rows(); ++iwf) {
                short[] samples = new short[12];
                for (int j=0; j<12; ++j)
                    samples[j] = dest.getShort("wf", j);
                for (Pulse p : extract(samples)) {
                    copyIndices(src, dest, iwf, iadc);
                    dest.setFloat("ADC", iadc, p.integral());
                    dest.setFloat("time", iadc, p.time());
                    iadc++;
                }
            }
        }
    }
}
