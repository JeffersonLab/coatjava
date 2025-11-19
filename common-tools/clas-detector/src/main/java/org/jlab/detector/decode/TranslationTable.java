package org.jlab.detector.decode;

import org.jlab.detector.base.DetectorType;
import org.jlab.utils.groups.IndexedTable;

/**
 *
 * @author baltzell
 */
public class TranslationTable extends IndexedTable {

    public TranslationTable() {
        super(3);
    };
    
    public void add(IndexedTable it, DetectorType dt) {
        for (Object key : it.getList().getMap().keySet()) {
            int crate = IndexedTable.DEFAULT_GENERATOR.getIndex((long) key, 0);
            int slot = IndexedTable.DEFAULT_GENERATOR.getIndex((long) key, 1);
            int channel = IndexedTable.DEFAULT_GENERATOR.getIndex((long) key, 2);
            long hash = IndexedTable.DEFAULT_GENERATOR.hashCode(crate, slot, channel);
            // add row to the new table:
            addEntry(crate, slot, channel);
            // add all entries to the new row:
            int column = 0;
            for (int c : it.getEntryMap().values()) {
                column = c;
                int value = it.getIntValueByHash(column, hash);
                setIntValueByHash(value, column, hash);
            }
            // add new entry, the detector type:
            setIntValueByHash(dt.getDetectorId(), column+1, hash);
        }
    }

}
