package org.jlab.detector.helicity;

import java.util.Date;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;

public class DecoderBoardTest {

    public static void main(String args[]) {
        String inputFile = args.length>0?args[0]:"/Users/baltzell/Software/coatjava/nab-cleanup/0.hipo";
        String outputFile = true ? null : String.format("/Users/baltzell/xx-%d.hipo",(new Date()).getTime());
        //HelicitySequenceManager hsm = new HelicitySequenceManager(8, inputFile);
        HipoWriterSorted writer = new HipoWriterSorted();
            if (outputFile != null) {
                writer.getSchemaFactory().initFromDirectory("/Users/baltzell/Software/coatjava/nab-cleanup/etc/bankdefs/hipo4");
                writer.setCompressionType(2);
                writer.open(outputFile);
            }
            HipoReader reader = new HipoReader();
            reader.open(inputFile);
            Event event = new Event();
            Bank decoder = new Bank(reader.getSchemaFactory().getSchema("HEL::decoder"));
            Bank online = new Bank(reader.getSchemaFactory().getSchema("HEL::online"));
            Bank config = new Bank(reader.getSchemaFactory().getSchema("RUN::config"));
            //Bank compare = new Bank(writer.getSchemaFactory().getSchema("hel"));
            int n=0;
            while (reader.hasNext()) {
                reader.nextEvent(event);
                if (event.hasBank(reader.getSchemaFactory().getSchema("HEL::decoder"))) {
                    event.read(decoder);
                    event.read(config);
                    event.read(online);
                    //decoder.copyTo(compare);
                    //compare.putByte("board", 0, DecoderBoardUtil.QUARTET.getWindowHelicity(decoder,8));
                    //compare.putByte("online", 0, online.getByte("helicityRaw",0));
                    //compare.putByte("offline", 0, hsm.search(event).value());
                    //compare.putLong("timestamp", 0, config.getLong("timestamp",0));
                    //Event e = new Event();
                    //e.write(compare);
                    //if (outputFile != null) writer.addEvent(e,event.getEventTag());
                    System.out.println(DecoderBoardUtil.toString(decoder));
                    if (!DecoderBoardUtil.QUARTET.check(decoder)) {
                        System.out.println(DecoderBoardUtil.toString(decoder));
                        ++n;
                    }//System.out.println(hsm.search(event));
                    //break;
                    //if (++n>100) break;
                }
            }
                System.out.format("____________________________________________ %d ERRORS\n",n);
    }
}
