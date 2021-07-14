package org.jlab.rec.cvt.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.swimtools.Swim;
import org.jlab.detector.base.DetectorType;
import org.jlab.detector.base.GeometryFactory;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.detector.geant4.v2.CTOFGeant4Factory;
import org.jlab.detector.geant4.v2.SVT.SVTConstants;
import org.jlab.detector.geant4.v2.SVT.SVTStripFactory;
import org.jlab.geom.base.ConstantProvider;
import org.jlab.geom.base.Detector;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.rec.cvt.Constants;
import org.jlab.rec.cvt.banks.HitReader;
import org.jlab.rec.cvt.banks.RecoBankWriter;
import org.jlab.rec.cvt.bmt.CCDBConstantsLoader;
import org.jlab.rec.cvt.cluster.Cluster;
import org.jlab.rec.cvt.cluster.ClusterFinder;
import org.jlab.rec.cvt.cross.Cross;
import org.jlab.rec.cvt.cross.CrossMaker;
import org.jlab.rec.cvt.hit.ADCConvertor;
import org.jlab.rec.cvt.hit.FittedHit;
import org.jlab.rec.cvt.hit.Hit;

/**
 * Service to return reconstructed TRACKS
 * format
 *
 * @author ziegler
 *
 */
public class CVTRecNewKF extends ReconstructionEngine {

    org.jlab.rec.cvt.svt.Geometry SVTGeom;
    org.jlab.rec.cvt.bmt.BMTGeometry BMTGeom;
    CTOFGeant4Factory CTOFGeom;
    Detector          CNDGeom ;
    SVTStripFactory svtIdealStripFactory;
    CosmicTracksRec strgtTrksRec;
    TracksFromTargetRec trksFromTargetRec;
    
    public CVTRecNewKF() {
        super("CVTTracks", "ziegler", "4.0");
        
        SVTGeom = new org.jlab.rec.cvt.svt.Geometry();
        BMTGeom = new org.jlab.rec.cvt.bmt.BMTGeometry();
        strgtTrksRec = new CosmicTracksRec();
        trksFromTargetRec = new TracksFromTargetRec();
    }

    String FieldsConfig = "";
    int Run = -1;
    public boolean isSVTonly = false;
    public boolean isCosmic = false;
    public boolean exclLayrs = false;
    
    public void setRunConditionsParameters(DataEvent event, String FieldsConfig, int iRun, boolean addMisAlignmts, String misAlgnFile) {
        if (event.hasBank("RUN::config") == false) {
            System.err.println("RUN CONDITIONS NOT READ!");
            return;
        }

        boolean isMC = false;
        boolean isCosmics = false;
        DataBank bank = event.getBank("RUN::config");
        if (bank.getByte("type", 0) == 0) {
            isMC = true;
        }
        if (bank.getByte("mode", 0) == 1) {
            isCosmics = true;
        }

        

        // Load the fields
        //-----------------
        String newConfig = "SOLENOID" + bank.getFloat("solenoid", 0);

        if (FieldsConfig.equals(newConfig) == false) {
            // Load the Constants
            
            this.setFieldsConfig(newConfig);
        }
        FieldsConfig = newConfig;

        // Load the constants
        //-------------------
        int newRun = bank.getInt("run", 0); 
        if (Run != newRun) {
            boolean align=false;
            //Load field scale
            double SolenoidScale =(double) bank.getFloat("solenoid", 0);
            if(SolenoidScale==0)
                SolenoidScale=0.000001;
            Constants.setSolenoidscale(SolenoidScale);
            float[]b = new float[3];
            Swim swimmer = new Swim();
            swimmer.BfieldLab(0, 0, 0, b);
            Constants.setSolenoidVal(Math.abs(b[2]));
            Constants.Load(isCosmics, isSVTonly);
            this.setRun(newRun); 
            
            if(newRun<100) { 
                org.jlab.rec.cvt.bmt.Constants.isMC = true;
            } else {
                org.jlab.rec.cvt.bmt.Constants.isMC = false;
            }
        }
      
        Run = newRun;
        this.setRun(Run);
    }

    public int getRun() {
        return Run;
    }

    public void setRun(int run) {
        Run = run;
    }

    public String getFieldsConfig() {
        return FieldsConfig;
    }

    public void setFieldsConfig(String fieldsConfig) {
        FieldsConfig = fieldsConfig;
    }
   
    @Override
    public boolean processDataEvent(DataEvent event) {
        this.setRunConditionsParameters(event, FieldsConfig, Run, false, "");
        double shift = 0;//org.jlab.rec.cvt.Constants.getZoffset();

        this.FieldsConfig = this.getFieldsConfig();
        
        Swim swimmer = new Swim();
        ADCConvertor adcConv = new ADCConvertor();

        RecoBankWriter rbc = new RecoBankWriter();

        HitReader hitRead = new HitReader();
        hitRead.fetch_SVTHits(event, adcConv, -1, -1, SVTGeom);
        if(isSVTonly==false)
          hitRead.fetch_BMTHits(event, adcConv, BMTGeom, swimmer);

        List<Hit> hits = new ArrayList<Hit>();
        //I) get the hits
        List<Hit> svt_hits = hitRead.get_SVTHits();
        if(svt_hits.size()>org.jlab.rec.cvt.svt.Constants.MAXSVTHITS)
            return true;
        if (svt_hits != null && svt_hits.size() > 0) {
            hits.addAll(svt_hits);
        }

        List<Hit> bmt_hits = hitRead.get_BMTHits();
        if (bmt_hits != null && bmt_hits.size() > 0) {
            hits.addAll(bmt_hits);

            if(bmt_hits.size()>org.jlab.rec.cvt.bmt.Constants.MAXBMTHITS)
                 return true;
        }

        //II) process the hits		
        List<FittedHit> SVThits = new ArrayList<FittedHit>();
        List<FittedHit> BMThits = new ArrayList<FittedHit>();
        //1) exit if hit list is empty
        if (hits.size() == 0) {
            return true;
        }
       
        List<Cluster> clusters = new ArrayList<Cluster>();
        List<Cluster> SVTclusters = new ArrayList<Cluster>();
        List<Cluster> BMTclusters = new ArrayList<Cluster>();

        //2) find the clusters from these hits
        ClusterFinder clusFinder = new ClusterFinder();
        clusters.addAll(clusFinder.findClusters(svt_hits, SVTGeom, BMTGeom));     
        if(bmt_hits != null && bmt_hits.size() > 0)
            clusters.addAll(clusFinder.findClusters(bmt_hits, SVTGeom, BMTGeom)); 
        
        if (clusters.size() == 0) {
            rbc.appendCVTBanks(event, SVThits, BMThits, null, null, null, null, shift);
            return true;
        }
        
        // fill the fitted hits list.
        if (clusters.size() != 0) {
            for (int i = 0; i < clusters.size(); i++) {
                if (clusters.get(i).get_Detector() == 0) {
                    SVTclusters.add(clusters.get(i));
                    SVThits.addAll(clusters.get(i));
                }
                if (clusters.get(i).get_Detector() == 1) {
                    BMTclusters.add(clusters.get(i));
                    BMThits.addAll(clusters.get(i));
                }
            }
        }

        List<ArrayList<Cross>> crosses = new ArrayList<ArrayList<Cross>>();
        CrossMaker crossMake = new CrossMaker();
        crosses = crossMake.findCrosses(clusters, SVTGeom, BMTGeom);
        if(crosses.get(0).size() > org.jlab.rec.cvt.svt.Constants.MAXSVTCROSSES ) {
            rbc.appendCVTBanks(event, SVThits, BMThits, SVTclusters, BMTclusters, null, null, shift);
            return true; 
        }
        
        if(this.isCosmic) {
            if(this.isSVTonly) {
                List<ArrayList<Cross>> crosses_svtOnly = new ArrayList<ArrayList<Cross>>();
                crosses_svtOnly.add(0, crosses.get(0));
                crosses_svtOnly.add(1, new ArrayList<Cross>());
            } 
            strgtTrksRec.processEvent(event, SVThits, BMThits, SVTclusters, BMTclusters, 
                    crosses, SVTGeom, BMTGeom, CTOFGeom, CNDGeom, rbc, shift, this.exclLayrs, swimmer);
        } else {
            trksFromTargetRec.processEvent(event, SVThits, BMThits, SVTclusters, BMTclusters, 
                crosses, SVTGeom, BMTGeom, CTOFGeom, CNDGeom, rbc, shift, swimmer, 
                this.isSVTonly, this.exclLayrs);
        }
        return true;
    }
     
    @Override
    public boolean init() {
        // Load config
        String rmReg = this.getEngineConfigString("removeRegion");
        
        if (rmReg!=null) {
            System.out.println("["+this.getName()+"] run with region "+rmReg+"removed config chosen based on yaml");
            Constants.setRmReg(Integer.valueOf(rmReg));
        }
        else {
            rmReg = System.getenv("COAT_CVT_REMOVEREGION");
            if (rmReg!=null) {
                System.out.println("["+this.getName()+"] run with region "+rmReg+"removed config chosen based on env");
                Constants.setRmReg(Integer.valueOf(rmReg));
            }
        }
        if (rmReg==null) {
             System.out.println("["+this.getName()+"] run with all region (default) ");
        }
        //svt stand-alone
        String svtStAl = this.getEngineConfigString("svtOnly");
        
        if (svtStAl!=null) {
            System.out.println("["+this.getName()+"] run with SVT only "+svtStAl+" config chosen based on yaml");
            this.isSVTonly= Boolean.valueOf(svtStAl);
        }
        else {
            svtStAl = System.getenv("COAT_SVT_ONLY");
            if (svtStAl!=null) {
                System.out.println("["+this.getName()+"] run with SVT only "+svtStAl+" config chosen based on env");
                this.isSVTonly= Boolean.valueOf(svtStAl);
            }
        }
        if (svtStAl==null) {
             System.out.println("["+this.getName()+"] run with both CVT systems (default) ");
        }
        //svt stand-alone
        String svtCosmics = this.getEngineConfigString("cosmics");
        
        if (svtCosmics!=null) {
            System.out.println("["+this.getName()+"] run with cosmics settings "+svtCosmics+" config chosen based on yaml");
            this.isCosmic= Boolean.valueOf(svtCosmics);
            org.jlab.rec.cvt.Constants.setCosmicsData(isCosmic);
        }
        else {
            svtCosmics = System.getenv("COAT_CVT_COSMICS");
            if (svtCosmics!=null) {
                System.out.println("["+this.getName()+"] run with cosmics settings "+svtCosmics+" config chosen based on env");
                this.isCosmic= Boolean.valueOf(svtCosmics);
                org.jlab.rec.cvt.Constants.setCosmicsData(isCosmic);
            }
        }
        if (svtCosmics==null) {
             System.out.println("["+this.getName()+"] run with cosmics settings default = false");
        }
        //all layers used --> 1
        for(int i = 0; i < 12; i++)
            Constants.getLayersUsed().put(i+1, 1);
        
        //Skip layers
         String exLys = this.getEngineConfigString("excludeLayers");
        
        if (exLys!=null) {
            System.out.println("["+this.getName()+"] run with layers "+exLys+"excluded in fit config chosen based on yaml");
            String exlys = String.valueOf(exLys);
            String[] values = exlys.split(",");
            for(int k = 0; k < values.length; k++)
                Constants.getLayersUsed().put(Integer.valueOf(values[k]), 0);
        }
        else {
            exLys = System.getenv("COAT_CVT_EXCLUDELAYERS");
            if (exLys!=null) {
                System.out.println("["+this.getName()+"] run with region "+rmReg+"excluded in fit  config chosen based on env");
                String exlys = String.valueOf(exLys);
                String[] values = exlys.split(",");
                for(int k = 0; k < values.length; k++)
                    Constants.getLayersUsed().put(Integer.valueOf(values[k]), 0);// layer excluded --->0
            }
        }
        if (exLys==null) {
             System.out.println("["+this.getName()+"] run with all layer in fit (default) ");
        }
        
        int exlyrsnb = 0;
        for(int ilayrs = 0; ilayrs<12; ilayrs++) {
            if((int)org.jlab.rec.cvt.Constants.getLayersUsed().get(ilayrs+1)<1) {
                System.out.println("EXCLUDE CVT LAYER "+(ilayrs+1));
                exlyrsnb++;
            }
        }
        if(exlyrsnb>0)
            exclLayrs = true;
        
        //new clustering
        String newClustering = this.getEngineConfigString("newclustering");
        
        if (newClustering!=null) {
            System.out.println("["+this.getName()+"] run with new clustering settings "+newClustering+" config chosen based on yaml");
            org.jlab.rec.cvt.bmt.Constants.newClustering= Boolean.valueOf(newClustering);
        }
        else {
            newClustering = System.getenv("COAT_CVT_NEWCLUSTERING");
            if (newClustering!=null) {
                System.out.println("["+this.getName()+"] run with new clustering settings "+newClustering+" config chosen based on env");
                org.jlab.rec.cvt.bmt.Constants.newClustering= Boolean.valueOf(newClustering);
            }
        }
        if (newClustering==null) {
             System.out.println("["+this.getName()+"] run with newclustering settings default = false");
        }
        
        // Load other geometries
        
        variationName = Optional.ofNullable(this.getEngineConfigString("variation")).orElse("default");
        System.out.println(" CVT YAML VARIATION NAME + "+variationName);
        ConstantProvider providerCTOF = GeometryFactory.getConstants(DetectorType.CTOF, 11, variationName);
        CTOFGeom = new CTOFGeant4Factory(providerCTOF);        
        CNDGeom =  GeometryFactory.getDetector(DetectorType.CND, 11, variationName);
        //
          
        
        System.out.println(" LOADING CVT GEOMETRY...............................variation = "+variationName);
        CCDBConstantsLoader.Load(new DatabaseConstantProvider(11, variationName));
        System.out.println("SVT LOADING WITH VARIATION "+variationName);
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, variationName);
        cp = SVTConstants.connect( cp );
        cp.disconnect();  
        SVTStripFactory svtFac = new SVTStripFactory(cp, true);
        SVTGeom.setSvtStripFactory(svtFac);

        return true;
    }
  
    private String variationName;
    

}