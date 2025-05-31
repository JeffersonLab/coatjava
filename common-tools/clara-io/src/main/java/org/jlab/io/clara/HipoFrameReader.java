package org.jlab.io.clara;

import java.nio.ByteOrder;
import java.nio.file.Path;
import org.jlab.clara.engine.EngineDataType;
import org.jlab.clara.std.services.AbstractEventReaderService;
import org.jlab.clara.std.services.EventReaderException;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.DataFrame;
import org.jlab.jnp.hipo4.data.DataFrameBuilder;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.json.JSONObject;

/**
 *
 * @author gavalian
 */
public class HipoFrameReader extends AbstractEventReaderService<HipoReader> {
    
    private int maxEventsFrame = 10;
    private int maxSizeFrame   = 1024*1024;
    
    
    public void setMaxEvents(int __nevents){ maxEventsFrame = __nevents;}
    public void setMaxSize(int __nsize){ maxSizeFrame = __nsize;}
    
    @Override
    protected HipoReader createReader(Path file, JSONObject opts)
            throws EventReaderException {
        try {
            HipoReader reader = new HipoReader();
            reader.open(file.toString());
            return reader;
        } catch (Exception e) {
            throw new EventReaderException(e);
        }
    }

    @Override
    protected void closeReader() {
        reader.close();
    }

    @Override
    public int readEventCount() throws EventReaderException {
        int numberOfEvents = reader.getEventCount()/this.maxEventsFrame;
        int leftOver       = reader.getEventCount()%this.maxEventsFrame;
        if(leftOver>0) numberOfEvents++;
        return numberOfEvents;
    }

    @Override
    public ByteOrder readByteOrder() throws EventReaderException {
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Override
    public Object readEvent(int eventNumber) throws EventReaderException {
        try {
            int startEvent = eventNumber*this.maxEventsFrame;
            DataFrameBuilder builder = new DataFrameBuilder(this.maxEventsFrame,this.maxSizeFrame);
            Event            event   = new Event();
            
            if(eventNumber==0){
                event.reset();
                Bank  runConfig = new Bank(reader.getSchemaFactory().getSchema("RUN::config"),1);
                event.write(runConfig);
                event.setEventTag(1001);
                builder.addEvent(event.getEventBuffer().array(), 0, 
                            event.getEventBufferSize());
                System.out.println("[FRAMEREADER] --> COMPOSING START FILE EVENT...");
            }
            
            for(int i = 0; i < this.maxEventsFrame; i++){
                if(reader.hasNext()==true){
                    reader.nextEvent(event);
                    event.clearEventBitMask();
                
                    boolean status = builder.addEvent(event.getEventBuffer().array(), 0, 
                            event.getEventBufferSize());
                    if(status==false){
                        System.out.println("[HipoFrameReader] >>>>>> out of space on event # " + i + " from banch # " + eventNumber);
                    }
                }
            }
            DataFrame  frame = builder.build();
            return frame;
        } catch (Exception e) {
            throw new EventReaderException(e);
        }
    }

    @Override
    protected EngineDataType getDataType() {
        return Clas12Types.HIPOFRAME;
    }
}
