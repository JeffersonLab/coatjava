package org.jlab.detector.calib.utils;

import java.util.Map;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.utils.groups.IndexedTable.IndexedEntry;

/**
 * Occupancy bookkeeper based on IndexedTable, with I/O helpers for indexed banks.
 *
 * @author baltzell 
 */
public class OccupanceTable {

    IndexedTable table;
    Bank hitBank;
    Bank occBank;

    /**
     * A 3-index table.
     * @param schema
     * @param hits name of the hits bank
     */
    public OccupanceTable(SchemaFactory schema, String hits) {
        hitBank = schema.getBank(hits);
        occBank = schema.getBank("OCC::"+hits);
        table = new IndexedTable(3, new String[]{"occ/F"});
    }

    /**
     * A N-index table.
     * @param schema
     * @param hits name of the hits bank
     * @param indexCount number of inidices in the hits bank
     */
    public OccupanceTable(SchemaFactory schema, String hits, int indexCount) {
        hitBank = schema.getBank(hits);
        occBank = schema.getBank("OCC::"+hits);
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

    /**
     * Fill the occupancy table.
     * @param weight
     * @param index 
     */
    public final void fill(float weight, int... index) {
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
    public void fill(Bank bank, boolean weighted) {
        int rows = bank.getRows();
        int[] idx = new int[table.getList().getIndexSize()];
        for (int i=0; i<rows; i++) {
            for (int j=0; j<table.getList().getIndexSize(); j++) {
                if (j==2) idx[j] = bank.getShort(j,i);
                else idx[j] = bank.getByte(j,i);
            }
            if (weighted) fill(bank.getFloat(table.getList().getIndexSize(),i), idx);
            else fill(1.0f, idx);
        }
    }

    /**
     * Fill occupancy table from a user-defined bank. 
     * @param bank 
     * @param weighted 
     */
    public void fill(DataBank bank, boolean weighted) {
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
    public void fill(Event event) {
        event.read(hitBank);
        fill(hitBank, false);
    }

    /**
     * Fill occupancy table from the hit bank, unweighted.
     * @param event
     */
    public void fill(DataEvent event) {
        fill(event.getBank(hitBank.getSchema().getName()), false);
    }

    /**
     * Get an occupancy bank, normalized by number of events.
     * @param events
     * @param event
     * @return 
     */
    public final DataBank create(long events, DataEvent event) {
        DataBank b = event.createBank(occBank.getSchema().getName(), table.getRowCount());
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

    /**
     * Get an occupancy bank, normalized by number of events.
     * @param events
     * @return 
     */
    public final Bank create(long events) {
        Bank b = new Bank(occBank.getSchema(), table.getRowCount());
        int row = 0;
        for (long hash : ((Map<Long,IndexedEntry>)table.getList().getMap()).keySet()) {
            int[] idx = IndexedTable.DEFAULT_GENERATOR.getIndices(hash, table.getList().getIndexSize());
            for (int j=0; j<table.getList().getIndexSize(); j++){
                if (j == 2) b.putShort(j, row, (short)idx[j]);
                else b.putByte(j, row, (byte)idx[j]);
            }
            b.putFloat(table.getList().getIndexSize(), row, (float)table.getDoubleValueByHash(0, hash)/events);
            row++;
        }
        return b;
    }

    /**
     * Utility for processing a bunch of occupancies.
     */
    public static final class OccupanceDriver {
        int events=0,prescale;
        OccupanceTable[] tables;
        public OccupanceDriver(SchemaFactory schema, int prescale) {
            this.prescale = prescale;
            tables = new OccupanceTable[] {
                new OccupanceTable(schema,"DC::tot"),
                new OccupanceTable(schema,"DC::tdc"),
                new OccupanceTable(schema,"ECAL::adc"),
                new OccupanceTable(schema,"ECAL::tdc"),
                new OccupanceTable(schema,"FTOF::adc"),
                new OccupanceTable(schema,"FTOF::tdc"),
                new OccupanceTable(schema,"CTOF::adc"),
                new OccupanceTable(schema,"CTOF::tdc"),
                new OccupanceTable(schema,"HTCC::adc"),
                new OccupanceTable(schema,"HTCC::tdc"),
                new OccupanceTable(schema,"LTCC::adc"),
                new OccupanceTable(schema,"LTCC::tdc"),
                new OccupanceTable(schema,"BST::adc"),
                new OccupanceTable(schema,"BMT::adc"),
                new OccupanceTable(schema,"FTC::adc"),
                new OccupanceTable(schema,"FTH::adc"),
                new OccupanceTable(schema,"FTT::adc"),
                new OccupanceTable(schema,"RICH::tdc"),
                new OccupanceTable(schema,"BAND::adc"),
                new OccupanceTable(schema,"BAND::tdc"),
            };
        }
        public void process(DataEvent event) {
            for (OccupanceTable t : tables) t.fill(event);
            write(event);
        }
        public void reset() {
            for (OccupanceTable t : tables) t.reset();
            events = 0;
        }
        synchronized void write(DataEvent event) {
            if (++events % prescale == 0) {
                for (OccupanceTable t : tables) {
                    if (t.table.getRowCount() > 0) event.appendBank(t.create(events, event));
                    t.reset();
                }
                events = 0;
            }
        }
    }

}