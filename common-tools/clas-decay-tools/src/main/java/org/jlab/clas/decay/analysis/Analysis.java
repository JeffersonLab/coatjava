package org.jlab.clas.decay.analysis;

import cnuphys.magfield.MagneticFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.clas.swimtools.Swim;
import org.jlab.clas.decay.banks.Reader;
import org.jlab.clas.decay.banks.Writer;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.detector.calib.utils.RCDBConstants;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataEvent;
import org.jlab.utils.groups.IndexedTable;
/**
 * Service to reconstruct a decay chain using multi-vertex reconstruction
 * e.g. YAML setting 3122:2212:-211:0:1.0:1.5;-3122:-2212:211:0:1.0:1.5 corresponds to 
 * reconstruction of Lambda into p + pi- and LambdaBar into pBar + pi+
 * The parent particle is the first one followed by the daughters 2212:-212:0(no 3rd daughter);
 * The last 2 fields are the upper and lower mass bounds for reconstructed the parent particle
 *
 * @author ziegler
 *
 */


public class Analysis extends ReconstructionEngine {

    public Analysis(String name) {
        super(name, "ziegler", "2.0");
    }
    
    String FieldsConfig = "";
    private int Run = -1;
    int passn =0;
    int counter =0;

    private String decays;
    RCDBConstants rcdb = null;
    
    

    
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
 
    public  double beamE = 10.6;
    public  Reader.Target target = Reader.Target.valueOf("LH2");;
    private int pass =1;
    public double[] xyBeam;
    double solenoidShift=0;
    
       
    @Override
    public boolean processDataEventUser(DataEvent event) {
        Reader reader ;
        this.FieldsConfig = this.getFieldsConfig();
        if (event.hasBank("RUN::config") == false) {
            event.show();
            //System.err.println("RUN CONDITIONS NOT READ!");
            return false;
        }
        counter++;
        reader = new Reader();
        //
         if (event.hasBank("RUN::config") == false) {
            event.show();
            //System.err.println("RUN CONDITIONS NOT READ!");
            return false;
        }
        
        int newRun = event.getBank("RUN::config").getInt("run", 0); 
        IndexedTable beamPos            = this.getConstantsManager().getConstants(newRun, "/geometry/beam/position");
        IndexedTable tarZ               = this.getConstantsManager().getConstants(newRun, "/geometry/shifts/target");
        xyBeam = this.getBeamSpot(event, beamPos); 
        
        if(tarZ.getDoubleValue("z", 0, 0, 0)!=solenoidShift) { 
            solenoidShift = tarZ.getDoubleValue("z", 0, 0, 0); 
            MagneticFields.getInstance().setSolenoidShift(solenoidShift);
           // System.out.println("SHIFT "+(solenoidShiftCorr+solenoidShift));
        }
        //
        if (Run != newRun)  {
            this.setRun(newRun); 
            if (event.getBank("RUN::config").getInt("run",0) >= 100) {
                rcdb = this.getConstantsManager().getRcdbConstants(event.getBank("RUN::config").getInt("run",0));
                beamE=rcdb.getDouble("beam_energy")/1000.0;
                String tar = rcdb.getString("target"); System.out.println("TARGET "+tar); 
                if(tar!="LH2") tar="LH2";
                try {
                    target = Reader.Target.valueOf(tar.trim()); 
                } catch (IllegalArgumentException e){
                    target = Reader.Target.valueOf("UNDEF");
                }
            }
        } 
        //System.out.println("pass"+pass+" EVENT "+event.getBank("RUN::config").getInt("event", 0) +" beamE "+beamE+" tar "+target.name());
        this.Run = this.getRun();
        
        
        DataBank recRun    = null;
        DataBank recBankEB = null;
        DataBank recBankFT = null;
        DataBank recEvenEB = null;
        DataBank recParts = null;
        if(event.hasBank("RUN::config"))            recRun      = event.getBank("RUN::config");
        if(event.hasBank("REC::Particle"))          recBankEB   = event.getBank("REC::Particle");
        if(event.hasBank("RECFT::Particle"))        recBankFT   = event.getBank("RECFT::Particle");
        if(event.hasBank("REC::Event"))             recEvenEB   = event.getBank("REC::Event");
        if(event.hasBank("DECAYS::Particle"))         recParts   = event.getBank("DECAYS::Particle");
        if(recRun == null) return true;
        if(pass>1 && recParts == null) {
            return true;
        } 
        int ev  = recRun.getInt("event",0);
        int run = recRun.getInt("run",0);
        Swim swim = new Swim();
        swim.stepSize = 5.00 * 1.e-5; // 50 microns
        swim.distanceBetweenSaves= 10.0*swim.stepSize;
        reader.readBanks(event, pass);
       
        List<Particle> allparts = new ArrayList<>();
        
        reader.getDaus().forEach((key,value) -> allparts.add(value));
        
        
        List<Particle> parts = new ArrayList<>();
        for(int k = 0; k < react.parent.size(); k++) {
            //System.out.println(react.parent.get(k)+"-->"+react.dau1.get(k)+":"+react.dau2.get(k));
            Decay dec = new Decay(react.parent.get(k), 
                    react.dau1.get(k), 
                    react.dau2.get(k), 
                    react.dau3.get(k), 
                    react.lowBound.get(k), 
                    react.highBound.get(k), 
                    allparts, swim,pass, xyBeam);
            if(dec!=null) {
                if(dec.getParticles()!=null) {
                    parts.addAll(dec.getParticles()); 
                }
            }
        } 
        if(pass>1 && parts.isEmpty()) { 
            return true;
        }
        
        if(pass==1 && !parts.isEmpty()) {
            DataBank evBank = event.createBank("DECAYS::Event", 1);
            evBank.setFloat("tarmass",0, (float) target.getMass());
            evBank.setFloat("beamenergy",0, (float) beamE);
            event.appendBank(evBank);
        }

        DataBank bank =null;
        if(parts.isEmpty()) {
            if(event.hasBank("DECAYS::Particle")) event.removeBank("DECAYS::Particle");
        } else {
            bank = Writer.fillBank(event, parts, reader.getElectron(), "DECAYS::Particle", pass);
        }
        if(bank!=null) {
            event.appendBanks(bank);
            //bank.show();
            passn++;
            //System.out.println("================================================");
            //System.out.println("PASS"+pass+" total so far "+counter+" passed "+passn);
            //System.out.println("================================================");
            //event.appendBank(bank);
        } else {
            ((HipoDataEvent) event).getHipoEvent().reset();
        }
        //event.show();
        return true;
   }

    private Reaction react;
    @Override
    public boolean init() {
        this.initConstantsTables();
        this.registerBanks();
        this.loadConfiguration();
        react = new Reaction();
        react.initialize(this.getName(),
                decays);
        this.printConfiguration();
        return true;
    }
    private void registerBanks() {
        super.registerOutputBank("DECAYS::Event");
        super.registerOutputBank("DECAYS::Particle");
    } 
    

    
    public void loadConfiguration() {            
        
        // general (pass-independent) settings
        String dec = "decays";
        if (this.getEngineConfigString(dec)!=null) 
            decays = this.getEngineConfigString(dec);
        if (this.getEngineConfigString("pass")!=null) {
            pass=Integer.parseInt(this.getEngineConfigString("pass"));
        }
        
    }
    
    
    public void printConfiguration() {            
        for(int i =0; i<react.parent.size(); i++) {
            System.out.println("["+this.getName()+"] Reconstructing "+
                    react.parent.get(i)+"-->"+react.dau1.get(i)+":"+react.dau2.get(i));   
        }
        
    }

     
    public void initConstantsTables() {
        String[] tables = new String[]{
            "/geometry/beam/position",
            "/geometry/shifts/target"
        };
        requireConstants(Arrays.asList(tables));
        this.getConstantsManager().setVariation("default");
        if(this.getEngineConfigString("variation")!=null) 
            this.getConstantsManager().setVariation(this.getEngineConfigString("variation"));
    }
    
    public double[] getBeamSpot(DataEvent event, IndexedTable beamPos) {
        double[] xybeam = new double[2];
        xybeam[0] = beamPos.getDoubleValue("x_offset", 0, 0, 0);
        xybeam[1] = beamPos.getDoubleValue("y_offset", 0, 0, 0);
        if(event.hasBank("RASTER::position")){
            DataBank raster_bank = event.getBank("RASTER::position");
            xybeam[0] += raster_bank.getFloat("x", 0);
            xybeam[1] += raster_bank.getFloat("y", 0);
        }
        return xybeam;
    }

    public void detectorChanged(int run) {}
    
}
