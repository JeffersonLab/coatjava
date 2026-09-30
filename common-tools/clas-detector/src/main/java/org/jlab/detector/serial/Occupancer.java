package org.jlab.detector.serial;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import org.jlab.detector.banks.RawBank.OrderGroups;
import org.jlab.detector.banks.RawDataBank;
import org.jlab.detector.calib.utils.OccupanceTable;
import org.jlab.io.base.DataEvent;
import org.jlab.utils.system.ClasUtilsFile;

public class Occupancer extends ArrayList<OccupanceTable> {
    
    static final String BANKDIR = ClasUtilsFile.getResourceDir("CLAS12DIR","etc/bankdefs/hipo4/singles/occupancy");

    int events;
    int prescale;

    public Occupancer(int prescale) {
        super();
        this.prescale = prescale;
    }

    public boolean process(DataEvent event) {
        forEach(t -> {
            RawDataBank b = new RawDataBank(t.getHitBank(), 1000, OrderGroups.NODENOISE);
            b.read(event);
            t.fill(b, false);
        });
        if (++events % prescale == 0) {
            forEach(t -> {
                if (t.getTable().getRowCount() > 0)
                    event.appendBank(t.create(events, event));
                t.reset();
            });
            events = 0;
        }
        return true;
    }

    public boolean init() throws IOException {
        addAll(Files.list(Paths.get(BANKDIR))
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .map(s -> s.substring(0, s.length()-5))
                .map(s -> s.substring(5, s.length()))
                .map(OccupanceTable::new).toList());
        return true;
    }

}
