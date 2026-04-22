package org.jlab.clas.reco;

import java.util.Set;
import java.util.HashSet;
import java.nio.ByteBuffer;
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
public class DecodingEngine implements Engine {

    SchemaFactory schema;
    CLASDecoder decoder;

    public DecodingEngine() {
        schema = new SchemaFactory();
        schema.initFromDirectory(System.getenv("CLAS12DIR") + "/etc/bankdefs/hipo4");
    }
   
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
    public Set<EngineDataType> getInputDataTypes() {
        return ClaraUtil.buildDataTypes(Clas12Types.EVIO);
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return ClaraUtil.buildDataTypes(Clas12Types.HIPO);
    }

    @Override
    public EngineData configure(EngineData ed) {
        JSONObject j = new JSONObject(ed.getData());
        if (j.has("variation")) decoder.setVariation(j.getString("variation"));
        if (j.has("timestamp")) decoder.setVariation(j.getString("timestamp"));
        return ed;
    }

    @Override
    public EngineData execute(EngineData input) {
        EngineData output = input;
        EvioDataEvent evio;
        HipoDataEvent hipo;
        try {
            ByteBuffer bb = (ByteBuffer) input.getData();
            evio = new EvioDataEvent(bb.array(), bb.order());
        } catch (Exception e) {
            String msg = String.format("Error reading input event%n%n%s", ClaraUtil.reportException(e));
            output.setStatus(EngineStatus.ERROR);
            output.setDescription(msg);
            return output;
        }
        
        try {
            hipo = new HipoDataEvent(decoder.getDecodedEvent(evio),schema);
            output.setData("binary/data-hipo", hipo.getHipoEvent());
        } catch (Exception e) {
            String msg = String.format("Error processing input event%n%n%s", ClaraUtil.reportException(e));
            output.setStatus(EngineStatus.ERROR);
            output.setDescription(msg);
            return output;
        }
        return output;
    }

}
