package org.jlab.io.utils;

import java.io.File;
import java.nio.file.Files;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.io.hipo.HipoDataSync;
import org.jlab.jnp.hipo4.data.SchemaFactory;
import org.jlab.utils.options.OptionParser;
import org.jlab.utils.system.ClasUtilsFile;

public class DstMaker {
   
    public static void main(String args[]) {

        OptionParser opt = new OptionParser("dst-maker");
        opt.addOption("-s","dst","schema path, or stock schema name (default=dst)");
        opt.addRequired("-o","output file");
        opt.setRequiresInputList(true);
        opt.parse(args);

        HipoDataSync w = new HipoDataSync();
        w.setCompressionType(2);

        SchemaFactory schema = new SchemaFactory();
        String stock = ClasUtilsFile.getResourceDir("CLAS12DIR","etc/bankdefs/hipo4/singles");
        String user = opt.getOption("-s").stringValue();
        if (Files.isDirectory((new File(stock+"/"+user)).toPath())) {
            System.out.println("Assuming -s is a stock schema:  "+user);
            schema.initFromDirectory(stock+"/"+user);
        }
        else if (Files.isDirectory((new File(user)).toPath())) {
            System.out.println("Assuming -s is a schema path:  "+user);
            schema.initFromDirectory(user);
        }
        else {
            System.err.println("Unable to initialize schema from -s "+user);
            System.exit(2);
        }
        w.open(opt.getOption("-o").stringValue());

        for (String input : opt.getInputList()) {
            HipoDataSource r = new HipoDataSource();
            r.open(input);
            while (r.hasEvent()) {
                DataEvent e = r.getNextEvent();
                for (String name : e.getBankList()) {
                    if (!schema.hasSchema(name)) {
                        e.removeBank(name);
                    }
                }
                w.writeEvent(e);
            }
            r.close();
        }
        w.close();
    }
}
