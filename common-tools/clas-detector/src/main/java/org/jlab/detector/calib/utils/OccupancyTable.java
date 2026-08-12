package org.jlab.detector.calib.utils;

import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.utils.groups.IndexedTable.IndexedEntry;

public abstract class OccupancyTable {

    public static void main(String[] args) {
        DC d = new DC();
        d.add(1,1,1);
        d.add(1,2,1);
        d.add(1,3,1);
        d.add(1,3,1);
        IndexedTable o = d.getOccupancy(10);
        o.show();
        o.getList().show();
    }

    public static class DC extends OccupancyTable {
        public DC() { super("DC::occ","sector","layer","component"); }
        @Override
        public void add(DataEvent e) {
            DataBank b = e.getBank("DC::tot");
            if (b == null) b = e.getBank("DC::tdc");
            if (b == null) return;
            final int rows = b.rows();
            for (int i=0; i<rows; i++)
                add(b.getByte(0, i), b.getByte(1,i), b.getByte(2,i));
        }
    }

    protected String[] valueNames = {"occ/I"};
    protected String[] indexNames;
    protected IndexedTable table;

    public OccupancyTable(String bank, String... index) {
        indexNames = index;
        table = new IndexedTable(indexNames.length, valueNames);
    }

    public OccupancyTable(String bank) {
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
            t.setDoubleValueByHash(((double)table.getIntValueByHash(0, hash))/events, 0, hash);
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
        table.setIntValueByHash(table.getIntValueByHash(0, hash) + 1, 0, hash);
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
     * @param b occupancy bank
     */
    public final void update(DataBank b) {
        int i = 0;
        Map<Long,IndexedEntry> m = table.getList().getMap();
        for (long hash : m.keySet()) {
            int[] indices = IndexedTable.DEFAULT_GENERATOR.getIndices(hash, indexNames.length);
            b.setByte(0, i, (byte)indices[0]);
            b.setByte(1, i, (byte)indices[1]);
            b.setShort(2, i, (short)indices[2]);
            if (indexNames.length == 4) b.setByte(3, i, (byte)indices[3]);
            b.setInt(indexNames.length, i, m.get(hash).getValue(0).intValue());
            i++;
        }
    }

    public final void update(DataEvent e, String bank) {
        DataBank b = e.createBank(bank, getRows());
        if (e.hasBank(bank)) e.removeBank(bank);
        update(b);
        e.appendBank(b);
    }

    /**
     * Fill occupancy from hits bank.
     * @param e
     */
    protected abstract void add(DataEvent e);
}