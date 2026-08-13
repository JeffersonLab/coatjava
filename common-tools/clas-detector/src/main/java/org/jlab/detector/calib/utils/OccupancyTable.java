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
        /*public void fill(DataEvent e) {
            DataBank b = e.getBank("DC::tot");
            if (b == null) b = e.getBank("DC::tdc");
            if (b == null) return;
            final int rows = b.rows();
            for (int i=0; i<rows; i++)
                fill(b.getByte(0, i), b.getByte(1,i), b.getByte(2,i));
        }*/
    }

    protected String[] valueNames = {"occ/F"};
    protected String[] indexNames = {"sector","layer","component"};
    protected IndexedTable table;

    public OccupancyTable(String... index) {
        indexNames = index;
        reset();
    }

    public OccupancyTable() {
        reset();
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
     * @param bank 
     * @param weighted 
     */
    public void fill(DataBank bank, boolean weighted) {
        final int rows = bank.rows();
        int[] indices = new int[indexNames.length];
        for (int i=0; i<rows; i++) {
            for (int j=0; j<indexNames.length; j++) {
                if (j==2) indices[j] = bank.getShort(j,i);
                else indices[j] = bank.getByte(j,i);
            }
            if (weighted) fill(bank.getFloat(indexNames.length,i), indices);
            else fill(indices);
        }
    }

    /**
     * 
     * @param event
     * @param bank
     * @param weighted 
     */
    public void fill(DataEvent event, String bank, boolean weighted) {
        DataBank b = event.getBank(bank);
        if (b != null) fill(b, weighted);
    }

    /**
     * 
     * @param bank 
     * @param weighted 
     */
    public final void fill(Bank bank, boolean weighted) {
        int rows = bank.getRows();
        int[] indices = new int[indexNames.length];
        for (int i=0; i<rows; i++) {
            for (int j=0; j<indexNames.length; j++) {
                if (j==2) indices[j] = bank.getShort(j,i);
                else indices[j] = bank.getByte(j,i);
            }
            if (weighted) fill(bank.getFloat(indexNames.length,i), indices);
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

    /**
     * Create an occupancy bank of the given name.
     * @param event
     * @param bank
     * @return 
     */
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
     * Modify the user-provided occupancy bank. 
     * @param events
     * @param b
     */
    public final void create(long events, Bank b) {
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
     * @param events
     * @return 
     */
    public final IndexedTable create(long events) {
        IndexedTable t = new IndexedTable(indexNames.length, valueNames);
        for (long hash : ((Map<Long,IndexedEntry>)table.getList().getMap()).keySet()) {
            t.addEntry(IndexedTable.DEFAULT_GENERATOR.getIndices(hash, indexNames.length));
            t.setDoubleValueByHash((table.getDoubleValueByHash(0, hash))/events, 0, hash);
        }
        return t;
    }

}