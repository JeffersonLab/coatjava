package org.jlab.io.clara.histo;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import org.jlab.clara.std.services.EventWriterException;
import org.jlab.groot.data.TDirectory;
import org.jlab.io.clara.HipoToHipoWriter;
import org.jlab.jnp.hipo4.data.Event;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.json.JSONArray;
import org.json.JSONObject;
//import org.jlab.io.clara.histo.Hister.Banker;

public class HistoWriter extends HipoToHipoWriter {

    static final String CONF_OUTDIR = "outDir";
    static final String CONF_HISTERS = "histers";
    //static final String CONF_BANKERS = "bankers";

    //Banker banker = new Banker();
    ArrayList<Hister> histers = new ArrayList<>();
    SchemaFactory schemaFactory = new SchemaFactory();
    String filename = "";

    void add(String name) {
        try {
            Constructor<?> c = Class.forName(name).getDeclaredConstructor();
            Hister m = (Hister)c.newInstance();
            m.configure();
            histers.add(m);
        } catch (NoSuchMethodException | ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            System.getLogger(HistoWriter.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }

    @Override
    protected void configure(HipoWriterSorted writer, JSONObject opts) {
        super.configure(writer,opts);
        if (opts.has(CONF_HISTERS)) {
            JSONArray a = opts.getJSONArray(CONF_HISTERS);
            for (int i=0; i<a.length(); i++)
                add(a.getString(i));
        }
        //if (opts.has(CONF_BANKERS)) {
        //    banker.add(opts.getJSONArray(CONF_BANKERS));
        //}
    }

    @Override
    protected HipoWriterSorted createWriter(Path file, JSONObject opts) throws EventWriterException {
        String dir = opts.optString(CONF_OUTDIR,file.getParent().toString());
        String basename = file.getFileName().toString();
        if (basename.startsWith("rec_")) basename = basename.substring(4);
        basename = "hist_" + basename;
        filename = dir + "/" + basename;
        schemaFactory.initFromDirectory(HipoToHipoWriter.getSchemaDir(opts));
        for (Hister m : histers) m.setSchemaFactory(schemaFactory);
        return super.createWriter(file, opts);
    }

    @Override
    protected void writeEvent(Object event) throws EventWriterException {
        super.writeEvent(event);
        for (int i=0; i<histers.size(); i++)
            histers.get(i).fill((Event)event);
    }

    @Override
    protected void closeWriter() {
        super.closeWriter();
        TDirectory tdir = new TDirectory();
        for (Hister h : histers) h.write(tdir);
        tdir.writeFile(filename);
        histers.clear();
    }

}
