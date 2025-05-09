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
public class EvioToHipoDecoder extends AbstractEventReaderService<EvioSource> {

    CLASDecoder4 decoder;
    private ByteOrder byteOrder;
    private long maxEvents;
    private int runNumber=-1;
    private float torus=1;
    private float solenoid=1;
    Bank rawScaler;
    Bank rawRunConf;

    @Override
    protected EvioSource createReader(Path file, JSONObject opts) throws EventReaderException {
        EvioSource s = new EvioSource();
        s.open(file.toString());
        byteOrder = s.getFileByteOrder();
        maxEvents = s.getEventCount();
        decoder = new CLASDecoder4();
        rawScaler   = new Bank(decoder.getSchemaFactory().getSchema("RAW::scaler"));
        rawRunConf  = new Bank(decoder.getSchemaFactory().getSchema("RUN::config"));
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

    public void decodeEvent(EvioDataEvent e) {
        
    }
    
    @Override
    public Object readEvent(int eventNumber) throws EventReaderException {
        if (eventNumber >= maxEvents) return null;
        try {
            ByteBuffer bb = reader.getEventBuffer(++eventNumber, true);
            EvioDataEvent evio = new EvioDataEvent(bb.array(), byteOrder, reader.getDictionary());
            Event hipo = decoder.getDecodedEvent(evio, runNumber, eventNumber, torus, solenoid);
            for (Bank b : decoder.createReconScalerBanks(hipo)) hipo.write(b);
            Bank epics = decoder.createEpicsBank();
            if (epics != null) hipo.write(epics);
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