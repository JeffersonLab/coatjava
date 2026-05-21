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
import org.json.JSONObject;

/**
 * Combined with Clas12Writer, a port of the standard "decoder" to CLARA.
 * 
 * 1. Convert EVIO to HIPO
 * 2. CCDB translation tables, c/s/c -> s/l/c/o
 * 3. Pulse extraction, e.g., Mode-1 FADC250
 * 
 * @author baltzell
 */
public class DecoderReader extends AbstractEventReaderService<EvioSource> {

    CLASDecoder4 decoder;
    private long maxEvents;
    private Double torus;
    private Double solenoid;

    @Override
    protected EvioSource createReader(Path file, JSONObject opts) throws EventReaderException {
        EvioSource s = new EvioSource();
        s.open(file.toString());
        maxEvents = s.getEventCount();
        decoder = new CLASDecoder4();
        torus = opts.has("torus") ? opts.getDouble("torus") : null;
        solenoid = opts.has("solenoid") ? opts.getDouble("solenoid") : null;
        if (opts.has("variation")) decoder.setVariation(opts.getString("variation"));
        if (opts.has("timestamp")) decoder.setTimestamp(opts.getString("timestamp"));
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
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Override
    public Object readEvent(int eventNumber) throws EventReaderException {
        if (eventNumber++ >= maxEvents) return null;
        try {
            ByteBuffer bb = reader.getEventBuffer(eventNumber, true);
            EvioDataEvent evio = new EvioDataEvent(bb.array(), readByteOrder());
            Event hipo = decoder.getDecodedEvent(evio, -1, eventNumber, torus, solenoid);
            // FIXME:  IIRC, this was added to (try to) address a memory leak in
            // CompactEvioReader, but it was ineffective and could/should be removed.  
            if (eventNumber % 25000 == 0) System.gc();
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
