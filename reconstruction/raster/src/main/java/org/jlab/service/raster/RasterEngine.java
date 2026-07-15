package org.jlab.service.raster;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataEvent;
import org.jlab.io.base.DataBank;
import org.jlab.utils.groups.IndexedTable;

/*
 * Raster reconstruction engine:
 * converts the ADC values recorded for the raster signals 
 * into XY beam positions
 * 
 * @author devita, pilleux
 */

public class RasterEngine extends ReconstructionEngine {

    private volatile int nErrors = 0;

    private final double udfPos = -999;
    private final int    xComponent = 1;
    private final int    yComponent = 2;

    public static final Logger LOGGER = Logger.getLogger(RasterEngine.class.getName());

    public RasterEngine() {
        super("RasterEngine","devita","1.0");
    }

    @Override
    public boolean init() {
        
        // register list of CCDB tables the engine will access
        List<String> tableNames = new ArrayList<>();
        tableNames.add("/calibration/raster/adc_to_position");
        this.requireConstants(tableNames);
        
        //remove raster bank in case it existed previously
        this.registerOutputBank("RASTER::position");
        
        System.out.println("["+this.getName()+"] --> raster is ready....");
        return true;
    }

    @Override
    public void detectorChanged(int runNumber) {}
    
    @Override
    public boolean processDataEventUser(DataEvent event) {
               
        // Read run number from RUN::config bank
        int run=-1;
        if (event.hasBank("RUN::config")) {
            run = event.getBank("RUN::config").getInt("run",0);
        }
        
        IndexedTable adc2position = this.getConstantsManager().getConstants(run, "/calibration/raster/adc_to_position");
        
        // check if input bank exist, otherwise do nothing
        if(!event.hasBank("RASTER::adc")) {
            return true;
        }
        
        
        // check if input bank has two rows, otherwise give warning
        DataBank adcBank = event.getBank("RASTER::adc");
        if(adcBank.rows()!=2) {
            if (10 > ++nErrors)
                LOGGER.log(Level.WARNING,"RasterEngine:  RASTER::adc bank has incorrect number of rows, skipping event.");
            return false;
        }
        
        // get calibration table
        double xpos = udfPos;
        double ypos = udfPos;
        for(int i=0; i<adcBank.rows(); i++) {
            int component = adcBank.getShort("component", i);
            int adc       = adcBank.getInt("ped", i);
            if(component == xComponent) xpos = this.convertADC(adc2position, component, adc);
            if(component == yComponent) ypos = this.convertADC(adc2position, component, adc);
        }
        
        // check that both x and y are now defined
        if(xpos == udfPos || ypos == udfPos) {
            LOGGER.log(Level.WARNING,"RasterEngine:  missing entry in RASTER::adc bank, skipping event.");
            return false;            
        }
            
        DataBank outputBank = event.createBank("RASTER::position", 1);
        outputBank.setFloat("x", 0, (float) xpos);
        outputBank.setFloat("y", 0, (float) ypos);
        event.appendBank(outputBank);
        
        return true;
    }

    private double convertADC(IndexedTable adc2pos, int component, int ADC) {
        double pos = adc2pos.getDoubleValue("p0", 0, 0, component)+
                     adc2pos.getDoubleValue("p1", 0, 0, component)*ADC;
        return pos;
    }
    
}
