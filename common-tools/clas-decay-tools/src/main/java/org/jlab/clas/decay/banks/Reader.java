/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.decay.banks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.clas.decay.analysis.Constants;
import org.jlab.clas.decay.analysis.Decay;
import org.jlab.clas.decay.analysis.Particle;
import org.jlab.detector.base.DetectorType;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
/**
 *
 * @author ziegler
 */
public class Reader {
    private Map<Integer, Particle> parts = new HashMap<>();
    private Map<Integer, Particle> daus = new HashMap<>();
    //public static boolean useMCTruth = true;
    public boolean iseDetected = false;
    //default is center of the target
    private Particle electron;
    
    public List<Particle> getPartsFromRec(DataEvent event) {
        DataBank recBankEB = null;
        DataBank recBankFT = null;
        DataBank recDeteEB = null; 
        DataBank vertBankEB = null;
        DataBank trkBankEB = null;
        DataBank utrkBankEB = null;
        List<Particle> parts = new ArrayList<>();
        if(event.hasBank("REC::Particle")) recBankEB = event.getBank("REC::Particle");
        if(event.hasBank("RECFT::Particle")) recBankFT = event.getBank("RECFT::Particle");
        if(event.hasBank("REC::Track")) trkBankEB = event.getBank("REC::Track");
        if(event.hasBank("REC::UTrack")) utrkBankEB = event.getBank("REC::UTrack");
        if(event.hasBank("REC::Calorimeter")) recDeteEB = event.getBank("REC::Calorimeter");
        if(event.hasBank("REC::VertDoca")) vertBankEB = event.getBank("REC::VertDoca");
        Decay.setVertBank(vertBankEB);        
        
        Map<Integer, double[]> uTrkMap = new HashMap<>();
        Map<Integer, double[]> pTrkMap = new HashMap<>();
        if(utrkBankEB!=null) {
            int nrows2 = utrkBankEB.rows();
            for(int loop = 0; loop < nrows2; loop++){
                int uindex = utrkBankEB.getInt("index", loop);
                double px = utrkBankEB.getFloat("px", loop);
                double py = utrkBankEB.getFloat("py", loop);
                double pz = utrkBankEB.getFloat("pz", loop);
                double vx = utrkBankEB.getFloat("vx", loop);
                double vy = utrkBankEB.getFloat("vy", loop);
                double vz = utrkBankEB.getFloat("vz", loop);
                double[] t = new double[]{px,py,pz,vx,vy,vz};
                uTrkMap.put(uindex, t);
            }
        }
        if(recBankEB!=null) {
            
            int nrows = recBankEB.rows();
            for(int loop = 0; loop < nrows; loop++){
                double px = recBankEB.getFloat("px", loop);
                double py = recBankEB.getFloat("py", loop);
                double pz = recBankEB.getFloat("pz", loop);
                double vx = recBankEB.getFloat("vx", loop);
                double vy = recBankEB.getFloat("vy", loop);
                double vz = recBankEB.getFloat("vz", loop);
                double[] t = new double[]{px,py,pz,vx,vy,vz};
                pTrkMap.put(loop, t);
            }
        }
        if(trkBankEB!=null) {
            int nrows2 = trkBankEB.rows();
            for(int loop = 0; loop < nrows2; loop++){
                int detector = trkBankEB.getInt("detector", loop);
                if(detector!=5) 
                    continue;
                int index = trkBankEB.getInt("index", loop);
                int pindex = trkBankEB.getInt("pindex", loop);
                if(uTrkMap.containsKey(index) && pTrkMap.containsKey(pindex)) {
                    pTrkMap.put(pindex, uTrkMap.get(index));
                }
            }
        }
        boolean isFTEvent = false;
        if(recBankFT!=null) {
            for(int loop = 0; loop < recBankFT.rows(); loop++){
                int pidCode = recBankFT.getInt("pid", loop);
                int status = recBankFT.getInt("status", loop);
                if(pidCode==11 && status<0) 
                    isFTEvent=true;
            }
        }
        if(recBankEB!=null) {
            int nrows = recBankEB.rows();
            for(int loop = 0; loop < nrows; loop++){
                int pidCode = recBankEB.getInt("pid", loop);
                if(isFTEvent) pidCode = recBankFT.getInt("pid", loop); 
                if(pidCode==0) continue;
                
                double px = recBankEB.getFloat("px", loop);
                double py = recBankEB.getFloat("py", loop);
                double pz = recBankEB.getFloat("pz", loop);
                double vx = recBankEB.getFloat("vx", loop);
                double vy = recBankEB.getFloat("vy", loop);
                double vz = recBankEB.getFloat("vz", loop);
                double beta = recBankEB.getFloat("beta", loop);
                int status = recBankEB.getInt("status", loop);
                if(isFTEvent) {
                    beta = recBankFT.getFloat("beta", loop);
                    status = recBankFT.getInt("status", loop);
                }
                int charge = (int) recBankEB.getByte("charge", loop);
                int det = -1;
                boolean isTriggerPart=false;
                if((int) (Math.abs(status)/1000)==4) 
                    det = 0;
                if((int) (Math.abs(status)/1000)==2) 
                    det = 1;
                if((int) (Math.abs(status)/1000)==1) 
                    det = 3; //FT 
                if(status<0) {
                    isTriggerPart=true;
                }
                
                //if(noBeamConst.contains(pidCode)) {
                if(det==0) { 
                    double[] t = pTrkMap.get(loop);
                    px = t[0];
                    py = t[1];
                    pz = t[2];
                    vx = t[3];
                    vy = t[4];
                    vz = t[5];
                } 
                
                if((pidCode==22 || pidCode==2112) && recDeteEB!=null) { 
                    double energy1=0;
                    double energy4=0;
                    double energy7=0;
                    //int    sector =0;
                    int  detector =0;
                    for(int j=0; j<recDeteEB.rows(); j++) {
                        if(recDeteEB.getShort("pindex",j)==loop && recDeteEB.getByte("detector",j)==DetectorType.ECAL.getDetectorId()) {
                            detector = recDeteEB.getByte("detector",j);
                            if(energy1 >= 0 && recDeteEB.getByte("layer",j) == 1) {
                                energy1 += recDeteEB.getFloat("energy",j);
                                //sector = recDeteEB.getByte("sector",j);
                            }
                            if(energy4 >= 0 && recDeteEB.getByte("layer",j) == 4) energy4 += recDeteEB.getFloat("energy",j);
                            if(energy7 >= 0 && recDeteEB.getByte("layer",j) == 7) energy7 += recDeteEB.getFloat("energy",j);
                        }
                    }
                   
                    if(detector==DetectorType.ECAL.getDetectorId()) {
                        if(energy1>0.05 && energy4>0.0) {
                            double energy=(energy1+energy4+energy7)/0.245;
                            double p = Math.sqrt(px*px+py*py+pz*pz);
                            px = energy*px/p;
                            py = energy*py/p;
                            pz = energy*pz/p;
                            Particle part = new Particle(energy,beta, px, py, pz, vx, vy, vz, charge);
                            part.setIdx(loop);
                            part.setDet(det);
                            
                            part.setPx(px);
                            part.setPy(py);
                            part.setPz(pz);
                            parts.add(part); 
                        }
                    }
                }
//                if(pidCode==2112) {
//                    if(beta>1.0) beta =1;
//                    if(beta>0.1) {
//                        Particle part = new Particle(beta, pidCode, px, py, pz, vx, vy, vz, charge);
//                                part.setIdx(loop+1);
//                                part.setDet(det);
//                                parts.add(part);
//                    }
//                }
                if(charge!=0) {
                    double chi2pid = (double) Math.abs(recBankEB.getFloat("chi2pid", loop));
                    if(isFTEvent) chi2pid = (double) Math.abs(recBankFT.getFloat("chi2pid", loop));
                    if(chi2pid<Constants.CHI2PIDCUT) {
                        if(beta>1.0) beta =1;
                        if(beta>0.1) {
                            Particle part = new Particle(beta, pidCode, px, py, pz, vx, vy, vz, charge);
                                    part.setIdx(loop);
                                    part.setDet(det); 
                                    part.isTriggerParticle=isTriggerPart;
                                    boolean passMassCut=true;
                                    if(Math.abs(part.getPid())==321) {
                                        if(Math.abs(part.getMass()-part.getUncormass())>Res3Sigma.K.getResolution())
                                            passMassCut=false;
                                    }
                                    if(Math.abs(part.getPid())==2212) {
                                        if(Math.abs(part.getMass()-part.getUncormass())>Res3Sigma.p.getResolution())
                                            passMassCut=false;
                                    } 
                                    if(passMassCut)
                                        parts.add(part); 
                        }
                    }
                }
            }
        }
        boolean eventTriggerElectronFound=false;
        for(Particle p : parts) {
            //if((p.getDet()==3 && p.getPid()==11) ||
             //       (recBankFT==null && p.getIdx()==1 && p.getPid()==11) )
              if(p.getPid()==11 && p.isTriggerParticle) {  
                 this.setElectron(p); 
                 eventTriggerElectronFound=true;
              } 
             p.isEBParticle=true;
        } 
        if(!eventTriggerElectronFound) {
            for(Particle p : parts) {
                if(p.getDet()==3 && p.getPid()==11)
                    this.setElectron(p); 
            }
        }
        if(this.getElectron()==null) {
//            if(recBankFT!=null )
//                recBankFT.show();
//            if(recBankEB!=null )
//                recBankEB.show();
        parts.clear();
            
        }
        return parts;
    }
    public void readBanks(DataEvent de, int pass) {
        if(pass>1)
            readDecayBanks(de);
        List<Particle> parts = this.getPartsFromRec(de);
        
        if(parts!=null) {
            for(Particle part : parts) {
                if(!getDaus().containsKey(part.getIdx()))
                    getDaus().put(part.getIdx(), part);
            }
        }
    }
    
    public void readDecayBanks(DataEvent de) {
        getParts().clear();
        getDaus().clear();
        DataBank partBank = null;
        if(de.hasBank("DECAYS::Particle")) partBank = de.getBank("DECAYS::Particle");
        if(partBank == null) 
            return;
        for(int i=0; i<partBank.rows(); i++) {
            int idx = partBank.getShort("idx",i);
            int pid = partBank.getInt("pid",i);
            double e = partBank.getFloat("e",i);
            double erec = partBank.getFloat("erec",i);
            double emc = partBank.getFloat("emc",i);
            double px = partBank.getFloat("px",i);
            double py = partBank.getFloat("py",i);
            double pz = partBank.getFloat("pz",i);
            double upx = partBank.getFloat("upx",i);
            double upy = partBank.getFloat("upy",i);
            double upz = partBank.getFloat("upz",i);
            double vx = partBank.getFloat("vx",i);
            double vy = partBank.getFloat("vy",i);
            double vz = partBank.getFloat("vz",i);
            double r = partBank.getFloat("r",i);
            int charge = partBank.getByte("charge",i);
            double mass = partBank.getFloat("mass",i);
            double umass = partBank.getFloat("umass",i);
            int ndaus = partBank.getByte("ndau",i);
            int dau1idx = partBank.getShort("dau1idx",i);
            int dau2idx = partBank.getShort("dau2idx",i);
            int dau3idx = partBank.getShort("dau3idx",i); 
            
            int[] ndau = new int[ndaus];
            if(ndaus>0) {
                ndau[0] = dau1idx;
                if(ndaus>1)
                    ndau[1] = dau2idx;
                if(ndaus>2)
                    ndau[2] = dau3idx;
            }
            
            Particle part = new Particle( idx,  pid,  e,  px,  py,  pz, 
             emc,  erec,  upx,  upy, upz,
             vx,  vy,  vz, 
             charge, mass,
             ndau,  dau1idx,  dau2idx,  dau3idx);
            int det = (int) partBank.getByte("det",i);
            part.setIdx(idx);
            part.setDet(det);
            part.setRecMass(mass);
            part.setR(r); 
            part.setUncormass(umass);
            getParts().put(idx, part); 
        }
        for(Particle p : getParts().values()) {
            int[] idxes = p.getNdau();
            for(int j = 0; j<idxes.length; j++) {
                p.getDaughters().add(getParts().get(idxes[j]));
            }
        }
        
        getDaus().clear();
        getParts().forEach((key,value) -> getDaus().put(key, value));
        getDaus().forEach((key,value)->value.isEBParticle=false);
    }

    
    /**
     * @return the parts
     */
    public Map<Integer, Particle> getParts() {
        return parts;
    }

    /**
     * @param parts the parts to set
     */
    public void setParts(Map<Integer, Particle> parts) {
        this.parts = parts;
    }

    /**
     * @return the daus
     */
    public Map<Integer, Particle> getDaus() {
        return daus;
    }

    /**
     * @param daus the daus to set
     */
    public void setDaus(Map<Integer, Particle> daus) {
        this.daus = daus;
    }
    boolean passEvent = false;
    

    public void updateDausList(List<Particle> parts) {
        for(Particle part : parts) {
            if(!getDaus().containsKey(part.getIdx())) {
                getDaus().put(part.getIdx(), part);
            }
        }
        
    }

    /**
     * @return the electron
     */
    public Particle getElectron() {
        return electron;
    }

    /**
     * @param electron the electron to set
     */
    public void setElectron(Particle electron) {
        this.electron = electron;
    }

   
    public enum Target {
        LH2(0.93827),
        LD2(1.875612),
        CuSn(170.08548),
        C(11.20866095),
        CxC(22.41732),
        empty(0.0),
        UNDEF(10.0);
        private final double mass;

        Target(double mass) {
            this.mass = mass;
        }

        public double getMass() {
            return mass;
        }
    }
    
    public enum Res3Sigma {
        K(0.035),
        p(0.060);

        private final double resolution;

        Res3Sigma(double resol) {
            this.resolution = resol;
        }

        public double getResolution() {
            return resolution;
        }
    }
}
