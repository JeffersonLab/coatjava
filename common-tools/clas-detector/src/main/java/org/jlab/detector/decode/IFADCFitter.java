package org.jlab.detector.decode;

import org.jlab.detector.decode.FADCData.Peak;

public interface IFADCFitter {

    public Peak[] fit(int nsa, int nsb, int tet, int pedr, short[] pulse);

}
