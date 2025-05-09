package org.jlab.io.clara;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import org.jlab.clara.engine.EngineDataType;
import org.jlab.clara.std.services.AbstractEventReaderService;
import org.jlab.clara.std.services.EventReaderException;
import org.jlab.coda.jevio.EvioException;
import org.jlab.detector.decode.CLASDecoder4;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.evio.EvioSource;
import org.jlab.jnp.hipo4.data.Bank;
import org.jlab.jnp.hipo4.data.Event;
import org.json.JSONObject;

/**
 *
 * @author baltzell
 */
public class EvioToHipoReader extends AbstractEventReaderService<EvioSource> {

    boolean collectGarbage = true; // for memory leak in CompactEvioReader

    CLASDecoder4 decoder;
    private ByteOrder byteOrder;
    private long maxEvents;

    @Override
    protected EvioSource createReader(Path file, JSONObject opts) throws EventReaderException {
        EvioSource s = new EvioSource();
        s.open(file.toString());
        byteOrder = s.getFileByteOrder();
        maxEvents = s.getEventCount();
        decoder = new CLASDecoder4();
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
            EvioDataEvent evio = new EvioDataEvent(bb.array(), byteOrder, reader.getDictionary());
            Event hipo = decoder.getDecodedEvent(evio, -1, eventNumber, -1, -1);
            if (eventNumber % 25000 == 0 && collectGarbage) System.gc();
            return hipo;
        } catch (EvioException e) {
            throw new EventReaderException(e);
        }
    }

    @Override
    protected EngineDataType getDataType() {
        return Clas12Types.HIPO;
    }
}
