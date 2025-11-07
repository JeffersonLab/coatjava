package org.jlab.io.clara.evio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;

import org.jlab.clara.engine.EngineDataType;
import org.jlab.clara.std.services.AbstractEventReaderService;
import org.jlab.clara.std.services.EventReaderException;
import org.jlab.coda.jevio.EvioException;
import org.jlab.io.clara.Clas12Types;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioSource;
import org.json.JSONObject;

/**
 * Converter service that converts EvIO persistent data to EvIO transient data
 * (i.e. Reads EvIO events from an input file)
 */
public class EvioToEvioSource extends AbstractEventReaderService<EvioSource> {

    private ByteOrder byteOrder;
    private long maxEvents;

    @Override
    protected EvioSource createReader(Path file, JSONObject opts) throws EventReaderException {
        EvioSource s = new EvioSource();
        s.open(file.toString());
        byteOrder = s.getFileByteOrder();
        maxEvents = s.getEventCount();
        return s;
    }

    @Override
    protected void closeReader() {
        reader.close();
    }

    @Override
    public int readEventCount() throws EventReaderException {
        return reader.getEventCount();
    }

    @Override
    public ByteOrder readByteOrder() throws EventReaderException {
        return reader.getFileByteOrder();
    }

    @Override
    public Object readEvent(int eventNumber) throws EventReaderException {
        if (eventNumber >= maxEvents) return null;
        try {
            ByteBuffer bb = reader.getEventBuffer(++eventNumber, true);
            EvioDataEvent event = new EvioDataEvent(bb.array(), byteOrder);
            return event;
        } catch (EvioException e) {
            throw new EventReaderException(e);
        }
    }

    @Override
    protected EngineDataType getDataType() {
        return Clas12Types.EVIO;
    }
}
