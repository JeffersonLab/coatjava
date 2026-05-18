package org.jlab.detector.pulse;

import java.util.List;
import org.jlab.utils.groups.NamedEntry;

public interface IExtractor <T> {
   
    public List<T> extract(NamedEntry pars, int id, long par1, long par2, short... samples);

}
