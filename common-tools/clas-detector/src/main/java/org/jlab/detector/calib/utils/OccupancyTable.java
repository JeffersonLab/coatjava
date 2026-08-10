package org.jlab.detector.calib.utils;

import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.groups.IndexedTable;

/**
 *
 * @author baltzell
 */
public class OccupancyTable {

    String bankName;
    private IndexedTable table;
    
    public OccupancyTable(String bank, int indexCount) {
        bankName = bank;
        table = new IndexedTable(indexCount, new String[]{"nhits/I"});
    }

    public void add(int nhits, int... index) {
        if (!table.hasEntry(index)) {
            table.addEntry(index);
        }
        table.setIntValue(nhits, "nhits", index);
    }

    public void update(DataEvent e) {
        DataBank b = e.getBank(bankName);
        if (b != null) e.removeBank(bankName);
        b = e.createBank(bankName, table.getRowCount());
        Map<Long,Integer> m = table.getList().getMap();
        int i = 0;
        for (Map.Entry<Long,Integer> entry : m.entrySet()) {
            long hash = entry.getKey();
            int sector = IndexedTable.DEFAULT_GENERATOR.getIndex(hash, 0);
            int layer = IndexedTable.DEFAULT_GENERATOR.getIndex(hash, 1);
            int component = IndexedTable.DEFAULT_GENERATOR.getIndex(hash, 2);
            b.setInt("sector", i, sector);
            b.setInt("sector", i, layer);
            b.setInt("sector", i, component);
            b.setInt("nhits", i++, entry.getValue());
        }
        e.appendBank(b);
    }
}
