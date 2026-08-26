package org.jlab.calibration.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.jlab.io.base.DataEvent;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.detector.calib.utils.OccupanceTable;
import org.jlab.utils.system.ClasUtilsFile;

public class OccupanceEngine extends ReconstructionEngine {

    static final String BANKDIR = ClasUtilsFile.getResourceDir("CLAS12DIR","etc/bankdefs/hipo4/singles/occupancy");
    
    int events;
    int prescale;
    OccupanceTable[] tables;
        
    public OccupanceEngine() {
        super("Occupance", "baltzell","0.1");
    }

    @Override
    public boolean processDataEventUser(DataEvent event) {
        for (OccupanceTable t : tables) t.fill(event);
            if (++events % prescale == 0) {
                for (OccupanceTable t : tables) {
                    if (t.getTable().getRowCount() > 0)
                        event.appendBank(t.create(events, event));
                    t.reset();
                }
                events = 0;
            }
        return true;
    }

    @Override
    public boolean init() {
        prescale = Integer.parseInt(getEngineConfigString("occupancyPrescale","100"));
        try  {
            tables = Files.list(Paths.get(BANKDIR))
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .map(s -> s.substring(0, s.length()-5))
                    .map(s -> s.substring(5, s.length()))
                    .map(OccupanceTable::new)
                    .toArray(OccupanceTable[]::new);
        } catch (IOException ex) {
            System.getLogger(OccupanceEngine.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            return false;
        }
        
        return true;
    }

    @Override
    public void detectorChanged(int runNumber) {
        for (OccupanceTable table : tables) table.reset();
        events = 0;
    }

}