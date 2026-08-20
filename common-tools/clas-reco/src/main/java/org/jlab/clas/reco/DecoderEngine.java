package org.jlab.clas.reco;

import java.util.Set;
import java.util.HashSet;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.jlab.clara.base.ClaraUtil;
import org.jlab.clara.engine.Engine;
import org.jlab.clara.engine.EngineData;
import org.jlab.clara.engine.EngineDataType;
import org.jlab.clara.engine.EngineStatus;
import org.jlab.detector.decode.CLASDecoder;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.json.JSONObject;

/**
 *
 * @author baltzell
 */
public class DecoderEngine implements Engine {

    public static class DecoderPool {
        BlockingQueue<CLASDecoder> pool;
        int constantsShared = 64;
        public DecoderPool(int size, String variation, String timestamp) {
        	pool = new ArrayBlockingQueue<>(size);
        	CLASDecoder d0 = null;
        	for (int i=0; i<size; i++) {
                CLASDecoder d;
                if (i % constantsShared == 0) {
                    d0 = new CLASDecoder();
                    if (variation != null) d0.setVariation(variation);
                    if (timestamp != null) d0.setTimestamp(timestamp);
                    d = d0;
                }
                else d = new CLASDecoder(d0);
                pool.add(d);
            }
        }
        public CLASDecoder take() throws InterruptedException { return pool.take(); }
        public void put(CLASDecoder d) throws InterruptedException { pool.put(d); }
    }

    static final int POOL_SIZE = 64;
    static final Set<EngineDataType> ED_TYPES = ClaraUtil.buildDataTypes(
        Clas12Types.EVIO,Clas12Types.HIPO,EngineDataType.JSON,EngineDataType.STRING);

    SchemaFactory schema;
	DecoderPool pool;

    public DecoderEngine() {
        schema = new SchemaFactory();
        schema.initFromDirectory(System.getenv("CLAS12DIR") + "/etc/bankdefs/hipo4");
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() { return ED_TYPES; }
    @Override
    public Set<EngineDataType> getOutputDataTypes() { return ED_TYPES; }
    @Override
    public EngineData executeGroup(Set<EngineData> set) { return null; }
    @Override
    public Set<String> getStates() { return new HashSet<>(); }
    @Override
    public String getDescription() { return "decoder engine"; }
    @Override
    public String getVersion() { return "1.0"; }
    @Override
    public String getAuthor() { return "baltzell"; }
    @Override
    public void reset() {}
    @Override
    public void destroy() {}

    @Override
    public EngineData configure(EngineData ed) {
        JSONObject j = new JSONObject(ed.getData());
        pool = new DecoderPool(POOL_SIZE,
            j.optString("variation","default"),
            j.optString("timestamp",null));
        return ed;
    }

    @Override
    public EngineData execute(EngineData input) {

        EngineData output = input;
       
        // if it's EVIO, decode it, otherwise just pass it along
        if (input.getMimeType().equals("binary/data-evio")) {
            EvioDataEvent evio;
            try {
                ByteBuffer bb = (ByteBuffer) input.getData();
                //evio = new EvioDataEvent(bb.array(), bb.order());
                evio = new EvioDataEvent(bb.array(), ByteOrder.LITTLE_ENDIAN);
            } catch (Exception e) {
                String msg = String.format("Error reading input event%n%n%s", ClaraUtil.reportException(e));
                output.setStatus(EngineStatus.ERROR);
                output.setDescription(msg);
                return output;
            }
            HipoDataEvent hipo;
            try {
                CLASDecoder d = pool.take();
                hipo = new HipoDataEvent(d.getDecodedEvent(evio),schema);
                pool.put(d);
                output.setData("binary/data-hipo", hipo.getHipoEvent());
            } catch (Exception e) {
                String msg = String.format("Error processing input event%n%n%s", ClaraUtil.reportException(e));
                output.setStatus(EngineStatus.ERROR);
                output.setDescription(msg);
                return output;
            }
        }

        return output;
    }
}
