package org.jlab.detector.pulse;

import java.util.List;
import org.jlab.io.base.DataBank;
import org.jlab.jnp.hipo4.data.Bank;

public interface IPulseExtractor <T> {

    public List<T> extract(int id, short... samples);

    public void extract(Bank src, Bank dest);
    
    public void extract(DataBank src, DataBank dest);

}
