package org.jlab.clas.reco;

import java.util.Set;
import java.util.HashSet;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jlab.clara.base.ClaraUtil;
import org.jlab.clara.engine.Engine;
import org.jlab.clara.engine.EngineData;
import org.jlab.clara.engine.EngineDataType;
import org.jlab.clara.engine.EngineStatus;
import org.jlab.detector.decode.CLASDecoder;
import org.jlab.detector.decode.CLASDecoderPool;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.json.JSONObject;

/**
 *
 * @author baltzell
 */
public class DecoderEngine implements Engine {

    static final int POOL_SIZE = 64;
    static final Set<EngineDataType> ED_TYPES = ClaraUtil.buildDataTypes(
        Clas12Types.EVIO,Clas12Types.HIPO,EngineDataType.JSON,EngineDataType.STRING);

    SchemaFactory schema;
    CLASDecoderPool pool;

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
        JSONObject json = new JSONObject(ed.getData());
        pool = new CLASDecoderPool(POOL_SIZE,
                json.optString("variation","default"),
                json.optString("timestamp",null));
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
