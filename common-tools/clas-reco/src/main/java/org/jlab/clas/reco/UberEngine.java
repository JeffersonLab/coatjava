package org.jlab.clas.reco;

import java.util.ArrayList;
import org.jlab.clara.engine.EngineData;
import org.jlab.io.base.DataEvent;

/**
 * A ReconstructionEngine that is a chain of ReconstructionEngines.
 * 
 * @author baltzell
 */
public abstract class UberEngine extends ReconstructionEngine {

    private final ArrayList<ReconstructionEngine> engines = new ArrayList<>();

    public UberEngine(String name, String author, String version) {
        super(name,author,version);
    }

    /**
     * Add engines to the chain. 
     * @param e
     */
    protected void add(ReconstructionEngine... e) {
        for (int i=0; i<e.length; ++i) {
            if (UberEngine.class.isInstance(e[i])) 
                throw new RuntimeException("UberEngine cannot contain an UberEngine.");
            engines.add(e[i]);
        }
    }

    @Override
    public boolean processDataEventUser(DataEvent event) {
        throw new RuntimeException("UberEngine does not implement processDataEvent.");
    }

    /**
     * Process one event through the chain of engines.
     * @param event 
     */
    @Override
    public final void processDataEvent(DataEvent event) {
        for (ReconstructionEngine e : engines) 
            e.processDataEvent(event);
    }

    /**
     * Run all engines' init methods.
     * @return
     */ 
    @Override
    public final boolean init() {
        boolean ret = true;
        for (ReconstructionEngine e : engines)
            if (!e.init()) ret = false;
        return ret;
    }

    /**
     * Run all engines' dettectorChanged methods.
     * @param runNumber
     */ 
    @Override
    public final void detectorChanged(int runNumber) {
        for (ReconstructionEngine e : engines)
            e.detectorChanged(runNumber);
    }
   
    /**
     * Run all engines' configure methods.
     * @param ed
     * @return 
     */
    @Override
    public EngineData configure(EngineData ed) {
        for (ReconstructionEngine e : engines) { 
            e.configure(ed);
        }
        return ed;
    }

    /**
     * Run all engines' execute methods.
     * @param ed
     * @return 
     */
    @Override
    public EngineData execute(EngineData ed) {
        for (ReconstructionEngine e : engines) 
            ed = e.execute(ed);
        return ed;
    }
}
