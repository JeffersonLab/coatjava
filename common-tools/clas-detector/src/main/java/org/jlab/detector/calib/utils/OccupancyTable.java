package org.jlab.detector.calib.utils;

import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.utils.groups.IndexedTable.IndexedEntry;

public class OccupancyTable {

    protected Bank hitBank;
    protected Bank occBank;

    protected String[] valueNames = {"occ/F"};
    protected int[] indices = {0,1,2};
    protected IndexedTable table;

    public OccupancyTable(SchemaFactory schema, String hits, String occupancy, int... index) {
        indices = index;
        hitBank = schema.getBank(hits);
        occBank = schema.getBank(occupancy);
        reset();
    }

    public OccupancyTable(SchemaFactory schema, String hits, String occupancy) {
        hitBank = schema.getBank(hits);
        occBank = schema.getBank(occupancy);
        reset();
    }

    /**
     * Reset occupancy table.
     */
    public final void reset() {
        table = new IndexedTable(indices.length, valueNames);
    }

    /**
     * Get number of rows for the occupancy table/bank.
     * @return rows 
     */
    public final int getRows() {
        return table.getRowCount();
    }

    /**
     * Get the raw occupancy table.
     * @return 
     */
    public final IndexedTable getOccupancy() {
        return table;
    }

    /**
     * Get the normalized occupancy table.
     * @param events
     * @return 
     */
    public final IndexedTable getOccupancy(long events) {
        IndexedTable t = new IndexedTable(indices.length, valueNames);
        for (long hash : ((Map<Long,IndexedEntry>)table.getList().getMap()).keySet()) {
            t.addEntry(IndexedTable.DEFAULT_GENERATOR.getIndices(hash, indices.length));
            t.setDoubleValueByHash((table.getDoubleValueByHash(0, hash))/events, 0, hash);
        }
        return t;
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
     * Fill occupancy table from a user-defined bank. 
     * @param bank 
     * @param weighted 
     */
    public void fill(Bank bank, boolean weighted) {
        int rows = bank.getRows();
        int[] idx = new int[indices.length];
        for (int i=0; i<rows; i++) {
            for (int j=0; j<indices.length; j++) {
                if (j==2) idx[j] = bank.getShort(j,i);
                else idx[j] = bank.getByte(j,i);
            }
            if (weighted) fill(bank.getFloat(indices.length,i), idx);
            else fill(idx);
        }
    }

    /**
     * Fill occupancy table from the hit bank, unweighted.
     * @param event
     */
    public void fill(Event event) {
        event.read(hitBank);
        fill(hitBank, false);
    }

    /**
     * Fill occupancy table from a user-defined bank, unweighted. 
     * @param bank 
     */
    public void fill(DataBank bank) {
        if (bank != null) {
            final int rows = bank.rows();
            int[] idx = new int[indices.length];
            for (int i=0; i<rows; i++) {
                for (int j=0; j<indices.length; j++) {
                    if (j==2) idx[j] = bank.getShort(j,i);
                    else idx[j] = bank.getByte(j,i);
                }
                fill(idx);
            }
        }
    }

    /**
     * Create an occupancy bank.
     * @param events
     * @param event
     * @return 
     */
    public final DataBank create(long events, DataEvent event) {
        DataBank b = event.createBank(occBank.getSchema().getName(), getRows());
        int i = 0;
        Map<Long,IndexedEntry> m = table.getList().getMap();
        for (long hash : m.keySet()) {
            int[] idx = IndexedTable.DEFAULT_GENERATOR.getIndices(hash, indices.length);
            for (int j=0; j<indices.length; j++) {
                if (j == 2) b.setShort(j, i, (short)idx[j]);
                else b.setByte(j, i, (byte)idx[j]);
            }
            b.setFloat(indices.length, i, ((float)m.get(hash).getValue(0).intValue())/events);
            i++;
        }
        return b;
    }

    /**
     * Modify the user-provided occupancy bank with normalized occupancies. 
     * @param events
     * @return 
     */
    public Bank create(long events) {
        Bank b = new Bank(occBank.getSchema());
        int row = 0;
        for (long hash : ((Map<Long,IndexedEntry>)table.getList().getMap()).keySet()) {
            int[] idx = IndexedTable.DEFAULT_GENERATOR.getIndices(hash, indices.length);
            for (int j=0; j<indices.length; j++){
                if (j == 2) b.putShort(j, row, (short)idx[j]);
                else b.putByte(j, row, (byte)idx[j]);
            }
            b.putFloat(indices.length, row, (float)table.getDoubleValueByHash(0, hash)/events);
            row++;
        }
        return b;
    }

}