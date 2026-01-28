package org.jlab.utils;

import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriter;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Schema;
import org.jlab.jnp.hipo4.data.SchemaFactory;

public class HipoDiffBanks {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: HipoDiffBanks run1.hipo run2.hipo out.hipo");
            System.exit(1);
        }

        String f1   = args[0];
        String f2   = args[1];
        String fout = args[2];

        // open the input files
        HipoReader r1 = new HipoReader();
        HipoReader r2 = new HipoReader();
        r1.open(f1);
        r2.open(f2);

        // grab schemas (dictionary) from run1
        SchemaFactory dict = r1.getSchemaFactory();

        // prepare output writer with same dictionary
        HipoWriter w = new HipoWriter();
        w.getSchemaFactory().copy(dict);
        w.open(fout);

        Event e1   = new Event();
        Event e2   = new Event();
        Event eOut = new Event();

        long n1 = r1.getEventCount();
        long n2 = r2.getEventCount();
        long nEvents = Math.min(n1, n2);

        // loop over events
        for (int ievt = 0; ievt < nEvents; ievt++) {

            r1.getEvent(e1, ievt);
            r2.getEvent(e2, ievt);

            eOut.reset();

            // loop over every schema (bank definition) in file1
            for (Schema schema : dict.getSchemaList()) {

                // only diff this bank if BOTH events actually have it
                if (!e1.hasBank(schema) || !e2.hasBank(schema)) {
                    continue;
                }

                // build banks for this schema
                Bank b1    = new Bank(schema);
                Bank b2    = new Bank(schema);

                // read bank content from each event
                e1.read(b1);
                e2.read(b2);

                int rows = Math.min(b1.getRows(), b2.getRows());

                // create output (diff) bank with same schema
                Bank bDiff = new Bank(schema);
                bDiff.setRows(rows); // coatjava 13.0.2 Bank has setRows()

                // loop rows/cols and subtract numeric columns
                int ncols = b1.getSchema().getElements();
                for (int row = 0; row < rows; row++) {

                    for (int col = 0; col < ncols; col++) {
                        int    ftype = b1.getSchema().getType(col);
                        String fname = b1.getSchema().getElementName(col);

                        switch (ftype) {
                            // NOTE: mapping here matches coatjava's internal codes
                            // 1 = byte  (int8)
                            // 2 = short (int16)
                            // 3 = int   (int32)
                            // 4 = float (float32)
                            // 5 = double(float64)
                            // 6 = long  (int64)
                            case 1: // byte
                                bDiff.putByte(fname, row,
                                        (byte)(b2.getByte(fname,row) - b1.getByte(fname,row)));
                                break;
                            case 2: // short
                                bDiff.putShort(fname, row,
                                        (short)(b2.getShort(fname,row) - b1.getShort(fname,row)));
                                break;
                            case 3: // int
                                bDiff.putInt(fname, row,
                                        b2.getInt(fname,row) - b1.getInt(fname,row));
                                break;
                            case 4: // float
                                bDiff.putFloat(fname, row,
                                        b2.getFloat(fname,row) - b1.getFloat(fname,row));
                                break;
                            case 5: // double
                                bDiff.putDouble(fname, row,
                                        b2.getDouble(fname,row) - b1.getDouble(fname,row));
                                break;
                            case 6: // long
                                bDiff.putLong(fname, row,
                                        b2.getLong(fname,row) - b1.getLong(fname,row));
                                break;
                            default:
                                // unhandled type (arrays, etc.) -> just copy run2's value
                                copyFieldNoSub(ftype, fname, row, b2, bDiff);
                                break;
                        }
                    } // end col loop
                } // end row loop

                // attach this diff bank into the output event
                eOut.write(bDiff);
            } // end schema loop

            // write the diff event
            w.addEvent(eOut);
        } // end event loop

        w.close();
        r1.close();
        r2.close();
    }

    // fallback copy logic for non-subtracted types
    private static void copyFieldNoSub(int ftype, String fname, int row,
                                       Bank src, Bank dst) {

        switch (ftype) {
            case 1: // byte
                dst.putByte(fname, row, src.getByte(fname,row));
                break;
            case 2: // short
                dst.putShort(fname, row, src.getShort(fname,row));
                break;
            case 3: // int
                dst.putInt(fname, row, src.getInt(fname,row));
                break;
            case 4: // float
                dst.putFloat(fname, row, src.getFloat(fname,row));
                break;
            case 5: // double
                dst.putDouble(fname, row, src.getDouble(fname,row));
                break;
            case 6: // long
                dst.putLong(fname, row, src.getLong(fname,row));
                break;
            default:
                // arrays / strings / etc.
                // We don't currently have a generic copier for those in this script.
                // It's ok to skip: they just won't appear in the diff bank.
                break;
        }
    }
}

   
