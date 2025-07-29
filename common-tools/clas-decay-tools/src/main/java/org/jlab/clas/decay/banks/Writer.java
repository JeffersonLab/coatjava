/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.clas.decay.banks;

import java.util.List;
import org.jlab.clas.decay.analysis.Particle;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author ziegler
 */
public class Writer {
    
    public static DataBank fillBank(DataEvent event, List<Particle> partlist, Particle el,
            String bankName, int pass) {
        if (partlist == null || partlist.isEmpty()) return null;
        int bankSize = 0;
        for(Particle p : partlist) {
            bankSize++;
            int nDau = p.getDaughters().size();
            bankSize+=nDau;
            for(Particle p2: p.getDaughters()) {
                int nDau2 = p2.getDaughters().size();
                bankSize+=nDau2;
                for(Particle p3: p2.getDaughters()) {
                    bankSize+=p3.getDaughters().size();
                }
            }
        }
        if(event.hasBank(bankName)) event.removeBank(bankName);
        DataBank partBank = event.createBank(bankName, bankSize);
        int ev = event.getBank("RUN::config").getInt("event", 0); 
        int i = -1;
        for (int ii = 0; ii < partlist.size(); ii++) {
            i++;
            if(partlist.get(ii).keepSameIdx) {
                partBank.setShort("idx",i, (short) partlist.get(ii).getIdx());
            } else {
                partBank.setShort("idx",i, (short) (pass*100+ii));
            }
            Particle parent = partlist.get(ii);
            parent.setOvx(el.getOvx());
            parent.setOvy(el.getOvy());
            parent.setOvz(el.getOvz());
            fillBankValues(partBank, i, parent);
            if(!parent.getDaughters().isEmpty()) {
                String ds;
                for(int j =0; j<partlist.get(ii).getDaughters().size(); j++) {
                    ds = "dau";
                    ds+=j+1;
                    ds+="idx";
                    partBank.setShort(ds,i, (short) parent.getDaughters().get(j).getIdx());
                }
                for(int j =0; j<parent.getDaughters().size(); j++) {
                    i++;
                    Particle dau1stgen = parent.getDaughters().get(j);
                    partBank.setShort("idx",i, (short) dau1stgen.getIdx());
                    dau1stgen.setOvx(parent.getVx());
                    dau1stgen.setOvy(parent.getVy());
                    dau1stgen.setOvz(parent.getVz());
                    fillBankValues(partBank, i, dau1stgen);
                    
                    for(int jj =0; jj<dau1stgen.getDaughters().size(); jj++) {
                        ds = "dau";
                        ds+=jj+1;
                        ds+="idx";
                        partBank.setShort(ds,i, (short) dau1stgen.getDaughters().get(jj).getIdx());
                    }
                    for(int jj =0; jj<dau1stgen.getDaughters().size(); jj++) {
                        i++;
                        Particle dau2ndgen = dau1stgen.getDaughters().get(jj);
                        partBank.setShort("idx",i, (short) dau2ndgen.getIdx());
                        dau2ndgen.setOvx(dau1stgen.getVx());
                        dau2ndgen.setOvy(dau1stgen.getVy());
                        dau2ndgen.setOvz(dau1stgen.getVz());
                        fillBankValues(partBank, i, dau2ndgen);
                        
                        
                        //
                        for(int jjj =0; jjj<dau2ndgen.getDaughters().size(); jjj++) {
                        ds = "dau";
                        ds+=jjj+1;
                        ds+="idx";
                        partBank.setShort(ds,i, (short) dau2ndgen.getDaughters().get(jjj).getIdx());
                        }
                        for(int jjj =0; jjj<dau2ndgen.getDaughters().size(); jjj++) {
                            i++;
                            Particle dau3rdgen = dau2ndgen.getDaughters().get(jjj);
                            partBank.setShort("idx",i, (short) dau3rdgen.getIdx());
                            dau3rdgen.setOvx(dau2ndgen.getVx());
                            dau3rdgen.setOvy(dau2ndgen.getVy());
                            dau3rdgen.setOvz(dau2ndgen.getVz());
                            fillBankValues(partBank, i, dau3rdgen);

                        }
                    }
                }  
            }
        }
        
        //partBank.show();
        return partBank;

    }
    
    
    private static void fillBankValues(DataBank partBank, int i, Particle p) {
        partBank.setInt("pid", i, p.getPid());
        partBank.setFloat("emc",i, (float) p.massConstrE());
        partBank.setFloat("e",i, (float) p.getE());
        partBank.setFloat("erec",i, (float) p.getuE());
        partBank.setFloat("px",i, (float) p.getPx());
        partBank.setFloat("py",i, (float) p.getPy());
        partBank.setFloat("pz",i, (float) p.getPz());
        partBank.setFloat("upx",i, (float) p.getUpx());
        partBank.setFloat("upy",i, (float) p.getUpy());
        partBank.setFloat("upz",i, (float) p.getUpz());
        partBank.setFloat("vx",i, (float) p.getVx());
        partBank.setFloat("vy",i, (float) p.getVy());
        partBank.setFloat("vz",i, (float) p.getVz());
        partBank.setFloat("ovx",i, (float) p.getOvx());
        partBank.setFloat("ovy",i, (float) p.getOvy());
        partBank.setFloat("ovz",i, (float) p.getOvz());
        partBank.setFloat("r",i, (float) p.getR());
        partBank.setByte("charge", i, (byte) p.getCharge());
        partBank.setFloat("mass",i, (float) p.getMass());
        partBank.setFloat("umass",i, (float) p.getUncormass());
        partBank.setByte("ndau",i, (byte) p.getDaughters().size());
        partBank.setByte("det", i, (byte) p.getDet());
    }
}
