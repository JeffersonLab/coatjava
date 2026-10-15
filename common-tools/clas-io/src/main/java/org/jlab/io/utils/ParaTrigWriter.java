package org.jlab.io.utils;

import org.jlab.io.banks.RUN_config;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.Schema;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.utils.CLASResources;

public class ParaTrigWriter extends HipoWriterSorted {

    Schema runConfig;
    long count = 0;
    long mask;
    int prescale;
    
    public ParaTrigWriter(long mask, int prescale) {
        super();
        this.mask = mask;
        this.prescale = prescale;
        SchemaFactory schema = CLASResources.getFullSchema();
        runConfig = schema.getSchema("RUN::config");
        setCompressionType(2);
        getSchemaFactory().copy(schema);
    }
    
    @Override
    public void addEvent(Event e) {
        Bank b = e.read(new Bank(runConfig,1));
        if (b.getRows() > 0) {
            if (mask > 0 && 0 != (b.getLong(RUN_config.trigger,0) & mask)) {
                if (prescale<=0 || (++count % prescale) == 0) {
                    super.addEvent(e);
                }
            }
        }
    }

}
