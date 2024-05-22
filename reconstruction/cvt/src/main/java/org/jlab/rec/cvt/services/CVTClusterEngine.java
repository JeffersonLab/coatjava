package org.jlab.rec.cvt.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.swimtools.Swim;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.Constants;
import org.jlab.rec.cvt.Geometry;
import org.jlab.rec.cvt.banks.RecoBankWriter;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.hit.Hit;
import org.jlab.utils.groups.IndexedTable;

/**
 * Service to return reconstructed TRACKS
 * format
 *
 * @author ziegler
 *
 */
public class CVTClusterEngine extends ReconstructionEngine {


    /**
     * @param docacutsum the docacutsum to set
     */
    public void setDocacutsum(double docacutsum) {
        this.docacutsum = docacutsum;
    }

    private int Run = -1;

    private String svtHitBank;
    private String svtClusterBank;
    private String bmtHitBank;
    private String bmtClusterBank;
    private String bankPrefix = "";
    
    // run-time options
    private int     pid = 0;
    private int     kfIterations = 5;
    private boolean kfFilterOn = true;
    private boolean initFromMc = false;    
    
    // yaml setting passed to Constants class
    private boolean isCosmics           = false;
    private boolean svtOnly             = false;
    private String  excludeLayers       = null;
    private String  excludeBMTLayers    = null;
    private int     removeRegion        = 0;
    private int     beamSpotConstraint  = 2;
    private double  beamSpotRadius      = 0.3;
    private String  targetMaterial      = "LH2";
    private boolean elossPrecorrection  = true;
    private boolean svtSeeding          = true;
    private boolean timeCuts            = true;
    private boolean hvCuts              = false;
    public boolean useSVTTimingCuts     =  false;
    public boolean removeOverlappingSeeds = true;
    public boolean flagSeeds = true;
    public boolean gemcIgnBMT0ADC = false;
    public boolean KFfailRecovery = true;
    public boolean KFfailRecovMisCls = true;
    private String  matrixLibrary       = "EJML";
    private boolean useOnlyTruth        = false;
    private boolean useSVTLinkerSeeder  = true;
    private double docacut = 0.75;
    private double docacutsum = 1.15;
    private int svtmaxclussize = 100;
    private int bmtcmaxclussize = 100;
    private int bmtzmaxclussize = 100;
    private double rcut = 120.0;
    private double z0cut = 10;
    public boolean runML =false;
    
    public CVTClusterEngine(String name) {
        super(name, "ziegler", "6.0");
    }

    public CVTClusterEngine() {
        super("CVTEngine", "ziegler", "6.0");
    }

    
    @Override
    public boolean init() {        
        this.loadConfiguration();
        Constants.getInstance().initialize(this.getName(),
                                           isCosmics,
                                           svtOnly,
                                           excludeLayers,
                                           excludeBMTLayers,
                                           removeRegion,
                                           beamSpotConstraint,
                                           beamSpotRadius,
                                           targetMaterial,
                                           elossPrecorrection,
                                           svtSeeding,
                                           timeCuts,
                                           hvCuts,
                                           useSVTTimingCuts,
                                           removeOverlappingSeeds,
                                           flagSeeds,
                                           gemcIgnBMT0ADC,
                                           KFfailRecovery,
                                           KFfailRecovMisCls, 
                                           matrixLibrary,
                                           useOnlyTruth,
                                           useSVTLinkerSeeder, 
                                           docacut, 
                                           docacutsum, 
                                           svtmaxclussize, 
                                           bmtcmaxclussize, 
                                           bmtzmaxclussize,
                                           rcut,
                                           z0cut);

        this.initConstantsTables();
        this.registerBanks();
        this.printConfiguration();
        return true;    
    }
    
    public final void setOutputBankPrefix(String prefix) {
        this.bankPrefix = prefix;
    }

    public void registerBanks() {
        String prefix = "";
        this.setBmtHitBank("BMT" + prefix + "::Hits");
        this.setBmtClusterBank("BMT" + prefix + "::Clusters");
        this.setSvtHitBank("BST" + prefix + "::Hits");
        this.setSvtClusterBank("BST" + prefix + "::Clusters");
        super.registerOutputBank(this.bmtHitBank);
        super.registerOutputBank(this.bmtClusterBank);
        super.registerOutputBank(this.svtHitBank);
        super.registerOutputBank(this.svtClusterBank);
    }
    
    public int getRun(DataEvent event) {
    
        if (event.hasBank("RUN::config") == false) {
            System.err.println("RUN CONDITIONS NOT READ!");
            return 0;
        }

        DataBank bank = event.getBank("RUN::config");
        int run = bank.getInt("run", 0);  
        if(Constants.getInstance().seedingDebugMode) {
            System.out.println("EVENT "+bank.getInt("event", 0));
        }
        return run;
    }

    public int getPid() {
        return pid;
    }

    public int getKfIterations() {
        return kfIterations;
    }

    public boolean isKfFilterOn() {
        return kfFilterOn;
    }

    public boolean isInitFromMc() {
        return initFromMc;
    }

    public boolean seedBeamSpot() {
        return this.beamSpotConstraint>0;
    }
    
    public boolean kfBeamSpot() {
        return this.beamSpotConstraint==2;
    }
    
    /**
     * @return the docacut
     */
    public double getDocacut() {
        return docacut;
    }

    /**
     * @param docacut the docacut to set
     */
    public void setDocacut(double docacut) {
        this.docacut = docacut;
    }

    /**
     * @return the docacutsum
     */
    public double getDocacutsum() {
        return docacutsum;
    }

    /**
     * @return the svtmaxclussize
     */
    public int getSvtmaxclussize() {
        return svtmaxclussize;
    }

    /**
     * @param svtmaxclussize the svtmaxclussize to set
     */
    public void setSvtmaxclussize(int svtmaxclussize) {
        this.svtmaxclussize = svtmaxclussize;
    }

    /**
     * @return the bmtcmaxclussize
     */
    public int getBmtcmaxclussize() {
        return bmtcmaxclussize;
    }

    /**
     * @param bmtcmaxclussize the bmtcmaxclussize to set
     */
    public void setBmtcmaxclussize(int bmtcmaxclussize) {
        this.bmtcmaxclussize = bmtcmaxclussize;
    }

    /**
     * @return the bmtzmaxclussize
     */
    public int getBmtzmaxclussize() {
        return bmtzmaxclussize;
    }

    /**
     * @param bmtzmaxclussize the bmtzmaxclussize to set
     */
    public void setBmtzmaxclussize(int bmtzmaxclussize) {
        this.bmtzmaxclussize = bmtzmaxclussize;
    }
    
    @Override
    public boolean processDataEvent(DataEvent event) {
        
        Swim swimmer = new Swim();
        
        int run = this.getRun(event); 
        
        IndexedTable svtStatus          = this.getConstantsManager().getConstants(run, "/calibration/svt/status");
        IndexedTable svtLorentz         = this.getConstantsManager().getConstants(run, "/calibration/svt/lorentz_angle");
        IndexedTable bmtStatus          = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_status");
        IndexedTable bmtTime            = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_time");
        IndexedTable bmtVoltage         = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_voltage");
        IndexedTable bmtStripVoltage    = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_strip_voltage");
        IndexedTable bmtStripThreshold  = this.getConstantsManager().getConstants(run, "/calibration/mvt/bmt_strip_voltage_thresholds");
        IndexedTable beamPos            = this.getConstantsManager().getConstants(run, "/geometry/beam/position");
        
        Geometry.initialize(this.getConstantsManager().getVariation(), 11, svtLorentz, bmtVoltage);
        
        CVTReconstruction reco = new CVTReconstruction(swimmer);
        
        List<ArrayList<Hit>>         hits = reco.readHits(event, svtStatus, bmtStatus, bmtTime, 
                                                            bmtStripVoltage, bmtStripThreshold);
        List<ArrayList<Cluster>> clusters = reco.findClusters();
        List<DataBank> banks = new ArrayList<>();
        
        banks.add(RecoBankWriter.fillSVTHitBank(event, hits.get(0), this.getSvtHitBank()));
        banks.add(RecoBankWriter.fillBMTHitBank(event, hits.get(1), this.getBmtHitBank()));
        banks.add(RecoBankWriter.fillSVTClusterBank(event, clusters.get(0), this.getSvtClusterBank()));
        banks.add(RecoBankWriter.fillBMTClusterBank(event, clusters.get(1), this.getBmtClusterBank()));

        event.appendBanks(banks.toArray(new DataBank[0]));
            
        
        return true;
    }

         
    public void loadConfiguration() {            
        
        
    }


    public void initConstantsTables() {
        String[] tables = new String[]{
            "/calibration/svt/status",
            "/calibration/svt/lorentz_angle",
            "/calibration/mvt/bmt_time",
            "/calibration/mvt/bmt_status",
            "/calibration/mvt/bmt_voltage",
            "/calibration/mvt/bmt_strip_voltage",
            "/calibration/mvt/bmt_strip_voltage_thresholds",
            "/geometry/beam/position"
        };
        requireConstants(Arrays.asList(tables));
        this.getConstantsManager().setVariation("default");
    }
    
    public void setSvtHitBank(String bstHitBank) {
        this.svtHitBank = bstHitBank;
    }
    
   
    
    public void setSvtClusterBank(String bstClusterBank) {
        this.svtClusterBank = bstClusterBank;
    }

    

    public void setBmtHitBank(String bmtHitBank) {
        this.bmtHitBank = bmtHitBank;
    }

    public void setBmtClusterBank(String bmtClusterBank) {
        this.bmtClusterBank = bmtClusterBank;
    }
    
    public String getSvtHitBank() {
        return svtHitBank;
    }
    
   
        
    public String getSvtClusterBank() {
        return svtClusterBank;
    }

   

    public String getBmtHitBank() {
        return bmtHitBank;
    }

    public String getBmtClusterBank() {
        return bmtClusterBank;
    }

    
    
    public void printConfiguration() {            
        
        System.out.println("["+this.getName()+"] run with cosmics setting set to "+Constants.getInstance().isCosmics);        
        System.out.println("["+this.getName()+"] run with SVT only set to "+Constants.getInstance().svtOnly);
        if(this.excludeLayers!=null)
            System.out.println("["+this.getName()+"] run with layers "+this.excludeLayers+" excluded in fit, based on yaml");
        if(this.excludeBMTLayers!=null)
            System.out.println("["+this.getName()+"] run with BMT layers "+this.getEngineConfigString("excludeBMTLayers")+" excluded");
        if(this.removeRegion>0)
            System.out.println("["+this.getName()+"] run with region "+this.getEngineConfigString("removeRegion")+" removed");
        System.out.println("["+this.getName()+"] run with beamSpotConst set to "+Constants.getInstance().beamSpotConstraint+ " (0=no-constraint, 1=seed only, 2=seed and KF)");        
        System.out.println("["+this.getName()+"] run with beam spot size set to "+Constants.getInstance().getBeamRadius());                
        System.out.println("["+this.getName()+"] Target material set to "+ Constants.getInstance().getTargetMaterial().getName());
        System.out.println("["+this.getName()+"] Pre-Eloss correction set to " + Constants.getInstance().preElossCorrection);
        System.out.println("["+this.getName()+"] run SVT-based seeding set to "+ Constants.getInstance().svtSeeding);
        System.out.println("["+this.getName()+"] run BMT timing cuts set to "+ Constants.getInstance().timeCuts);
        System.out.println("["+this.getName()+"] run BMT HV masks "+ Constants.getInstance().bmtHVCuts);
        System.out.println("["+this.getName()+"] run with matLib "+ Constants.getInstance().KFMatrixLibrary.toString() + " library");
        System.out.println("["+this.getName()+"] ELoss mass set for particle "+ pid);
        System.out.println("["+this.getName()+"] run with Kalman-Filter status set to "+this.kfFilterOn);
        System.out.println("["+this.getName()+"] initialize KF from true MC information "+this.initFromMc);
        System.out.println("["+this.getName()+"] number of KF iterations set to "+this.kfIterations);
        System.out.println("["+this.getName()+"] SLA doca cut "+this.docacut);
        System.out.println("["+this.getName()+"] SLA docasum cut "+this.docacutsum);
        System.out.println("["+this.getName()+"] max svt  cluster size "+this.getSvtmaxclussize());
        System.out.println("["+this.getName()+"] max bmt-c  cluster size "+this.getBmtcmaxclussize());
        System.out.println("["+this.getName()+"] max btm-z  cluster size "+this.getBmtzmaxclussize());
        System.out.println("["+this.getName()+"] helix radius cut (mm) "+this.rcut);
        System.out.println("["+this.getName()+"] z0 cut (mm from target edges) "+this.z0cut); 
        
        
    }


}
