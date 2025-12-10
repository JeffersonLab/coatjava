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
public class Clas12Reader extends AbstractEventReaderService<Object> {

    boolean evio;
    CLASDecoder4 decoder;
    private long maxEvents;
    private Double torus;
    private Double solenoid;

    @Override
    protected Object createReader(Path path, JSONObject opts) throws EventReaderException {
        if (path.toString().endsWith(".hipo")) {
            evio = false;
            HipoReader r = new HipoReader();
            r.open(path.toString());
            return r;
        }
        else {
            evio = true;
            EvioSource r = new EvioSource();
            r.open(path.toString());
            maxEvents = r.getEventCount();
            decoder = new CLASDecoder4();
            torus = opts.has("torus") ? opts.getDouble("torus") : null;
            solenoid = opts.has("solenoid") ? opts.getDouble("solenoid") : null;
            if (opts.has("variation")) decoder.setVariation(opts.getString("variation"));
            if (opts.has("timestamp")) decoder.setTimestamp(opts.getString("timestamp"));
            return r;
        }
    }

    @Override
    protected void closeReader() {
        if (evio) ((EvioSource)reader).close();
        else ((HipoReader)reader).close();
    }

    @Override
    protected int readEventCount() throws EventReaderException {
        if (evio) return ((EvioSource)reader).getEventCount();
        else return ((HipoReader)reader).getEventCount();
    }

    @Override
    protected Object readEvent(int eventNumber) throws EventReaderException {
        try {
            if (evio) {
                if (eventNumber++ >= maxEvents) return null;
                ByteBuffer b = ((EvioSource)reader).getEventBuffer(eventNumber, true);
                EvioDataEvent e = new EvioDataEvent(b.array(), readByteOrder());
                return decoder.getDecodedEvent(e, -1, eventNumber, torus, solenoid);
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
        return Clas12Types.HIPO;
    }

}
