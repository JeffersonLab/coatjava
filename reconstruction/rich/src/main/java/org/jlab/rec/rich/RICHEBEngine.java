package org.jlab.rec.rich;

import java.util.Optional;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataEvent;
import org.jlab.detector.geom.RICH.RICHGeoFactory;

public class RICHEBEngine extends ReconstructionEngine {
    
    private int Ncalls = 0;
    private RICHGeoFactory richgeo;
    
    public RICHEBEngine() {
        super("RICHEB", "mcontalb", "3.0");
    }
    
    @Override
    public boolean init() {
        
        String[] richTables = new String[]{
            "/geometry/rich/setup",
            "/geometry/rich/geo_parameter",
            "/geometry/rich/module1/aerogel",
            "/geometry/rich/module2/aerogel",
            "/geometry/rich/module1/alignment",
            "/geometry/rich/module2/alignment",
            "/calibration/rich/reco_flag",
            "/calibration/rich/reco_parameter",
            "/calibration/rich/module1/time_walk",
            "/calibration/rich/module1/time_offset",
            "/calibration/rich/module1/cherenkov_angle",
            "/calibration/rich/module1/mapmt_pixel",
            "/calibration/rich/module1/status_mirror",
            "/calibration/rich/module1/status_aerogel",
            "/calibration/rich/module1/status_mapmt",
            "/calibration/rich/module2/time_walk",
            "/calibration/rich/module2/time_offset",
            "/calibration/rich/module2/cherenkov_angle",
            "/calibration/rich/module2/mapmt_pixel",
            "/calibration/rich/module2/status_mirror",
            "/calibration/rich/module2/status_aerogel",
            "/calibration/rich/module2/status_mapmt"
        };
        
        requireConstants(richTables);
        
        String v = Optional.ofNullable(this.getEngineConfigString("variation")).orElse("default");
        this.getConstantsManager().setVariation(v);
        
        return true;
    }
    
    @Override
    public void detectorChanged(int runNumber) {
        richgeo = new RICHGeoFactory(1, this.getConstantsManager(), runNumber, false);
    }
    
    @Override
    public boolean processDataEventUser(DataEvent event) {
        
        // create instances of all event-dependent classes in processDataEventUser to avoid interferences between different threads when running in clara
        RICHEvent              richevent = new RICHEvent();
        RICHio                 richio    = new RICHio();
        RICHCalibration        richcal   = new RICHCalibration();
        RICHParameters         richpar   = new RICHParameters();
        RICHPMTReconstruction  rpmt      = new RICHPMTReconstruction(richevent, richgeo, richio);
        RICHEventBuilder       reb       = new RICHEventBuilder(event, richevent, richgeo, richio);
        RICHRayTrace           richtrace = new RICHRayTrace(richgeo, richpar);
        
        //  Initialize the CCDB information
        int run = richevent.get_RunID();
        if(run>0){
            richpar.load_CCDB(this.getConstantsManager(), run, Ncalls, false);
            richcal.load_CCDB(this.getConstantsManager(), run, Ncalls, richgeo, richpar);
        }else{
            richpar.load_CCDB(this.getConstantsManager(),  11, Ncalls, false);
            richcal.load_CCDB(this.getConstantsManager(),  11, Ncalls, richgeo, richpar);
        }
        Ncalls++;
        
        //  Process RICH signals to get hits and clusters
        if(richpar.PROCESS_RAWDATA==1){
            richio.clear_LowBanks(event);
            rpmt.process_RawData(event, richpar, richcal);
        }
        
        //  Process RICH-DC event reconstruction
        if(richpar.PROCESS_DATA==1){
            richio.clear_HighBanks(event);
            if( !reb.process_Data(event, richpar, richcal, richtrace)) return false;
        }
        
        return true;
    }
}
