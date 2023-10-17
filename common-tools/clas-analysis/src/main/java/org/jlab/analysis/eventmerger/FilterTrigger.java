package org.jlab.analysis.eventmerger;
import org.jlab.detector.scalers.DaqScalersSequence;
import org.jlab.jnp.hipo4.data.*;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.jlab.jnp.utils.data.*;

/**
 * Hipo Reduce Worker: filter event based on trigger bit
 
 Inputs: selected and vetoed trigger bit masks (64 selectedBits)
 Returns "true" if one of the bits in the selectedBits mask is set in the trigger 
 word and none of the bit in the vetoedBits mask is
 * @author devita
 */
public class FilterTrigger implements Worker {

    Bank triggerBank = null;
    DaqScalersSequence chargeSeq = null;
    long selectedBits = 0L;
    long vetoedBits = 0L;
    
    public FilterTrigger(long bits, long veto){
        this.selectedBits=bits;
        this.vetoedBits=veto;
        System.out.println("\nInitializing trigger reduction:");
        System.out.println("\t selected bit mask set to 0x" + Long.toHexString(bits));
        System.out.println("\t   vetoed bit mask set to 0x" + Long.toHexString(veto));
    }

    /**
     * Initialize bank schema
     * 
     * @param reader
     */
    @Override
    public void init(HipoReader reader) {
        triggerBank = new Bank(reader.getSchemaFactory().getSchema("RUN::config"));
    }

    /**
     * Event filter: select events according to trigger bit
     * 
     * @param event
     * @return
     */
    @Override
    public boolean processEvent(Event event) {
        event.read(triggerBank);
        if(triggerBank.getRows()>0){
            long triggerBit = triggerBank.getLong("trigger",0);
            // If returned true, the event will be write to the output
            if((triggerBit & selectedBits) !=0L && (triggerBit & vetoedBits) == 0L) return true;
            }
        return false;
    }
    
    // This function has to be implemented, but not used if
    // HipoStream is not trying to classify the events.
    @Override
    public long clasifyEvent(Event event) { return 0L; }

}
