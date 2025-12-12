package org.jlab.clas.reco;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.IDataSet;
import org.jlab.groot.data.TDirectory;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author baltzell
 */
public abstract class HistoEngine extends ReconstructionEngine {

    Hister hister;

    public HistoEngine(String name, String author, String version) {
        super(name, author, version);
    }

    public abstract Hister createHister();

    public Hister createDefaultHister() {
        return new Hister() {
            @Override
            public void configure() {};
            @Override
            public void fill(Object event) {};
        };
    }

    @Override
    public boolean processDataEvent(DataEvent event) {
        hister.fill(event);
        return true;
    }

    @Override
    public boolean init() {
        hister = createHister();
        return true;
    }

    public static abstract class Hister {

        abstract public void configure();
        abstract public void fill(Object event);

        protected HashMap<String,ArrayList<IDataSet>> histos = new HashMap<>();

        public void write(TDirectory dir) {
            for (HashMap.Entry<String,ArrayList<IDataSet>> e : histos.entrySet()) {
                String path = e.getKey().startsWith("/") ? e.getKey() : "/"+e.getKey();
                dir.mkdir(path);
                dir.cd(path);
                for (IDataSet d : e.getValue()) dir.addDataSet(d);
            }
        }

        public void add(String dir, IDataSet... data) {
            if (!histos.containsKey(dir)) histos.put(dir, new ArrayList<>());
            histos.get(dir).addAll(Arrays.asList(data));
        }

    }
 
    public static class Example extends Hister {
        H1F q2 = new H1F("q2","Q^{2}",100,0,5);
        @Override
        public void fill(Object event) { q2.fill(0.1); }
        @Override
        public void configure() { add("/TEST/dir1", q2); }
    }
}
