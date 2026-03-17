package org.jlab.detector.decode;

import java.util.stream.Collectors;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.calib.utils.ConstantsManager;

/**
 * A global, CCDB/IndexedTable, DAQ translation table.  Technically, just an IndexedTable
 * assuming the format (c/s/c/s/l/c/o) and appended by a DetectorType column.  
 *
 * @author baltzell
 */
public class TranslationTable extends IndexedTable {

    public TranslationTable() {
        super(3,new String[]{"sector/I","layer/I","component/I","order/I","type/I"});
    };

    /**
     * Add a detector's entire translation table.
     * 
     */
    public void add(DetectorType dt, IndexedTable it) {

        for (Object key : it.getList().getMap().keySet()) {

            // get the indices:
            long hash = (long)key;
            int crate = IndexedTable.DEFAULT_GENERATOR.getIndex(hash, 0);
            int slot = IndexedTable.DEFAULT_GENERATOR.getIndex(hash, 1);
            int channel = IndexedTable.DEFAULT_GENERATOR.getIndex(hash, 2);

            // first one wins, print error message for loser:
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
                for (int column=0; column<it.getEntryMap().values().size(); column++)
                    setIntValueByHash(it.getIntValueByHash(column, hash), column, hash);

                // add the new detector type, as the last column:
                setIntValueByHash(dt.getDetectorId(), it.getEntryMap().values().size(), hash);
            }
        }
    }

    public void dump() {
        for (Object key : getList().getMap().keySet()) {
            int[] idx = IndexedTable.DEFAULT_GENERATOR.getIndices((long)key, 0,1,2);
            System.out.print(String.format("%d/%d/%d ", idx[0],idx[1],idx[2]));
            System.out.print(getIntegersByHash((long)key).stream().
                map(String::valueOf).collect(Collectors.joining("/")));
            System.out.print("\n");
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
        for (int i=0; i<TYPES.length; i++)
            tt.add(TYPES[i],conman.getConstants(18779,STYPES[i]));
        tt.show();
        tt.dump();
    }
}
