package org.jlab.io.clara;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import org.jlab.clara.std.services.EventWriterException;
import org.jlab.groot.data.TDirectory;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.jlab.analysis.clara.Hister;
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
    
    private void init(JSONObject opts) {
        if (opts.has("histers")) {
            JSONArray a = opts.getJSONArray("histers");
            for (int i=0; i<a.length(); i++) {
                try {
                    Constructor<?> c = Class.forName(a.getString(i)).getDeclaredConstructor();
                    Hister m = (Hister)c.newInstance();
                    m.configure();
                    histers.add(m);
                    System.out.println("INFO Clas12Writer - created hister:  "+a.getString(i));
                } catch (NoSuchMethodException | ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    System.getLogger(DecoderWriter.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                }
            }
        }
    }
    
    @Override
    protected HipoWriterSorted createWriter(Path file, JSONObject opts) throws EventWriterException {
        HipoWriterSorted w = super.createWriter(file,opts);
        init(opts);
        if (!histers.isEmpty()) {
            String dirname = file.getParent().toString();
            String basename = file.getFileName().toString();
            if (basename.startsWith("rec_")) basename = basename.substring(4);
            basename = "hist_" + basename;
            if (!basename.endsWith(".hipo")) basename += ".hipo";
            histoFilename = dirname + "/" + basename;
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
}
