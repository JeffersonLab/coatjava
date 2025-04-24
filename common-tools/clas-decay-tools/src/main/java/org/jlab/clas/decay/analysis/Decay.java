/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.decay.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jlab.clas.pdg.PDGDatabase;
import org.jlab.clas.swimtools.Swim;
import org.jlab.geom.prim.Line3D;
import org.jlab.geom.prim.Point3D;
import org.jlab.io.base.DataBank;
import org.jlab.rec.vtx.DoubleSwim;

/**
 *
 * @author ziegler
 */
/**
 * 
 * A class to represent the decay product of a list of reactions
 */
public class Decay extends Particle {
    
    private int _parPID;
    private int _dau1PID;
    private int _dau2PID;
    private int _dau3PID;
    
    private double _loMassCut;
    private double _hiMassCut;
    
    private List<Particle> _daughters;
    private List<Particle> _particles;
    
    private Map<Integer, List<Particle>> listsByPID = new HashMap<>();
    private static DataBank vertBank;
    private Swim _swim;
    
    /**
     * 
     * @param parPID the parent pid
     * @param dau1PID the pid of the first daughter in the decay
     * @param dau2PID the pid of the second daughter in the decay
     * @param dau3PID the pid of the third daughter in the decay
     * @param loMassCut the parent reconstructed invariant mass low mass bound
     * @param hiMassCut the parent reconstructed invariant mass high mass bound
     * @param daughters the parent daughters
     * @param swim swimmer
     * @param pass analysis pass: pass 1 is the reconstructed decay using fS particles; pass2 is the reconstructed decay using pass1 decay + another particle
     * @param xyBeam  beam spot
     */
    public Decay(int parPID, int dau1PID, int dau2PID, int dau3PID, double loMassCut, double hiMassCut, 
             List<Particle> daughters, Swim swim, int pass, double[] xyBeam) { 
        _swim = swim;
        _parPID = parPID;
        _dau1PID = dau1PID;
        _dau2PID = dau2PID;
        _dau3PID = dau3PID;
        _loMassCut = loMassCut;
        _hiMassCut = hiMassCut;
        _daughters = daughters;
        
        // Sorting daughters by PID
        sortByPID(daughters, dau1PID, dau2PID, dau3PID);

        List<Particle> list1 = getParticlesByPID(dau1PID);
        List<Particle> list2 = getParticlesByPID(dau2PID);
        List<Particle> list3 = getParticlesByPID(dau3PID);

        if (listsByPID != null) {
            if (!list1.isEmpty() && !list2.isEmpty()) {
                _particles = new ArrayList<>();
                if (list3.isEmpty()) {
                    processTwoParticleCombinations(list1, list2, pass, xyBeam);
                } else {
                    processThreeParticleCombinations(list1, list2, list3, pass, xyBeam);
                }
            }
        }
    }

    /**
     * 
     * @param pid the pid
     * @return  the list of Particles for a given pid
     */
    private List<Particle> getParticlesByPID(int pid) {
        List<Particle> list = new ArrayList<>();
        if (listsByPID.containsKey(pid) && pid != 0) {
            list.addAll(listsByPID.get(pid));
        }
        return list;
    }

    /**
     * 
     * @param list1 the first list of Particles
     * @param list2 the second list of Particles
     * @param pass the analysis pass
     * @param xyBeam the beam spot
     */
    private void processTwoParticleCombinations(List<Particle> list1, List<Particle> list2, int pass, 
                                                   double[] xyBeam) {
        for (Particle part1 : list1) {
            for (Particle part2 : list2) {
                Particle part = new Particle();
                if (overlaps(part1, part2)) continue; 
                
                // Handle valid particle combinations
                if (processValidCombination(part1, part2, part, pass, xyBeam)) {
                    _particles.add(part);
                }
            }
        }
    }
    /**
     * 
     * @param list1 the first list of Particles
     * @param list2 the second list of Particles
     * @param list3 the third list of Particles
     * @param pass the analysis pass
     * @param xyBeam the beam spot
     */
    private void processThreeParticleCombinations(List<Particle> list1, List<Particle> list2, List<Particle> list3, 
                                                   int pass, double[] xyBeam) {
        for (Particle part1 : list1) {
            for (Particle part2 : list2) {
                for (Particle part3 : list3) {
                    Particle part = new Particle();
                    if (overlaps(part1, part2)) continue;
                    if (overlaps(part1, part3)) continue;
                    if (overlaps(part2, part3)) continue;
                    
                    // Handle valid particle combinations
                    if (processValidCombination(part1, part2, part, part3, pass, xyBeam)) {
                        _particles.add(part);
                    }
                }
            }
        }
    }
    /**
     * 
     * @param list1 the first list of Particles
     * @param list2 the second list of Particles
     * @param list3 the third list of Particles
     * @param pass the analysis pass
     * @param xyBeam the beam spot
     * @return the validity of the combination; fills the combination particle information
     */
    private boolean processValidCombination(Particle part1, Particle part2, Particle part3, Particle part, int pass, 
                                              double[] xyBeam) {
        if (part1.getCharge() != 0 && part2.getCharge() != 0 && part3.getCharge() != 0 ) {
            
            if(Math.signum(part1.getCharge())+Math.signum(part2.getCharge())==0 
                    && Math.signum(part1.getCharge())+Math.signum(part3.getCharge())==0 ) 
                return handleTripleChargedCombination(pass, part1, part2, part3, part, xyBeam);
            if(Math.signum(part2.getCharge())+Math.signum(part1.getCharge())==0 
                    && Math.signum(part2.getCharge())+Math.signum(part3.getCharge())==0 ) 
                return handleTripleChargedCombination(pass, part2, part1, part3, part, xyBeam);    
            if(Math.signum(part3.getCharge())+Math.signum(part2.getCharge())==0 
                    && Math.signum(part3.getCharge())+Math.signum(part1.getCharge())==0 ) 
                return handleTripleChargedCombination(pass, part3, part2, part1, part, xyBeam);
            
        } else if (part1.getCharge() != 0 && part2.getCharge() != 0 && part3.getCharge() == 0) {
            return handleDoubleChargedNeutralCombination(pass, part1, part2, part3, part);
        } else if (part3.getCharge() != 0 && part2.getCharge() != 0 && part1.getCharge() == 0) {
            return handleDoubleChargedNeutralCombination(pass, part3, part2, part1, part);
        } else if (part1.getCharge() != 0 && part3.getCharge() != 0 && part2.getCharge() == 0) {
            return handleDoubleChargedNeutralCombination(pass, part1, part3, part2, part);
            
        } else if (part1.getCharge() != 0 && part2.getCharge() == 0 && part3.getCharge() == 0) {
           return handleSingleChargedDoubleNeutralCombination(part1, part2, part3,  part, xyBeam);
        } else if (part2.getCharge() != 0 && part1.getCharge() == 0 && part3.getCharge() == 0) {
           return handleSingleChargedDoubleNeutralCombination(part2, part1, part3,  part, xyBeam);
        } else if (part3.getCharge() != 0 && part1.getCharge() == 0 && part2.getCharge() == 0) {
           return handleSingleChargedDoubleNeutralCombination(part3, part1, part2,  part, xyBeam);    
           
        } else {
            if (!handleNeutralCombination(part1, part2, part3, part)) return false;
        }
        return true;
    }
    /**
     * 
     * @param list1 the first list of Particles
     * @param list2 the second list of Particles
     * @param pass the analysis pass
     * @param xyBeam the beam spot
     * @return the validity of the combination; fills the combination particle information
     */
    private boolean processValidCombination(Particle part1, Particle part2, Particle part, int pass, 
                                              double[] xyBeam) {
        if (part1.getCharge() != 0 && part2.getCharge() != 0) {
            if (!handleDoubleChargedCombination(pass, part1, part2, part)) return false;
        } else if (part1.getCharge() != 0 && part2.getCharge() == 0) {
            if (!handleSingleChargedCombination(part1, part2, part, xyBeam)) return false;
        } else if (part2.getCharge() != 0 && part1.getCharge() == 0) {
            if (!handleSingleChargedCombination(part2, part1, part, xyBeam)) return false;
        } else {
            if (!handleNeutralCombination(part1, part2, part)) return false;
        }
        return true;
    }
    /**
     * 
     * @param pass the analysis pass
     * @param part1 the first charged particle
     * @param part2 the second charged particle
     * @return the validity of the combination
     */
    private boolean checkParticleCombination(int pass, Particle part1, Particle part2) {
        
        if(PDGDatabase.isValidPid(_parPID)) {
            double pdgMass = PDGDatabase.getParticleById(_parPID).mass();
            double emc1 = part1.getE();
            double emc2 = part2.getE();
            double p1x = part1.getUpx(), p1y = part1.getUpy(), p1z = part1.getUpz();
            double p2x = part2.getUpx(), p2y = part2.getUpy(), p2z = part2.getUpz();
            double m1 = Math.sqrt(emc1 * emc1 - p1x * p1x - p1y * p1y - p1z * p1z);
            double m2 = Math.sqrt(emc2 * emc2 - p2x * p2x - p2y * p2y - p2z * p2z);
            double p1 = Math.sqrt(p1x * p1x + p1y * p1y + p1z * p1z);
            double p2 = Math.sqrt(p2x * p2x + p2y * p2y + p2z * p2z);
            double cos_p1p2 = (m1 * m1 + m2 * m2 + 2.0 * emc1 * emc2 - pdgMass * pdgMass) / (2.0 * p1 * p2);
            if(cos_p1p2<-1 || cos_p1p2>1) return false;
        }
        if(pass==1)
            if(this.checkVertex(part1,part2)==false) { 
               return false;
            } 
        if(pass>1 )
            if(this.makeVertex(part1,part2)==false) {
                return false;
            }
        return true;
    }
    /**
     * 
     * @param cpart the charged particle
     * @param npart1 the first neutral particle
     * @param npart2 the second neutral particle
     * @param part the combined particle
     * @param xyBeam the beam spot
     * @return the validity of the combination; fills the combined particle information
     */
    private boolean handleSingleChargedDoubleNeutralCombination(Particle cpart, Particle npart1, Particle npart2, Particle part,
            double[] xyBeam) {
        Particle part12 = new Particle();
        if(!handleNeutralCombination(npart1, npart2, part12)) return false;
        if (!handleSingleChargedCombination(cpart, part12, part, xyBeam)) return false;
        
        List<Particle> daus = new ArrayList<>();
        daus.add(cpart);
        daus.add(npart1);
        daus.add(npart2);
        part.setDaughters(daus);
        
        return true;        
    }
    /**
     * 
     * @param part1 the first charged particle
     * @param part2 the second charged particle
     * @param part3 the  neutral particle
     * @param part the combined particle
     * @param xyBeam the beam spot
     * @return the validity of the combination; fills the combined particle information
     */
    private boolean handleDoubleChargedNeutralCombination(int pass, Particle part1, Particle part2, Particle part3, Particle part) {
        Particle part12 = new Particle();
        if (!handleDoubleChargedCombination(pass, part1, part2, part12)) return false;
        if(!handleNeutralCombination(part12, part3, part)) return false;
        List<Particle> daus = new ArrayList<>();
        daus.add(part1);
        daus.add(part2);
        daus.add(part3);
        part.setDaughters(daus);
        
        return true;        
    }
    /**
     * 
     * @param part1 the first charged particle
     * @param part2 the second charged particle
     * @param part3 the third  charged particle
     * @param part the combined particle
     * @param xyBeam the beam spot
     * @return the validity of the combination; fills the combined particle information
     */
    private boolean handleTripleChargedCombination(int pass, Particle part1, Particle part2, Particle part3, Particle part,
            double[] xyBeam) {
        Particle part12 = new Particle();
        Particle part13 = new Particle();
        //get the 2 charged combinations
        if (!handleDoubleChargedCombination(pass, part1, part2, part12)) return false;
        if (!handleDoubleChargedCombination(pass, part1, part3, part13)) return false;
        Particle part123 = new Particle();
        Particle part132 = new Particle();
        //chose the 2-charge combination whose vertex is closer to the 3rd charged particle vertex
        boolean isOK = false;
        if(handleSingleChargedCombination(part3, part12, part123, xyBeam)) {
            if(handleSingleChargedCombination(part2, part13, part132, xyBeam)) {
                if(new Point3D(part2.getVx(),part2.getVy(),part2.getVz()).distance(part13.getVx(),part13.getVy(),part13.getVz()) <
                        new Point3D(part3.getVx(),part3.getVy(),part3.getVz()).distance(part12.getVx(),part12.getVy(),part12.getVz())) {
                    part = part132;
                    isOK=true;
                } else {
                    part = part123;
                    isOK=true;
                }
            } else {
                part = part123;
                isOK=true;
            }
        } else {
            if(handleSingleChargedCombination(part2, part13, part132, xyBeam)) {
                part = part132;    
                isOK=true;
            }
        }
        if(isOK) {
            List<Particle> daus = new ArrayList<>();
            daus.add(part1);
            daus.add(part2);
            daus.add(part3);
            part.setDaughters(daus);
        }   
        return isOK;
    }
     /**
     * 
     * @param part1 the first charged particle
     * @param part2 the second charged particle
     * @param part the combined particle
     * @param xyBeam the beam spot
     * @return the validity of the combination; fills the combined particle information
     */
    private boolean handleDoubleChargedCombination(int pass, Particle part1, Particle part2, Particle part) {
        if(!this.checkParticleCombination(pass, part1, part2) ) return false;
        if(!part.combine(part1, part2, _parPID, _loMassCut, _hiMassCut) ) return false; 
        return !(part.getR()>Constants.rCUT);
    }
     /**
     * 
     * @param chargedPart the charged particle
     * @param neutralPart the neutral particle
     * @param part the combined particle
     * @param xyBeam the beam spot
     * @return the validity of the combination; fills the combined particle information
     */
    private boolean handleSingleChargedCombination(Particle chargedPart, Particle neutralPart, Particle part, 
                                                    double[] xyBeam) {
        double[] p = chargedPart.SwimTo(neutralPart, _swim, xyBeam);
        Line3D neutrparExtrap = createExtrapolatedLine(neutralPart);
        double v1x = p[0];
        double v1y = p[1];
        double v1z = p[2];
        
        Point3D point2 = neutrparExtrap.distance(new Point3D(v1x,v1y,v1z)).origin();
        if(!combineParticles(chargedPart, neutralPart, v1x,v1y,v1z, point2.x(), point2.y(), point2.z(), part)) return false;
        List<Particle> daus = new ArrayList<>();
        daus.add(neutralPart);
        daus.add(chargedPart);
        part.setDaughters(daus);
        return true;
    }
    /**
     * 
     * @param part1 the first neutral particle
     * @param part2 the second neutral particle
     * @param part3 the third neutral particle
     * @param part the combined particle
     * @return the validity of the combination; fills the combined particle information
     */
    private boolean handleNeutralCombination(Particle part1, Particle part2, Particle part3, Particle part) {
        Line3D par1Extrap = createExtrapolatedLine(part1);
        Line3D par2Extrap = createExtrapolatedLine(part2);
        Line3D par3Extrap = createExtrapolatedLine(part3);
        Line3D parExtrap12 = par1Extrap.distance(par2Extrap);
        Line3D parExtrap = parExtrap12.distance(par3Extrap);
        double v1x = parExtrap.origin().x(), v1y = parExtrap.origin().y(), v1z = parExtrap.origin().z();
        double v2x = parExtrap.end().x(), v2y = parExtrap.end().y(), v2z = parExtrap.end().z();
       
        if(!combineParticles(part1, part2, v1x, v1y, v1z, v2x, v2y, v2z, part)) return false;
        List<Particle> daus = new ArrayList<>();
        daus.add(part1);
        daus.add(part2);
        daus.add(part3);
        part.setDaughters(daus);
        return true;
    }
    /**
     * 
     * @param part1 the first neutral particle
     * @param part2 the second neutral particle
     * @param part the combined particle
     * @return the validity of the combination; fills the combined particle information
     */
    private boolean handleNeutralCombination(Particle part1, Particle part2, Particle part) {
        Line3D par1Extrap = createExtrapolatedLine(part1);
        Line3D par2Extrap = createExtrapolatedLine(part2);
        Line3D parExtrap = par1Extrap.distance(par2Extrap);
        double v1x = parExtrap.origin().x(), v1y = parExtrap.origin().y(), v1z = parExtrap.origin().z();
        double v2x = parExtrap.end().x(), v2y = parExtrap.end().y(), v2z = parExtrap.end().z();
       
        if(!combineParticles(part1, part2, v1x, v1y, v1z, v2x, v2y, v2z, part)) return false;
        List<Particle> daus = new ArrayList<>();
        daus.add(part1);
        daus.add(part2);
        part.setDaughters(daus);
        return true;
    }
    /**
     * 
     * @param part1 the first particle
     * @param part2 the second particle
     * @param v1x the first particle vertex at the DOCA to the other particle x-component
     * @param v1y the first particle vertex at the DOCA to the other particle y-component
     * @param v1z the first particle vertex at the DOCA to the other particle z-component
     * @param v2x the second particle vertex at the DOCA to the other particle x-component
     * @param v2y the second particle vertex at the DOCA to the other particle y-component
     * @param v2z the second particle vertex at the DOCA to the other particle z-component
     * @param part the combined particle
     * @return the validity of the combination; fills the combined particle information
     */
    private boolean combineParticles(Particle part1, Particle part2, double v1x, double v1y, double v1z, 
                                     double v2x, double v2y, double v2z, Particle part) {
        int q1 = part1.getCharge(), q2 = part2.getCharge();
        double ue1 = part1.getuE(), e1 = part1.getE(), emc1 = part1.getMassConstrE();
        double p1x = part1.getPx(), p1y = part1.getPy(), p1z = part1.getPz();
        double up1x = part1.getUpx(), up1y = part1.getUpy(), up1z = part1.getUpz();
        double ue2 = part2.getuE(), e2 = part2.getE(), emc2 = part2.getMassConstrE();
        double p2x = part2.getPx(), p2y = part2.getPy(), p2z = part2.getPz();
        double up2x = part2.getUpx(), up2y = part2.getUpy(), up2z = part2.getUpz();

        return part.combine(q1, ue1, e1, emc1, v1x, v1y, v1z, p1x, p1y, p1z, up1x, up1y, up1z, 
                            q2, ue2, e2, emc2, v2x, v2y, v2z, p2x, p2y, p2z, up2x, up2y, up2z, 
                            _parPID, _loMassCut, _hiMassCut);
    }
    /**
     * 
     * @param part the reconstructed neutral particle
     * @return a line defined by the neutral particle vertex and momentum vector
     */
    private Line3D createExtrapolatedLine(Particle part) {
        Point3D P1X1 = new Point3D(part.getVx() - 100 * part.getPx(), part.getVy() - 100 * part.getPy(),
                part.getVz() - 100 * part.getPz());
        Point3D P1X2 = new Point3D(part.getVx() + 100 * part.getPx(), part.getVy() + 100 * part.getPy(),
                part.getVz() + 100 * part.getPz());
        return new Line3D(P1X1, P1X2);
    }

    /**
     * 
     * @param daughters the list of daughters = decays product of the parent particle
     * @param dau1PID the first daughter PID
     * @param dau2PID the second daughter PID
     * @param dau3PID the third daughter PID
     */
    private void sortByPID(List<Particle> daughters, int dau1PID, int dau2PID, int dau3PID) {
        listsByPID.clear();
        for(Particle par : daughters) { 
            if(par.getPid()==dau1PID || par.getPid()==dau2PID || par.getPid()==dau3PID) {
                if(listsByPID.containsKey(par.getPid())) {
                    listsByPID.get(par.getPid()).add(par); 
                } else {
                    listsByPID.put(par.getPid(), new ArrayList<Particle>());
                    listsByPID.get(par.getPid()).add(par);  
                }
            }
        }
    }

    /**
     * @return the _particles
     */
    public List<Particle> getParticles() {
        return _particles;
    }

    /**
     * @param _particles the _particles to set
     */
    public void setParticles(List<Particle> _particles) {
        this._particles = _particles;
    }

    /**
     * @return the vertBank
     */
    public DataBank getVertBank() {
        return vertBank;
    }

    /**
     * @param vertBank the vertBank to set
     */
    public static void setVertBank(DataBank vertBank) {
        Decay.vertBank = vertBank;
    }
    private DoubleSwim _ds = new DoubleSwim();
    /**
     * 
     * @param p1 the first particle
     * @param p2 the second particle
     * @return the common vertex between particles p1 and p2
     */
    private boolean makeVertex(Particle p1, Particle p2) { 
        boolean pass = false;
        double vx1,vy1,vz1,vx2,vy2,vz2;
        double px1,py1,pz1,px2,py2,pz2; 
        if(p1.getVx()==0 && p1.getVy()==0 && p1.getVz()==0) {
            vx1 = p1.getOvx();
            vy1 = p1.getOvy();
            vz1 = p1.getOvz();
            px1 = p1.getUpx();
            py1 = p1.getUpy();
            pz1 = p1.getUpz();
        } else {
            vx1 = p1.getVx();
            vy1 = p1.getVy();
            vz1 = p1.getVz();
            px1 = p1.getPx();
            py1 = p1.getPy();
            pz1 = p1.getPz();
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
        _ds.init(vx1,vy1,vz1,px1,py1,pz1, p1.getCharge(),
                 vx2,vy2,vz2,px2,py2,pz2, p2.getCharge());

        double[][] t = _ds.getDoubleSwimVertexes();
        
        if(t==null) return pass;
            
        double r = Math.sqrt((t[0][0]-t[1][0])*(t[0][0]-t[1][0])
                     +(t[0][1]-t[1][1])*(t[0][1]-t[1][1])
                     +(t[0][2]-t[1][2])*(t[0][2]-t[1][2])  );

        p1.setVx(t[0][0]);
        p1.setVy(t[0][1]);
        p1.setVz(t[0][2]);
        p1.setPx(t[0][3]);
        p1.setPy(t[0][4]);
        p1.setPz(t[0][5]);
        p2.setVx(t[1][0]);
        p2.setVy(t[1][1]);
        p2.setVz(t[1][2]);
        p2.setPx(t[1][3]);
        p2.setPy(t[1][4]);
        p2.setPz(t[1][5]);
        
        pass= true;
        
        return pass;
     }
    /**
     * 
     * @param p1 the first particle
     * @param p2 the second particle
     * @return the validity of the vertex bank
     */
    private boolean checkVertDocaBank(Particle p1, Particle p2) {
        boolean pass = false;
        if(getVertBank()==null) return pass;
        
        if(getVertBank()!=null) { 
            int nrows2 = getVertBank().rows();
            for(int loop2 = 0; loop2 < nrows2; loop2++){
                if(p1.getIdx()-1==(int) getVertBank().getShort("index1", loop2)
                        && p2.getIdx()-1==(int) getVertBank().getShort("index2", loop2)) {
                    p1.vIndex=loop2;
                    p2.vIndex=loop2;
                    
                    //double r =  (double) getVertBank().getFloat("r", loop2);
                    //vx = (double) getVertBank().getFloat("x", loop2);
                    //vy = (double) getVertBank().getFloat("y", loop2);
                    //vz = (double) getVertBank().getFloat("z", loop2);
                    double vx1 = (double) getVertBank().getFloat("x1", loop2);
                    double vy1 = (double) getVertBank().getFloat("y1", loop2);
                    double vz1 = (double) getVertBank().getFloat("z1", loop2);
                    double px1 = (double) getVertBank().getFloat("cx1", loop2);
                    double py1 = (double) getVertBank().getFloat("cy1", loop2);
                    double pz1 = (double) getVertBank().getFloat("cz1", loop2);
                    double vx2 = (double) getVertBank().getFloat("x2", loop2);
                    double vy2 = (double) getVertBank().getFloat("y2", loop2);
                    double vz2 = (double) getVertBank().getFloat("z2", loop2);
                    double px2 = (double) getVertBank().getFloat("cx2", loop2);
                    double py2 = (double) getVertBank().getFloat("cy2", loop2);
                    double pz2 = (double) getVertBank().getFloat("cz2", loop2);
                    p1.setVx(vx1);
                    p1.setVy(vy1);
                    p1.setVz(vz1);
                    p1.setPx(px1);
                    p1.setPy(py1);
                    p1.setPz(pz1);
                    p2.setVx(vx2);
                    p2.setVy(vy2);
                    p2.setVz(vz2);
                    p2.setPx(px2);
                    p2.setPy(py2);
                    p2.setPz(pz2);
                    pass=true;
                    return pass;
                }
                if(p2.getIdx()-1==(int) getVertBank().getShort("index1", loop2)
                        && p1.getIdx()-1==(int) getVertBank().getShort("index2", loop2)) {
                    p1.vIndex=loop2;
                    p2.vIndex=loop2;
                    //r =  (double) getVertBank().getFloat("r", loop2);
                    //vx = (double) getVertBank().getFloat("x", loop2);
                    //vy = (double) getVertBank().getFloat("y", loop2);
                    //vz = (double) getVertBank().getFloat("z", loop2);
                    double vx1 = (double) getVertBank().getFloat("x2", loop2);
                    double vy1 = (double) getVertBank().getFloat("y2", loop2);
                    double vz1 = (double) getVertBank().getFloat("z2", loop2);
                    double px1 = (double) getVertBank().getFloat("cx2", loop2);
                    double py1 = (double) getVertBank().getFloat("cy2", loop2);
                    double pz1 = (double) getVertBank().getFloat("cz2", loop2);
                    double vx2 = (double) getVertBank().getFloat("x1", loop2);
                    double vy2 = (double) getVertBank().getFloat("y1", loop2);
                    double vz2 = (double) getVertBank().getFloat("z1", loop2);
                    double px2 = (double) getVertBank().getFloat("cx1", loop2);
                    double py2 = (double) getVertBank().getFloat("cy1", loop2);
                    double pz2 = (double) getVertBank().getFloat("cz1", loop2);
                    p1.setVx(vx1);
                    p1.setVy(vy1);
                    p1.setVz(vz1);
                    p1.setPx(px1);
                    p1.setPy(py1);
                    p1.setPz(pz1);
                    p2.setVx(vx2);
                    p2.setVy(vy2);
                    p2.setVz(vz2);
                    p2.setPx(px2);
                    p2.setPy(py2);
                    p2.setPz(pz2);
                    //this.resetE(p1,p2);
                    pass=true; 
                    return pass;
                }
                
            }
            
        }
       
        return pass;
    }    
    
    /**
     * 
     * @param p1 the first particle
     * @param p2 the second particle
     * @return the validity of the vertex 
     */
    private boolean checkVertex(Particle p1, Particle p2) {
        boolean pass = this.checkVertDocaBank(p1, p2);
        if(getVertBank()==null) 
            pass = this.makeVertex(p1, p2);
        
        return pass;
    }

    /**
     * 
     * @param part01 the first particle
     * @param part02 the second particle
     * @return a check for repeated usage of the same particle(s) in the combination
     */
    private boolean overlaps(Particle part01, Particle part02) {
        boolean overlaps = false;
        
        List<Particle> part1 = new ArrayList<>();
        List<Particle> part2 = new ArrayList<>();
        part1.add(part01);
        part2.add(part02);
        
        for(int i1 = 0; i1<part01.getDaughters().size(); i1++) {
            part1.add(part01.getDaughters().get(i1)); 
            for(int ii1 = 0; ii1<part01.getDaughters().get(i1).getDaughters().size(); ii1++) {
                part1.add(part01.getDaughters().get(i1).getDaughters().get(ii1));
                for(int iii1 = 0; iii1<part01.getDaughters().get(i1).getDaughters().get(ii1).getDaughters().size(); iii1++) {
                    part1.add(part01.getDaughters().get(i1).getDaughters().get(ii1).getDaughters().get(iii1));
                }
            }
        }
        for(int i2 = 0; i2<part02.getDaughters().size(); i2++) {
            part2.add(part02.getDaughters().get(i2));
            for(int ii2 = 0; ii2<part02.getDaughters().get(i2).getDaughters().size(); ii2++) {
                part2.add(part02.getDaughters().get(i2).getDaughters().get(ii2));
                for(int iii2 = 0; iii2<part02.getDaughters().get(i2).getDaughters().get(ii2).getDaughters().size(); iii2++) {
                    part2.add(part02.getDaughters().get(i2).getDaughters().get(ii2).getDaughters().get(iii2));
                }
            }
        }
        for(Particle p1 : part1) {
            for(Particle p2 : part2) { 
                if(p1.getIdx()==p2.getIdx()) {
                    overlaps = true;
                }
            }
        }
        
        return overlaps;
    }
   
    private void resetE(Particle p1, Particle p2) {
        double beta1 = p1.getBeta();
        double p_1 = Math.sqrt(p1.getPx()*p1.getPx()+p1.getPy()*p1.getPy()+p1.getPz()*p1.getPz());
        double m_1 = p1.getMass();
        p1.setUncormass(Math.sqrt((p_1/beta1)*(p_1/beta1)-p_1*p_1));
        double mu_1 = p1.getUncormass();
        double beta2 = p2.getBeta();
        double p_2 = Math.sqrt(p2.getPx()*p2.getPx()+p2.getPy()*p2.getPy()+p2.getPz()*p2.getPz());
        p2.setUncormass(Math.sqrt((p_2/beta2)*(p_2/beta2)-p_2*p_2));
        double m_2 = p2.getMass();
        double mu_2 = p2.getUncormass();
        double E1 = Math.sqrt(p_1*p_1+m_1*m_1);
        double E2 = Math.sqrt(p_2*p_2+m_2*m_2);
        double uE1 = Math.sqrt(p_1*p_1+mu_1*mu_1);
        double uE2 = Math.sqrt(p_2*p_2+mu_2*mu_2);
        p1.setE(E1);
        p2.setE(E2);
        p1.setMassConstrE(E1);
        p2.setMassConstrE(E2);
        p1.setuE(uE1);
        p2.setuE(uE2); 
    }
    
}
