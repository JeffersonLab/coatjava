package org.jlab.io.clara.histo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.IDataSet;
import org.jlab.groot.data.TDirectory;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.json.JSONArray;

public abstract class Hister {
   
    protected SchemaFactory schemaFactory;
    protected HashMap<String,ArrayList<IDataSet>> histos;

    abstract public void fill(Event event);

    abstract public void configure();

    public void write(TDirectory dir) {
        for (HashMap.Entry<String,ArrayList<IDataSet>> e : histos.entrySet()) {
            if (!e.getKey().startsWith("/")) dir.cd("/"+e.getKey()); 
            else dir.cd(e.getKey());
            for (IDataSet d : e.getValue()) dir.addDataSet(d);
        }
    }

    public void add(String dir, IDataSet... data) {
        if (!histos.containsKey(dir)) histos.put(dir, new ArrayList<>());
        histos.get(dir).addAll(Arrays.asList(data));
    }

    protected void setSchemaFactory(SchemaFactory s) { schemaFactory = s; }

    public static class Banker extends Hister {
    
        @Override
        public void configure() {}
    
        @Override
        final public void fill(Event event) {
            for (int i=0; i<banks.size(); i++) {
                if (event.hasBank(banks.get(i).getSchema())) {
                    Bank b = banks.get(i);
                    event.read(b);
                    for (IDataSet d : histos.get(b.getSchema().getName())) {
                        String[] names = b.getSchema().getEntryArray();
                        for (String name : names) {
                            ((H1F)d).fill(b.getFloat(name, 0));
                        }
                    }
                }
            }
        }
    
        ArrayList<Bank> banks;
    
        public void add(JSONArray bankNames) {
            for (int i=0; i<bankNames.length(); i++) {
                banks.add(new Bank(schemaFactory.getSchema(bankNames.getString(i))));
            }
        }
    }

}