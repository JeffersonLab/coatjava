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
 * Occupancy bookkeeper based on IndexedTable with I/O helpers for indexed banks.
 *
 * @author baltzell 
 */
public class OccupanceTable {

    protected Bank hitBank;
    protected Bank occBank;
    protected IndexedTable table;
    protected String[] valueNames = {"occ/F"};
    protected int[] indices = {0,1,2};

    public OccupanceTable(SchemaFactory schema, String hits, String occupancy, int... index) {
        indices = index;
        hitBank = schema.getBank(hits);
        occBank = schema.getBank(occupancy);
        reset();
    }

    public OccupanceTable(SchemaFactory schema, String hits, String occupancy) {
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
     * Fill occupancy table from the hit bank, unweighted.
     * @param event
     */
    public void fill(DataEvent event) {
        fill(event.getBank(hitBank.getSchema().getName()));
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
     * Create an occupancy bank.
     * @param events
     * @return 
     */
    public Bank create(long events) {
        Bank b = new Bank(occBank.getSchema(), getRows());
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

    public static final class OccupanceDriver {
        int events=0,prescale;
        OccupanceTable[] tables;
        public OccupanceDriver(SchemaFactory schema, int prescale) {
            this.prescale = prescale;
            tables = new OccupanceTable[]{
                new OccupanceTable(schema,"DC::tot","DC::occ"),
                new OccupanceTable(schema,"ECAL::adc","ECAL::aocc"),
                new OccupanceTable(schema,"ECAL::tdc","ECAL::tocc"),
                new OccupanceTable(schema,"FTOF::adc","FTOF::aocc"),
                new OccupanceTable(schema,"FTOF::tdc","FTOF::tocc"),
                new OccupanceTable(schema,"CTOF::adc","CTOF::aocc"),
                new OccupanceTable(schema,"CTOF::tdc","CTOF::tocc"),
                new OccupanceTable(schema,"HTCC::adc","HTCC::aocc"),
                new OccupanceTable(schema,"HTCC::tdc","HTCC::tocc"),
                new OccupanceTable(schema,"LTCC::adc","LTCC::aocc"),
                new OccupanceTable(schema,"LTCC::tdc","LTCC::tocc"),
                new OccupanceTable(schema,"SVT::adc","SVT::occ"),
                new OccupanceTable(schema,"BMT::adc","BMT::occ"),
                new OccupanceTable(schema,"FTC::adc","FTC::occ"),
                new OccupanceTable(schema,"FTH::adc","FTH::occ"),
                new OccupanceTable(schema,"FTT::adc","FTT::occ"),
                new OccupanceTable(schema,"RICH::tdc","RICH::occ"),
                new OccupanceTable(schema,"BAND::tdc","BAND::occ"),
            };
        }
        public void process(Event event) {
            for (OccupanceTable t : tables) t.fill(event);
            if (events++ % prescale == 0) {
                for (OccupanceTable t : tables) {
                    if (t.getRows() > 0) event.write(t.create(events));
                    t.reset();
                }
                events = 0;
            }
        }
        public void process(DataEvent event) {
            for (OccupanceTable t : tables) t.fill(event);
            if (events++ % prescale == 0) {
                for (OccupanceTable t : tables) {
                    if (t.getRows() > 0) event.appendBank(t.create(events, event));
                    t.reset();
                }
                events = 0;
            }
        }
        public void reset() {
            for (OccupanceTable t : tables) t.reset();
            events = 0;
        }
    }

}