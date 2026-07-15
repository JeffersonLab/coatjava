package org.jlab.rec.ft;

import java.util.Arrays;
import java.util.List;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.evio.EvioDataBank;
import org.jlab.io.evio.EvioDataEvent;

public class FTEBEngine extends ReconstructionEngine {

    FTEventBuilder reco = new FTEventBuilder();
    int Run = -1;
    double Solenoid;
    
    public FTEBEngine() {
        super("FTEB", "devita", "3.0");
    }

    @Override
    public boolean init() {
        reco.debugMode = 0;
        String[] tables = new String[]{
            "/calibration/ft/ftcal/cluster",
            "/calibration/ft/ftcal/thetacorr",
            "/calibration/ft/ftcal/phicorr",
            "/geometry/shifts/solenoid"
        };
        requireConstants(Arrays.asList(tables));
        this.getConstantsManager().setVariation("default");
        this.registerOutputBank("FT::particles");
        return true;
    }

    @Override
    public void detectorChanged(int runNumber) {}

    @Override
    public boolean processDataEventUser(DataEvent event) {
        
        int run = this.setRunConditionsParameters(event);
        if (run>=0) {
            reco.init(this.getSolenoid());
            List<FTResponse> FTresponses = reco.addResponses(event, this.getConstantsManager(), run);

            List<FTParticle> FTparticles = reco.initFTparticles(FTresponses, this.getConstantsManager(), run);
            if(!FTparticles.isEmpty()){
                reco.matchToTRKTwoDetectorsMultiHits(FTresponses, FTparticles);
                reco.matchToHODO(FTresponses, FTparticles);
                reco.correctDirection(FTparticles, this.getConstantsManager(), run);
                reco.writeBanks(event, FTparticles);
            }
        }
        return true;
    }

    public int setRunConditionsParameters(DataEvent event) {
        int run = -1;
        if (event.hasBank("RUN::config") == false) {
            System.err.println("RUN CONDITIONS NOT READ!");
        }
        else {
            double fieldScale = 0;

            boolean isMC = false;
            boolean isCosmics = false;

            if (event instanceof EvioDataEvent) {
                EvioDataBank bank = (EvioDataBank) event.getBank("RUN::config");
                if (bank.getByte("Type",0) == 0) {
                    isMC = true;
                }
                if (bank.getByte("Mode",0)== 1) {
                    isCosmics = true;
                }
                run = bank.getInt("Run",0);
                fieldScale = bank.getFloat("Solenoid")[0];
            } else {
                DataBank bank = event.getBank("RUN::config");
                if (bank.getByte("type",0) == 0) {
                    isMC = true;
                }
                if (bank.getByte("mode",0)== 1) {
                    isCosmics = true;
                }
                run = bank.getInt("run",0);
                fieldScale = bank.getFloat("solenoid",0);
            }
            this.setSolenoid(fieldScale);
        }
        return run;
    }

    public int getRun() {
        return Run;
    }

    public void setRun(int run) {
        Run = run;
    }

    public double getSolenoid() {
        return Solenoid;
    }

    public void setSolenoid(double Solenoid) {
        this.Solenoid = Solenoid;
    }
    
    public int getDebugMode() {
        return this.reco.debugMode;
    }
}
