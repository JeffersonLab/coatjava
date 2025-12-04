package org.jlab.detector.decode;

import org.jlab.detector.base.DetectorType;
import org.jlab.utils.groups.IndexedTable;

/**
 *
 * @author baltzell
 */
public class TranslationTable extends IndexedTable {

    public TranslationTable() {
        super(3,new String[]{"sector/I","layer/I","component/I","order/I","type/I"});
        String[] x = new String[entryMap.keySet().size()];
        for (String name : entryMap.keySet())
            x[entryMap.get(name)] = name + "/" + entryTypes.get(name);
    };

    public void add(DetectorType dt, IndexedTable it) {
        for (Object key : it.getList().getMap().keySet()) {
            int crate = IndexedTable.DEFAULT_GENERATOR.getIndex((long) key, 0);
            int slot = IndexedTable.DEFAULT_GENERATOR.getIndex((long) key, 1);
            int channel = IndexedTable.DEFAULT_GENERATOR.getIndex((long) key, 2);
            long hash = IndexedTable.DEFAULT_GENERATOR.hashCode(crate, slot, channel);
            // add row to the new table:
            addEntry(crate, slot, channel);
            // add all entries to the new row:
            int column;
            for (column=0; column<it.getEntryMap().values().size(); column++) {
                int value = it.getIntValueByHash(column, hash);
                setIntValueByHash(value, column, hash);
            }
            // add the new detector type:
            setIntValueByHash(dt.getDetectorId(), column, hash);
        }
    }
}
