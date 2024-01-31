package org.jlab.io.hipo;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventList;
import org.jlab.io.base.DataEventType;
import org.jlab.io.base.DataSource;
import org.jlab.io.base.DataSourceType;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoReader;

/**
 *
 * @author gavalian
 */
public class HipoDataSource implements DataSource {

    public static final Logger LOGGER = Logger.getLogger(HipoDataSource.class.getName());

    HipoReader reader = null;
    int currentEventNumber = 0;
    
    public HipoDataSource(){
        this.reader = new HipoReader();
    }
    
    @Override
    public boolean hasEvent() {
        return reader.hasNext();
    }

    @Override
    public void open(File file) {
        this.open(file.getAbsolutePath());
    }

    /**
     * Creates a Writer class with Dictionary from the Reader.
     * This method should be used when filtering the input file
     * to ensure consistency of dictionaries and banks in the output.
     * @return HipoDataSync object for writing an output.
     */
    public HipoDataSync createWriter(){
        SchemaFactory factory = reader.getSchemaFactory();
        HipoDataSync   writer = new HipoDataSync(factory);
        return writer;
    }
    
    @Override
    public void open(String filename) {
        this.currentEventNumber = 0;
        this.reader.open(filename);
        LOGGER.log(Level.INFO,"[DataSourceDump] --> opened file with events # " );
    }

    public void open(ByteBuffer buff) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void close() {
        
    }

    public  HipoReader getReader(){ return reader;}
    @Override
    public int getSize() {
        return reader.getEventCount();
    }

    @Override
    public DataEventList getEventList(int start, int stop) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public DataEventList getEventList(int nrecords) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public DataEvent getNextEvent() {
        Event event = new Event();
        reader.nextEvent(event);
        HipoDataEvent  hipoEvent = new HipoDataEvent(event,reader.getSchemaFactory());
        if(reader.hasNext()==true){
            hipoEvent.setType(DataEventType.EVENT_ACCUMULATE);
        } else {
            hipoEvent.setType(DataEventType.EVENT_STOP);
        }
        this.currentEventNumber++;
        return hipoEvent;
    }

    @Override
    public DataEvent getPreviousEvent() {
        return null;
    }

    @Override
    public DataEvent gotoEvent(int index) {
        Event event = new Event();
        reader.getEvent(event, index);
        HipoDataEvent  hipoEvent = new HipoDataEvent(event,reader.getSchemaFactory());
        if(reader.hasNext()==true){
            hipoEvent.setType(DataEventType.EVENT_ACCUMULATE);
        } else {
            hipoEvent.setType(DataEventType.EVENT_STOP);
        }
        return hipoEvent;       
    }
    
    @Override
    public void reset() {
        this.currentEventNumber = 0;
    }

    @Override
    public int getCurrentIndex() {
        return this.currentEventNumber;
    }
        
    public static void main(String[] args){
        HipoDataSource reader = new HipoDataSource();
        reader.open("test_hipoio.hipo");
        int counter = 0;
        while(reader.hasEvent()==true){
            DataEvent  event = reader.getNextEvent();
            System.out.println("EVENT # " + counter);
            event.show();
            counter++;
        }
    }

    @Override
    public DataSourceType getType() {
        return DataSourceType.FILE;        
    }

    @Override
    public void waitForEvents() {
        
    }
}
