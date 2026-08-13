package org.jlab.clas.reco;

import org.jlab.detector.calib.utils.OccupancyTable;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author baltzell
 */
public class OccupancyEngine extends ReconstructionEngine {

    OccupancyTable[] tables = {new OccupancyTable.DC()};
    String[] banks = {"DC::occ"};
    
    public OccupancyEngine() {
        super("OccE", "baltzell", "0.1");
    }

    @Override
    public boolean processDataEventUser(DataEvent event) {
        for (int i=0; i<tables.length; i++)
            tables[i].fill(event, banks[i], false);
        for (int i=0; i<tables.length; i++) {
            event.appendBank(tables[i].create(event, banks[i]));
            tables[i].reset();
        }
        return true;
    }

    @Override
    public boolean init() { return true; }

    @Override
    public void detectorChanged(int runNumber) {}

}