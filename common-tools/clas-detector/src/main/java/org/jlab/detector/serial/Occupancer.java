package org.jlab.detector.serial;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
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
public class Occupancer extends ArrayList<Occupancer.OccupanceTable> {
    
    static final String BANKDIR = ClasUtilsFile.getResourceDir("CLAS12DIR","etc/bankdefs/hipo4/singles/occupancy");
    static final SchemaFactory schema = new SchemaFactory();
    static { schema.initFromDirectory(ClasUtilsFile.getResourceDir("CLAS12DIR","etc/bankdefs/hipo4")); }
    
    int nevents;
    int prescale;

    public Occupancer(int prescale) {
        super();
        this.prescale = prescale;
        this.nevents = 0;
        try {
            addAll(Files.list(Paths.get(BANKDIR))
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .map(s -> s.substring(0, s.length()-5))
                    .map(s -> s.substring(5, s.length()))
                    .map(OccupanceTable::new).toList());
        } catch (IOException ex) {
            System.getLogger(Occupancer.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }
    
    public boolean process(Event event) {
        forEach(t -> { t.fill(event, false); });
        if (++nevents % prescale == 0) {
            forEach(t -> {
                if (t.getTable().getRowCount() > 0) {
                    event.write(t.create(nevents, event));
                }
                t.reset();
            });
            nevents = 0;
        }
        return true;
    }
    
    public void reset() {
        forEach(t -> { t.reset(); nevents = 0; });
    }
    
    public static final class OccupanceTable {

        Schema occSchema;
        Schema hitSchema;
        IndexedTable table;
        
        /**
         * A 3-index table, e.g., sector/layer/component.
         * @param hitBank name of the hit bank
         */
        public OccupanceTable(String hitBank) {
            hitSchema = schema.getSchema(hitBank);
            occSchema = schema.getSchema("OCC::" + hitBank);
            table = new IndexedTable(3, new String[]{"occ/F"});
        }
        
        /**
         * An N-index table.
         * @param hitBank name of the hit bank
         * @param indexCount number of inidices in the hit bank
         */
        public OccupanceTable(String hitBank, int indexCount) {
            hitSchema = schema.getSchema(hitBank);
            occSchema = schema.getSchema("OCC::" + hitBank);
            table = new IndexedTable(indexCount, new String[]{"occ/F"});
        }
        
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
            System.err.println(hitSchema.getName());
            RawBank bank = new RawBank(hitSchema, 1000, OrderGroups.NODENOISE);
            bank.read(event);
            System.err.println(bank.getRows()); hitSchema.show();
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
}
