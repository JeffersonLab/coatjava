/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.jlab.display.ec;

import org.jlab.clas.physics.LorentzVector;
import org.jlab.clas.reco.ReconstructionEngine;
import org.jlab.groot.data.H1F;
import org.jlab.groot.ui.TGCanvas;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;

/**
 *
 * @author gavalian
 */
public class ECRECMonitor extends ReconstructionEngine {
    TGCanvas  canvas = null;
    H1F       pion   = null;
    
    public ECRECMonitor(){
        super("ECMon","gavalian","1.0");
    }
   
    @Override
    public void detectorChanged(int runNumber) {}

    @Override
    public boolean processDataEventUser(DataEvent event) {
        if(event.hasBank("REC::Particle")==true){
            DataBank bank = event.getBank("REC::Particle");
            int index1 = this.index(bank, 22, 0);
            int index2 = this.index(bank, 22, 1);
            if(index1>0&&index2>0){
                LorentzVector vL_g1 = this.getVector(bank, index1, 0.0);
                LorentzVector vL_g2 = this.getVector(bank, index2, 0.0);
                if(vL_g1.p()>1.0&&vL_g2.p()>1.0){
                    vL_g1.add(vL_g2);
                    pion.fill(vL_g1.mass());
                }
            }
        }
        return true;
    }
    
    private LorentzVector getVector(DataBank b, int index, double mass){
        LorentzVector v = new LorentzVector();
        v.setPxPyPzM(b.getFloat("px", index),
                b.getFloat("py", index),
                b.getFloat("pz", index),
                mass);
        return v;
    }
    
    private int index(DataBank b, int pid, int skip){
        int   nrows = b.rows();
        int skipped = 0;
        for(int r = 0; r < nrows; r++){
            int id = b.getInt("pid", r);
            if(id==pid){
                if(skipped==skip) return r;
                skipped++;
            }
        }
        return -1; 
    }

    @Override
    public boolean init() {
        canvas = new TGCanvas("c","EC Engine Monitoring",500,500);
        canvas.getCanvas().initTimer(5000);
        pion = new H1F("pion",120,0.005,0.6);
        return true;
    }

    public void detectorChanged(int run) {
    }

}
