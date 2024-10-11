package org.jlab.detector.waveform;

import java.util.List;
import org.jlab.io.base.DataBank;
import org.jlab.jnp.hipo4.data.Bank;

/**
 *
 * @author baltzell
 */
public interface IPulseExtractor {

    public List<Pulse> extract(int id, short... samples);

    public void extract(Bank src, Bank dest);
    
    public void extract(DataBank src, DataBank dest);

}
