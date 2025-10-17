package org.jlab.clas.reco;

import java.util.ArrayList;
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
        for (int i=0; i<e.length; ++i) engines.add(e[i]);
    }

    /**
     * Process one event through the chain of engines.
     * @param event
     * @return 
     */
    @Override
    public final boolean processDataEvent(DataEvent event) {
        boolean ret = true;
        for (ReconstructionEngine e : engines) 
            if (!e.processDataEvent(event)) ret = false;
        return ret;
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
}
