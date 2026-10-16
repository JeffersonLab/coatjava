/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/
package org.jlab.clas.decay.analysis;

import java.util.ArrayList;
import java.util.List;
import org.jlab.clas.swimtools.Swim;
import cnuphys.swim.SwimTrajectory;
import org.jlab.clas.pdg.PDGDatabase;
import org.jlab.geom.prim.Line3D;

import org.jlab.geom.prim.Point3D;
import org.jlab.geom.prim.Vector3D;
import org.jlab.rec.vtx.DoubleSwim;
/**
 *
 * @author ziegler
 */
public class Particle {

    public boolean isEBParticle;
    private int[] _ndau;
    public boolean keepSameIdx=false;
    public boolean isTriggerParticle=false;
    /**
     * 
     * @param part1 the particle
     */
    public Particle(Particle part1) {
        this._E=part1._E;
        this._Idx = part1._Idx;
        this._beta = part1._beta;
        this._calcBeta = part1._calcBeta;
        this._charge=part1._charge;
        this._det = part1._det;
        this._daughters = part1._daughters;
        this._mass = part1._mass;
        this._massConstrE = part1._massConstrE;
        this._mass = part1._mass;
        this._umass = part1._umass;
        this._uncormass = part1._uncormass;
        this._p = part1._p;
        this._pid = part1._pid;
        this._pt = part1._pt;
        this._px = part1._px;
        this._py = part1._py;
        this._pz = part1._pz;
        this._upx = part1._upx;
        this._upy = part1._upy;
        this._upz = part1._upz;
        this._r = part1._r;
        this._recmass = part1._recmass;
        this._status = part1._status;
        this._uE = part1._uE;
        this._massConstrE = part1._massConstrE;
        this._ovx = part1._ovx;
        this._ovy = part1._ovy;
        this._ovz = part1._ovz;
        this._vx = part1._vx;
        this._vy = part1._vy;
        this._vz = part1._vz;
    }
    /**
     * 
     * @return PDF mass-constrained energy
     */
    public double massConstrE() {
        double e = 0;
        if(this.hasDaughters()==false) {
            return this.getMassConstrE();
        } else {
            
            for(int i = 0; i<this.getDaughters().size(); i++) {
                e+=this.getDaughters().get(i).getMassConstrE();
            }
            
            if(PDGDatabase.isValidPid(this.getPid())) {
                double cmass = PDGDatabase.getParticleById(this.getPid()).mass();
                e = Math.sqrt(this.getP()*this.getP()+cmass*cmass);
            }
        }
        return e;
    }
    /**
     * @return the recParticle
     */
    public org.jlab.clas.physics.Particle getRecParticle() {
        return recParticle;
    }

    /**
     * @param recParticle the recParticle to set
     */
    public void setRecParticle(org.jlab.clas.physics.Particle recParticle) {
        this.recParticle = recParticle;
    }
    
    public boolean isUsed = false;
    private double _mass ;
    private double _recmass ;
    private double _E;
    private double _massConstrE;
    private double _px;
    private double _py;
    private double _pz;
    private double _pxcm;
    private double _pycm;
    private double _pzcm;
    private double _vx;
    private double _vy;
    private double _vz;
    private double _r; // distance of closest approach between 2 track helices
    private double _p;
    private double _pt;
    private double _uncormass;
    private double _umass ;
    private double _uE;
    private double _upx;
    private double _upy;
    private double _upz;
    private double _ovx;
    private double _ovy;
    private double _ovz;
    
    private double _beta;
    private double _calcBeta;
    private int _charge;
    private int _status;
    private int _Idx=-1;
    private int _pid;
    public int vIndex=-1; 
    
    private int _det = -1;
    
    private List<Particle> _daughters = new ArrayList<Particle>();
    private org.jlab.clas.physics.Particle recParticle ;
    
    public Particle() {
        _daughters = new ArrayList<Particle>();
    }

    /**
     * @return the _ndau
     */
    public int[] getNdau() {
        return _ndau;
    }

    /**
     * @param _ndau the _ndau to set
     */
    public void setNdau(int[] _ndau) {
        this._ndau = _ndau;
    }
    /**
     * 
     * @param beta the beta from REC bank
     * @param pid the pid
     * @param upx the momentum before any vertex correction x-component
     * @param upy the momentum before any vertex correction y-component
     * @param upz the momentum before any vertex correction z-component
     * @param vx the vertex before any vertex correction x-component
     * @param vy the vertex before any vertex correction y-component
     * @param vz the vertex before any vertex correction z-component
     * @param charge the track charge
     */
    private void setEBParticle(double beta, int pid, double upx, double upy, double upz,
            double vx, double vy, double vz, int charge) {
        _daughters = new ArrayList<Particle>();
        this._beta = beta;
        this._ovx = vx;
        this._ovy = vy;
        this._ovz = vz;
        this._upx = upx;
        this._upy = upy;
        this._upz = upz;
        this._pid = pid; 
        this._p = Math.sqrt(upx*upx+upy*upy+upz*upz);
        this._pt = Math.sqrt(upx*upx+upy*upy); 
        double cmass = PDGDatabase.getParticleById(Math.abs(pid)).mass();
        double mass = Math.sqrt((this._p/beta)*(this._p/beta)-this._p*this._p);
        double calcBeta = this._p/Math.sqrt(this._p*this._p+cmass*cmass);
        this._charge = charge;
        this._mass = cmass;
        this._umass = mass;
        this._calcBeta = calcBeta;
        this._beta = beta;
        double E = Math.sqrt(this._p*this._p+mass*mass);
        double cE = Math.sqrt(this._p*this._p+cmass*cmass);
        this._uE =E;
        this._E = cE;  // use final states mass-constrained energy
        this._massConstrE = cE;
        this.isEBParticle = true; 
    }
    /**
     * 
     * @param energy the neutral energy
     * @param beta the beta from REC bank
     * @param upx the momentum before any vertex correction x-component
     * @param upy the momentum before any vertex correction y-component
     * @param upz the momentum before any vertex correction z-component
     * @param vx the vertex before any vertex correction x-component
     * @param vy the vertex before any vertex correction y-component
     * @param vz the vertex before any vertex correction z-component
     * @param charge the track charge
     */
    private void setPhoton(double energy, double beta, double upx, double upy, double upz,
            double vx, double vy, double vz, int charge) {
        _daughters = new ArrayList<Particle>();
        this._beta =beta;
        this._calcBeta =1;
        this._ovx = vx;
        this._ovy = vy;
        this._ovz = vz;
        this._upx = upx;
        this._upy = upy;
        this._upz = upz;
        this._pid = 22;
        this._p = Math.sqrt(upx*upx+upy*upy+upz*upz);
        this._pt = Math.sqrt(upx*upx+upy*upy);
        double cmass = PDGDatabase.getParticleById(22).mass();
        
        this._charge = charge;
        this._mass = cmass;
        double m2 = energy*energy-this._p*this._p;
        if(m2<0) m2 = 0;
        this._umass = Math.sqrt(m2);
        this._E =energy;
        this._massConstrE = Math.sqrt(cmass*cmass+this._p*this._p);
        this.isEBParticle = true;
    }
    /**
     * 
     * @param beta the beta from REC bank
     * @param pid the pid
     * @param upx the momentum before any vertex correction x-component
     * @param upy the momentum before any vertex correction y-component
     * @param upz the momentum before any vertex correction z-component
     * @param vx the vertex before any vertex correction x-component
     * @param vy the vertex before any vertex correction y-component
     * @param vz the vertex before any vertex correction z-component
     * @param charge the track charge
     */
    public Particle(double beta, int pid, double upx, double upy, double upz,
            double vx, double vy, double vz, int charge) {
        this.setEBParticle(beta, pid, upx, upy, upz, vx, vy, vz, charge);
    }    
    /**
     * 
     * @param energy the neutral energy
     * @param beta the beta from REC bank
     * @param upx the momentum before any vertex correction x-component
     * @param upy the momentum before any vertex correction y-component
     * @param upz the momentum before any vertex correction z-component
     * @param vx the vertex before any vertex correction x-component
     * @param vy the vertex before any vertex correction y-component
     * @param vz the vertex before any vertex correction z-component
     * @param charge the track charge
     */
    public Particle(double energy, double beta, double upx, double upy, double upz,
            double vx, double vy, double vz, int charge) {
        this.setPhoton(energy, beta, upx, upy, upz, vx, vy, vz, charge);
    }
   

    /**
     * @return the _uE
     */
    public double getuE() {
        return _uE;
    }

    /**
     * @param _uE the _uE to set
     */
    public void setuE(double _uE) {
        this._uE = _uE;
    }

    /**
     * @return the _upx
     */
    public double getUpx() {
        return _upx;
    }

    /**
     * @param _upx the _upx to set
     */
    public void setUpx(double _upx) {
        this._upx = _upx;
    }

    /**
     * @return the _upy
     */
    public double getUpy() {
        return _upy;
    }

    /**
     * @param _upy the _upy to set
     */
    public void setUpy(double _upy) {
        this._upy = _upy;
    }

    /**
     * @return the _upz
     */
    public double getUpz() {
        return _upz;
    }

    /**
     * @param _upz the _upz to set
     */
    public void setUpz(double _upz) {
        this._upz = _upz;
    }

    /**
     * @return the _uvx
     */
    public double getOvx() {
        return _ovx;
    }

    /**
     * @param _uvx the _uvx to set
     */
    public void setOvx(double _uvx) {
        this._ovx = _uvx;
    }

    /**
     * @return the _uvy
     */
    public double getOvy() {
        return _ovy;
    }

    /**
     * @param _uvy the _uvy to set
     */
    public void setOvy(double _uvy) {
        this._ovy = _uvy;
    }

    /**
     * @return the _uvz
     */
    public double getOvz() {
        return _ovz;
    }

    /**
     * @param _uvz the _uvz to set
     */
    public void setOvz(double _uvz) {
        this._ovz = _uvz;
    }

    /**
     * @return the _det
     */
    public int getDet() {
        return _det;
    }

    /**
     * @param _det the _det to set
     */
    public void setDet(int _det) {
        this._det = _det;
    }

    public Particle(int idx, int pid, double e, double px, double py, double pz, 
            double emc, double erec, double upx, double upy, double upz,
            double vx, double vy, double vz, 
            int charge,double mass,
            int[] ndau, int dau1idx, int dau2idx, int dau3idx) {
        this._Idx = idx;
        this._ndau = ndau;
        this._pid = pid;
        this._massConstrE = emc;
        this._E = e;
        this._uE = erec;
        this._px = px;
        this._py = py;
        this._pz = pz;
        this._vx = vx;
        this._vy = vy;
        this._vz = vz;
        this._upx = upx;
        this._upy = upy;
        this._upz = upz;
        this._charge = charge;
        this._mass = mass;
        //this._daughters = new ArrayList<Particle>();
        this.setP(Math.sqrt(_px*_px+_py*_py+_pz*_pz));
        this.setPt(Math.sqrt(_px*_px+_py*_py));
        this._daughters = new ArrayList<>(ndau.length);
//        for(int i =0; i< ndau; i++) {
//            this._daughters.add(new Particle());
//        }
//        if(ndau>1) {
//            this._daughters.get(0).setIdx(dau1idx);
//            this._daughters.get(1).setIdx(dau2idx);
//            if(ndau==3) {
//                this._daughters.get(2).setIdx(dau3idx);
//            } 
//        }
    }
    
    public boolean combine(Particle part1, Particle part2, int pid) {
        double mLo= Double.NEGATIVE_INFINITY;
        double mHi= Double.POSITIVE_INFINITY;
        
        return this.combine(part1, part2, pid, mLo, mHi);
    }
    
    public boolean combine(Particle part1, Particle part2, int pid, double mLo, double mHi) {
        
        int q1 = part1.getCharge();
        double ue1 = part1.getuE();
        double e1 = part1.getE();
        double emc1 = part1.getMassConstrE();
        
        double v1x = part1.getVx();
        double v1y = part1.getVy();
        double v1z = part1.getVz();
        double p1x = part1.getPx();
        double p1y = part1.getPy();
        double p1z = part1.getPz();
        double up1x = part1.getUpx();
        double up1y = part1.getUpy();
        double up1z = part1.getUpz();
        int q2 = part2.getCharge();
        double ue2 = part2.getuE();
        double e2 = part2.getE();
        double emc2 = part2.getMassConstrE();
        double v2x = part2.getVx();
        double v2y = part2.getVy();
        double v2z = part2.getVz();
        double p2x = part2.getPx();
        double p2y = part2.getPy();
        double p2z = part2.getPz();
        double up2x = part2.getUpx();
        double up2y = part2.getUpy();
        double up2z = part2.getUpz();
        
        boolean pass = this.combine(q1, ue1, e1, emc1, v1x, v1y, v1z, p1x, p1y, p1z, up1x, up1y, up1z, 
                q2, ue2, e2, emc2, v2x, v2y, v2z, p2x, p2y, p2z, up2x, up2y, up2z, pid, mLo, mHi);
        this._daughters.add(part1);
        this._daughters.add(part2);
        //System.out.println("check"); System.out.println(part1.toString()); System.out.println(part2.toString());
        
        return pass;
    }
    
    public boolean combine(int q1, double ue1, double e1, double emc1, double v1x, double v1y, double v1z, double p1x, double p1y, double p1z, double up1x, double up1y, double up1z,
            int q2, double ue2, double e2, double emc2, double v2x, double v2y, double v2z, double p2x, double p2y, double p2z, double up2x, double up2y, double up2z, 
            int pid, double mLo, double mHi) {
        double Vx = 0.5*(v1x+v2x);
        double Vy = 0.5*(v1y+v2y);
        double Vz = 0.5*(v1z+v2z);
        
        double dx = v1x-v2x;
        double dy = v1y-v2y;
        double dz = v1z-v2z;
        
        double r = Math.sqrt(dx*dx+dy*dy+dz*dz);
        
        double Px = p1x+p2x;
        double Py = p1y+p2y;
        double Pz = p1z+p2z;
        
        double uPx = up1x+up2x;
        double uPy = up1y+up2y;
        double uPz = up1z+up2z;
             
        double mass = this.calcMass(e1, p1x, p1y, p1z, e2, p2x, p2y, p2z);
        double uncormass = this.calcMass(e1, up1x, up1y, up1z, e2, up2x, up2y, up2z);
        //double unconstmass = this.calcMass(ue1, p1x, p1y, p1z, ue2, p2x, p2y, p2z);
        if(mass < mLo || mass > mHi) 
            return false;
        
        this.setPid(pid);
        this.setCharge(q1+q2);
        this.setE(e1+e2);
        this.setuE(ue1+ue2);
        this.setMassConstrE(emc1+emc2);
        this.setUncormass(uncormass);
        this.setRecMass(mass);
        this.setMass(mass);
        this.setR(r);
        this.setVx(Vx);
        this.setVy(Vy);
        this.setVz(Vz);
        this.setPx(Px);
        this.setPy(Py);
        this.setPz(Pz);
        this.setUpx(uPx);
        this.setUpy(uPy);
        this.setUpz(uPz);
        
        
        return true;
    }
    
    
    public double getECM() {
        double m = this.getMass();
        double px = this.getPxcm();
        double py = this.getPycm();
        double pz = this.getPzcm();
        
        return Math.sqrt(m*m+pz*px+py*py+pz*pz);
    }
    
    /**
     * @return the _Idx
     */
    public int getIdx() {
        return _Idx;
    }

    /**
     * @param _Idx the _Idx to set
     */
    public void setIdx(int _Idx) {
        this._Idx = _Idx;
    }
    /**
     * @return the _mass
     */
    public double getMass() {
        return _mass;
    }

    /**
     * @param _mass the _mass to set
     */
    public void setMass(double _mass) {
        this._mass = _mass;
    }

    /**
     * @return the _uncormass
     */
    public double getUncormass() {
        return _umass;
    }

    /**
     * @param _uncormass the _uncormass to set
     */
    public void setUncormass(double _uncormass) {
        this._umass = _uncormass;
    }
    
    /**
     * @return the _mass
     */
    public double getRecMass() {
        return _recmass;
    }

    /**
     * @param _mass the _mass to set
     */
    public void setRecMass(double _mass) {
        this._recmass = _mass;
    }

    /**
     * @return the _pid
     */
    public int getPid() {
        return _pid;
    }

    /**
     * @param _pid the _pid to set
     */
    public void setPid(int _pid) {
        this._pid = _pid;
    }

    /**
     * @return the _E
     */
    public double getE() {
        return _E;
    }

    /**
     * @param _E the _E to set
     */
    public void setE(double _E) {
        this._E = _E;
    }

    /**
     * @return the _massConstrE
     */
    public double getMassConstrE() {
        return _massConstrE;
    }

    /**
     * @param _massConstrE the _massConstrE to set
     */
    public void setMassConstrE(double _massConstrE) {
        this._massConstrE = _massConstrE;
    }

    /**
     * @return the _px
     */
    public double getPx() {
        return _px;
    }

    /**
     * @param _px the _px to set
     */
    public void setPx(double _px) {
        this._px = _px;
    }

    /**
     * @return the _py
     */
    public double getPy() {
        return _py;
    }

    /**
     * @param _py the _py to set
     */
    public void setPy(double _py) {
        this._py = _py;
    }

    /**
     * @return the _pz
     */
    public double getPz() {
        return _pz;
    }

    /**
     * @param _pz the _pz to set
     */
    public void setPz(double _pz) {
        this._pz = _pz;
    }

    /**
     * @return the _vx
     */
    public double getVx() {
        return _vx;
    }

    /**
     * @param _vx the _vx to set
     */
    public void setVx(double _vx) {
        this._vx = _vx;
    }

    /**
     * @return the _vy
     */
    public double getVy() {
        return _vy;
    }

    /**
     * @param _vy the _vy to set
     */
    public void setVy(double _vy) {
        this._vy = _vy;
    }

    /**
     * @return the _vz
     */
    public double getVz() {
        return _vz;
    }

    /**
     * @param _vz the _vz to set
     */
    public void setVz(double _vz) {
        this._vz = _vz;
    }

    /**
     * @return the _r
     */
    public double getR() {
        return _r;
    }

    /**
     * @param _r the _r to set
     */
    public void setR(double _r) {
        this._r = _r;
    }

    /**
     * @return the _p
     */
    public double getP() {
        return Math.sqrt(_px*_px+_py*_py+_pz*_pz);
    }

    /**
     * @param _p the _p to set
     */
    public void setP(double _p) {
        this._p = _p;
    }

    /**
     * @return the _pt
     */
    public double getPt() {
        return _pt;
    }

    /**
     * @param _pt the _pt to set
     */
    public void setPt(double _pt) {
        this._pt = _pt;
    }

    /**
     * @return the _pxcm
     */
    public double getPxcm() {
        return _pxcm;
    }

    /**
     * @param _pxcm the _pxcm to set
     */
    public void setPxcm(double _pxcm) {
        this._pxcm = _pxcm;
    }

    /**
     * @return the _pycm
     */
    public double getPycm() {
        return _pycm;
    }

    /**
     * @param _pycm the _pycm to set
     */
    public void setPycm(double _pycm) {
        this._pycm = _pycm;
    }

    /**
     * @return the _pzcm
     */
    public double getPzcm() {
        return _pzcm;
    }

    /**
     * @param _pzcm the _pzcm to set
     */
    public void setPzcm(double _pzcm) {
        this._pzcm = _pzcm;
    }

    /**
     * @return the _beta
     */
    public double getBeta() {
        return _beta;
    }

    /**
     * @param _beta the _beta to set
     */
    public void setBeta(double _beta) {
        this._beta = _beta;
    }

    /**
     * @return the _calcBeta
     */
    public double getCalcBeta() {
        return _calcBeta;
    }

    /**
     * @param _calcBeta the _calcBeta to set
     */
    public void setCalcBeta(double _calcBeta) {
        this._calcBeta = _calcBeta;
    }

    /**
     * @return the _charge
     */
    public int getCharge() {
        return _charge;
    }

    /**
     * @param _charge the _charge to set
     */
    public void setCharge(int _charge) {
        this._charge = _charge;
    }

    
    /**
     * @return the _status
     */
    public int getStatus() {
        return _status;
    }

    /**
     * @param _status the _status to set
     */
    public void setStatus(int _status) {
        this._status = _status;
    }

    /**
     * @return the _daughters
     */
    public List<Particle> getDaughters() {
        return _daughters;
    }

    /**
     * @param _daughters the _daughters to set
     */
    public void setDaughters(List<Particle> _daughters) {
        this._daughters = _daughters;
    }
    
    public boolean isFT() {
        return Math.abs(this.getStatus())/1000==1;
    }

    public boolean isFD() {
        return Math.abs(this.getStatus())/1000==2;
    }

    public boolean isCD() {
        return Math.abs(this.getStatus())/1000==4;
    }

    private double calcMass(double e1, double px1, double py1, double pz1,
            double e2, double px2, double py2, double pz2) {
       
        double Px = px1+px2;
        double Py = py1+py2;
        double Pz = pz1+pz2;
        
        double E = e1+e2;
        if((E*E - Px*Px-Py*Py-Pz*Pz)<0) return 0;
            //System.err.println("error calculating mass "+(float)e1+","+(float)px1+","+(float)py1+","+(float)pz1+"; "
            //+(float)e2+","+(float)px2+","+(float)py2+","+(float)pz2+". ");
        
        return Math.sqrt(E*E - Px*Px-Py*Py-Pz*Pz);
    }
    private double calcMass(Particle part1, Particle part2) {
        double e1 = part1.getE();
        double px1 = part1.getPx();
        double py1 = part1.getPy();
        double pz1 = part1.getPz();
        double e2 = part2.getE();
        double px2 = part2.getPx();
        double py2 = part2.getPy();
        double pz2 = part2.getPz();
        return this.calcMass(e1, px1, py1, pz1, e2, px2, py2, pz2); 
    }
    
    private double calcUnconstrainedMass(Particle part1, Particle part2) {
        double e1 = part1.getuE();
        double px1 = part1.getPx();
        double py1 = part1.getPy();
        double pz1 = part1.getPz();
        double e2 = part2.getuE();
        double px2 = part2.getPx();
        double py2 = part2.getPy();
        double pz2 = part2.getPz();
        
        return this.calcMass(e1, px1, py1, pz1, e2, px2, py2, pz2);
    }
    
    private double calcUncorrMass(Particle part1, Particle part2) { //momentum not corrected at the vertex
        double e1 = part1.getE();
        double px1 = part1.getUpx();
        double py1 = part1.getUpy();
        double pz1 = part1.getUpz();
        double e2 = part2.getE();
        double px2 = part2.getUpx();
        double py2 = part2.getUpy();
        double pz2 = part2.getUpz();
        
        return this.calcMass(e1, px1, py1, pz1, e2, px2, py2, pz2);
    }
    public double buffer = 10.0; //cm
    //private Swim swim;
    private DoubleSwim _ds = new DoubleSwim();
    double[] SwimTo(Particle p2, Swim swim, double[] xyBeam) {
        double vx1,vy1,vz1,vx2,vy2,vz2;
        double px1,py1,pz1,px2,py2,pz2;
        int q = this.getCharge(); 
        if(this.getVx()==0 && this.getVy()==0 && this.getVz()==0) {
            vx1 = this.getOvx();
            vy1 = this.getOvy();
            vz1 = this.getOvz();
            px1 = this.getUpx();
            py1 = this.getUpy();
            pz1 = this.getUpz();
        } else {
            vx1 = this.getVx();
            vy1 = this.getVy();
            vz1 = this.getVz();
            px1 = this.getPx();
            py1 = this.getPy();
            pz1 = this.getPz();
        }
        if(p2.getVx()==0 && p2.getVy()==0 && p2.getVz()==0) {
            vx2 = p2.getOvx();
            vy2 = p2.getOvy();
            vz2 = p2.getOvz();
            px2 = p2.getUpx();
            py2 = p2.getUpy();
            pz2 = p2.getUpz();
        } else {
            vx2 = p2.getVx();
            vy2 = p2.getVy();
            vz2 = p2.getVz();
            px2 = p2.getPx();
            py2 = p2.getPy();
            pz2 = p2.getPz();
        }
        swim.SetSwimParameters(vx1,vy1,vz1, px1, py1, pz1, _charge);
        double[] t = new double[] {vx1,vy1,vz1, px1, py1, pz1};
        //for(int j =0; j<6; j++) System.out.println("t["+j+"]="+t[j]);
         if(vz2+buffer>vz1) {
            double[] p = swim.SwimToZ(vz2+buffer, 1);
            if(p!=null) {
                Line3D l = new Line3D(new Point3D(vx2,vy2,vz2), new Vector3D(-px2,-py2,-pz2).asUnit());
                swim.SetSwimParameters(p[0],p[1],p[2],-p[3],-p[4], -p[5], -_charge); //swimbackwards
                double[] ts = swim.SwimToLine(l);
                if(ts!=null) {
                    t[0] = ts[0];
                    t[1] = ts[1];
                    t[2] = ts[2];
                    t[3] = -ts[3];
                    t[4] = -ts[4];
                    t[5] = -ts[5];
                }
            }
        }
        this.setVx(t[0]);
        this.setVy(t[1]);
        this.setVz(t[2]);
        this.setPx(t[3]);
        this.setPy(t[4]);
        this.setPz(t[5]);
        
        
        return new double[] {t[0],t[1],t[2], t[3], t[4], t[5]};
    }  
    
    double[] SwimTo(int q, double vxch, double vych, double vzch, double pxch, double pych, double pzch,
            double vxvo, double vyvo, double vzvo, double pxvo, double pyvo, double pzvo, Swim swim) {
        double buffer = 2.0;
        if(vzvo-buffer>vzch) {
            swim.SetSwimParameters(vxch, vych, vzch, 
                pxch, pych, pzch, q);
        } else {
            swim.SetSwimParameters(vxch, vych, vzch, 
                -pxch, -pych, -pzch, -q);
            double[] tr1 = swim.SwimToPlaneBoundary((vzvo-buffer),new Vector3D(0,0,1), -1);
            
            swim.SetSwimParameters(tr1[0], tr1[1], tr1[2], -tr1[3], -tr1[4], -tr1[5], q);
        }
        
        double p = Math.sqrt(pxvo*pxvo+pyvo*pyvo+pzvo*pzvo);
        double theta = Math.toDegrees(Math.acos(pzvo/p));
        double phi = Math.toDegrees(Math.atan2(pyvo, pxvo));
        
        double step = 0.02; //cm
        double vxt = vxvo/100.0; //swimTrajctory arg in m
        double vyt = vyvo/100.0;
        double vzt = vzvo/100.0;
        
        
        int nstep = (int)(Math.sqrt((vxvo-vxch)*(vxvo-vxch)+(vyvo-vych)*(vyvo-vych)+(vzvo-vzch)*(vzvo-vzch))/step)+2;
        
        SwimTrajectory tr = new SwimTrajectory(q,vxt-nstep*pxvo/p*step/100.0,vyt-nstep*pyvo/p*step/100.0,vzt-nstep*pzvo/p*step/100.0,p,theta,phi,nstep+2);
        for(int i =nstep-1; i>-1; i--) {
            tr.add(vxt-i*pxvo/p*step/100.0,vyt-i*pyvo/p*step/100.0,vzt-i*pzvo/p*step/100.0, p, theta, phi); 
        }
        double[] pars = swim.SwimToDCA(tr);//ch pars
        
        return pars;
    }
    public boolean hasDaughters() {
        boolean d = false;
        if(this.getDaughters()!=null && !this.getDaughters().isEmpty())
            d = true;
        return d;
    }
    private String printVP(Particle p) {
        String s = "\n Dec X ";
        s+=new Point3D(p.getVx(), p.getVy(), p.getVz()).toString()+ "\n Prod X "+
                new Point3D(p.getOvx(), p.getOvy(), p.getOvz()).toString()
                +"\n P "+new Point3D(p.getPx(), p.getPy(), p.getPz()).toString()
                +"\n uP "+new Point3D(p.getUpx(), p.getUpy(), p.getUpz()).toString();
        return s;
    }
    @Override
    public String toString() {
        String s="";
        s+=this.getIdx()+" pid "+this.getPid()+printVP(this)+"\n mass "+this.getMass()
                +"\n E "+this.getE()+" uE "+this.getuE() +"mc E "+this.getMassConstrE()
                +"\n ndau "+this.getDaughters().size();
        if(this.hasDaughters()) {
            for(Particle p : this.getDaughters()) {
                if(p!=null) {
                    s+="\ndaughter "+p.getIdx()+" pid "+p.getPid()+printVP(p)+"\n mass "+p.getMass()+"\n E "+this.getE()+" uE "+this.getuE()+"mc E "+this.getMassConstrE();
                    if(p.hasDaughters()) {
                        for(int i = 0; i<p.getDaughters().size(); i++) {
                            Particle p2 = p.getDaughters().get(i);
                            s+="\n granddaughter "+p2.getIdx()+" pid "+p2.getPid()+printVP(p2)+"\n mass "+p2.getMass()+"\n E "+this.getE()+" uE "+this.getuE()+"mc E "+this.getMassConstrE();
                        }
                    }
                }
            }
        } 
        return s;
    }

  
}
