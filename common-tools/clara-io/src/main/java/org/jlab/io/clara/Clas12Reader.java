package org.jlab.io.clara;

import java.nio.ByteOrder;
import java.nio.file.Path;
import org.jlab.clara.engine.EngineDataType;
import org.jlab.clara.std.services.AbstractEventReaderService;
import org.jlab.clara.std.services.EventReaderException;
import org.jlab.coda.jevio.EvioException;
import org.jlab.io.evio.EvioSource;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.json.JSONObject;

/**
 * Just reads EVIO or HIPO, and passes it along.
 * 
 * @author baltzell
 */
public class Clas12Reader extends AbstractEventReaderService<Object> {

    EngineDataType type = Clas12Types.HIPO;
    private long maxEvents;

    @Override
    protected Object createReader(Path path, JSONObject opts) throws EventReaderException {
        if (path.toString().endsWith(".hipo")) {
            type = Clas12Types.HIPO;
            HipoReader r = new HipoReader();
            r.open(path.toString());
            return r;
        }
        else {
            type = Clas12Types.EVIO;
            EvioSource r = new EvioSource();
            r.open(path.toString());
            maxEvents = r.getEventCount();
            return r;
        }
    }

    @Override
    protected void closeReader() {
        if (type == Clas12Types.EVIO) {
            ((EvioSource)reader).close();
        }
        else {
            ((HipoReader)reader).close();
        }
    }

    @Override
    protected int readEventCount() throws EventReaderException {
        if (type == Clas12Types.EVIO) {
            return ((EvioSource)reader).getEventCount();
        }
        else {
            return ((HipoReader)reader).getEventCount();
        }
    }

    @Override
    protected Object readEvent(int eventNumber) throws EventReaderException {
        try {
            if (type == Clas12Types.EVIO) {
                if (eventNumber++ >= maxEvents) return null;
                return ((EvioSource)reader).getEventBuffer(eventNumber, true);
                //return new EvioDataEvent(b.array(), readByteOrder());
            }
            else {
                return ((HipoReader)reader).getEvent(new Event(),eventNumber);
            }
        } catch (EvioException e) {
            throw new EventReaderException(e);
        }
    }

    @Override
    public ByteOrder readByteOrder() throws EventReaderException {
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Override
    protected EngineDataType getDataType() {
        return type;
    }
}
