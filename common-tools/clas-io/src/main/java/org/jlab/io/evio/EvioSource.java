package org.jlab.io.evio;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jlab.coda.jevio.EvioCompactReader;
import org.jlab.coda.jevio.EvioException;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataEventList;
import org.jlab.io.base.DataSource;
import org.jlab.io.base.DataSourceType;

public final class EvioSource implements DataSource {

    static final Logger LOGGER = Logger.getLogger(EvioSource.class.getName());
    private ByteOrder storeByteOrder = ByteOrder.BIG_ENDIAN;
    private EvioCompactReader evioReader = null;
    private int currentEvent;
    private int currentFileEntries;

    @Override
    public void close() {
        evioReader.close();
    }
    public int getEventCount() {
        return evioReader.getEventCount();
    }
    public ByteOrder getFileByteOrder() {
        return evioReader.getFileByteOrder();
    }
    public ByteBuffer getEventBuffer(int eventNumber, boolean asdf) throws EvioException {
        return evioReader.getEventBuffer(eventNumber, asdf);
    }
    
    public EvioSource() {}

    public EvioSource(String filename) {
        this.open(filename);
    }

    @Override
    public void open(File file) {
        this.open(file.getAbsolutePath());
    }

    @Override
    public void open(String filename) {
        try {
            evioReader = new EvioCompactReader(new File(filename));
            currentEvent = 1;
            currentFileEntries = evioReader.getEventCount();
            storeByteOrder = evioReader.getFileByteOrder();
            LOGGER.log(Level.INFO, "****** opened FILE [] ** NEVENTS = {0} *******", currentFileEntries);
        } catch (EvioException ex) {
            Logger.getLogger(EvioSource.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(EvioSource.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void open(ByteBuffer buff) {
        try {
            evioReader = new EvioCompactReader(buff);
            currentEvent = 1;
            currentFileEntries = evioReader.getEventCount()+1;
            storeByteOrder = evioReader.getFileByteOrder();
        } catch (EvioException ex) {
            Logger.getLogger(EvioSource.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public int getSize() {
        return currentFileEntries;
    }

    @Override
    public DataEventList getEventList(int start, int stop) {
        return null;
    }

    @Override
    public DataEventList getEventList(int nrecords) {
        return null;
    }

    @Override
    public void reset() {
        currentEvent = 1;
    }

    @Override
    public int getCurrentIndex() {
        return currentEvent;
    }

    @Override
    public DataEvent getPreviousEvent() {
        if (currentEvent > currentFileEntries || currentEvent == 2)
            return null;
        try {
            currentEvent--;
            currentEvent--;
            ByteBuffer evioBuffer = evioReader.getEventBuffer(currentEvent, true);
            EvioDataEvent event = new EvioDataEvent(evioBuffer.array(), storeByteOrder);
            currentEvent++;
            return event;
        } catch (EvioException ex) {
            Logger.getLogger(EvioSource.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public DataEvent gotoEvent(int index) {
        if (index <= 1 || index > currentFileEntries)
            return null;
        try {
            ByteBuffer evioBuffer = evioReader.getEventBuffer(index, true);
            EvioDataEvent event = new EvioDataEvent(evioBuffer.array(), storeByteOrder);
            currentEvent = index + 1;
            return event;
        } catch (EvioException ex) {
            Logger.getLogger(EvioSource.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public EvioDataEventHandler getNextEventHandler() {
        if (currentEvent > currentFileEntries)
            return null;
        try {
            ByteBuffer evioBuffer = evioReader.getEventBuffer(currentEvent, true);
            EvioDataEventHandler event = new EvioDataEventHandler(evioBuffer.array(), storeByteOrder);
            currentEvent++;
            return event;
        } catch (EvioException ex) {
            Logger.getLogger(EvioSource.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public DataEvent getNextEvent() {
        if (currentEvent > currentFileEntries)
            return null;
        try {
            ByteBuffer evioBuffer = evioReader.getEventBuffer(currentEvent, true);
            EvioDataEvent event = new EvioDataEvent(evioBuffer.array(), storeByteOrder);
            currentEvent++;
            return event;
        } catch (EvioException ex) {
            Logger.getLogger(EvioSource.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public boolean hasEvent() {
        return currentEvent <= currentFileEntries;
    }

    public static void main(String[] args) {

    }

    @Override
    public DataSourceType getType() {
        return DataSourceType.FILE;
    }

    @Override
    public void waitForEvents() {
    }
}
