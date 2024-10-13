package org.jlab.detector.pulse;

import java.util.List;

public interface IExtractor <T> {

    public List<T> extract(ExtractorPars pars, int id, short... samples);

}
