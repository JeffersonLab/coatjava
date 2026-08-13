package org.jlab.detector.calib.utils;

import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.utils.groups.IndexedTable.IndexedEntry;

public abstract class OccupancyTable {

    public static class DC extends OccupancyTable {
        public DC() { super(); }
        public void add(DataEvent e) {
            DataBank b = e.getBank("DC::tot");
            if (b == null) b = e.getBank("DC::tdc");
            if (b == null) return;
            final int rows = b.rows();
            for (int i=0; i<rows; i++)
                add(b.getByte(0, i), b.getByte(1,i), b.getByte(2,i));
        }
    }

    protected String[] valueNames = {"occ/F"};
    protected String[] indexNames;
    protected IndexedTable table;

    public OccupancyTable(String... index) {
        indexNames = index;
        table = new IndexedTable(indexNames.length, valueNames);
    }

    public OccupancyTable() {
        indexNames = new String[]{"sector","layer","component"};
        table = new IndexedTable(indexNames.length, valueNames);
    }

    /**
     * Get the occupancy table.
     * @param events number of events that have been added
     * @return 
     */
    public final IndexedTable getOccupancy(int events) {
        IndexedTable t = new IndexedTable(indexNames.length, new String[]{"occ/F"});
        for (long hash : ((Map<Long,IndexedEntry>)table.getList().getMap()).keySet()) {
            t.addEntry(IndexedTable.DEFAULT_GENERATOR.getIndices(hash, indexNames.length));
            t.setDoubleValueByHash((table.getDoubleValueByHash(0, hash))/events, 0, hash);
        }
        return t;
    }

    /**
     * Reset occupancy table.
     */
    public final void reset() {
        table = new IndexedTable(indexNames.length, valueNames);
    }

    /**
     * Fill occupancy table.
     * @param index 
     */
    public final void add(int... index) {
        final long hash = IndexedTable.DEFAULT_GENERATOR.hashCode(index);
        if (!table.hasEntryByHash(hash)) {
            table.addEntry(index);
            table.setIntValueByHash(0, 0, hash);
        }
        table.setDoubleValueByHash(table.getDoubleValueByHash(0, hash) + 1, 0, hash);
    }

    /**
     * Get number of rows for the occupancy bank.
     * @return rows 
     */
    public final int getRows() {
        return table.getRowCount();
    }

    /**
     * Update an occupancy bank, assuming 3/4-index s/l/c[/o].
     * @param bank occupancy bank
     */
    public final void update(DataBank bank) {
        int i = 0;
        Map<Long,IndexedEntry> m = table.getList().getMap();
        for (long hash : m.keySet()) {
            int[] indices = IndexedTable.DEFAULT_GENERATOR.getIndices(hash, indexNames.length);
            bank.setByte(0, i, (byte)indices[0]);
            bank.setByte(1, i, (byte)indices[1]);
            bank.setShort(2, i, (short)indices[2]);
            if (indexNames.length == 4) bank.setByte(3, i, (byte)indices[3]);
            bank.setFloat(indexNames.length, i, m.get(hash).getValue(0).intValue());
            i++;
        }
    }

    /**
     * Create occupancy bank, assuming 3/4-indexing, and add it to the event. 
     * @param event
     * @param bank 
     */
    public final void update(DataEvent event, String bank) {
        DataBank b = event.createBank(bank, getRows());
        if (event.hasBank(bank)) event.removeBank(bank);
        update(b);
        event.appendBank(b);
    }
}