package org.jlab.detector.calib.utils;

import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.utils.groups.IndexedTable;

/**
 * Utility class for storing occupancy/multiplicity in an IndexedTable and
 * writing it to an occupancy bank with format (index... weight [*])
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
        table = new IndexedTable(indexNames.length, new String[]{"occ/D"});
    }

    /**
     * Reset the occupancy table.
     */
    public void reset() {
        table = new IndexedTable(indexNames.length, new String[]{"occ/D"});
    }

    /**
     * Fill occupancy, user-defined weight.
     * @param weight
     * @param index 
     */
    public void add(double weight, int... index) {
        final long hash = IndexedTable.DEFAULT_GENERATOR.hashCode(index);
        if (!table.hasEntry(index)) {
            table.addEntry(index);
            table.setDoubleValueByHash(0.0, indexNames.length, hash);
        }
        table.setDoubleValueByHash(weight + table.getDoubleValueByHash(indexNames.length, hash), indexNames.length, hash);
    }

    /**
     * Fill occupancy, unity weight.
     * @param index 
     */
    public void add(int... index) {
        add(1, index);
    }

    /**
     * Fill occupancy from hits bank, one per bank row.
     * Note, bank index data types are hard-coded here! 
     * @param b hit bank
     */
    public void add(DataBank b) {
        final int rows = b.rows();
        for (int i=0; i<rows; i++) {
            if (indexNames.length == 3)
                add(b.getByte(0, i), b.getByte(1,i), b.getShort(2,i));
            else
                add(b.getByte(0, i), b.getByte(1,i), b.getShort(2,i), b.getByte(3,i));
        }
    }

    /**
     * Get the number of rows for the occupancy bank.
     * @return rows 
     */
    public int getRows() {
        return table.getRowCount();
    }
   
    /**
     * Update an occupancy bank.
     * @param b occupancy bank
     */
    public void update(DataBank b) {
        int i = 0;
        Map<Long,Integer> m = table.getList().getMap();
        for (Map.Entry<Long,Integer> entry : m.entrySet()) {
            b.setInt(0, i, IndexedTable.DEFAULT_GENERATOR.getIndex(entry.getKey(), 0));
            b.setInt(1, i, IndexedTable.DEFAULT_GENERATOR.getIndex(entry.getKey(), 1));
            b.setInt(2, i, IndexedTable.DEFAULT_GENERATOR.getIndex(entry.getKey(), 2));
            if (indexNames.length == 4)
                b.setInt(3, i, IndexedTable.DEFAULT_GENERATOR.getIndex(entry.getKey(), 3));
            b.setInt(indexNames.length, i, entry.getValue());
            i++;
        }
    }
}