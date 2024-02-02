package org.jlab.detector.helicity;

import java.util.Date;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;

public class DecoderBoardTest {

    public static String toString(int bits) {
        StringBuilder s = new StringBuilder();
        for (int i=31; i>=0; --i) s.append((bits>>i)&1);
        return s.toString();
    }

    public static String toString(Bank b) {
        StringBuilder s = new StringBuilder();
        s.append("Timestamp/nPair/seed: ");
        s.append(b.getLong("timestamp",0));
        s.append("/");
        s.append(b.getInt("nPair",0));
        s.append("/");
        s.append(b.getInt("helicitySeed",0));
        s.append("\nPair:      ");
        s.append(toString(b.getInt("pairArray",0)));
        s.append(String.format("  %d",b.getInt("pair",0)));
        s.append("\nPattern:   ");
        s.append(toString(b.getInt("patternArray",0)));
        s.append(String.format("  %d",b.getInt("pattern",0)));
        s.append("\nHelicity:  ");
        s.append(toString(b.getInt("helicityArray",0)));
        s.append(String.format("  %d",b.getInt("helicity",0)));
        s.append("\nHelicityP: ");
        s.append(toString(b.getInt("helicityPArray",0)));
        s.append("\nHelicityP0:");
        StringBuilder s3 = new StringBuilder();
        for (int i=31; i>=0; --i) {
            if (((b.getInt("patternArray",0)>>i)&1) == 1) {
                 s3.append((b.getInt("helicityArray",0)>>i)&1); 
            }
        }
        s.append(String.format("%32s",s3));
        s.append("\nHelicityP1:");
        StringBuilder s2 = new StringBuilder();
        for (int i=31; i>=0; --i) {
            if (((b.getInt("patternArray",0)>>i)&1) == 0) {
                 s2.append((b.getInt("helicityArray",0)>>i)&1); 
            }
        }
        s.append(String.format("%32s",s2));
        s.append("\n");
        return s.toString();
    }

    public static void main(String args[]) {
        DecoderBoardUtil.INVERT_BITS_CHECK = false;
        String filename = "/Users/baltzell/Software/coatjava/iss166+167-eventordering+maurik/clas_019400.evio.00040.hipo";
        HelicitySequenceManager hsm = new HelicitySequenceManager(8, filename);
        try (HipoWriterSorted writer = new HipoWriterSorted()) {
            writer.getSchemaFactory().initFromDirectory("/Users/baltzell/Software/coatjava/iss171-heldecoder/etc/bankdefs/hipo4");
            writer.setCompressionType(2);
            writer.open(String.format("/Users/baltzell/xx-%d.hipo",(new Date()).getTime()));
            HipoReader reader = new HipoReader();
            SchemaFactory schema = writer.getSchemaFactory();
            reader.open(filename);
            Bank decoder = new Bank(schema.getSchema("HEL::decoder"));
            Bank online = new Bank(schema.getSchema("HEL::online"));
            Bank config = new Bank(schema.getSchema("RUN::config"));
            Bank compare = new Bank(schema.getSchema("hel"));
            Event event = new Event();
            while (reader.hasNext()) {
                reader.nextEvent(event);
                if (event.hasBank(schema.getSchema("HEL::decoder"))) {
                    event.read(decoder);
                    event.read(config);
                    event.read(online);
                    decoder.copyTo(compare);
                    compare.putByte("board", 0, (byte)DecoderBoardUtil.getQuartetWindowHelicity(decoder,8));
                    compare.putByte("online", 0, online.getByte("helicityRaw",0));
                    compare.putByte("offline", 0, hsm.search(event).value());
                    compare.putLong("timestamp", 0, config.getLong("timestamp",0));
                    Event e = new Event();
                    e.write(compare);
                    writer.addEvent(e,event.getEventTag());
                    System.out.println(toString(decoder));
                    if (!DecoderBoardUtil.checkQuartetAll(decoder)) break;
                    //System.out.println(hsm.search(event));
                    //break;
                }
            }
        }
    }
}
