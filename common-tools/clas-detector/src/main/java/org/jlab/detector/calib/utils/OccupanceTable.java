package org.jlab.detector.calib.utils;

import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.utils.groups.IndexedTable.IndexedEntry;
import org.jlab.detector.banks.RawDataBank;
import org.jlab.detector.banks.RawBank.OrderGroups;

/**
 * Occupancy bookkeeper based on IndexedTable, with I/O helpers for indexed banks.
 *
 * @author baltzell 
 */
public class OccupanceTable {

    String hitBank;
    String occBank;
    IndexedTable table;

    /**
     * A 3-index table, e.g., sector/layer/component.
     * @param hitBank name of the hit bank
     */
    public OccupanceTable(String hitBank) {
        this.hitBank = hitBank;
        occBank = "OCC::" + hitBank;
        table = new IndexedTable(3, new String[]{"occ/F"});
    }

    /**
     * An N-index table.
     * @param hitBank name of the hit bank
     * @param indexCount number of inidices in the hit bank
     */
    public OccupanceTable(String hitBank, int indexCount) {
        this.hitBank = hitBank;
        occBank = "OCC::" + hitBank;
        table = new IndexedTable(indexCount, new String[]{"occ/F"});
    }

    /**
     * Zero the occupancy table.
     */
    public final void reset() {
        table = new IndexedTable(table.getList().getIndexSize(), new String[]{"occ/F"});
    }

    /**
     * Get the occupancy table, normalized by number of events.
     * @param events
     * @return 
     */
    public final IndexedTable getOccupancy(long events) {
        IndexedTable t = new IndexedTable(table.getList().getIndexSize(), new String[]{"occ/F"});
        for (long hash : ((Map<Long,IndexedEntry>)table.getList().getMap()).keySet()) {
            t.addEntry(IndexedTable.DEFAULT_GENERATOR.getIndices(hash, table.getList().getIndexSize()));
            t.setDoubleValueByHash((table.getDoubleValueByHash(0, hash))/events, 0, hash);
        }
        return t;
    }

    public final IndexedTable getTable() {
        return table;
    }

    /**
     * Fill the occupancy table.
     * @param weight
     * @param index 
     */
    public synchronized final void fill(float weight, int... index) {
        for (int i=0; i<index.length; i++) if (index[i] < 0) return;
        final long hash = IndexedTable.DEFAULT_GENERATOR.hashCode(index);
        if (!table.hasEntryByHash(hash)) {
            table.addEntry(index);
            table.setDoubleValueByHash(0.0d, 0, hash);
        }
        table.setDoubleValueByHash(table.getDoubleValueByHash(0, hash) + weight, 0, hash);
    }
    
    /**
     * Fill occupancy table from a user-defined bank. 
     * @param bank 
     * @param weighted 
     */
    public void fill(RawDataBank bank, boolean weighted) {
        if (bank != null) {
            final int rows = bank.rows();
            int[] idx = new int[table.getList().getIndexSize()];
            for (int i=0; i<rows; i++) {
                for (int j=0; j<table.getList().getIndexSize(); j++) {
                    if (j==2) idx[j] = bank.getShort(j,i);
                    else idx[j] = bank.getByte(j,i);
                }
                if (weighted) fill(bank.getFloat(table.getList().getIndexSize(),i),idx);
                else fill(1.0f, idx);
            }
        }
    }

    /**
     * Fill occupancy table from the hit bank, unweighted.
     * @param event
     */
    public void fill(DataEvent event) {
        RawDataBank b = new RawDataBank(hitBank, 1000, OrderGroups.NOMINAL);
        b.read(event);
        fill(b, false);
    }

    /**
     * Get an occupancy bank, normalized by number of events.
     * @param events
     * @param event
     * @return 
     */
    public DataBank create(long events, DataEvent event) {
        DataBank b = event.createBank(occBank, table.getRowCount());
        int i = 0;
        Map<Long,IndexedEntry> m = table.getList().getMap();
        for (long hash : m.keySet()) {
            int[] idx = IndexedTable.DEFAULT_GENERATOR.getIndices(hash, table.getList().getIndexSize());
            for (int j=0; j<table.getList().getIndexSize(); j++) {
                if (j == 2) b.setShort(j, i, (short)idx[j]);
                else b.setByte(j, i, (byte)idx[j]);
            }
            b.setFloat(table.getList().getIndexSize(), i, ((float)m.get(hash).getValue(0).intValue())/events);
            i++;
        }
        return b;
    }
}
