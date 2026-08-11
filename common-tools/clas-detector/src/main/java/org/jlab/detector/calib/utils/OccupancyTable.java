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

    private String[] indexNames;
    private IndexedTable table;
    
    public OccupancyTable(String... index) {
        if (index.length != 3 && index.length != 4)
            throw new IllegalArgumentException("Invalid index length: "+index.length);
        indexNames = index;
        table = new IndexedTable(indexNames.length, new String[]{"nhits/D"});
    }

    public void reset() {
        table = new IndexedTable(indexNames.length, new String[]{"nhits/D"});
    }

    public void add(double weight, int... index) {
        if (!table.hasEntry(index)) {
            table.addEntry(index);
            table.setDoubleValue(0.0, "nhits", index);
        }
        table.setDoubleValue(table.getDoubleValue("nhits",index) + weight, "nhits", index);
    }

    public void add(int... index) {
        add(1, index);
    }

    public void add(DataBank b) {
        final int rows = b.rows();
        for (int i=0; i<rows; i++) {
            if (indexNames.length == 3) add(b.getByte(0, i), b.getByte(1,i), b.getByte(2,i));
            else add(b.getByte(0, i), b.getByte(1,i), b.getByte(2,i), b.getByte(3,i));
        }
    }

    public void update(DataBank b) {
        int i = 0;
        Map<Long,Integer> m = table.getList().getMap();
        for (Map.Entry<Long,Integer> entry : m.entrySet()) {
            b.setInt("sector", i, IndexedTable.DEFAULT_GENERATOR.getIndex(entry.getKey(), 0));
            b.setInt("layer", i, IndexedTable.DEFAULT_GENERATOR.getIndex(entry.getKey(), 1));
            b.setInt("component", i, IndexedTable.DEFAULT_GENERATOR.getIndex(entry.getKey(), 2));
            if (indexNames.length == 4) b.setInt("order", i, IndexedTable.DEFAULT_GENERATOR.getIndex(entry.getKey(), 3));
            b.setInt("nhits", i, entry.getValue());
            i++;
        }
    }

    public void update(DataEvent e, String bankName) {
        DataBank b = e.getBank(bankName);
        if (b != null) e.removeBank(bankName);
        b = e.createBank(bankName, table.getRowCount());
        update(b);
        e.appendBank(b);
    }

}
