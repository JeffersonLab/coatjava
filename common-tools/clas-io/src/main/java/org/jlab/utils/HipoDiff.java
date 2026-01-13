package org.jlab.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.Schema;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.utils.options.OptionParser;

public class HipoDiff {

    /**
     * Bank sortable by any integer columns.
     * 
     */
    static class SortedBank extends Bank {
        SortedBank(Schema s) { super(s); }
        /**
         * @param index the bank column indices to sort on
         * @return the sorted row indices
         */
        int[] getSorted(int... index) {
            int[] rows = new int[getRows()];
            for (int row = 0; row < rows.length; row++) rows[row] = row;
            // bubble sort:
            for (int i = 0; i < rows.length - 1; i++) {
                for (int j = 0; j < rows.length - i - 1; j++) {
                    for (int idx : index) {
                        if (idx >= this.getSchema().getElements()) break;
                        int x1 = getInt(idx, rows[j]);
                        int x2 = getInt(idx, rows[j + 1]);
                        if (x1 > x2) {
                            int tmp = rows[j];
                            rows[j] = rows[j + 1];
                            rows[j + 1] = tmp;
                            break;
                        }
                        else if (x1 < x2) break;
                    }
                }
            }
            return rows;
        }
    }

    static int nrow = 0;
    static int nevent = -1;
    static int nentry = 0;
    static int nbadevent = 0;
    static int nbadrow = 0;
    static int nbadentry = 0;
    static int[] sortIndex = null;

    static int nmax;
    static double tolerance;
    static boolean verboseMode;
    static boolean quietMode;
    static Bank runConfigBank;

    static ArrayList<SortedBank> banksA = new ArrayList<>();
    static ArrayList<SortedBank> banksB = new ArrayList<>();
    static HashMap<String, HashMap<String, Integer>> badEntries = new HashMap<>();

    public static void compare(HipoReader a, HipoReader b) {
        Event eventA = new Event();
        Event eventB = new Event();
        while (a.hasNext() && b.hasNext() && (nmax < 1 || nevent < nmax)) {
            if (++nevent % 10000 == 0) System.out.println("Analyzed " + nevent + " events");
            a.nextEvent(eventA);
            b.nextEvent(eventB);
            eventA.read(runConfigBank);
            compare(eventA, eventB);
        }
        System.out.println("\n Analyzed " + nevent + " with " + nbadevent + " bad banks");
        System.out.println(nbadrow + "/" + nrow + " mismatched rows");
        System.out.println(nbadentry + "/" + nentry + " mismatched entry");
        for (String name : badEntries.keySet()) {
            System.out.println(name + " " + badEntries.get(name));
        }
        System.exit(nbadevent + nbadrow + nbadentry);
    }

    public static void compare(Event a, Event b) {
        for (int i = 0; i < banksA.size(); i++) {
            a.read(banksA.get(i));
            b.read(banksB.get(i));
            compare(banksA.get(i), banksB.get(i));
        }
    }

    public static void compare(SortedBank a, SortedBank b) {
        if (a.getRows() != b.getRows()) {
            System.out.println("========================= Different number of rows:");
            runConfigBank.show();
            a.show();
            b.show();
            nbadevent++;
            System.out.println("=========================");
            return;
        }
        int[] rowsA = sortIndex == null ? null : a.getSorted(sortIndex);
        int[] rowsB = sortIndex == null ? null : b.getSorted(sortIndex);
        for (int row = 0; row < a.getRows(); row++) {
            boolean mismatch = false;
            nrow++;
            int rowA = sortIndex == null ? row : rowsA[row];
            int rowB = sortIndex == null ? row : rowsB[row];
            for (int j = 0; j < a.getSchema().getElements(); j++) {
                final int type = a.getSchema().getType(j);
                final String name = a.getSchema().getElementName(j);
                int element = -1;
                String values = "";
                nentry++;
                switch (type) {
                    case 1:
                        if (a.getByte(name, rowA) != b.getByte(name, rowB)) {
                            element = j;
                            values += a.getByte(name, rowA) + "/" + b.getByte(name, rowB);
                        }
                        break;
                    case 2:
                        if (a.getShort(name, rowA) != b.getShort(name, rowB)) {
                            element = j;
                            values += a.getShort(name, rowA) + "/" + b.getShort(name, rowB);
                        }
                        break;
                    case 3:
                        if (a.getInt(name, rowA) != b.getInt(name, rowB)) {
                            element = j;
                            values += a.getInt(name, rowA) + "/" + b.getInt(name, rowB);
                        }
                        break;
                    case 4:
                        if ((!Double.isNaN(a.getFloat(name, rowA)) || !Double.isNaN(b.getFloat(name, rowB)))
                            && (!Double.isInfinite(a.getFloat(name, rowA)) || !Double.isInfinite(b.getFloat(name, rowB)))
                            && Math.abs(a.getFloat(name, rowA) - b.getFloat(name, rowB)) > tolerance) {
                            element = j;
                            values += a.getFloat(name, rowA) + "/" + b.getFloat(name, rowB);
                        }
                        break;
                }
                if (element >= 0) {
                    if (verboseMode) {
                        System.out.println("Bank.show " + a.getSchema().getName());
                        a.show();
                        b.show();
                    }
                    if (!quietMode) {
                        System.out.println(a.getSchema().getName() + " mismatch at event " + runConfigBank.getInt("event", 0)
                            + " in row " + row + " for variable " + name + " with values " + values);
                    }
                    mismatch = true;
                    nbadentry++;
                    String bankName = a.getSchema().getName();
                    String elementName = a.getSchema().getElementName(element);
                    if (!badEntries.containsKey(bankName)) {
                        badEntries.put(bankName, new HashMap<>());
                    }
                    Map<String, Integer> m = badEntries.get(bankName);
                    if (!m.containsKey(elementName)) {
                        m.put(elementName, 0);
                    }
                    m.put(elementName, m.get(elementName) + 1);
                }
            }
            if (mismatch) {
                nbadrow++;
            }
        }
    }

    public static void main(String args[]) {

        OptionParser op = new OptionParser("hipo-diff");
        op.addOption("-t", "0.00001", "absolute tolerance for comparisons");
        op.addOption("-n", "-1", "number of events");
        op.addOption("-q", null, "quiet mode");
        op.addOption("-Q", null, "verbose mode");
        op.addOption("-b", null, "name of bank to diff");
        op.addOption("-s", null, "sort on column index");
        op.setRequiresInputList(true);
        op.parse(args);
        if (op.getInputList().size() != 2) {
            System.out.println(op.getUsageString());
            System.out.println("ERROR:  Exactly 2 input files are required.");
            System.exit(1);
        }

        if (op.getOption("-s").stringValue() != null) {
            String[] stmp = op.getOption("-s").stringValue().split(",");
            sortIndex = new int[stmp.length];
            for (int i = 0; i < stmp.length; i++) sortIndex[i] = Integer.parseInt(stmp[i]);
        }
        verboseMode = op.getOption("-Q").stringValue() != null;
        quietMode = op.getOption("-q").stringValue() != null;
        nmax = op.getOption("-n").intValue();
        tolerance = op.getOption("-t").doubleValue();

        HipoReader readerA = new HipoReader();
        HipoReader readerB = new HipoReader();
        readerA.open(op.getInputList().get(0));
        readerB.open(op.getInputList().get(1));
        SchemaFactory sf = readerA.getSchemaFactory();
        runConfigBank = new Bank(sf.getSchema("RUN::config"));

        if (op.getOption("-b").stringValue() == null) {
            for (Schema s : sf.getSchemaList()) {
                banksA.add(new SortedBank(s));
                banksB.add(new SortedBank(s));
            }
        } else {
            banksA.add(new SortedBank(sf.getSchema(op.getOption("-b").stringValue())));
            banksB.add(new SortedBank(sf.getSchema(op.getOption("-b").stringValue())));
        }

        compare(readerA, readerB);
    }

}
