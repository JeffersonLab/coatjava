package org.jlab.detector.calib.utils;

import java.util.Map;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.utils.groups.IndexedTable.IndexedEntry;
import org.jlab.detector.banks.RawBank;
import org.jlab.detector.banks.RawBank.OrderGroups;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.Schema;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.utils.system.ClasUtilsFile;

/**
 * Occupancy bookkeeper based on IndexedTable, with I/O helpers for indexed banks.
 *
 * @author baltzell 
 */
public class OccupanceTable {
    
    static final String BANKDIR = ClasUtilsFile.getResourceDir("CLAS12DIR","etc/bankdefs/hipo4/singles/occupancy");
    static final SchemaFactory schema = new SchemaFactory();
    static { schema.initFromDirectory(BANKDIR); }

    String hitBank;
    String occBank;
    Schema occSchema;
    Schema hitSchema;
    IndexedTable table;

    /**
     * A 3-index table, e.g., sector/layer/component.
     * @param hitBank name of the hit bank
     */
    public OccupanceTable(String hitBank) {
        this.hitBank = hitBank;
        occBank = "OCC::" + hitBank;
        occSchema = schema.getSchema(occBank);
        hitSchema = schema.getSchema(hitBank);
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
        occSchema = schema.getSchema(occBank);
        hitSchema = schema.getSchema(hitBank);
        table = new IndexedTable(indexCount, new String[]{"occ/F"});
    }

    public String getHitBank() { return hitBank; }
    public String getOccBank() { return occBank; }
    public final IndexedTable getTable() { return table; }

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
     * @param event
     * @param weighted 
     */
    public void fill(Event event, boolean weighted) {
        RawBank bank = new RawBank(hitSchema, 1000, OrderGroups.NODENOISE);
        bank.read(event);
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

    /**
     * Get an occupancy bank, normalized by number of events.
     * @param events
     * @param event
     * @return 
     */
    public synchronized Bank create(long events, Event event) {
        Bank b = new Bank(occSchema, table.getRowCount());
        Map<Long,IndexedEntry> m = table.getList().getMap();
        int i = 0;
        for (long hash : m.keySet()) {
            int[] idx = IndexedTable.DEFAULT_GENERATOR.getIndices(hash, table.getList().getIndexSize());
            for (int j=0; j<table.getList().getIndexSize(); j++) {
                if (j == 2) b.putShort(j, i, (short)idx[j]);
                else b.putByte(j, i, (byte)idx[j]);
            }
            b.putFloat(table.getList().getIndexSize(), i, ((float)m.get(hash).getValue(0).intValue())/events);
            i++;
        }
        return b;
    }
}
