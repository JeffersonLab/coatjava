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
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoReader;
import org.json.JSONObject;

/**
 *
 * @author baltzell
 */
public class CLAS12Reader extends AbstractEventReaderService<Object> {

    EvioSource evio;
    HipoReader hipo;
    CLASDecoder4 decoder;
    private long maxEvents;
    private Double torus;
    private Double solenoid;

    @Override
    protected Object createReader(Path path, JSONObject opts) throws EventReaderException {
        if (path.toString().endsWith(".hipo")) {
            evio = null;
            hipo = new HipoReader();
            hipo.open(path.toString());
            return hipo;
        }
        else {
            hipo = null;
            evio = new EvioSource();
            evio.open(path.toString());
            maxEvents = evio.getEventCount();
            decoder = new CLASDecoder4();
            torus = opts.has("torus") ? opts.getDouble("torus") : null;
            solenoid = opts.has("solenoid") ? opts.getDouble("solenoid") : null;
            if (opts.has("variation")) decoder.setVariation(opts.getString("variation"));
            if (opts.has("timestamp")) decoder.setTimestamp(opts.getString("timestamp"));
            return evio;
        }
    }

    @Override
    protected void closeReader() {
        if (evio==null) ((HipoReader)reader).close();
        else ((EvioSource)reader).close();
    }

    @Override
    protected int readEventCount() throws EventReaderException {
        if (evio==null) return ((HipoReader)reader).getEventCount();
        else return ((EvioSource)reader).getEventCount();
    }

    @Override
    protected Object readEvent(int eventNumber) throws EventReaderException {
        try {
            if (evio==null) {
                return ((HipoReader)reader).getEvent(new Event(),eventNumber);
            }
            else {
                if (eventNumber++ >= maxEvents) return null;
                ByteBuffer bb = ((EvioSource)reader).getEventBuffer(eventNumber, true);
                EvioDataEvent evio = new EvioDataEvent(bb.array(), readByteOrder());
                Event hipo = decoder.getDecodedEvent(evio, -1, eventNumber, torus, solenoid);
                return hipo;
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
        return Clas12Types.HIPO;
    }

}
