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

    static int nrow = 0;
    static int nevent = -1;
    static int nentry = 0;
    static int nbadevent = 0;
    static int nbadrow = 0;
    static int nbadentry = 0;
    static double tolerance;
    static boolean verboseMode = false;
    static boolean quietMode = false;
    static Bank runConfigBank = null;
    static SchemaFactory schemaFactory = null;
    static ArrayList<Bank> banksA = new ArrayList<>();
    static ArrayList<Bank> banksB = new ArrayList<>();
    static HashMap<String, HashMap<String,Integer>> badEntries = new HashMap<>();

    public static void main(String args[]) {

        OptionParser op = new OptionParser("hipo-diff");
        op.addOption("-t", "0.00001", "absolute tolerance for comparisons");
        op.addOption("-n", "-1", "number of events");
        op.addOption("-q", null, "quiet mode");
        op.addOption("-Q", null, "verbose mode");
        op.addOption("-b", null,"name of bank to diff");
        op.setRequiresInputList(true);
        op.parse(args);
        if (op.getInputList().size() != 2) {
            System.out.println(op.getUsageString());
            System.out.println("ERROR:  Exactly 2 input files are required.");
            System.exit(1);
        }

        verboseMode = op.getOption("-Q").stringValue() != null;
        quietMode = op.getOption("-q").stringValue() != null;
        final int nmax = op.getOption("-n").intValue();
        tolerance = op.getOption("-t").doubleValue();

        HipoReader readerA = new HipoReader();
        HipoReader readerB = new HipoReader();
        readerA.open(op.getInputList().get(0));
        readerB.open(op.getInputList().get(1));
        Event eventA = new Event();
        Event eventB = new Event();

        schemaFactory = readerA.getSchemaFactory();
        runConfigBank = new Bank(schemaFactory.getSchema("RUN::config"));
        if (op.getOption("-b").stringValue() == null) {
            for (Schema s : schemaFactory.getSchemaList()) {
                banksA.add(new Bank(s));
                banksB.add(new Bank(s));
            }
        }
        else {
            banksA.add(new Bank(schemaFactory.getSchema(op.getOption("-b").stringValue())));
            banksB.add(new Bank(schemaFactory.getSchema(op.getOption("-b").stringValue())));
        }

        while (readerA.hasNext() && readerB.hasNext() && (nmax < 1 || nevent < nmax)) {
            if (++nevent % 10000 == 0) System.out.println("Analyzed " + nevent + " events");
            readerA.nextEvent(eventA);
            readerB.nextEvent(eventB);
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
        for (int i=0; i<banksA.size(); i++) {
            a.read(banksA.get(i));
            b.read(banksB.get(i));
            compare(banksA.get(i),banksB.get(i));
        }
    }
    
    public static void compare(Bank a, Bank b) {

        if (a.getRows() != b.getRows()) {
            System.out.println("========================= Different number of rows:");
            runConfigBank.show();
            a.show();
            b.show();
            nbadevent++;
            System.out.println("=========================");
        }
        else {
            for (int i = 0; i < a.getRows(); i++) {
                boolean mismatch = false;
                nrow++;
                for (int j = 0; j < a.getSchema().getElements(); j++) {
                    final int type = a.getSchema().getType(j);
                    final String name = a.getSchema().getElementName(j);
                    int element = -1;
                    String values = "";
                    nentry++;
                    switch (type) {
                        case 1:
                            if (a.getByte(name, i) != b.getByte(name, i)) {
                                element = j;
                                values += a.getByte(name, i) + "/" + b.getByte(name, i);
                            }
                            break;
                        case 2:
                            if (a.getShort(name, i) != b.getShort(name, i)) {
                                element = j;
                                values += a.getShort(name, i) + "/" + b.getShort(name, i);
                            }
                            break;
                        case 3:
                            if (a.getInt(name, i) != b.getInt(name, i)) {
                                element = j;
                                values += a.getInt(name, i) + "/" + b.getInt(name, i);
                            }
                            break;
                        case 4:
                            if ((!Double.isNaN(a.getFloat(name, i)) || !Double.isNaN(b.getFloat(name, i)))
                                && (!Double.isInfinite(a.getFloat(name, i)) || !Double.isInfinite(b.getFloat(name, i)))
                                && Math.abs(a.getFloat(name, i) - b.getFloat(name, i)) > tolerance) {
                                element = j;
                                values += a.getFloat(name, i) + "/" + b.getFloat(name, i);
                            }
                            break;
                    }
                    if (element >= 0) {
                        if (verboseMode) {
                            System.out.println("Bank.show "+a.getSchema().getName());
                            a.show();
                            b.show();
                        }
                        if (!quietMode) {
                            System.out.println(a.getSchema().getName()+" mismatch at event " + runConfigBank.getInt("event", 0)
                                + " in row " + i + " for variable " + name + " with values " + values);
                        }
                        mismatch = true;
                        nbadentry++;
                        String bankName = a.getSchema().getName();
                        String elementName = a.getSchema().getElementName(element);
                        if (!badEntries.containsKey(bankName)) badEntries.put(bankName, new HashMap<>());
                        Map<String,Integer> m = badEntries.get(bankName);
                        if (!m.containsKey(elementName)) m.put(elementName, 0);
                        m.put(elementName, m.get(elementName)+1);
                    }
                }
                if (mismatch) nbadrow++;
            }
        }
    }

}
