package org.jlab.io.clara;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import org.jlab.clara.std.services.EventWriterException;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.IDataSet;
import org.jlab.groot.data.TDirectory;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Add a histogram output file to DecoderWriter.
 * 
 * @author baltzell
 */
public class Clas12Writer extends DecoderWriter {
    
    String histoFilename;
    ArrayList<Hister> histers = new ArrayList<>();
    
    @Override
    protected void configure(HipoWriterSorted writer, JSONObject opts) {
        super.configure(writer, opts);
        if (opts.has("histers")) {
            JSONArray a = opts.getJSONArray("histers");
            for (int i=0; i<a.length(); i++) {
                try {
                    Constructor<?> c = Class.forName(a.getString(i)).getDeclaredConstructor();
                    Hister m = (Hister)c.newInstance();
                    m.configure();
                    histers.add(m);
                } catch (NoSuchMethodException | ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    System.getLogger(DecoderWriter.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                }
            }
        }
    }
    
    @Override
    protected HipoWriterSorted createWriter(Path file, JSONObject opts) throws EventWriterException {
        HipoWriterSorted w = super.createWriter(file, opts);
        if (!histers.isEmpty()) {
            String dir = opts.optString("outDir",file.getParent().toString());
            String basename = file.getFileName().toString();
            if (basename.startsWith("rec_")) basename = basename.substring(4);
            basename = "hist_" + basename;
            histoFilename = dir + "/" + basename;
        }
        return w;
    }
    
    @Override
    protected void writeEvent(Object event) throws EventWriterException {
        super.writeEvent(event);
        for (int i=0; i<histers.size(); i++) histers.get(i).fill((Event)event);
    }
    
    
    @Override
    protected void closeWriter() {
        super.closeWriter();
        if (!histers.isEmpty()) {
            TDirectory d = new TDirectory();
            for (Hister h : histers) h.write(d);
            d.writeFile(histoFilename);
            histers.clear();
        }
    }
    
    abstract class Hister {
        abstract public void fill(Event event);
        abstract public void configure();
        protected HashMap<String,ArrayList<IDataSet>> histos;
        public void write(TDirectory dir) {
            for (HashMap.Entry<String,ArrayList<IDataSet>> e : histos.entrySet()) {
                dir.cd( e.getKey().startsWith("/") ? e.getKey() : "/"+e.getKey());
                for (IDataSet d : e.getValue()) dir.addDataSet(d);
            }
        }
        public void add(String dir, IDataSet... data) {
            if (!histos.containsKey(dir)) histos.put(dir, new ArrayList<>());
            histos.get(dir).addAll(Arrays.asList(data));
        }
        public static class ExampleHister extends Hister {
            H1F q2 = new H1F("q2","Q^{2}",100,0,5);
            H1F m2 = new H1F("m2","M^{2}",100,0,5);
            @Override
            public void fill(Event event) {
                q2.fill(0.1);
            }
            @Override
            public void configure() {
                add("/TEST/dir1", q2);
                add("/TEST/dir2", m2);
            }
        }
    }
}
