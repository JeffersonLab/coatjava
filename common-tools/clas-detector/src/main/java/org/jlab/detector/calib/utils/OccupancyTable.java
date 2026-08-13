package org.jlab.detector.calib.utils;

import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.jnp.hipo4.data.Bank;
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
                fill(b.getByte(0, i), b.getByte(1,i), b.getByte(2,i));
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
     * Reset occupancy table.
     */
    public final void reset() {
        table = new IndexedTable(indexNames.length, valueNames);
    }

    /**
     * Fill occupancy table with a weight.
     * @param weight
     * @param index 
     */
    public final void fill(float weight, int... index) {
        final long hash = IndexedTable.DEFAULT_GENERATOR.hashCode(index);
        if (!table.hasEntryByHash(hash)) {
            table.addEntry(index);
            table.setIntValueByHash(0, 0, hash);
        }
        table.setDoubleValueByHash(table.getDoubleValueByHash(0, hash) + weight, 0, hash);
    }

    /**
     * Fill occupancy table.
     * @param index 
     */
    public final void fill(int... index) {
        fill(1.0f, index);
    }

    /**
     * 
     * @param b 
     * @param weighted 
     */
    public void fill(DataBank b, boolean weighted) {
        final int rows = b.rows();
        int[] indices = new int[indexNames.length];
        for (int i=0; i<rows; i++) {
            for (int j=0; j<indexNames.length; j++) {
                if (j==2) indices[j] = b.getShort(j,i);
                else indices[j] = b.getByte(j,i);
            }
            if (weighted) fill(b.getFloat(indexNames.length,i), indices);
            else fill(indices);
        }
    }

    /**
     * 
     * @param e
     * @param bank
     * @param weighted 
     */
    public void fill(DataEvent e, String bank, boolean weighted) {
        DataBank b = e.getBank(bank);
        if (b != null) fill(b, weighted);
    }

    /**
     * 
     * @param b 
     * @param weighted 
     */
    public final void fill(Bank b, boolean weighted) {
        int rows = b.getRows();
        int[] indices = new int[indexNames.length];
        for (int i=0; i<rows; i++) {
            for (int j=0; j<indexNames.length; j++) {
                if (j==2) indices[j] = b.getShort(j,i);
                else indices[j] = b.getByte(j,i);
            }
            if (weighted) fill(b.getFloat(indexNames.length,i), indices);
            else fill(indices);
        }
    }
    
    /**
     * Get number of rows for the occupancy bank.
     * @return rows 
     */
    public final int getRows() {
        return table.getRowCount();
    }

    public final DataBank create(DataEvent event, String bank) {
        DataBank b = event.createBank(bank, getRows());
        int i = 0;
        Map<Long,IndexedEntry> m = table.getList().getMap();
        for (long hash : m.keySet()) {
            int[] indices = IndexedTable.DEFAULT_GENERATOR.getIndices(hash, indexNames.length);
            for (int j=0; j<indexNames.length; j++) {
                if (j == 2) b.setShort(j, i, (short)indices[j]);
                else b.setByte(j, i, (byte)indices[j]);
            }
            b.setFloat(indexNames.length, i, m.get(hash).getValue(0).intValue());
            i++;
        }
        return b;
    }

    /**
     * 
     * @param events
     * @param b
     */
    public final void create(int events, Bank b) {
        b.setRows(getRows());
        int row = 0;
        for (long hash : ((Map<Long,IndexedEntry>)table.getList().getMap()).keySet()) {
            int[] idx = IndexedTable.DEFAULT_GENERATOR.getIndices(hash, indexNames.length);
            for (int j=0; j<indexNames.length; j++){
                if (j == 2) b.putShort(j, row, (short)idx[j]);
                else b.putByte(j, row, (byte)idx[j]);
            }
            b.putFloat(indexNames.length, row, (float)table.getDoubleValueByHash(0, hash)/events);
            row++;
        }
    }

    /**
     * Get the occupancy table.
     * @param events number of events that have been added
     * @return 
     */
    public final IndexedTable create(int events) {
        IndexedTable t = new IndexedTable(indexNames.length, new String[]{"occ/F"});
        for (long hash : ((Map<Long,IndexedEntry>)table.getList().getMap()).keySet()) {
            t.addEntry(IndexedTable.DEFAULT_GENERATOR.getIndices(hash, indexNames.length));
            t.setDoubleValueByHash((table.getDoubleValueByHash(0, hash))/events, 0, hash);
        }
        return t;
    }

}