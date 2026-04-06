package org.jlab.io.clara;

import java.util.ArrayList;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;

public class Tagger {
   
    byte tag;
    Bank runConfig;
    ArrayList<Bank> taggerBanks;
    ArrayList<Bank> antiTaggerBanks;
    SchemaFactory schemaFactory;

    public Tagger(SchemaFactory schema, int tag, String... banks) {
        this.tag = (byte)tag;
        taggerBanks = new ArrayList<>();
        schemaFactory = schema;
        runConfig = new Bank(schemaFactory.getSchema("RUN::config"));
        for (String bank : banks) taggerBanks.add(new Bank(schema.getSchema(bank)));
        for (Bank sBank : schema.getBanks()) {
            boolean found = false;
            for (Bank tBank : taggerBanks) {
                if (tBank.getSchema().getName().equals(sBank.getSchema().getName())) {
                    found = true;
                    break;
                }
            }
            if (!found) antiTaggerBanks.add(new Bank(sBank.getSchema()));
        }
    }

    public void tagAndWrite(HipoWriterSorted writer, Event event) {
        if (event.getEventTag() == 0) {
            Event t = new Event();
            for (Bank b : taggerBanks) {
                event.read(b);
                if (b.getRows() > 0) t.write(b);
            }
            if (!t.isEmpty()) {
                event.read(runConfig);
                t.write(runConfig);
                writer.addEvent(t, tag);
                event.reduceEvent(antiTaggerBanks);
            }
        }
        writer.addEvent(event, event.getEventTag());
    }
}
