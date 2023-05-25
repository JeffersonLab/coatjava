package org.jlab.utils;

import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.Schema;
import java.util.HashMap;
import org.jlab.utils.options.OptionParser;

public class HipoDiff {

    public static void main(String args[]) {

        OptionParser op = new OptionParser();
        op.addOption("-r", "0.00001", "resolution");
        op.addOption("-n", "-1", "number of events");
        op.addRequired("-b", "name of bank to diff");
        op.setRequiresInputList(true);
        op.parse(args);
        if (op.getInputList().size() != 2) {
            op.show();
            System.err.println("ERROR:  Exactly 2 input files are required.");
            System.exit(1);
        }

        final String bankName = op.getOption("-b").stringValue();
        final double resolution = op.getOption("-r").doubleValue();
        final int nmax = op.getOption("-n").intValue();

        HipoReader readerA = new HipoReader();
        HipoReader readerB = new HipoReader();
        readerA.open(op.getInputList().get(0));
        readerB.open(op.getInputList().get(1));

        Schema schema = readerA.getSchemaFactory().getSchema(bankName);
        Bank bankA = new Bank(schema);
        Bank bankB = new Bank(schema);

        Bank runConfigBank = new Bank(readerA.getSchemaFactory().getSchema("RUN::config"));
        Event event = new Event();

        int nevent = -1;
        int nrow = 0;
        int nentry = 0;
        int nbadevent = 0;
        int nbadrow = 0;
        int nbadentry = 0;
        HashMap<String, Integer> badEntries = new HashMap<>();

        while (readerA.hasNext() && readerB.hasNext() && (nmax<1 || nevent<nmax)) {

            if (++nevent % 10000 == 0) {
                System.out.println("Analyzed " + nevent + " events");
            }

            readerA.nextEvent(event);
            event.read(bankA);
            readerB.nextEvent(event);
            event.read(bankB);

            event.read(runConfigBank);

            if (bankA.getRows() != bankB.getRows()) {
                System.out.println("========================= Different number of rows:");
                runConfigBank.show();
                bankA.show();
                bankB.show();
                nbadevent++;
                System.out.println("=========================");
            }

            else {
                for (int i = 0; i < bankA.getRows(); i++) {
                    boolean mismatch = false;
                    nrow++;
                    for (int j = 0; j < schema.getElements(); j++) {
                        final int type = schema.getType(j);
                        final String name = schema.getElementName(j);
                        int element = -1;
                        nentry++;
                        switch (type) {
                            case 1:
                                if (bankA.getByte(name, i) != bankB.getByte(name, i)) {
                                    element = j;
                                }
                                break;
                            case 2:
                                if (bankA.getShort(name, i) != bankB.getShort(name, i)) {
                                    element = j;
                                }
                                break;
                            case 3:
                                if (bankA.getInt(name, i) != bankB.getInt(name, i)) {
                                    element = j;
                                }
                                break;
                            case 4:
                                if ((!Double.isNaN(bankA.getFloat(name, i)) || !Double.isNaN(bankB.getFloat(name, i)))
                                        && (!Double.isInfinite(bankA.getFloat(name, i)) || !Double.isInfinite(bankB.getFloat(name, i)))
                                        && Math.abs(bankA.getFloat(name, i) - bankB.getFloat(name, i)) > resolution) {
                                    element = j;
                                }
                                break;
                        }
                        if (element >= 0) {
                            System.out.println("mismatch at event " + runConfigBank.getInt("event", 0)
                                    + ", in row " + i + ", s/l/c " + bankA.getByte("sector", i) + "/"
                                    + bankA.getByte("layer", i) + "/" + bankA.getShort("component", i)
                                    + " for variable " + name + " with values " + bankA.getByte(name, i) + "/"
                                    + bankB.getByte(name, i));
                            mismatch = true;
                            nbadentry++;
                            if (badEntries.containsKey(schema.getElementName(element))) {
                                int nbad = badEntries.get(schema.getElementName(element)) + 1;
                                badEntries.replace(schema.getElementName(element), nbad);
                            } else {
                                badEntries.put(schema.getElementName(element), 1);
                            }
                        }
                    }
                    if (mismatch) {
                        nbadrow++;
                    }
                }
            }
        }
        System.out.println("Analyzed " + nevent + " with " + nbadevent + " bad banks");
        System.out.println(nbadrow + "/" + nrow + " mismatched rows");
        System.out.println(nbadentry + "/" + nentry + " mismatched entry");
        for (String name : badEntries.keySet()) {
            System.out.println(name + " " + badEntries.get(name));
        }
    }
}
