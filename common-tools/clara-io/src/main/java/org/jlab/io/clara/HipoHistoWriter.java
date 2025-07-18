package org.jlab.io.clara;

import java.nio.file.Path;
import java.util.Arrays;
import org.jlab.clara.std.services.EventWriterException;
import org.jlab.jnp.hipo4.io.HipoWriterSorted;
import org.json.JSONObject;

public abstract class HipoHistoWriter extends HipoToHipoWriter {

    String basename;
    abstract void init(JSONObject opts);
    abstract void save();

    @Override
    protected HipoWriterSorted createWriter(Path file, JSONObject opts) throws EventWriterException {
        //basename = Arrays.asList(file.toString().split("/")).getLast();
        if (basename.endsWith(".hipo"))
            basename = basename.substring(0,basename.length()-5);
        return super.createWriter(file,opts);
    }

    @Override
    protected void closeWriter() {
        save();
        super.closeWriter();
    }

    @Override
    protected void configure(HipoWriterSorted writer, JSONObject opts) {
        init(opts);
        super.configure(writer,opts);
    }
}
