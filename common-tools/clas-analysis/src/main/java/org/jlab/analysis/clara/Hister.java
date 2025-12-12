package org.jlab.analysis.clara;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.IDataSet;
import org.jlab.groot.data.TDirectory;
import org.jlab.jnp.hipo4.data.Event;

/**
 *
 * @author baltzell
 */
public abstract class Hister {

    abstract public void configure();
    abstract public void fill(Event event);

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

    public static class Example extends Hister {
        H1F q2 = new H1F("q2","Q^{2}",100,0,5);
        @Override
        public void fill(Event event) { q2.fill(0.1); }
        @Override
        public void configure() { add("/TEST/dir1", q2); }
    }

}
