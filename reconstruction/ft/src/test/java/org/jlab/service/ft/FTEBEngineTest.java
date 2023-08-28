package org.jlab.service.ft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JFrame;
import org.jlab.clas.detector.DetectorData;
import org.jlab.clas.detector.DetectorEvent;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.detector.base.DetectorLayer;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.data.DataLine;
import org.jlab.groot.ui.LatexText;
import org.jlab.groot.math.F1D;
import org.jlab.groot.fitter.DataFitter;
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.groot.data.GraphErrors;
import org.jlab.clas.pdg.PhysicsConstants;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.evio.EvioDataBank;
import org.jlab.io.evio.EvioDataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.rec.ft.cal.FTCALConstantsLoader;
import org.jlab.rec.ft.cal.FTCALEngine;
import org.jlab.rec.ft.hodo.FTHODOEngine;
import org.jlab.rec.ft.trk.FTTRKEngine;
import org.jlab.rec.ft.trk.FTTRKConstantsLoader;
import org.jlab.rec.ft.trk.FTTRKReconstruction;
import org.jlab.rec.ft.FTEventBuilder;
import org.jlab.rec.ft.FTParticle;
import org.jlab.rec.ft.FTResponse;
import org.jlab.rec.ft.FTEBEngine;
import org.jlab.rec.ft.FTConstants;

public class FTEBEngineTest extends ReconstructionEngine {

    FTEventBuilder reco;
    int Run = -1;
    double Solenoid;
    
    public static boolean timeEnergyDiagnosticHistograms = true; 
    
    public static H1F h500 = new H1F("Time Difference FTCAL-response", 100, 0., 200.);
    public static H1F h501 = new H1F("Cross Energy TRK0", 100, 0., 2000.);
    public static H1F h502 = new H1F("Cross Energy TRK1", 100, 0., 2000.);
    public static H1F h503 = new H1F("Cross time TRK0", 100, 0.0, 500.);
    public static H1F h504 = new H1F("Cross time TRK1", 100, 0.0, 500.);
    public static H2F h505 = new H2F("Cross energy vs time TRK0", 100, 0.0, 500., 100, 0., 2000.);
    public static H2F h506 = new H2F("Cross energy vs time TRK1", 100, 0.0, 500., 100, 0., 2000.);
    public static H2F h507 = new H2F("Cross energy vs time TRK0+1", 100, 0.0, 500., 100, 0., 2000.);
    public static H2F h510 = new H2F("Clusters total energies TRK0", 100, 0., 2000., 100, 0., 2000.);
    public static H2F h511 = new H2F("Clusters total energies TRK1", 100, 0., 2000., 100, 0., 2000.);
    public static H1F h512 = new H1F("Clusters total energies TRK0", 100, 0., 2000.);
    public static H1F h513 = new H1F("Clusters total energies TRK1", 100, 0., 2000.);
    // there is no time information yet in banks for clusters
    //public static H1F h520 = new H1F("Time of strips in cluster1 TRK0", 100, 0., 500.);
    //public static H1F h521 = new H1F("Time of strips in cluster2 TRK0", 100, 0., 500.);
    //public static H1F h522 = new H1F("time of strips in cluster1 TRK1", 100, 0., 500.);
    //public static H1F h523 = new H1F("Time of strips in cluster2 TRK1", 100, 0., 500.);
    
    public static H1F h600 = new H1F("TRK response position", 100, 5.93, 6.03);
    public static H2F h601 = new H2F("TRK tof vs time", 100, 0., 500., 100, 5.93, 6.03);
    
    public static H2F hSecDet0 = new H2F("lay 2 vs lay1 sectors fo form a cross", 20, -0.5, 19.5, 20, -0.5, 19.5);
    public static H2F hSecDet1 = new H2F("lay 4 vs lay3 sectors fo form a cross", 20, -0.5, 19.5, 20, -0.5, 19.5);
    public static H2F hSeedDet0 = new H2F("lay 2 vs lay1 cluster seeds fo form a cross", 768/4, -0.5, 767.5, 768/4, -0.5, 767.5);
    public static H2F hSeedDet1 = new H2F("lay 4 vs lay3 cluster seeds fo form a cross", 768/4, -0.5, 767.5, 768/4, -0.5, 767.5);
    
    public static Point3D centerOfTarget = new Point3D(0., 0., -3.);
    
   

    public FTEBEngineTest() {
        super("FTEB", "devita", "3.0");
    }

    @Override
    public boolean init() {
        reco = new FTEventBuilder();
        reco.debugMode = 0;
        String[] tables = new String[]{
            "/calibration/ft/ftcal/cluster",
            "/calibration/ft/ftcal/thetacorr",
            "/calibration/ft/ftcal/phicorr"
        };
        requireConstants(Arrays.asList(tables));
        this.getConstantsManager().setVariation("default");

        return true;
    }

    @Override
    public boolean processDataEventUser(DataEvent event) {
        List<FTParticle> FTparticles = new ArrayList<FTParticle>();
        List<FTResponse> FTresponses = new ArrayList<FTResponse>();

        int run = this.setRunConditionsParameters(event);
        if (run>=0) {
            reco.init(this.getSolenoid());
            FTresponses = reco.addResponses(event, this.getConstantsManager(), run);
            FTparticles = reco.initFTparticles(FTresponses, this.getConstantsManager(), run);
            if(FTparticles.size()>0){
                reco.matchToTRKTwoDetectorsMultiHits(FTresponses, FTparticles);
                reco.matchToHODO(FTresponses, FTparticles);
//                reco.correctDirection(FTparticles, this.getConstantsManager(), run);  // correction to be applied only to FTcal and FThodo
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
