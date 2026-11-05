package org.jlab.io.utils;

import org.jlab.io.banks.RUN_config;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.Schema;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.utils.CLASResources;

/**
 *
 * @author baltzell
 */
public class ParaTrigWriter extends HipoWriterSorted {
   
    SchemaFactory schema = CLASResources.getFullSchema();
    Schema runConfig = schema.getSchema("RUN::config");
    
    long count;
    long mask;
    int prescale;
    
    public ParaTrigWriter(long mask, int prescale) {
        super();
        this.mask = mask;
        this.prescale = prescale;
        setCompressionType(2);
        getSchemaFactory().copy(schema);
    }
    
    @Override
    public void addEvent(Event e) {
        Bank b = new Bank(runConfig,1);
        e.read(b);
        if (b.getRows() > 0) {
            if (mask > 0 && 0 != (b.getLong(RUN_config.trigger,0) & mask)) {
                if (prescale<=0 || (++count % prescale) == 0) {
                    super.addEvent(e);
                }
            }
        }
    }

}
