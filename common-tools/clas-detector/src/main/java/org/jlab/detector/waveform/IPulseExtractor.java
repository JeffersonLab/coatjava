package org.jlab.detector.waveform;

import java.util.List;
import org.jlab.jnp.hipo4.data.Bank;

/**
 *
 * @author baltzell
 */
public interface IPulseExtractor {

    public List<Pulse> extract(short... samples);

    public void extract(Bank src, Bank dest);

}
