package org.jlab.detector.decode;

import org.jlab.detector.base.DetectorType;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.utils.groups.IndexedTable;

/**
 *
 * @author baltzell
 */
public class TranslationTable extends IndexedTable {

    public TranslationTable() {
        super(3,new String[]{"sector/I","layer/I","component/I","order/I","type/I"});
    };

    public void add(DetectorType dt, IndexedTable it) {

        for (Object key : it.getList().getMap().keySet()) {

            // get the indices:
            long hash = (long)key;
            int crate = IndexedTable.DEFAULT_GENERATOR.getIndex(hash, 0);
            int slot = IndexedTable.DEFAULT_GENERATOR.getIndex(hash, 1);
            int channel = IndexedTable.DEFAULT_GENERATOR.getIndex(hash, 2);

            if (hasEntryByHash(hash)) {
                System.err.print("TranslationTable:  found CCDB overlap for ");
                System.err.println(String.format("type %d/%s versus %s and c/s/c=%d/%d/%d",
                    getIntValueByHash("type",hash),
                    DetectorType.getType(getIntValueByHash("type",hash)),
                    dt,crate,slot,channel));
            }
            else {
                // add row to the new table:
                addEntry(crate, slot, channel);

                // add each column's entry to the new row:
                for (int column=0; column<it.getEntryMap().values().size(); column++) {
                    int value = it.getIntValueByHash(column, hash);
                    setIntValueByHash(value, column, hash);
                }

                // add the new detector type, as the last column:
                setIntValueByHash(dt.getDetectorId(), it.getEntryMap().values().size(), hash);
            }
        }
    }

    
    public static final DetectorType[] TYPES = new DetectorType[]{
        DetectorType.FTCAL,DetectorType.FTHODO,DetectorType.FTTRK,
        DetectorType.LTCC,DetectorType.ECAL,DetectorType.FTOF,
        DetectorType.HTCC,DetectorType.DC,DetectorType.CTOF,
        DetectorType.CND,DetectorType.BST,DetectorType.RF,
        DetectorType.BMT,DetectorType.FMT,DetectorType.RICH,
        DetectorType.HEL,DetectorType.BAND,DetectorType.RTPC,
        DetectorType.RASTER,DetectorType.ATOF,DetectorType.AHDC};
   
    public static final String[] STYPES = new String[]{
        "/daq/tt/ftcal","/daq/tt/fthodo","/daq/tt/fttrk",
        "/daq/tt/ltcc","/daq/tt/ec","/daq/tt/ftof",
        "/daq/tt/htcc","/daq/tt/dc","/daq/tt/ctof",
        "/daq/tt/cnd","/daq/tt/svt","/daq/tt/rf",
        "/daq/tt/bmt","/daq/tt/fmt","/daq/tt/rich2",
        "/daq/tt/hel","/daq/tt/band","/daq/tt/rtpc",
        "/daq/tt/raster","/daq/tt/atof","/daq/tt/ahdc"};

    public static void main(String[] args) {
        ConstantsManager conman = new ConstantsManager();
        conman.init(STYPES);
        TranslationTable tt = new TranslationTable();
        for (int i=0; i<STYPES.length; i++)
            tt.add(TYPES[i],conman.getConstants(18779,STYPES[i]));
        tt.show();
    }
}
